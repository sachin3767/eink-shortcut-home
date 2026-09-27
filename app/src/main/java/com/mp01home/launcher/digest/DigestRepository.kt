package com.mp01home.launcher.digest

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import com.mp01home.launcher.data.db.DigestNotification
import com.mp01home.launcher.data.db.DigestNotificationDao
import com.mp01home.launcher.data.settings.SettingsRepository
import com.mp01home.launcher.mail.MailNotificationListener
import com.mp01home.launcher.mail.NotificationHandler
import com.mp01home.launcher.mail.SerialWorkQueue
import com.mp01home.launcher.sync.SyncStatusRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * FOC-4 notification digest.
 *
 * - Whitelisted apps: mirrored into `digest_notifications` and summarised on home.
 * - Everything else (when "hold" is on): snoozed via NotificationListenerService until the next
 *   digest time and counted as held; when the system re-posts it at the digest time it is
 *   recorded as released and appears in the home digest.
 * Rows disappear when the notification is dismissed (the digest mirrors the shade).
 */
class DigestRepository(
    private val context: Context,
    private val dao: DigestNotificationDao,
    private val settingsRepository: SettingsRepository,
    private val syncStatusRepository: SyncStatusRepository,
    val prefs: DigestPrefs,
    scope: CoroutineScope
) : NotificationHandler {

    private val queue = SerialWorkQueue(scope, "DigestRepository")

    /** Keys we snoozed; their onRemoved must not delete the held row. */
    private val snoozedKeys: MutableSet<String> = ConcurrentHashMap.newKeySet<String>()
    private val labelCache = ConcurrentHashMap<String, String>()

    @Volatile
    private var lastMarkedMillis = 0L

    val rows: Flow<List<DigestRow>> = dao.observeAll().map { list -> list.map { it.toRow() } }

    val lastUpdateMillis: Flow<Long?> = syncStatusRepository.lastSuccessMillis(SYNC_KEY)

    override fun onSnapshot(active: List<StatusBarNotification>) {
        val captured = active.map { capture(it) }
        queue.submit {
            val now = System.currentTimeMillis()
            dao.deleteOlderThan(now - MAX_AGE_MILLIS)
            val recordedKeys = HashSet<String>()
            for (item in captured) {
                if (handle(item, now)) recordedKeys += item.candidate.key
            }
            // Visible rows whose notification is gone (dismissed while we were disconnected).
            dao.observeAll().first()
                .filter { it.released && it.key !in recordedKeys }
                .forEach { dao.deleteByKey(it.key) }
            markUpdated(force = true)
        }
    }

    override fun onPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == context.packageName) return
        val item = capture(sbn)
        queue.submit {
            val now = System.currentTimeMillis()
            if (handle(item, now)) markUpdated()
        }
    }

    override fun onRemoved(sbn: StatusBarNotification) {
        val key = sbn.key
        if (snoozedKeys.remove(key)) return
        queue.submit { dao.deleteByKey(key) }
    }

    /** Clears rows (e.g. after the user opened that app from the digest). */
    fun dismiss(keys: Collection<String>) {
        queue.submit { keys.forEach { dao.deleteByKey(it) } }
    }

    fun clearAll() {
        queue.submit { dao.clear() }
    }

    /** @return true if the notification was recorded or held. */
    private suspend fun handle(item: Captured, now: Long): Boolean {
        val settings = settingsRepository.current()
        val decision = DigestPolicy.decide(
            candidate = item.candidate,
            ownPackage = context.packageName,
            whitelist = settings.digestWhitelist,
            holdEnabled = prefs.isHoldEnabled(),
            digestTimes = settings.digestTimes,
            nowMillis = now,
            zone = ZoneId.systemDefault()
        )
        return when (decision) {
            DigestDecision.Ignore -> {
                // It may have been recorded before (e.g. whitelist changed); keep rows honest.
                dao.deleteByKey(item.candidate.key)
                false
            }
            DigestDecision.Record -> {
                dao.upsert(item.toEntity(labelFor(item.candidate.packageName), released = true))
                true
            }
            is DigestDecision.Hold -> {
                snoozedKeys += item.candidate.key
                if (!MailNotificationListener.snooze(item.candidate.key, decision.durationMillis)) {
                    snoozedKeys -= item.candidate.key
                    return false
                }
                if (!item.candidate.isGroupSummary) {
                    dao.upsert(item.toEntity(labelFor(item.candidate.packageName), released = false))
                }
                true
            }
        }
    }

    private fun labelFor(packageName: String): String = labelCache.getOrPut(packageName) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
        }.getOrDefault(packageName)
    }

    private suspend fun markUpdated(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (force || now - lastMarkedMillis >= 60_000L) {
            lastMarkedMillis = now
            syncStatusRepository.markSuccess(SYNC_KEY, now)
        }
    }

    /** Copied on the listener thread; StatusBarNotification is not touched afterwards. */
    private class Captured(val candidate: DigestCandidate, val title: String, val text: String?)

    private fun capture(sbn: StatusBarNotification): Captured {
        val n = sbn.notification
        val extras = n.extras
        fun text(key: String): String? = runCatching { extras?.getCharSequence(key)?.toString() }.getOrNull()
        val title = text(Notification.EXTRA_TITLE)
            ?: text(Notification.EXTRA_CONVERSATION_TITLE)
            ?: ""
        val body = text(Notification.EXTRA_TEXT) ?: text(Notification.EXTRA_BIG_TEXT)
        return Captured(
            candidate = DigestCandidate(
                key = sbn.key,
                packageName = sbn.packageName,
                postTimeMillis = sbn.postTime,
                category = n.category,
                isOngoing = sbn.isOngoing || (n.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0,
                isClearable = sbn.isClearable,
                isGroupSummary = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0
            ),
            title = title.trim(),
            text = body?.trim()
        )
    }

    private fun Captured.toEntity(appLabel: String, released: Boolean) = DigestNotification(
        key = candidate.key,
        packageName = candidate.packageName,
        appLabel = appLabel,
        title = title,
        text = text,
        postedAtMillis = candidate.postTimeMillis,
        released = released
    )

    companion object {
        const val SYNC_KEY = "digest"
        private const val MAX_AGE_MILLIS = 24L * 60 * 60 * 1000
    }
}

internal fun DigestNotification.toRow() = DigestRow(
    key = key,
    packageName = packageName,
    appLabel = appLabel,
    title = title,
    text = text,
    postedAtMillis = postedAtMillis,
    released = released
)
