package com.mp01home.launcher.habits

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakCalcTest {

    private val today = LocalDate.of(2026, 9, 26)
    private fun days(vararg offsets: Long) = offsets.map { today.minusDays(it) }.toSet()

    @Test
    fun streakIncludingToday() {
        assertEquals(3, currentStreak(days(0, 1, 2, 4), today))
    }

    @Test
    fun streakStillAliveWhenTodayUnchecked() {
        assertEquals(2, currentStreak(days(1, 2, 4), today))
    }

    @Test
    fun brokenStreak() {
        assertEquals(0, currentStreak(days(2, 3), today))
        assertEquals(0, currentStreak(emptySet(), today))
    }

    @Test
    fun longest() {
        assertEquals(4, longestStreak(days(0, 1, 5, 6, 7, 8, 10)))
        assertEquals(0, longestStreak(emptySet()))
    }

    @Test
    fun parseAndStrip() {
        val parsed = parseIsoDates(listOf("2026-09-25", "garbage", "2026-09-23"))
        assertEquals(days(1, 3), parsed)
        assertEquals("□□■□■", historyStrip(parsed, today, 5))
        assertEquals("", formatStreak(0))
        assertEquals("5d", formatStreak(5))
    }
}
