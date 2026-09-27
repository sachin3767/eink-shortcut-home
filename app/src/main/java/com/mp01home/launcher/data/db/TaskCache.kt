package com.mp01home.launcher.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A Google Tasks task mirrored locally (TASK-2/3/4). */
@Entity(tableName = "cached_tasks", indices = [Index("listId"), Index("dueEpochDay")])
data class CachedTask(
    /** Google task id, or "local:<uuid>" for a task created offline and not yet pushed. */
    @PrimaryKey val id: String,
    val listId: String,
    val title: String,
    val notes: String? = null,
    /** LocalDate.toEpochDay() of the due date (Google Tasks due dates carry no time). */
    val dueEpochDay: Long? = null,
    val completed: Boolean = false,
    val completedAtMillis: Long? = null,
    /** Google's lexicographic ordering string. */
    val position: String? = null,
    val parentId: String? = null,
    val updatedAtMillis: Long = System.currentTimeMillis(),
    /** True while a local change has not yet been confirmed by the server. */
    val dirty: Boolean = false
)

enum class PendingTaskOpType { CREATE, COMPLETE, UNCOMPLETE, UPDATE, DELETE }

/** Offline queue of task mutations, replayed in [id] order on the next successful sync. */
@Entity(tableName = "pending_task_ops", indices = [Index("taskId")])
data class PendingTaskOp(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: PendingTaskOpType,
    val taskId: String,
    val listId: String,
    /** Optional JSON body (e.g. title/notes/due for CREATE/UPDATE). */
    val payloadJson: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
    val lastError: String? = null
)

@Dao
abstract class CachedTaskDao {

    /** Open tasks due on or before [epochDay] (i.e. due today + overdue), soonest first. */
    @Query(
        "SELECT * FROM cached_tasks WHERE completed = 0 AND dueEpochDay IS NOT NULL " +
            "AND dueEpochDay <= :epochDay ORDER BY dueEpochDay ASC, position ASC"
    )
    abstract fun observeDueOnOrBefore(epochDay: Long): Flow<List<CachedTask>>

    @Query("SELECT COUNT(*) FROM cached_tasks WHERE completed = 0 AND dueEpochDay IS NOT NULL AND dueEpochDay < :todayEpochDay")
    abstract fun observeOverdueCount(todayEpochDay: Long): Flow<Int>

    @Query("SELECT * FROM cached_tasks WHERE completed = 0 ORDER BY listId, position ASC")
    abstract fun observeOpen(): Flow<List<CachedTask>>

    @Query("SELECT * FROM cached_tasks WHERE completed = 1 AND completedAtMillis >= :sinceMillis ORDER BY completedAtMillis DESC")
    abstract fun observeCompletedSince(sinceMillis: Long): Flow<List<CachedTask>>

    @Query("SELECT MAX(updatedAtMillis) FROM cached_tasks WHERE dirty = 0")
    abstract fun observeLastSyncedAt(): Flow<Long?>

    @Query("SELECT * FROM cached_tasks WHERE id = :id")
    abstract suspend fun getById(id: String): CachedTask?

    @Upsert
    abstract suspend fun upsert(task: CachedTask)

    @Upsert
    abstract suspend fun upsertAll(tasks: List<CachedTask>)

    @Query("UPDATE cached_tasks SET completed = :completed, completedAtMillis = :completedAtMillis, dirty = 1 WHERE id = :id")
    abstract suspend fun setCompleted(id: String, completed: Boolean, completedAtMillis: Long?)

    /** Swap a temporary "local:" id for the server id after a queued CREATE succeeds. */
    @Query("UPDATE cached_tasks SET id = :newId, dirty = 0 WHERE id = :oldId")
    abstract suspend fun replaceId(oldId: String, newId: String)

    @Query("DELETE FROM cached_tasks WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    @Query("DELETE FROM cached_tasks WHERE listId = :listId AND dirty = 0")
    abstract suspend fun deleteCleanInList(listId: String)

    @Query("DELETE FROM cached_tasks")
    abstract suspend fun clear()

    /** Replace a list's server snapshot while keeping un-pushed local edits. */
    @Transaction
    open suspend fun replaceList(listId: String, serverTasks: List<CachedTask>) {
        deleteCleanInList(listId)
        upsertAll(serverTasks)
    }
}

@Dao
interface PendingTaskOpDao {

    @Query("SELECT * FROM pending_task_ops ORDER BY id ASC")
    suspend fun getAll(): List<PendingTaskOp>

    @Query("SELECT COUNT(*) FROM pending_task_ops")
    fun observeCount(): Flow<Int>

    @Insert
    suspend fun insert(op: PendingTaskOp): Long

    @Delete
    suspend fun delete(op: PendingTaskOp)

    @Query("UPDATE pending_task_ops SET attempts = attempts + 1, lastError = :error WHERE id = :id")
    suspend fun markFailed(id: Long, error: String?)

    @Query("UPDATE pending_task_ops SET taskId = :newId WHERE taskId = :oldId")
    suspend fun replaceTaskId(oldId: String, newId: String)

    @Query("DELETE FROM pending_task_ops")
    suspend fun clear()
}
