package com.mp01home.launcher.mail

/*
 * MAIL-1/2: pure-JVM parsing of Gmail notifications (decision D1: notification listener only).
 * No Android types here so the rules are unit-testable; GmailNotificationExtractor turns a
 * StatusBarNotification into a [RawMailNotification].
 */

/** One InboxStyle line. [senderEnd] is where the sender's bold span ends, if known. */
data class RawMailLine(val text: String, val senderEnd: Int? = null)

/** The parts of a Gmail notification that matter, copied out of the notification extras. */
data class RawMailNotification(
    val key: String,
    val packageName: String,
    val postTimeMillis: Long,
    val title: String? = null,
    /** EXTRA_TITLE_BIG (InboxStyle big title, often "5 new messages"). */
    val bigTitle: String? = null,
    val text: String? = null,
    val bigText: String? = null,
    val subText: String? = null,
    val summaryText: String? = null,
    val infoText: String? = null,
    /** EXTRA_TEXT_LINES (InboxStyle). */
    val lines: List<RawMailLine> = emptyList(),
    /** Notification.number. */
    val number: Int = 0,
    val isGroupSummary: Boolean = false,
    val isOngoing: Boolean = false,
    val category: String? = null
)

/**
 * A parsed row. Maps 1:1 onto the Room entity `MailNotification`
 * ([isLine] rows are stored with a synthetic key, see [GmailNotificationParser.lineKey]).
 */
data class ParsedMail(
    val key: String,
    val packageName: String,
    val account: String?,
    val sender: String,
    val subject: String,
    val snippet: String? = null,
    val postedAtMillis: Long,
    val isGroupSummary: Boolean = false,
    val unreadCountHint: Int? = null,
    /** Synthetic message row derived from an InboxStyle line of a summary. */
    val isLine: Boolean = false
)

object GmailNotificationParser {

    const val GMAIL_PACKAGE = "com.google.android.gm"
    const val GMAIL_GO_PACKAGE = "com.google.android.gm.lite"
    val GMAIL_PACKAGES: Set<String> = setOf(GMAIL_PACKAGE, GMAIL_GO_PACKAGE)

    /** Synthetic key separator for rows derived from InboxStyle lines. */
    const val LINE_KEY_SEP = "#line"

    /** Upper bound of line rows per summary (InboxStyle shows at most ~7). */
    const val MAX_LINES = 10

    /** Notification.CATEGORY_EMAIL */
    private const val CATEGORY_EMAIL = "email"

    fun isGmailPackage(packageName: String?): Boolean = packageName in GMAIL_PACKAGES

    fun lineKey(key: String, index: Int): String = "$key$LINE_KEY_SEP$index"

    fun isLineKey(key: String): Boolean = key.contains(LINE_KEY_SEP)

    /** Keys that must be deleted when the notification [key] goes away or is replaced. */
    fun keysFor(key: String): List<String> = listOf(key) + (0 until MAX_LINES).map { lineKey(key, it) }

    /**
     * Parses one Gmail notification into 0..n rows: nothing for non-mail notifications
     * ("Sending…", sync errors, Chat), one row for a single message, and for a summary one
     * summary row (carrying the count) plus one row per InboxStyle line.
     */
    fun parse(raw: RawMailNotification): List<ParsedMail> {
        if (!isGmailPackage(raw.packageName)) return emptyList()
        if (raw.isOngoing) return emptyList()
        if (raw.category != null && raw.category != CATEGORY_EMAIL) return emptyList()

        val account = accountOf(raw)
        val isSummary = raw.isGroupSummary || raw.lines.isNotEmpty()
        return if (isSummary) parseSummary(raw, account) else parseSingle(raw, account)
    }

