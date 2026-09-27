package com.mp01home.launcher.sync

/** Why a sync run was started. */
enum class SyncReason { PERIODIC, SCREEN_ON, MANUAL }

enum class SyncResult {
    /** Fresh data written to the local cache; "synced HH:MM" is updated for this key. */
    SUCCESS,
    /** Nothing to do (feature disabled, not signed in, cache still fresh…). */
    SKIPPED,
    /** Transient failure (e.g. offline); WorkManager may retry with backoff. */
    RETRY,
    /** Permanent failure for this run; no retry. */
    FAILURE
}

/**
 * Implemented by each integration that keeps a local cache fresh (EINK-6/7). Register an
 * instance in [com.mp01home.launcher.AppContainer] with `registerSyncable(...)`.
 *
 * Contract:
 * - [sync] runs on a WorkManager background thread; it must write results to Room/DataStore
 *   (never directly to UI state) and must be idempotent.
 * - Exceptions are caught by [SyncWorker] and treated as [SyncResult.FAILURE]; do not swallow
 *   CancellationException.
 * - Be cheap when nothing changed — this runs every 15–30 min and on every screen-on.
 */
interface Syncable {
    /** Stable id, e.g. "calendar", "tasks". Used for the per-feature "synced HH:MM" label. */
    val syncKey: String

    suspend fun sync(reason: SyncReason): SyncResult
}
