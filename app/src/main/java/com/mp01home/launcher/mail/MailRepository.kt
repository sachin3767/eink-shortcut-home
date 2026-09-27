package com.mp01home.launcher.mail

import android.service.notification.StatusBarNotification
import com.mp01home.launcher.data.db.MailNotification
import com.mp01home.launcher.data.db.MailNotificationDao
import com.mp01home.launcher.sync.SyncStatusRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * MAIL-1/2: mirrors Gmail notifications into `mail_notifications` (decision D1). The UI only
 * reads [summary]; nothing here talks to the network.
 */
class MailRepository(
    private val dao: MailNotificationDao,
    private val syncStatusRepository: SyncStatusRepository,
    scope: CoroutineScope
) : NotificationHandler {

    private val queue = SerialWorkQueue(scope, "MailRepository")

    @Volatile
    private var lastMarkedMillis = 0L

    /** Unread estimate + latest 3 messages, recomputed whenever the cache changes. */
    val summary: Flow<MailSummary> = dao.observeAll()
        .map { rows -> MailSummaryCalculator.summarize(rows.map { it.toParsed() }) }
        .distinctUntilChanged()

    /** When the listener last told us anything about Gmail (the "synced HH:MM" label). */
    val lastUpdateMillis: Flow<Long?> = syncStatusRepository.lastSuccessMillis(SYNC_KEY)

    override fun onSnapshot(active: List<StatusBarNotification>) {
        val rows = active
            .filter { GmailNotificationParser.isGmailPackage(it.packageName) }
            .flatMap { GmailNotificationParser.parse(GmailNotificationExtractor.extract(it)) }
            .map { it.toEntity() }
        queue.submit {
            dao.replaceAll(rows)
            markUpdated(force = true)
        }
    }

    override fun onPosted(sbn: StatusBarNotification) {
        if (!GmailNotificationParser.isGmailPackage(sbn.packageName)) return
        val key = sbn.key
        val rows = GmailNotificationParser.parse(GmailNotificationExtractor.extract(sbn)).map { it.toEntity() }
        queue.submit {
            // An update may shrink a summary's line list: drop old rows for this key first.
            GmailNotificationParser.keysFor(key).forEach { dao.deleteByKey(it) }
            if (rows.isNotEmpty()) dao.upsertAll(rows)
            markUpdated()
        }
    }

    override fun onRemoved(sbn: StatusBarNotification) {
        if (!GmailNotificationParser.isGmailPackage(sbn.packageName)) return
        val key = sbn.key
        queue.submit {
            GmailNotificationParser.keysFor(key).forEach { dao.deleteByKey(it) }
            markUpdated()
        }
    }

    private suspend fun markUpdated(force: Boolean = false) {
        val now = System.currentTimeMillis()
        // Minute resolution is all the label shows; avoid a DataStore write per event.
        if (force || now - lastMarkedMillis >= 60_000L) {
            lastMarkedMillis = now
            syncStatusRepository.markSuccess(SYNC_KEY, now)
        }
    }

    companion object {
        const val SYNC_KEY = "mail"
    }
}

internal fun ParsedMail.toEntity(): MailNotification = MailNotification(
    key = key,
    packageName = packageName,
    account = account,
    sender = sender,
    subject = subject,
    snippet = snippet,
    postedAtMillis = postedAtMillis,
    isGroupSummary = isGroupSummary,
    unreadCountHint = unreadCountHint,
    important = false
)

internal fun MailNotification.toParsed(): ParsedMail = ParsedMail(
    key = key,
    packageName = packageName,
    account = account,
    sender = sender,
    subject = subject,
    snippet = snippet,
    postedAtMillis = postedAtMillis,
    isGroupSummary = isGroupSummary,
    unreadCountHint = unreadCountHint,
    isLine = GmailNotificationParser.isLineKey(key)
)
