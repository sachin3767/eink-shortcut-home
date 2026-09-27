package com.mp01home.launcher.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GmailNotificationParserTest {

    private val gm = GmailNotificationParser.GMAIL_PACKAGE

    private fun raw(
        key: String = "k",
        title: String? = null,
        text: String? = null,
        bigText: String? = null,
        subText: String? = null,
        summaryText: String? = null,
        bigTitle: String? = null,
        lines: List<RawMailLine> = emptyList(),
        number: Int = 0,
        isGroupSummary: Boolean = false,
        isOngoing: Boolean = false,
        category: String? = "email",
        pkg: String = gm
    ) = RawMailNotification(
        key = key, packageName = pkg, postTimeMillis = 1_000L, title = title, bigTitle = bigTitle,
        text = text, bigText = bigText, subText = subText, summaryText = summaryText,
        lines = lines, number = number, isGroupSummary = isGroupSummary, isOngoing = isOngoing,
        category = category
    )

    @Test
    fun singleMessage() {
        val rows = GmailNotificationParser.parse(
            raw(title = "Alice Smith", text = "Lunch?", bigText = "Lunch?\nAre you free at noon", subText = "me@gmail.com")
        )
        assertEquals(1, rows.size)
        val r = rows[0]
        assertEquals("Alice Smith", r.sender)
        assertEquals("Lunch?", r.subject)
        assertEquals("Are you free at noon", r.snippet)
        assertEquals("me@gmail.com", r.account)
        assertFalse(r.isGroupSummary)
        assertFalse(r.isLine)
    }

    @Test
    fun subjectFallsBackToBigTextFirstLine() {
        val r = GmailNotificationParser.parse(raw(title = "Bob", bigText = "Invoice\nbody")).single()
        assertEquals("Invoice", r.subject)
    }

    @Test
    fun ignoresOngoingNonMailAndOtherPackages() {
        assertTrue(GmailNotificationParser.parse(raw(title = "Sending…", isOngoing = true)).isEmpty())
        assertTrue(GmailNotificationParser.parse(raw(title = "Chat", text = "hi", category = "msg")).isEmpty())
        assertTrue(GmailNotificationParser.parse(raw(title = "x", text = "y", pkg = "com.other")).isEmpty())
        assertTrue(GmailNotificationParser.parse(raw(title = " ", text = null)).isEmpty())
    }

    @Test
    fun nullCategoryAccepted() {
        assertEquals(1, GmailNotificationParser.parse(raw(title = "A", text = "B", category = null)).size)
    }

    @Test
    fun inboxStyleSummaryWithSpans() {
        val rows = GmailNotificationParser.parse(
            raw(
                key = "sum",
                title = "3 new messages",
                subText = "me@gmail.com",
                lines = listOf(
                    RawMailLine("Alice   Lunch?", senderEnd = 5),
                    RawMailLine("Bob Invoice 42", senderEnd = 3)
                ),
                isGroupSummary = true
            )
        )
        assertEquals(3, rows.size)
        val summary = rows[0]
        assertTrue(summary.isGroupSummary)
        assertEquals(3, summary.unreadCountHint)
        assertEquals("sum#line0", rows[1].key)
        assertEquals("Alice", rows[1].sender)
        assertEquals("Lunch?", rows[1].subject)
        assertEquals("Bob", rows[2].sender)
        assertEquals("Invoice 42", rows[2].subject)
        assertTrue(rows[1].postedAtMillis > rows[2].postedAtMillis)
        assertTrue(rows[1].isLine)
    }

    @Test
    fun linesWithoutSpansUseSeparators() {
        assertEquals("Alice" to "Hi there", GmailNotificationParser.splitLine(RawMailLine("Alice  Hi there")))
        assertEquals("Alice" to "Hi - there", GmailNotificationParser.splitLine(RawMailLine("Alice - Hi - there")))
        assertEquals("" to "Just a subject", GmailNotificationParser.splitLine(RawMailLine("Just a subject")))
        // A span covering the whole line is useless: fall back.
        assertEquals("" to "Whole", GmailNotificationParser.splitLine(RawMailLine("Whole", senderEnd = 5)))
    }

    @Test
    fun countUsesMoreSuffixAndNumber() {
        val lines = listOf(RawMailLine("A  x"), RawMailLine("B  y"))
        assertEquals(5, GmailNotificationParser.summaryCount(raw(title = "me@gmail.com", summaryText = "+3 more", lines = lines)))
        assertEquals(7, GmailNotificationParser.summaryCount(raw(title = "me@gmail.com", number = 7, isGroupSummary = true)))
        assertEquals(12, GmailNotificationParser.summaryCount(raw(bigTitle = "12 new messages", title = "Gmail", lines = lines)))
        assertNull(GmailNotificationParser.summaryCount(raw(title = "me@gmail.com", isGroupSummary = true)))
    }

    @Test
    fun parseCountIsRobust() {
        assertEquals(5, GmailNotificationParser.parseCount("5 new messages"))
        assertEquals(99, GmailNotificationParser.parseCount("99+ new emails"))
        assertEquals(3, GmailNotificationParser.parseCount("Neue Nachrichten: 3"))
        assertEquals(4, GmailNotificationParser.parseCount("4件の新着メール"))
        assertNull(GmailNotificationParser.parseCount("john123@gmail.com"))
        assertNull(GmailNotificationParser.parseCount("+3 more"))
        assertNull(GmailNotificationParser.parseCount(null))
        assertNull(GmailNotificationParser.parseCount("0 new"))
        assertEquals(3, GmailNotificationParser.parseMore("+3 more"))
    }

    @Test
    fun accountOnlyWhenEmailLike() {
        assertNull(GmailNotificationParser.parse(raw(title = "A", text = "B", subText = "Inbox")).single().account)
        assertEquals(
            "x@y.com",
            GmailNotificationParser.parse(raw(title = "A", text = "B", summaryText = "x@y.com")).single().account
        )
    }

    @Test
    fun keysForCoversLines() {
        val keys = GmailNotificationParser.keysFor("k")
        assertEquals("k", keys.first())
        assertEquals(GmailNotificationParser.MAX_LINES + 1, keys.size)
        assertTrue(GmailNotificationParser.isLineKey(keys.last()))
    }
}
