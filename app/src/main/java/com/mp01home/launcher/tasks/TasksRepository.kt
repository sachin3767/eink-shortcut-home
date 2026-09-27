package com.mp01home.launcher.tasks

import android.content.Context
import android.util.Log
import com.mp01home.launcher.data.db.AppDatabase
import com.mp01home.launcher.data.db.CachedTask
import com.mp01home.launcher.data.db.CachedTaskDao
import com.mp01home.launcher.data.db.PendingTaskOp
import com.mp01home.launcher.data.db.PendingTaskOpDao
import com.mp01home.launcher.data.db.PendingTaskOpType
import com.mp01home.launcher.data.settings.SettingsRepository
import com.mp01home.launcher.sync.SyncReason
import com.mp01home.launcher.sync.SyncResult
import com.mp01home.launcher.sync.SyncScheduler
import com.mp01home.launcher.sync.Syncable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Task actions other features may call (CAP-1 quick capture, FOC-1, PLAN-1/2).
 * Both are optimistic: the Room cache changes immediately and the change is queued in
 * `pending_task_ops`, pushed right away when possible and otherwise on the next sync.
 */
interface TasksActions {
    /** TASK-4: adds [title] to the default list, optionally due on [due]. Returns the (local) id. */
    suspend fun addTask(title: String, due: LocalDate?): String

    /** TASK-3: completes / re-opens a task. */
    suspend fun setCompleted(taskId: String, completed: Boolean)
}

/**
 * Google Tasks integration (TASK-1..5): Room cache + offline op queue + REST sync.
 * Registered as a [Syncable] ("tasks") in AppContainer.
 */
