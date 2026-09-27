package com.mp01home.launcher.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

class NaturalDateParserTest {

    /** Saturday 26 Sep 2026, 10:00. */
    private val now = LocalDateTime.of(2026, 9, 26, 10, 0)
    private val today = now.toLocalDate()

    private fun parse(text: String, at: LocalDateTime = now, dayFirst: Boolean = false) =
        NaturalDateParser.parse(text, at, dayFirst)

    private fun date(m: Int, d: Int, y: Int = 2026) = LocalDate.of(y, m, d)

    @Test
    fun weekdayWithPmTime() {
        val p = parse("call Sam fri 3pm")
        assertEquals("call Sam", p.title)
        assertEquals(date(10, 2), p.date)
        assertEquals(LocalTime.of(15, 0), p.time)
        assertEquals(LocalDateTime.of(2026, 10, 2, 15, 0), p.dateTime)
    }

    @Test
    fun tomorrow() {
        val p = parse("buy milk tomorrow")
        assertEquals("buy milk", p.title)
        assertEquals(today.plusDays(1), p.date)
        assertNull(p.time)
        assertEquals(today.plusDays(1), parse("tmrw pay rent").date)
        assertEquals("pay rent", parse("tmrw pay rent").title)
    }

    @Test
    fun onlyADateKeepsTheTextAsTitle() {
        val p = parse("tomorrow")
        assertEquals("tomorrow", p.title)
        assertEquals(today.plusDays(1), p.date)
    }

    @Test
    fun todayWith24hTime() {
        val p = parse("standup today 9:30")
        assertEquals("standup", p.title)
        assertEquals(today, p.date)
        assertEquals(LocalTime.of(9, 30), p.time)
    }

    @Test
    fun nextWeekday() {
        // Saturday → next Monday is in the following week anyway.
        assertEquals(date(9, 28), parse("review next mon").date)
        assertEquals("review", parse("review next mon").title)
        assertEquals(date(10, 2), parse("next fri").date)
        // From a Wednesday, "next fri" skips this week's Friday.
        val wed = LocalDateTime.of(2026, 9, 23, 10, 0)
        assertEquals(date(10, 2), parse("next fri", wed).date)
        assertEquals(date(9, 25), parse("fri", wed).date)
        assertEquals(date(9, 25), parse("this friday", wed).date)
        assertEquals(date(9, 28), parse("plan next week").date)
    }

    @Test
    fun weekdayTodayCounts() {
        assertEquals(today, parse("groceries sat").date)
        assertEquals(date(9, 27), parse("groceries sunday").date)
        assertEquals(date(10, 1), parse("thurs meeting").date)
    }

    @Test
    fun relativeDays() {
        val p = parse("renew passport in 2 days")
        assertEquals("renew passport", p.title)
        assertEquals(date(9, 28), p.date)
        assertEquals(date(10, 10), parse("in two weeks check").date)
        assertEquals(date(10, 26), parse("in a month dentist").date)
    }

    @Test
    fun relativeHours() {
        val p = parse("call back in 2 hours")
        assertEquals("call back", p.title)
        assertEquals(today, p.date)
        assertEquals(LocalTime.of(12, 0), p.time)
        val late = parse("in 3 hours", LocalDateTime.of(2026, 9, 26, 23, 0))
        assertEquals(date(9, 27), late.date)
        assertEquals(LocalTime.of(2, 0), late.time)
    }

    @Test
    fun slashDateMonthFirstRollsToNextYear() {
        val p = parse("tax 3/10")
        assertEquals("tax", p.title)
        assertEquals(date(3, 10, 2027), p.date)
        assertEquals(date(10, 3), parse("tax 3/10", dayFirst = true).date)
        assertEquals(date(12, 1), parse("party 12/1").date)
        assertEquals(date(3, 10, 2027), parse("x 3/10/27").date)
    }

    @Test
    fun invalidSlashDateIsLeftAlone() {
        val p = parse("ratio 13/45")
        assertEquals("ratio 13/45", p.title)
        assertNull(p.date)
    }

    @Test
    fun noonAndMidnight() {
        val p = parse("lunch with Ana at noon")
        assertEquals("lunch with Ana", p.title)
        assertEquals(today, p.date)
        assertEquals(LocalTime.NOON, p.time)
        // Past noon already → tomorrow.
        val afternoon = parse("noon", LocalDateTime.of(2026, 9, 26, 13, 0))
        assertEquals(date(9, 27), afternoon.date)
        assertEquals(LocalTime.MIDNIGHT, parse("backup midnight").time)
    }

    @Test
    fun timeFormats() {
        assertEquals(LocalTime.of(15, 30), parse("x 3:30pm").time)
        assertEquals(LocalTime.of(15, 0), parse("x 3 pm").time)
        assertEquals(LocalTime.of(9, 0), parse("x 9am").time)
        assertEquals(LocalTime.of(0, 15), parse("x 12:15am").time)
        assertEquals(LocalTime.of(12, 0), parse("x 12pm").time)
        assertEquals(LocalTime.of(15, 30), parse("x 15:30").time)
        assertEquals(LocalTime.of(17, 0), parse("call mom at 5").time)
        assertEquals("call mom", parse("call mom at 5").title)
        assertEquals(LocalTime.of(9, 0), parse("gym at 9").time)
        assertEquals(LocalTime.of(15, 0), parse("x @3pm").time)
        assertEquals(LocalTime.of(20, 0), parse("read tonight").time)
        assertEquals(today, parse("read tonight").date)
    }

    @Test
    fun timeOnlyInThePastMeansTomorrow() {
        val p = parse("email 9am")
        assertEquals(date(9, 27), p.date)
        assertEquals(LocalTime.of(9, 0), p.time)
        assertEquals(today, parse("email 11am").date)
    }

    @Test
    fun monthNames() {
        assertEquals(date(10, 3), parse("dinner oct 3").date)
        assertEquals("dinner", parse("dinner oct 3").title)
        assertEquals(date(10, 3, 2027), parse("dinner 3rd october 2027").date)
        assertEquals(date(10, 3), parse("dinner 3 of october").date)
        assertEquals(date(1, 5, 2027), parse("jan 5 renew").date)
        // "may" without a day is just a word.
        val p = parse("I may call")
        assertEquals("I may call", p.title)
        assertNull(p.date)
    }

    @Test
    fun isoDate() {
        assertEquals(date(10, 5), parse("launch 2026-10-05").date)
    }

    @Test
    fun connectorsAndPunctuationAreStripped() {
        assertEquals("meeting", parse("meeting on fri at 3pm").title)
        assertEquals("call Sam", parse("call Sam, fri 3pm").title)
        assertEquals("pay invoice", parse("pay invoice due tomorrow").title)
        assertEquals("Call Sam", parse("Call Sam FRI 3PM").title)
    }

    @Test
    fun plainTextIsUntouched() {
        val p = parse("call 3 people about the plan")
        assertEquals("call 3 people about the plan", p.title)
        assertNull(p.date)
        assertNull(p.time)
        assertEquals("", parse("   ").title)
    }

    @Test
    fun dayFirstLocales() {
        assertFalse(NaturalDateParser.isDayFirst(Locale.US))
        assertTrue(NaturalDateParser.isDayFirst(Locale.UK))
        assertTrue(NaturalDateParser.isDayFirst(Locale.GERMANY))
    }
}
