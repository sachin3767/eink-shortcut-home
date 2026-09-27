package com.mp01home.launcher.mail

/** One message shown on home (MAIL-2). */
data class MailItem(val sender: String, val subject: String, val postedAtMillis: Long)

/** What MailSection renders: unread estimate (MAIL-1) + latest messages (MAIL-2). */
data class MailSummary(val unreadCount: Int, val latest: List<MailItem>) {
    companion object {
        val EMPTY = MailSummary(0, emptyList())
    }
}

/** Pure aggregation of cached rows into a [MailSummary]. */
object MailSummaryCalculator {

    /**
     * Per account: messages = real per-message notifications, or (when Gmail only posted a
     * summary) its InboxStyle lines; unread = max(messages, count stated by the summary).
     * Accounts are summed. [latest] prefers real messages and falls back to lines.
     */
    fun summarize(rows: List<ParsedMail>, topN: Int = 3): MailSummary {
        if (rows.isEmpty()) return MailSummary.EMPTY
        var total = 0
        val candidates = mutableListOf<ParsedMail>()
        for ((_, accountRows) in rows.groupBy { it.account?.lowercase().orEmpty() }) {
            val messages = accountRows.filter { !it.isGroupSummary && !it.isLine }
            val lines = accountRows.filter { it.isLine }
            val shown = messages.ifEmpty { lines }
            val stated = accountRows.filter { it.isGroupSummary }.maxOfOrNull { it.unreadCountHint ?: 0 } ?: 0
            total += maxOf(shown.size, stated)
            candidates += shown
        }
        val latest = candidates
            .sortedByDescending { it.postedAtMillis }
            .take(topN)
            .map { MailItem(sender = it.sender, subject = it.subject, postedAtMillis = it.postedAtMillis) }
        return MailSummary(total, latest)
    }
}
