package com.mp01home.launcher.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A mail notification captured by [com.mp01home.launcher.mail.MailNotificationListener]
 * (MAIL-1/2, decision D1: NotificationListener only, no Gmail API).
 */
@Entity(tableName = "mail_notifications", indices = [Index("postedAtMillis")])
data class MailNotification(
    /** StatusBarNotification.key */
    @PrimaryKey val key: String,
    val packageName: String,
    val account: String? = null,
    val sender: String,
    val subject: String,
    val snippet: String? = null,
    val postedAtMillis: Long,
    /** Group summary notifications carry the count, not a message. */
    val isGroupSummary: Boolean = false,
    /** Unread count parsed from a summary (e.g. "5 new messages"), if available. */
    val unreadCountHint: Int? = null,
    val important: Boolean = false
)

@Dao
abstract class MailNotificationDao {

    @Query("SELECT * FROM mail_notifications WHERE isGroupSummary = 0 ORDER BY postedAtMillis DESC LIMIT :limit")
    abstract fun observeLatest(limit: Int): Flow<List<MailNotification>>

    @Query("SELECT * FROM mail_notifications ORDER BY postedAtMillis DESC")
    abstract fun observeAll(): Flow<List<MailNotification>>

    @Query("SELECT COUNT(*) FROM mail_notifications WHERE isGroupSummary = 0")
    abstract fun observeMessageCount(): Flow<Int>

    @Upsert
    abstract suspend fun upsert(notification: MailNotification)

    @Upsert
    abstract suspend fun upsertAll(notifications: List<MailNotification>)

    @Query("DELETE FROM mail_notifications WHERE `key` = :key")
    abstract suspend fun deleteByKey(key: String)

    @Query("DELETE FROM mail_notifications")
    abstract suspend fun clear()

    /** Rebuild from the listener's full active-notification snapshot. */
    @Transaction
    open suspend fun replaceAll(notifications: List<MailNotification>) {
        clear()
        upsertAll(notifications)
    }
}

/** FOC-4: a notification held back for the digest. */
@Entity(tableName = "digest_notifications", indices = [Index("postedAtMillis"), Index("packageName")])
data class DigestNotification(
    /** StatusBarNotification.key */
    @PrimaryKey val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String? = null,
    val postedAtMillis: Long,
    /** True once shown in a released digest. */
    val released: Boolean = false
)

@Dao
interface DigestNotificationDao {

    @Query("SELECT * FROM digest_notifications ORDER BY postedAtMillis DESC")
    fun observeAll(): Flow<List<DigestNotification>>

    @Query("SELECT * FROM digest_notifications WHERE packageName IN (:packages) ORDER BY postedAtMillis DESC")
    fun observeForPackages(packages: List<String>): Flow<List<DigestNotification>>

    @Upsert
    suspend fun upsert(notification: DigestNotification)

    @Query("UPDATE digest_notifications SET released = 1 WHERE postedAtMillis <= :untilMillis")
    suspend fun markReleasedUntil(untilMillis: Long)

    @Query("DELETE FROM digest_notifications WHERE `key` = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM digest_notifications WHERE postedAtMillis < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long)

    @Query("DELETE FROM digest_notifications")
    suspend fun clear()
}
