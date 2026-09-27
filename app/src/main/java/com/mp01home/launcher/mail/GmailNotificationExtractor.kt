package com.mp01home.launcher.mail

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.text.Spanned
import android.text.style.CharacterStyle

/** Copies what [GmailNotificationParser] needs out of a StatusBarNotification (MAIL-1/2). */
object GmailNotificationExtractor {

    fun extract(sbn: StatusBarNotification): RawMailNotification {
        val n: Notification = sbn.notification
        val extras: Bundle = n.extras ?: Bundle.EMPTY
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.filterNotNull()
            ?.map { RawMailLine(text = it.toString(), senderEnd = leadingStyledEnd(it)) }
            .orEmpty()
        return RawMailNotification(
            key = sbn.key,
            packageName = sbn.packageName,
            postTimeMillis = sbn.postTime,
            title = extras.text(Notification.EXTRA_TITLE),
            bigTitle = extras.text(Notification.EXTRA_TITLE_BIG),
            text = extras.text(Notification.EXTRA_TEXT),
            bigText = extras.text(Notification.EXTRA_BIG_TEXT),
            subText = extras.text(Notification.EXTRA_SUB_TEXT),
            summaryText = extras.text(Notification.EXTRA_SUMMARY_TEXT),
            infoText = extras.text(Notification.EXTRA_INFO_TEXT),
            lines = lines,
            number = n.number,
            isGroupSummary = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
            isOngoing = sbn.isOngoing || (n.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0,
            category = n.category
        )
    }

    private fun Bundle.text(key: String): String? =
        runCatching { getCharSequence(key)?.toString() }.getOrNull()

    /**
     * Gmail styles the sender at the start of each InboxStyle line (bold / text appearance).
     * Returns the end of the styled prefix, or null if there is none or it covers the whole line.
     */
    private fun leadingStyledEnd(text: CharSequence): Int? {
        val spanned = text as? Spanned ?: return null
        val end = spanned.getSpans(0, spanned.length, CharacterStyle::class.java)
            .filter { spanned.getSpanStart(it) == 0 }
            .maxOfOrNull { spanned.getSpanEnd(it) }
            ?: return null
        return end.takeIf { it in 1 until spanned.length }
    }
}
