package com.mp01home.launcher.calendar

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mp01home.launcher.appContainer
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * CAL-6: schedules a single one-off WorkManager job at "begin − lead time" for the next event
 * with a physical location. Re-evaluated after every calendar refresh; unique work (REPLACE),
 * so there is at most one pending leave-by job.
 */
class LeaveByScheduler(context: Context) {

    private val appContext = context.applicationContext
    private val workManager: WorkManager get() = WorkManager.getInstance(appContext)

    @Volatile
    private var scheduledKey: Pair<Long, Long>? = null

    fun schedule(event: CalEvent, leaveAtMillis: Long, nowMillis: Long) {
        val key = event.instanceId to leaveAtMillis
        if (key == scheduledKey) return
        val request = OneTimeWorkRequestBuilder<LeaveByWorker>()
            .setInitialDelay((leaveAtMillis - nowMillis).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    LeaveByWorker.KEY_INSTANCE_ID to event.instanceId,
                    LeaveByWorker.KEY_EVENT_ID to event.eventId,
                    LeaveByWorker.KEY_TITLE to event.title,
                    LeaveByWorker.KEY_LOCATION to event.location,
                    LeaveByWorker.KEY_BEGIN to event.beginMillis,
                    LeaveByWorker.KEY_END to event.endMillis
                )
            )
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        scheduledKey = key
    }

    /** Cancelling an absent job is cheap, so this is safe to call after every refresh. */
    fun cancel() {
        workManager.cancelUniqueWork(WORK_NAME)
        scheduledKey = null
    }

    companion object {
        const val WORK_NAME = "mp01_calendar_leave_by"
    }
}

class LeaveByWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val instanceId = inputData.getLong(KEY_INSTANCE_ID, -1L)
        val eventId = inputData.getLong(KEY_EVENT_ID, -1L)
        val title = inputData.getString(KEY_TITLE) ?: return Result.success()
        val location = inputData.getString(KEY_LOCATION).orEmpty()
        val begin = inputData.getLong(KEY_BEGIN, 0L)
        val end = inputData.getLong(KEY_END, begin)
        val now = System.currentTimeMillis()
        if (now >= begin) return Result.success() // fired too late (doze): nothing useful to say

        val container = applicationContext.appContainer
        val repo = container.calendarRepository
        if (!repo.prefsRepository.current().leaveByEnabled) return Result.success()
        if (repo.prefsRepository.lastLeaveByNotified() == instanceId) return Result.success()
        // Still in the cache (not deleted/moved since scheduling)?
        val stillThere = container.database.cachedEventDao()
            .observeBetween(begin, begin + 1)
            .first()
            .any { it.instanceId == instanceId && it.beginMillis == begin }
        if (!stillThere) return Result.success()

        if (notify(instanceId, eventId, title, location, begin, end, now)) {
            repo.prefsRepository.setLastLeaveByNotified(instanceId)
        }
        return Result.success()
    }

    @SuppressLint("MissingPermission") // checked just below for API 33+
    private fun notify(
        instanceId: Long,
        eventId: Long,
        title: String,
        location: String,
        begin: Long,
        end: Long,
        now: Long
    ): Boolean {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = NotificationManagerCompat.from(ctx)
        if (!manager.areNotificationsEnabled()) return false
        ensureChannel(ctx)

        val minutes = ((begin - now + 59_999L) / 60_000L).coerceAtLeast(0L)
        val use24h = android.text.format.DateFormat.is24HourFormat(ctx)
        val startText = formatClock(begin, ZoneId.systemDefault(), use24h)

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val openEvent = PendingIntent.getActivity(
            ctx, instanceId.hashCode(), CalendarActions.viewEventIntent(eventId, begin, end), flags
        )
        val directionsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(location)))
        val directions = PendingIntent.getActivity(ctx, instanceId.hashCode() + 1, directionsIntent, flags)

        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle("Leave now · $title in ${formatDuration(minutes)}")
            .setContentText("$startText · $location")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Starts $startText (in ${formatDuration(minutes)})\n$location"))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openEvent)
            .addAction(0, "Directions", directions)
            .setAutoCancel(true)
            .setTimeoutAfter((begin - now).coerceAtLeast(60_000L))
            .build()
        return try {
            manager.notify(NOTIFICATION_TAG, instanceId.hashCode(), notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    private fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Leave-by alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "\"Leave now\" reminders for calendar events with a location"
            }
        )
    }

    companion object {
        const val KEY_INSTANCE_ID = "instance_id"
        const val KEY_EVENT_ID = "event_id"
        const val KEY_TITLE = "title"
        const val KEY_LOCATION = "location"
        const val KEY_BEGIN = "begin"
        const val KEY_END = "end"
        const val CHANNEL_ID = "calendar_leave_by"
        private const val NOTIFICATION_TAG = "leave_by"
    }
}