    private fun parseSingle(raw: RawMailNotification, account: String?): List<ParsedMail> {
        val sender = raw.title.clean()
        val big = raw.bigText.clean()
        val subject = raw.text.clean().ifEmpty { big.lineSequence().firstOrNull().orEmpty().trim() }
        if (sender.isEmpty() && subject.isEmpty()) return emptyList()
        val snippet = when {
            big.isEmpty() || big == subject -> null
            big.startsWith(subject) -> big.removePrefix(subject).trim().ifEmpty { null }
            else -> big
        }
        return listOf(
            ParsedMail(
                key = raw.key,
                packageName = raw.packageName,
                account = account,
                sender = sender,
                subject = subject,
                snippet = snippet,
                postedAtMillis = raw.postTimeMillis
            )
        )
    }

    private fun parseSummary(raw: RawMailNotification, account: String?): List<ParsedMail> {
        val lines = raw.lines.filter { it.text.isNotBlank() }.take(MAX_LINES)
        val summary = ParsedMail(
            key = raw.key,
            packageName = raw.packageName,
            account = account,
            sender = raw.title.clean(),
            subject = raw.text.clean(),
            postedAtMillis = raw.postTimeMillis,
            isGroupSummary = true,
            unreadCountHint = summaryCount(raw, lines.size)
        )
        val lineRows = lines.mapIndexed { index, line ->
            val (sender, subject) = splitLine(line)
            ParsedMail(
                key = lineKey(raw.key, index),
                packageName = raw.packageName,
                account = account,
                sender = sender,
                subject = subject,
                // Gmail lists the newest line first; keep that order when sorting by time.
                postedAtMillis = raw.postTimeMillis - index,
                isLine = true
            )
        }
        return listOf(summary) + lineRows
    }

    /** Best estimate of the unread count a summary stands for, or null if it gives none. */
    fun summaryCount(raw: RawMailNotification, lineCount: Int = raw.lines.size): Int? {
        val fromTitle = parseCount(raw.bigTitle) ?: parseCount(raw.title) ?: parseCount(raw.text)
        val more = parseMore(raw.summaryText) ?: parseMore(raw.text) ?: 0
        val fromLines = if (lineCount > 0) lineCount + more else 0
        val best = maxOf(fromTitle ?: 0, fromLines, raw.number.coerceAtLeast(0))
        return best.takeIf { it > 0 }
    }

    // A standalone number, not part of an e-mail address or word ("john123@x.com" → none).
    private val COUNT_REGEX = Regex("""(?<![\w@.+])(\d{1,5})(?![\w@.])""")
    private val MORE_REGEX = Regex("""\+\s*(\d{1,5})""")

    /** "5 new messages" → 5, "99+ new" → 99, "Neue Nachrichten: 3" → 3; e-mail addresses ignored. */
    fun parseCount(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        return COUNT_REGEX.find(text)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }
    }

    /** "+3 more" → 3. */
    fun parseMore(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        return MORE_REGEX.find(text)?.groupValues?.get(1)?.toIntOrNull()
    }

    private val LINE_SEPARATORS = listOf("\t", "  ", " — ", " – ", " - ")

    /** InboxStyle line → (sender, subject). Uses the bold span end if known, else a separator. */
    fun splitLine(line: RawMailLine): Pair<String, String> {
        val text = line.text
        val end = line.senderEnd
        if (end != null && end in 1 until text.length) {
            val sender = text.substring(0, end).trim()
            val subject = text.substring(end).trim()
            if (sender.isNotEmpty()) return sender to subject
        }
        val best = LINE_SEPARATORS
            .mapNotNull { sep -> text.indexOf(sep).takeIf { it > 0 }?.let { it to sep } }
            .minByOrNull { it.first }
        if (best != null) {
            val (index, sep) = best
            val sender = text.substring(0, index).trim()
            val subject = text.substring(index + sep.length).trim()
            if (sender.isNotEmpty()) return sender to subject
        }
        return "" to text.trim()
    }

    private fun accountOf(raw: RawMailNotification): String? =
        listOf(raw.subText, raw.summaryText, raw.infoText)
            .map { it.clean() }
            .firstOrNull { it.looksLikeEmail() }

    private fun String.looksLikeEmail(): Boolean {
        val at = indexOf('@')
        return at > 0 && at < length - 1 && ' ' !in this
    }

    private fun String?.clean(): String = this?.replace(' ', ' ')?.trim().orEmpty()
}
