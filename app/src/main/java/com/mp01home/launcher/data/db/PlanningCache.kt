package com.mp01home.launcher.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * FOC-1: one of up to three daily priorities. [dateIso] is LocalDate.toString() ("yyyy-MM-dd"),
 * so string comparison orders dates correctly.
 *
 * NOTE: this is user data living in a destructive-migration DB; back it up via CORE-7 if needed.
 */
@Entity(tableName = "top_priorities", indices = [Index(value = ["dateIso", "slot"], unique = true)])
data class TopPriority(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateIso: String,
    /** 0, 1 or 2. */
    val slot: Int,
    val text: String,
    /** Optional link to a [CachedTask.id]. */
    val linkedTaskId: String? = null,
    val done: Boolean = false,
    /** True if carried over from a previous day (carry-over prompt). */
    val carriedOver: Boolean = false
)

@Dao
interface TopPriorityDao {

    @Query("SELECT * FROM top_priorities WHERE dateIso = :dateIso ORDER BY slot ASC")
    fun observeForDate(dateIso: String): Flow<List<TopPriority>>

    @Query("SELECT * FROM top_priorities WHERE dateIso = :dateIso ORDER BY slot ASC")
    suspend fun getForDate(dateIso: String): List<TopPriority>

    /** Most recent date before [dateIso] that has priorities (for carry-over). */
    @Query("SELECT MAX(dateIso) FROM top_priorities WHERE dateIso < :dateIso")
    suspend fun latestDateBefore(dateIso: String): String?

    @Upsert
    suspend fun upsert(priority: TopPriority)

    @Query("UPDATE top_priorities SET done = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)

    @Query("DELETE FROM top_priorities WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM top_priorities WHERE dateIso < :beforeDateIso")
    suspend fun deleteBefore(beforeDateIso: String)
}

/** EXT-2: a tracked habit. */
@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int = 0,
    val archived: Boolean = false
)

/** EXT-2: a habit checked on a given day. Row present + done = checked. */
@Entity(tableName = "habit_entries", primaryKeys = ["habitId", "dateIso"], indices = [Index("dateIso")])
data class HabitEntry(
    val habitId: Long,
    val dateIso: String,
    val done: Boolean = true
)

@Dao
interface HabitDao {

    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY position ASC, id ASC")
    fun observeActiveHabits(): Flow<List<Habit>>

    /** Inclusive ISO date range. */
    @Query("SELECT * FROM habit_entries WHERE dateIso >= :fromIso AND dateIso <= :toIso")
    fun observeEntriesBetween(fromIso: String, toIso: String): Flow<List<HabitEntry>>

    @Query("SELECT * FROM habit_entries WHERE habitId = :habitId AND done = 1 ORDER BY dateIso DESC")
    suspend fun getDoneEntries(habitId: Long): List<HabitEntry>

    @Upsert
    suspend fun upsertHabit(habit: Habit): Long

    @Upsert
    suspend fun upsertEntry(entry: HabitEntry)

    @Query("DELETE FROM habit_entries WHERE habitId = :habitId AND dateIso = :dateIso")
    suspend fun deleteEntry(habitId: Long, dateIso: String)

    @Query("UPDATE habits SET archived = 1 WHERE id = :habitId")
    suspend fun archive(habitId: Long)
}
