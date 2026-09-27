package com.mp01home.launcher.eink

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import com.mp01home.launcher.data.db.AppDatabase
import com.mp01home.launcher.data.settings.SettingsRepository
import com.mp01home.launcher.data.settings.TimeFormat
import com.mp01home.launcher.sync.SyncReason
import com.mp01home.launcher.sync.SyncResult
import com.mp01home.launcher.sync.Syncable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * UX-5: keeps the lock/sleep-screen agenda image current. Runs
 * - as the last [Syncable] of every sync run (register it after calendar/tasks), and
 * - on ACTION_SCREEN_OFF (runtime receiver, see [registerScreenOffTrigger]).
 * It reads only the Room cache (EINK-7) and skips the wallpaper write when the content is
 * unchanged since the last write.
 */
class SleepScreenUpdater(
    private val context: Context,
    private val database: () -> AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val extrasStore: ExtrasStore,
    private val scope: CoroutineScope
) : Syncable {

    override val syncKey: String = "sleep_screen"

    private val mutex = Mutex()
    @Volatile
    private var lastContentHash: Int? = null
    private var screenOffReceiver: BroadcastReceiver? = null

    private companion object {
        const val TAG = "SleepScreen"
    }

    override suspend fun sync(reason: SyncReason): SyncResult =
        if (update(force = false)) SyncResult.SUCCESS else SyncResult.SKIPPED

    /** @return true if a new image was written. */
    suspend fun update(force: Boolean): Boolean = mutex.withLock {
        if (!extrasStore.current().sleepScreenEnabled) return@withLock false
        val settings = settingsRepository.current()
        val use24h = settings.timeFormat == TimeFormat.HOUR_24
        val now = LocalDateTime.now()
        val lines = buildLines(now, use24h)
        val hash = lines.hashCode()
        if (!force && hash == lastContentHash) return@withLock false
        val ok = withContext(Dispatchers.Default) {
            val bitmap = SleepScreenRenderer.render(context, lines, SleepScreenContent.footer(now, use24h))
            try {
                SleepScreenRenderer.setLockScreen(context, bitmap)
            } finally {
                bitmap.recycle()
            }
        }
        if (ok) lastContentHash = hash
        ok
    }

    /** [update] for fire-and-forget callers: never throws (appScope has no exception handler). */
    suspend fun updateSafely(force: Boolean): Boolean = try {
        update(force)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "sleep screen update failed", e)
        false
    }

    /** Called when the user turns the feature off. */
    fun clear() {
        lastContentHash = null
        scope.launch(Dispatchers.IO) { SleepScreenRenderer.clearLockScreen(context) }
    }

    private suspend fun buildLines(now: LocalDateTime, use24h: Boolean): List<SleepLine> {
        val db = database()
        val zone = ZoneId.systemDefault()
        val today = now.toLocalDate()
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val events = db.cachedEventDao().observeBetween(start, end).first().map {
            SleepEvent(
                title = it.title,
                begin = toLocal(it.beginMillis, zone),
                end = toLocal(it.endMillis, zone),
                allDay = it.allDay
            )
        }
        val todayEpoch = today.toEpochDay()
        val tasks = db.cachedTaskDao().observeDueOnOrBefore(todayEpoch).first().map {
            SleepTask(it.title, overdue = (it.dueEpochDay ?: todayEpoch) < todayEpoch)
        }
        val top3 = db.topPriorityDao().getForDate(today.toString()).map { SleepPriority(it.text, it.done) }
        return SleepScreenContent.build(now, events, tasks, top3, use24h)
    }

    private fun toLocal(millis: Long, zone: ZoneId): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)

    /** ACTION_SCREEN_OFF can only be received by a runtime-registered receiver. Idempotent. */
    fun registerScreenOffTrigger() {
        if (screenOffReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) {
                    val pending = goAsync()
                    scope.launch {
                        try {
                            updateSafely(force = false)
                        } finally {
                            pending.finish()
                        }
                    }
                }
            }
        }
        ContextCompat.registerReceiver(
            context.applicationContext,
            receiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        screenOffReceiver = receiver
    }
}
