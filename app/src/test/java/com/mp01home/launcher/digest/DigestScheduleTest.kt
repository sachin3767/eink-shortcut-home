package com.mp01home.launcher.digest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class DigestScheduleTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val times = listOf(LocalTime.of(12, 0), LocalTime.of(18, 0))

    private fun at(day: Int, h: Int, m: Int, month: Int = 9) = ZonedDateTime.of(2026, month, day, h, m, 0, 0, zone)

    @Test
    fun nextLaterToday() {
        assertEquals(at(26, 12, 0), DigestSchedule.next(at(26, 9, 0), times))
        assertEquals(at(26, 18, 0), DigestSchedule.next(at(26, 12, 0), times)) // strictly after
    }

    @Test
    fun nextWrapsToTomorrow() {
        assertEquals(at(27, 12, 0), DigestSchedule.next(at(26, 19, 0), times))
    }

    @Test
    fun previousIncludesNowAndYesterday() {
        assertEquals(at(26, 12, 0), DigestSchedule.previous(at(26, 12, 0), times))
        assertEquals(at(25, 18, 0), DigestSchedule.previous(at(26, 8, 0), times))
        assertEquals(at(26, 18, 0), DigestSchedule.previous(at(26, 23, 59), times))
    }

    @Test
    fun unsortedTimesWork() {
        val unsorted = listOf(LocalTime.of(18, 0), LocalTime.of(7, 30), LocalTime.of(12, 0))
        assertEquals(at(26, 7, 30), DigestSchedule.next(at(26, 1, 0), unsorted))
    }

    @Test
    fun emptyTimes() {
        assertNull(DigestSchedule.next(at(26, 9, 0), emptyList()))
        assertNull(DigestSchedule.previous(at(26, 9, 0), emptyList()))
    }

    @Test
    fun dstSpringForwardGap() {
        // 2026-03-29 02:00-03:00 does not exist in Berlin; 02:30 resolves to 03:30.
        val gapTimes = listOf(LocalTime.of(2, 30))
        val next = DigestSchedule.next(ZonedDateTime.of(2026, 3, 29, 1, 0, 0, 0, zone), gapTimes)!!
        assertEquals(LocalTime.of(3, 30), next.toLocalTime())
    }

    @Test
    fun millisVariantsMatch() {
        val now = at(26, 9, 0)
        assertEquals(
            at(26, 12, 0).toInstant().toEpochMilli(),
            DigestSchedule.nextMillis(now.toInstant().toEpochMilli(), zone, times)
        )
        assertEquals(
            at(25, 18, 0).toInstant().toEpochMilli(),
            DigestSchedule.previousMillis(now.toInstant().toEpochMilli(), zone, times)
        )
    }
}
