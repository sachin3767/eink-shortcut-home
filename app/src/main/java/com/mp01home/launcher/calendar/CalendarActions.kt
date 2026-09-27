package com.mp01home.launcher.calendar

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import com.mp01home.launcher.appContainer
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

/**
 * Calendar intents shared with other features. All methods are safe to call from any Context
 * (FLAG_ACTIVITY_NEW_TASK is added when it isn't an Activity) and never throw when no calendar
 * app is installed — they return false / null instead.
 *
 * Public API for other agents:
 * - [insertEvent] (CAL-3, capture): open the calendar app's "new event" editor, prefilled.
 * - [scheduleIntoFreeSlot] (PLAN-4, tasks/planning): find the next free slot and open the editor
 *   prefilled with it.
 */
object CalendarActions {

    private const val TAG = "CalendarActions"

    /** CAL-3: ACTION_INSERT on Events.CONTENT_URI; [begin]/[end] in epoch millis, all optional. */
    fun insertEvent(context: Context, title: String? = null, begin: Long? = null, end: Long? = null): Boolean {
        val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
        if (!title.isNullOrBlank()) intent.putExtra(CalendarContract.Events.TITLE, title.trim())
        if (begin != null) intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
        if (end != null) intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
        return start(context, intent)
    }

    /** CAL-2: open one event (instance times make the calendar app show the right occurrence). */
    fun openEvent(context: Context, eventId: Long, begin: Long? = null, end: Long? = null): Boolean =
        start(context, viewEventIntent(eventId, begin, end))

    fun viewEventIntent(eventId: Long, begin: Long? = null, end: Long? = null): Intent {
        val intent = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId))
        if (begin != null) intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
        if (end != null) intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
        return intent
    }

    /** PLAN-3: open the calendar app on [date] (content://com.android.calendar/time/<millis>). */
    fun openDay(context: Context, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val millis = date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val builder = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
        ContentUris.appendId(builder, millis)
        return start(context, Intent(Intent.ACTION_VIEW).setData(builder.build()))
    }

    /** CAL-4: open a Meet/Zoom/Teams link (the matching app handles it if installed). */
    fun openUrl(context: Context, url: String): Boolean =
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    /**
     * PLAN-4 time blocking: finds the next free slot of [durationMinutes] (default: the user's
     * block length setting) in the cached calendar (working hours 08–20, next 7 days, starting
     * at the next quarter hour) and opens the new-event editor with [title] and the slot
     * prefilled. Falls back to an editor without times when no slot is free.
     *
     * Returns the slot used (null if none was found). Call from a coroutine, e.g.
     * `scope.launch { CalendarActions.scheduleIntoFreeSlot(context, task.title) }`.
     */
    suspend fun scheduleIntoFreeSlot(context: Context, title: String, durationMinutes: Int? = null): TimeRange? {
        val slot = findNextFreeSlot(context, durationMinutes)
        insertEvent(context, title, slot?.startMillis, slot?.endMillis)
        return slot
    }

    /** PLAN-4 without side effects: the slot [scheduleIntoFreeSlot] would use. */
    suspend fun findNextFreeSlot(context: Context, durationMinutes: Int? = null): TimeRange? {
        val repo = context.appContainer.calendarRepository
        val minutes = durationMinutes ?: repo.prefsRepository.current().blockMinutes
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val horizonEnd = LocalDate.now(zone).plusDays(SLOT_SEARCH_DAYS.toLong() + 1)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val cached = context.appContainer.database.cachedEventDao()
            .observeBetween(now, horizonEnd)
            .first()
            .map { it.toCalEvent() }
        return findFreeSlot(
            busy = busyRanges(cached),
            earliestMillis = now,
            durationMillis = minutes.coerceAtLeast(1) * 60_000L,
            zone = zone,
            maxDays = SLOT_SEARCH_DAYS
        )
    }

    private const val SLOT_SEARCH_DAYS = 7

    private fun start(context: Context, intent: Intent): Boolean {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "no activity for $intent", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "not allowed: $intent", e)
            false
        }
    }
}
