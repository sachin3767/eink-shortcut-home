package com.mp01home.launcher.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** One event *instance* (recurring events expand to many rows), cached from CalendarContract. */
@Entity(tableName = "cached_events", indices = [Index("beginMillis"), Index("calendarId")])
data class CachedEvent(
    /** CalendarContract.Instances._ID — unique per occurrence. */
    @PrimaryKey val instanceId: Long,
    /** CalendarContract.Instances.EVENT_ID — use for ContentUris.withAppendedId(Events.CONTENT_URI, id). */
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val location: String? = null,
    val description: String? = null,
    val beginMillis: Long,
    val endMillis: Long,
    val allDay: Boolean = false,
    /** CAL-4: first Meet/Zoom/Teams URL found in location/description, if any. */
    val meetingUrl: String? = null,
    val syncedAtMillis: Long = System.currentTimeMillis()
)

@Dao
abstract class CachedEventDao {

    /** Events overlapping [fromMillis, toMillis), all-day first, then by start. */
    @Query(
        "SELECT * FROM cached_events WHERE endMillis > :fromMillis AND beginMillis < :toMillis " +
            "ORDER BY allDay DESC, beginMillis ASC"
    )
    abstract fun observeBetween(fromMillis: Long, toMillis: Long): Flow<List<CachedEvent>>

    /** Next timed (non all-day) event that has not ended yet. */
    @Query(
        "SELECT * FROM cached_events WHERE allDay = 0 AND endMillis > :nowMillis " +
            "ORDER BY beginMillis ASC LIMIT 1"
    )
    abstract fun observeNext(nowMillis: Long): Flow<CachedEvent?>

    @Query("SELECT MAX(syncedAtMillis) FROM cached_events")
    abstract fun observeLastSyncedAt(): Flow<Long?>

    @Upsert
    abstract suspend fun upsertAll(events: List<CachedEvent>)

    @Query("DELETE FROM cached_events WHERE endMillis > :fromMillis AND beginMillis < :toMillis")
    abstract suspend fun deleteBetween(fromMillis: Long, toMillis: Long)

    @Query("DELETE FROM cached_events")
    abstract suspend fun clear()

    /** Atomically replaces the cached window with a fresh query result. */
    @Transaction
    open suspend fun replaceWindow(fromMillis: Long, toMillis: Long, events: List<CachedEvent>) {
        deleteBetween(fromMillis, toMillis)
        upsertAll(events)
    }
}
