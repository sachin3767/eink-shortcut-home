package com.mp01home.launcher.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mp01home.launcher.appContainer
import kotlinx.coroutines.CancellationException

/** Runs every registered [Syncable] sequentially. Scheduled by [SyncScheduler]. */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer
        val reason = SyncReason.entries.firstOrNull { it.name == inputData.getString(KEY_REASON) }
            ?: SyncReason.PERIODIC

        var needsRetry = false
        for (syncable in container.syncables) {
            val result = try {
                syncable.sync(reason)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "sync '${syncable.syncKey}' failed", e)
                SyncResult.FAILURE
            }
            when (result) {
                SyncResult.SUCCESS -> container.syncStatusRepository.markSuccess(syncable.syncKey)
                SyncResult.RETRY -> needsRetry = true
                SyncResult.SKIPPED, SyncResult.FAILURE -> Unit
            }
        }
        return if (needsRetry && runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
    }

    companion object {
        const val KEY_REASON = "reason"
        private const val TAG = "SyncWorker"
        private const val MAX_RETRIES = 3
    }
}
