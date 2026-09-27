package com.mp01home.launcher.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.mp01home.launcher.data.settings.LauncherSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * EINK-6: battery-friendly sync. A periodic WorkManager job (interval from settings, 15–30 min)
 * plus a throttled one-off run whenever the screen turns on. No foreground services.
 */
class SyncScheduler(context: Context) {

    private val appContext = context.applicationContext
    private val workManager: WorkManager get() = WorkManager.getInstance(appContext)

    @Volatile
    private var lastOneOffElapsed = 0L
    private var screenOnReceiver: BroadcastReceiver? = null

    /** (Re)schedules the periodic job; UPDATE keeps the existing schedule if unchanged. */
    fun schedulePeriodic(intervalMinutes: Int) {
        val minutes = intervalMinutes.coerceIn(LauncherSettings.MIN_SYNC_MINUTES, LauncherSettings.MAX_SYNC_MINUTES)
        val request = PeriodicWorkRequestBuilder<SyncWorker>(minutes.toLong(), TimeUnit.MINUTES)
            .setInputData(workDataOf(SyncWorker.KEY_REASON to SyncReason.PERIODIC.name))
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Keeps the periodic job in step with the sync-interval setting. */
    fun bindPeriodic(settings: Flow<LauncherSettings>, scope: CoroutineScope) {
        scope.launch {
            settings.map { it.syncIntervalMinutes }
                .distinctUntilChanged()
                .collect { schedulePeriodic(it) }
        }
    }

    /**
     * Enqueues a one-off sync. Unless [force], calls within [MIN_ONE_OFF_GAP_MILLIS] of the last
     * one are dropped (screen-on can fire many times an hour).
     */
    fun requestSync(reason: SyncReason, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && lastOneOffElapsed != 0L && now - lastOneOffElapsed < MIN_ONE_OFF_GAP_MILLIS) return
        lastOneOffElapsed = now
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(workDataOf(SyncWorker.KEY_REASON to reason.name))
            .build()
        val policy = if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        workManager.enqueueUniqueWork(ONE_OFF_WORK_NAME, policy, request)
    }

    /** ACTION_SCREEN_ON can only be received by a runtime-registered receiver. Idempotent. */
    fun registerScreenOnTrigger() {
        if (screenOnReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_ON) requestSync(SyncReason.SCREEN_ON)
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(Intent.ACTION_SCREEN_ON),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        screenOnReceiver = receiver
    }

    companion object {
        const val PERIODIC_WORK_NAME = "mp01_periodic_sync"
        const val ONE_OFF_WORK_NAME = "mp01_one_off_sync"
        const val MIN_ONE_OFF_GAP_MILLIS = 5 * 60 * 1000L
    }
}