class TasksRepository(
    context: Context,
    private val databaseProvider: () -> AppDatabase,
    val auth: GoogleTasksAuth,
    private val settingsRepository: SettingsRepository,
    private val syncScheduler: SyncScheduler,
    private val appScope: CoroutineScope
) : Syncable, TasksActions {

    override val syncKey: String = SYNC_KEY

    val meta = TasksMetaStore(context.applicationContext)

    private val taskDao: CachedTaskDao get() = databaseProvider().cachedTaskDao()
    private val opDao: PendingTaskOpDao get() = databaseProvider().pendingTaskOpDao()

    private val client: GoogleTasksClient by lazy {
        val http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        GoogleTasksClient(http, tokenProvider = { auth.accessToken() }, invalidateToken = { auth.invalidate(it) })
    }

    /** Serialises flushes and syncs so ops are never pushed twice. */
    private val mutex = Mutex()

    // ---- Observations (UI) ---------------------------------------------------------------------

    val accountName: StateFlow<String?> get() = auth.accountName
    val catalog: Flow<TaskListCatalog> get() = meta.catalog
    fun observeDueOnOrBefore(epochDay: Long): Flow<List<CachedTask>> = taskDao.observeDueOnOrBefore(epochDay)
    fun observeOverdueCount(todayEpochDay: Long): Flow<Int> = taskDao.observeOverdueCount(todayEpochDay)
    fun observePendingCount(): Flow<Int> = opDao.observeCount()

    // ---- Actions ----------------------------------------------------------------------------

    override suspend fun addTask(title: String, due: LocalDate?): String {
        val clean = title.trim()
        require(clean.isNotEmpty()) { "empty task title" }
        val listId = meta.current().defaultListId ?: TasksMapping.DEFAULT_LIST_ALIAS
        val id = TasksMapping.LOCAL_ID_PREFIX + UUID.randomUUID()
        taskDao.upsert(
            CachedTask(
                id = id,
                listId = listId,
                title = clean,
                dueEpochDay = due?.toEpochDay(),
                dirty = true
            )
        )
        val payload = TaskCreatePayload(title = clean, dueEpochDay = due?.toEpochDay())
        opDao.insert(
            PendingTaskOp(
                type = PendingTaskOpType.CREATE,
                taskId = id,
                listId = listId,
                payloadJson = GoogleTasksClient.json.encodeToString(TaskCreatePayload.serializer(), payload)
            )
        )
        pushSoon()
        return id
    }

    override suspend fun setCompleted(taskId: String, completed: Boolean) {
        val task = taskDao.getById(taskId) ?: return
        if (task.completed == completed) return
        taskDao.setCompleted(taskId, completed, if (completed) System.currentTimeMillis() else null)
        opDao.insert(
            PendingTaskOp(
                type = if (completed) PendingTaskOpType.COMPLETE else PendingTaskOpType.UNCOMPLETE,
                taskId = taskId,
                listId = task.listId
            )
        )
        pushSoon()
    }

    /** Tries to push queued ops now (fire-and-forget); failures stay queued for the next sync. */
    private fun pushSoon() {
        if (!auth.isSignedIn) return
        appScope.launch {
            try {
                mutex.withLock { flushPending() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.i(TAG, "push deferred: ${e.message}")
            }
        }
    }

    /** After sign-in or a list-selection change: fetch now. */
    fun requestSyncNow() {
        syncScheduler.requestSync(SyncReason.MANUAL, force = true)
    }

    suspend fun signOut() {
        auth.signOut()
        mutex.withLock {
            opDao.clear()
            taskDao.clear()
            meta.clear()
        }
    }

    // ---- Sync -------------------------------------------------------------------------------

    override suspend fun sync(reason: SyncReason): SyncResult {
        auth.load()
        if (!auth.isConfigured || !auth.isSignedIn) return SyncResult.SKIPPED
        return try {
            mutex.withLock { syncLocked() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: TasksAuthException) {
            Log.w(TAG, "auth: ${e.message}")
            SyncResult.FAILURE
        } catch (e: TasksHttpException) {
            Log.w(TAG, "http ${e.code}", e)
            if (e.isTransient) SyncResult.RETRY else SyncResult.FAILURE
        } catch (e: IOException) {
            Log.i(TAG, "offline: ${e.message}")
            SyncResult.RETRY
        }
    }

    private suspend fun syncLocked(): SyncResult {
        val flushedAll = flushPending()

        val lists = client.listTaskLists()
        val defaultId = runCatching { client.getTaskList(TasksMapping.DEFAULT_LIST_ALIAS).id }
            .getOrElse { if (it is CancellationException) throw it else lists.firstOrNull()?.id }
        meta.save(lists, defaultId)

        val selected = settingsRepository.current().selectedTaskListIds
        val listIds = lists.map { it.id }
        val feeding = TasksMapping.effectiveListIds(selected, listIds, defaultId)
        val zone = ZoneId.systemDefault()
        val completedMin = TasksMapping.completedMinFor(LocalDate.now(zone), zone)

        for (listId in feeding) {
            val items = client.listTasks(listId, completedMin)
            // Re-read the queue per list: ops keep their rows dirty until pushed.
            val pendingIds = opDao.getAll().mapTo(HashSet()) { it.taskId }
            val rows = TasksMapping.serverRows(items, listId, pendingIds, System.currentTimeMillis())
            taskDao.replaceList(listId, rows)
        }
        // Lists no longer feeding home (or the "@default" placeholder): drop their clean rows.
        for (listId in listIds - feeding.toSet()) taskDao.deleteCleanInList(listId)
        taskDao.deleteCleanInList(TasksMapping.DEFAULT_LIST_ALIAS)

        return if (flushedAll) SyncResult.SUCCESS else SyncResult.RETRY
    }

    /**
     * Replays queued ops in order. Must be called with [mutex] held.
     * @return true when the queue is empty afterwards (all pushed or dropped as permanent failures)
     * @throws IOException / TasksAuthException when the network or auth is unavailable
     */
    private suspend fun flushPending(): Boolean {
        val ops = opDao.getAll()
        if (ops.isEmpty()) return true
        val remap = HashMap<String, String>()

        for ((index, op) in ops.withIndex()) {
            val taskId = remap[op.taskId] ?: op.taskId
            val later = ops.subList(index + 1, ops.size)
            val hasLaterOps = later.any { it.taskId == op.taskId || it.taskId == taskId }
            try {
                when (op.type) {
                    PendingTaskOpType.CREATE -> {
                        val payload = op.payloadJson?.let {
                            runCatching {
                                GoogleTasksClient.json.decodeFromString(TaskCreatePayload.serializer(), it)
                            }.getOrNull()
                        }
                        if (payload == null) {
                            taskDao.deleteById(taskId)
                        } else {
                            val due = payload.dueEpochDay?.let { TasksMapping.formatDue(LocalDate.ofEpochDay(it)) }
                            val created = client.insertTask(op.listId, payload.title, due, payload.notes)
                            remap[taskId] = created.id
                            opDao.replaceTaskId(taskId, created.id)
                            taskDao.replaceId(taskId, created.id)
                            if (!hasLaterOps) {
                                val realList = resolveListId(op.listId)
                                taskDao.deleteById(created.id)
                                taskDao.upsert(TasksMapping.toCached(created, realList, System.currentTimeMillis()))
                            }
                        }
                    }
                    PendingTaskOpType.COMPLETE, PendingTaskOpType.UNCOMPLETE -> {
                        if (!TasksMapping.isLocalId(taskId)) {
                            client.setCompleted(op.listId, taskId, op.type == PendingTaskOpType.COMPLETE)
                            if (!hasLaterOps) markClean(taskId)
                        } // else: its CREATE was dropped; nothing to push.
                    }
                    PendingTaskOpType.UPDATE -> {
                        val body = op.payloadJson?.let {
                            runCatching { GoogleTasksClient.json.decodeFromString(JsonObject.serializer(), it) }.getOrNull()
                        }
                        if (body != null && !TasksMapping.isLocalId(taskId)) {
                            client.patchTask(op.listId, taskId, body)
                            if (!hasLaterOps) markClean(taskId)
                        }
                    }
                    PendingTaskOpType.DELETE -> {
                        if (!TasksMapping.isLocalId(taskId)) client.deleteTask(op.listId, taskId)
                        taskDao.deleteById(taskId)
                    }
                }
                opDao.delete(op)
            } catch (e: TasksHttpException) {
                val keep = e.isTransient ||
                    ((e.code == 401 || e.code == 403) && op.attempts + 1 < MAX_ATTEMPTS)
                if (keep) {
                    opDao.markFailed(op.id, e.message)
                    return false
                }
                // Permanent (404 task gone, 400 bad request, repeated 403): drop it.
                Log.w(TAG, "dropping ${op.type} for $taskId: ${e.message}")
                opDao.delete(op)
                if (op.type == PendingTaskOpType.CREATE) taskDao.deleteById(taskId)
                else if (!hasLaterOps) markClean(taskId)
            } catch (e: IOException) {
                opDao.markFailed(op.id, e.message)
                throw e
            }
        }
        return true
    }

    private suspend fun markClean(taskId: String) {
        val task = taskDao.getById(taskId) ?: return
        if (task.dirty) taskDao.upsert(task.copy(dirty = false, updatedAtMillis = System.currentTimeMillis()))
    }

    private suspend fun resolveListId(listId: String): String {
        if (listId != TasksMapping.DEFAULT_LIST_ALIAS) return listId
        meta.current().defaultListId?.let { return it }
        return client.getTaskList(TasksMapping.DEFAULT_LIST_ALIAS).id
    }

    companion object {
        const val SYNC_KEY = "tasks"
        private const val TAG = "TasksRepository"
        private const val MAX_ATTEMPTS = 5
    }
}
