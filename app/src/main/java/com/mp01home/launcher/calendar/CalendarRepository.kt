package com.mp01home.launcher.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.mp01home.launcher.data.db.CachedEvent
import com.mp01home.launcher.data.db.CachedEventDao
import com.mp01home.launcher.data.settings.SettingsRepository
import com.mp01home.launcher.sync.SyncReason
import com.mp01home.launcher.sync.SyncResult
import com.mp01home.launcher.sync.SyncStatusRepository
import com.mp01home.launcher.sync.Syncable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** A calendar the user can pick in settings (CAL-7). */
data class CalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val visible: Boolean
)

/**
 * CAL-1/2/5/6/7, PLAN-3/4: reads event instances from [CalendarContract] (all device accounts),
 * caches them in Room ([CachedEventDao]) for the UI (EINK-7), and keeps the cache fresh from:
 * - the periodic / screen-on sync ([Syncable]),
 * - a [ContentObserver] on the calendar provider (debounced),
 * - changes to the calendar selection (CAL-7) and the leave-by setting (CAL-6).
 *
 * Window cached: start of today .. start of today + [WINDOW_DAYS] (covers the week page).
 */
class CalendarRepository(
    private val context: Context,
    private val daoProvider: () -> CachedEventDao,
    private val settingsRepository: SettingsRepository,
    private val syncStatusRepository: SyncStatusRepository,
    val prefsRepository: CalendarPrefsRepository
) : Syncable {

    override val syncKey: String = SYNC_KEY

    private val appContext = context.applicationContext
    private val dao: CachedEventDao get() = daoProvider()
    private val refreshMutex = Mutex()
    private val leaveByScheduler = LeaveByScheduler(appContext)

    private var scope: CoroutineScope? = null
    private var observer: ContentObserver? = null
    private var debounceJob: Job? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Called once from the AppContainer calendar block. Cheap: only launches coroutines.
     * Refreshes when the calendar selection or leave-by prefs change (first emission included,
     * so the cache is fresh after process start).
     */
    fun start(appScope: CoroutineScope) {
        scope = appScope
        appScope.launch {
            settingsRepository.settings
                .map { it.selectedCalendarIds }
                .distinctUntilChanged()
                .collect { requestRefresh(debounceMillis = 0) }
        }
        appScope.launch {
            prefsRepository.prefs
                .map { it.leaveByEnabled to it.leaveByLeadMinutes }
                .distinctUntilChanged()
                .collect { rescheduleLeaveBy() }
        }
        registerObserverIfPermitted()
    }

    /** Call after READ_CALENDAR was granted at runtime. */
    fun onPermissionGranted() {
        registerObserverIfPermitted()
        requestRefresh(debounceMillis = 0)
    }

    override suspend fun sync(reason: SyncReason): SyncResult {
        if (!hasPermission()) return SyncResult.SKIPPED
        registerObserverIfPermitted()
        return if (refresh()) SyncResult.SUCCESS else SyncResult.FAILURE
    }

    /** Debounced refresh on the app scope (the provider fires many change notifications). */
    @Synchronized
    fun requestRefresh(debounceMillis: Long = OBSERVER_DEBOUNCE_MILLIS) {
        val s = scope ?: return
        debounceJob?.cancel()
        debounceJob = s.launch {
            if (debounceMillis > 0) delay(debounceMillis)
            if (hasPermission()) refresh()
        }
    }

    @Synchronized
    private fun registerObserverIfPermitted() {
        if (observer != null || !hasPermission()) return
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                requestRefresh()
            }
        }
        try {
            appContext.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, obs)
            observer = obs
        } catch (e: SecurityException) {
            Log.w(TAG, "cannot observe calendar provider", e)
        }
    }

    /** Re-queries the provider and replaces the Room cache. Returns false on failure. */
    suspend fun refresh(): Boolean = refreshMutex.withLock {
        if (!hasPermission()) return@withLock false
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(WINDOW_DAYS).atStartOfDay(zone).toInstant().toEpochMilli()
        val selected = settingsRepository.current().selectedCalendarIds
        val events = try {
            withContext(Dispatchers.IO) { queryInstances(from, to, selected, zone) }
        } catch (e: SecurityException) {
            Log.w(TAG, "calendar query denied", e)
            return@withLock false
        } catch (e: RuntimeException) {
            Log.w(TAG, "calendar query failed", e)
            return@withLock false
        }
        // Replace everything up to the window end (drops past days in the same transaction).
        dao.replaceWindow(0L, to, events)
        syncStatusRepository.markSuccess(SYNC_KEY)
        scheduleLeaveBy(events.map { it.toCalEvent() })
        true
    }

    private suspend fun rescheduleLeaveBy() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        scheduleLeaveBy(dao.observeBetween(from, to).first().map { it.toCalEvent() })
    }

    private suspend fun scheduleLeaveBy(events: List<CalEvent>) {
        val prefs = prefsRepository.current()
        if (!prefs.leaveByEnabled) {
            leaveByScheduler.cancel()
            return
        }
        val now = System.currentTimeMillis()
        val target = pickLeaveByTarget(events, now, prefs.leaveByLeadMinutes * 60_000L)
        if (target == null) leaveByScheduler.cancel() else leaveByScheduler.schedule(target.first, target.second, now)
    }

    /** CAL-7: all calendars on the device (every account). Empty without permission. */
    suspend fun listCalendars(): List<CalendarInfo> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.VISIBLE
        )
        val out = ArrayList<CalendarInfo>()
        try {
            appContext.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI, projection, null, null,
                "${CalendarContract.Calendars.ACCOUNT_NAME} ASC, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    out += CalendarInfo(
                        id = c.getLong(0),
                        displayName = c.getString(1) ?: "Calendar",
                        accountName = c.getString(2) ?: "",
                        visible = !c.isNull(3) && c.getInt(3) == 1
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "calendar list denied", e)
        }
        out
    }

    private fun queryInstances(from: Long, to: Long, selected: Set<Long>, zone: ZoneId): List<CachedEvent> {
        val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uriBuilder, from)
        ContentUris.appendId(uriBuilder, to)
        val projection = arrayOf(
            CalendarContract.Instances._ID,
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY
        )
        val clauses = mutableListOf(
            "(${CalendarContract.Instances.STATUS} IS NULL OR ${CalendarContract.Instances.STATUS} != ${CalendarContract.Instances.STATUS_CANCELED})",
            "(${CalendarContract.Instances.SELF_ATTENDEE_STATUS} IS NULL OR " +
                "${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != ${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED})"
        )
        if (selected.isEmpty()) {
            clauses += "${CalendarContract.Instances.VISIBLE} = 1"
        } else {
            clauses += "${CalendarContract.Instances.CALENDAR_ID} IN (${selected.joinToString(",")})"
        }
        val now = System.currentTimeMillis()
        val out = ArrayList<CachedEvent>()
        appContext.contentResolver.query(
            uriBuilder.build(), projection, clauses.joinToString(" AND "), null,
            "${CalendarContract.Instances.BEGIN} ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val allDay = !c.isNull(8) && c.getInt(8) == 1
                val rawBegin = c.getLong(6)
                val rawEnd = if (c.isNull(7)) rawBegin else c.getLong(7)
                val begin = if (allDay) allDayUtcToLocal(rawBegin, zone) else rawBegin
                val end = if (allDay) allDayUtcToLocal(rawEnd, zone) else rawEnd
                val location = c.getString(4)?.takeIf { it.isNotBlank() }
                val description = c.getString(5)?.takeIf { it.isNotBlank() }
                out += CachedEvent(
                    instanceId = c.getLong(0),
                    eventId = c.getLong(1),
                    calendarId = c.getLong(2),
                    title = c.getString(3)?.takeIf { it.isNotBlank() } ?: "(no title)",
                    location = location,
                    // Descriptions can be huge HTML blobs; the UI never shows them in full.
                    description = description?.take(MAX_DESCRIPTION_CHARS),
                    beginMillis = begin,
                    endMillis = maxOf(end, begin),
                    allDay = allDay,
                    meetingUrl = MeetingLinks.extract(location, description),
                    syncedAtMillis = now
                )
            }
        }
        return out
    }

    companion object {
        const val SYNC_KEY = "calendar"
        const val WINDOW_DAYS = 8L
        private const val OBSERVER_DEBOUNCE_MILLIS = 3_000L
        private const val MAX_DESCRIPTION_CHARS = 2_000
        private const val TAG = "CalendarRepository"
    }
}

fun CachedEvent.toCalEvent(): CalEvent =
    CalEvent(instanceId, eventId, title, location, beginMillis, endMillis, allDay, meetingUrl)
