package com.mp01home.launcher.digest

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

class DigestSummarizerTest {

    private val zone = ZoneOffset.UTC
    private val times = listOf(LocalTime.of(12, 0), LocalTime.of(18, 0))
    private fun millis(h: Int, m: Int) = ZonedDateTime.of(2026, 9, 26, h, m, 0, 0, zone).toInstant().toEpochMilli()

    private fun row(key: String, pkg: String, t: Long, released: Boolean, title: String = "t$key") =
        DigestRow(key, pkg, pkg.substringAfterLast('.'), title, null, t, released)

    @Test
    fun groupsVisibleRowsByAppNewestFirst() {
        val rows = listOf(
            row("1", "com.slack", millis(10, 0), true),
            row("2", "com.slack", millis(10, 30), true, title = "latest"),
            row("3", "com.signal", millis(10, 45), true),
            row("4", "com.game", millis(11, 0), false),
            row("5", "com.news", millis(11, 5), false)
        )
        val v = DigestSummarizer.summarize(rows, millis(11, 30), zone, times)
        assertEquals(listOf("com.signal", "com.slack"), v.groups.map { it.packageName })
        assertEquals(2, v.groups[1].count)
        assertEquals("latest", v.groups[1].latestTitle)
        assertEquals(listOf("2", "1"), v.groups[1].keys)
        assertEquals(2, v.heldCount)
        assertEquals(2, v.heldApps)
        assertEquals(millis(12, 0), v.nextReleaseMillis)
        assertEquals(3, v.totalVisible)
    }

    @Test
    fun heldRowsReleaseAtDigestTime() {
        val rows = listOf(row("4", "com.game", millis(11, 0), false))
        val before = DigestSummarizer.summarize(rows, millis(11, 59), zone, times)
        assertEquals(1, before.heldCount)
        val after = DigestSummarizer.summarize(rows, millis(12, 0), zone, times)
        assertEquals(0, after.heldCount)
        assertEquals("com.game", after.groups.single().packageName)
        assertEquals(millis(18, 0), after.nextReleaseMillis)
    }

    @Test
    fun blankLabelAndTitleFallBack() {
        val r = DigestRow("1", "com.x", "", "", "body", millis(10, 0), true)
        val g = DigestSummarizer.summarize(listOf(r), millis(11, 0), zone, times).groups.single()
        assertEquals("com.x", g.appLabel)
        assertEquals("body", g.latestTitle)
    }
}
