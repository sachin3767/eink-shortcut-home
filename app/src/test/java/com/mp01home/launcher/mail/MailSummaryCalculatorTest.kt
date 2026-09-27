package com.mp01home.launcher.mail

import org.junit.Assert.assertEquals
import org.junit.Test

class MailSummaryCalculatorTest {

    private fun msg(key: String, t: Long, account: String? = "a@x.com") = ParsedMail(
        key = key, packageName = "com.google.android.gm", account = account,
        sender = "S$key", subject = "Subj$key", postedAtMillis = t
    )

    private fun summary(key: String, count: Int?, account: String? = "a@x.com") = ParsedMail(
        key = key, packageName = "com.google.android.gm", account = account,
        sender = "", subject = "", postedAtMillis = 0, isGroupSummary = true, unreadCountHint = count
    )

    private fun line(key: String, t: Long, account: String? = "a@x.com") = msg(key, t, account).copy(isLine = true)

    @Test
    fun empty() {
        assertEquals(MailSummary.EMPTY, MailSummaryCalculator.summarize(emptyList()))
    }

    @Test
    fun childrenCountedAndTopThreeNewestFirst() {
        val s = MailSummaryCalculator.summarize(listOf(msg("1", 10), msg("2", 30), msg("3", 20), msg("4", 5)))
        assertEquals(4, s.unreadCount)
        assertEquals(listOf("S2", "S3", "S1"), s.latest.map { it.sender })
    }

    @Test
    fun summaryCountWinsWhenLarger() {
        val s = MailSummaryCalculator.summarize(listOf(msg("1", 10), msg("2", 20), summary("s", 9)))
        assertEquals(9, s.unreadCount)
        assertEquals(2, s.latest.size)
    }

    @Test
    fun linesOnlyUsedWithoutChildren() {
        val withChildren = listOf(msg("1", 10), summary("s", null), line("s#line0", 50), line("s#line1", 49))
        assertEquals(1, MailSummaryCalculator.summarize(withChildren).unreadCount)
        assertEquals("S1", MailSummaryCalculator.summarize(withChildren).latest.single().sender)

        val linesOnly = listOf(summary("s", null), line("s#line0", 50), line("s#line1", 49))
        assertEquals(2, MailSummaryCalculator.summarize(linesOnly).unreadCount)
    }

    @Test
    fun accountsAreSummed() {
        val rows = listOf(msg("1", 10, "a@x.com"), summary("s", 4, "b@x.com"), msg("2", 20, "B@x.com"))
        assertEquals(5, MailSummaryCalculator.summarize(rows).unreadCount)
    }
}
