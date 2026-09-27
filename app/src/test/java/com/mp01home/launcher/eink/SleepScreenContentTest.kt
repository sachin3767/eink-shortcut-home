package com.mp01home.launcher.eink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.util.Locale

class SleepScreenContentTest {

    private val now = LocalDateTime.of(2026, 9, 26, 10, 30)

    private fun event(title: String, h: Int, durationH: Long = 1, allDay: Boolean = false): SleepEvent {
        val begin = now.withHour(h).withMinute(0)
        return SleepEvent(title, begin, begin.plusHours(durationH), allDay)
    }

    @Test
    fun listsRemainingEventsTasksAndTop3() {
        val lines = SleepScreenContent.build(
            now = now,
            events = listOf(event("Past", 8), event("Review", 14), event("Holiday", 0, 24, allDay = true)),
            tasks = listOf(SleepTask("Invoice", overdue = true), SleepTask("Call Sam", overdue = false)),
            top3 = listOf(SleepPriority("Ship it", false), SleepPriority("", false), SleepPriority("Gym", true)),
            locale = Locale.UK
        ).map { it.text }

        assertEquals("Saturday 26 September", lines.first())
        assertTrue("1. Ship it" in lines)
        assertTrue("2. [x] Gym" in lines)
        assertFalse(lines.any { "Past" in it })
        // All-day first, then timed.
        assertTrue(lines.indexOf("all day  Holiday") < lines.indexOf("14:00  Review"))
        assertTrue("TASKS  1 overdue" in lines)
        assertTrue("[ ] Invoice" in lines)
    }

    @Test
    fun emptyStateAndOverflow() {
        val empty = SleepScreenContent.build(now, emptyList(), emptyList(), emptyList(), locale = Locale.UK).map { it.text }
        assertTrue("No more events today" in empty)
        assertTrue("Nothing due" in empty)
        assertFalse("TOP 3" in empty)

        val many = (1..9).map { SleepTask("t$it", overdue = false) }
        val lines = SleepScreenContent.build(now, emptyList(), many, emptyList(), locale = Locale.UK).map { it.text }
        assertTrue("+3 more" in lines)
        assertFalse("[ ] t7" in lines)
    }

    @Test
    fun twelveHourFooter() {
        assertEquals("updated 10:30AM", SleepScreenContent.footer(now, use24h = false, locale = Locale.US))
        assertEquals("updated 10:30", SleepScreenContent.footer(now, locale = Locale.UK))
    }
}
