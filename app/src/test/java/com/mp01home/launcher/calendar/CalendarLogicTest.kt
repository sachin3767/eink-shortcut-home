package com.mp01home.launcher.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class CalendarLogicTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private val day: LocalDate = LocalDate.of(2026, 9, 28) // a Monday
    private val min = 60_000L

    private fun at(h: Int, m: Int = 0, date: LocalDate = day): Long =
        date.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    private fun range(h1: Int, m1: Int, h2: Int, m2: Int) = TimeRange(at(h1, m1), at(h2, m2))

    private fun event(
        id: Long,
        begin: Long,
        end: Long,
        title: String = "E$id",
        location: String? = null,
        allDay: Boolean = false
    ) = CalEvent(id, id, title, location, begin, end, allDay, null)

    // ---- CAL-4 ------------------------------------------------------------------------------

    @Test
    fun extractsGoogleMeet() {
        assertEquals(
            "https://meet.google.com/abc-defg-hij",
            MeetingLinks.extract(null, "Join: https://meet.google.com/abc-defg-hij.")
        )
    }

    @Test
    fun extractsMeetWithoutScheme() {
        assertEquals("https://meet.google.com/abc-defg-hij", MeetingLinks.extract("meet.google.com/abc-defg-hij"))
    }

    @Test
    fun extractsZoomWithSubdomainAndQuery() {
        val text = "Zoom: https://us02web.zoom.us/j/123456789?pwd=AbC123, see you"
        assertEquals("https://us02web.zoom.us/j/123456789?pwd=AbC123", MeetingLinks.extract(text))
    }

    @Test
    fun extractsTeamsFromHtml() {
        val html = """<a href="https://teams.microsoft.com/l/meetup-join/19%3ameeting_x%40thread.v2/0?context=%7b%22Tid%22%7d&amp;x=1">Join</a>"""
        assertEquals(
            "https://teams.microsoft.com/l/meetup-join/19%3ameeting_x%40thread.v2/0?context=%7b%22Tid%22%7d&x=1",
            MeetingLinks.extract(html)
        )
    }

    @Test
    fun ignoresNonMeetingUrls() {
        assertNull(MeetingLinks.extract("Agenda at https://docs.google.com/doc/1 and https://zoom.us/pricing"))
        assertNull(MeetingLinks.extract(null, "", "Room 4"))
    }

    @Test
    fun locationWinsOverDescription() {
        assertEquals(
            "https://meet.google.com/aaa-bbbb-ccc",
            MeetingLinks.extract("https://meet.google.com/aaa-bbbb-ccc", "https://zoom.us/j/1")
        )
    }

    @Test
    fun upgradesHttpToHttps() {
        assertEquals("https://zoom.us/j/42", MeetingLinks.extract("http://zoom.us/j/42"))
    }

    @Test
    fun joinWindow() {
        val url = "https://zoom.us/j/1"
        val begin = at(10)
        val end = at(11)
        assertFalse(MeetingLinks.shouldShowJoin(begin - 11 * min, begin, end, url))
        assertTrue(MeetingLinks.shouldShowJoin(begin - 10 * min, begin, end, url))
        assertTrue(MeetingLinks.shouldShowJoin(begin + 30 * min, begin, end, url))
        assertFalse(MeetingLinks.shouldShowJoin(end, begin, end, url))
        assertFalse(MeetingLinks.shouldShowJoin(begin, begin, end, null))
    }

    // ---- CAL-1 ------------------------------------------------------------------------------

    @Test
    fun countdown() {
        val begin = at(10)
        val end = at(11)
        assertEquals("in 25 min", countdownLabel(begin - 25 * min, begin, end))
        assertEquals("in 25 min", countdownLabel(begin - 24 * min - 30_000, begin, end))
        assertEquals("in 1 h 5 min", countdownLabel(begin - 65 * min, begin, end))
        assertEquals("in 2 h", countdownLabel(begin - 120 * min, begin, end))
        assertEquals("in 1 d 2 h", countdownLabel(begin - 26 * 60 * min, begin, end))
        assertEquals("now · 45 min left", countdownLabel(begin + 15 * min, begin, end))
        assertEquals("ended", countdownLabel(end, begin, end))
    }

    @Test
    fun timeLabel() {
        val now = at(8)
        assertEquals("10:00–11:00", eventTimeLabel(at(10), at(11), false, now, zone, true, Locale.US))
        assertEquals(
            "tmrw 09:00–09:30",
            eventTimeLabel(at(9, date = day.plusDays(1)), at(9, 30, day.plusDays(1)), false, now, zone, true, Locale.US)
        )
        assertEquals("Thu 9:00 AM–9:30 AM", eventTimeLabel(at(9, date = day.plusDays(3)), at(9, 30, day.plusDays(3)), false, now, zone, false, Locale.US))
        assertEquals("all day", eventTimeLabel(at(0), at(0, date = day.plusDays(1)), true, now, zone, true))
    }

    @Test
    fun allDayConversion() {
        val utcMidnight = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(at(0), allDayUtcToLocal(utcMidnight, zone))
        val ny = ZoneId.of("America/New_York")
        assertEquals(day.atStartOfDay(ny).toInstant().toEpochMilli(), allDayUtcToLocal(utcMidnight, ny))
    }

    // ---- CAL-5 ------------------------------------------------------------------------------

    @Test
    fun mergesOverlappingAndAdjacent() {
        val merged = mergeBusy(
            listOf(range(9, 0, 10, 0), range(9, 30, 10, 30), range(10, 30, 11, 0), range(13, 0, 14, 0)),
            at(8), at(20)
        )
        assertEquals(listOf(range(9, 0, 11, 0), range(13, 0, 14, 0)), merged)
    }

    @Test
    fun freeGapsWithMinimum() {
        val busy = listOf(range(9, 0, 10, 0), range(10, 10, 12, 0), range(19, 0, 21, 0))
        val gaps = freeGaps(busy, at(8), at(20), 15 * min)
        assertEquals(listOf(range(8, 0, 9, 0), range(12, 0, 19, 0)), gaps)
    }

    @Test
    fun freeGapsWholeWindowWhenNoEvents() {
        assertEquals(listOf(range(8, 0, 20, 0)), freeGaps(emptyList(), at(8), at(20)))
        assertTrue(freeGaps(listOf(range(7, 0, 21, 0)), at(8), at(20)).isEmpty())
    }

    @Test
    fun stripTilesWindow() {
        val segs = stripSegments(listOf(range(9, 0, 10, 0), range(12, 0, 13, 0)), at(8), at(20))
        assertEquals(5, segs.size)
        assertEquals(listOf(false, true, false, true, false), segs.map { it.busy })
        assertEquals(at(8), segs.first().range.startMillis)
        assertEquals(at(20), segs.last().range.endMillis)
        segs.zipWithNext().forEach { (a, b) -> assertEquals(a.range.endMillis, b.range.startMillis) }
    }

    @Test
    fun stripWindowWidensToEvents() {
        assertEquals(range(8, 0, 20, 0), stripWindow(day, zone, emptyList()))
        assertEquals(range(7, 0, 22, 0), stripWindow(day, zone, listOf(range(7, 30, 8, 30), range(21, 0, 21, 15))))
    }

    @Test
    fun gapsLabel() {
        val gaps = listOf(range(8, 0, 9, 0), range(12, 0, 14, 0), range(15, 0, 16, 0))
        assertEquals("free now–09:00 · 12:00–14:00 +1", freeGapsLabel(gaps, at(8, 30), zone, true, 2, Locale.US))
        assertEquals("no free time left today", freeGapsLabel(emptyList(), at(8), zone, true))
    }

    // ---- PLAN-4 -----------------------------------------------------------------------------

    @Test
    fun findsFirstFreeSlotAfterNowRounded() {
        val busy = listOf(range(9, 0, 10, 0), range(10, 30, 11, 0))
        // now 08:50 -> rounded 09:00, busy until 10:00, 10:00-10:30 fits 30 min
        assertEquals(range(10, 0, 10, 30), findFreeSlot(busy, at(8, 50), 30 * min, zone))
        // 60 min doesn't fit at 10:00 -> 11:00
        assertEquals(range(11, 0, 12, 0), findFreeSlot(busy, at(8, 50), 60 * min, zone))
    }

    @Test
    fun rollsOverToNextDayAfterWorkingHours() {
        val next = day.plusDays(1)
        val slot = findFreeSlot(emptyList(), at(19, 45), 30 * min, zone)
        assertEquals(TimeRange(at(8, 0, next), at(8, 30, next)), slot)
    }

    @Test
    fun startsAtDayStartWhenEarly() {
        assertEquals(range(8, 0, 8, 30), findFreeSlot(emptyList(), at(6, 3), 30 * min, zone))
    }

    @Test
    fun noSlotWhenFullyBooked() {
        val busy = (0 until 3).map { TimeRange(at(0, date = day.plusDays(it.toLong())), at(23, date = day.plusDays(it.toLong()))) }
        assertNull(findFreeSlot(busy, at(7), 30 * min, zone, maxDays = 3))
    }

    @Test
    fun busyIgnoresAllDay() {
        val events = listOf(event(1, at(0), at(0, date = day.plusDays(1)), allDay = true), event(2, at(9), at(10)))
        assertEquals(listOf(range(9, 0, 10, 0)), busyRanges(events))
    }

    // ---- CAL-6 ------------------------------------------------------------------------------

    @Test
    fun leaveByPicksNextEventWithPlace() {
        val events = listOf(
            event(1, at(9), at(10), location = "https://meet.google.com/abc-defg-hij"),
            event(2, at(11), at(12), location = "Microsoft Teams Meeting"),
            event(3, at(13), at(14), location = null),
            event(4, at(15), at(16), location = "Dentist, Main St 5"),
            event(5, at(17), at(18), location = "Gym")
        )
        val target = pickLeaveByTarget(events, at(8), 30 * min)
        assertEquals(4L, target?.first?.instanceId)
        assertEquals(at(14, 30), target?.second)
    }

    @Test
    fun leaveBySkipsWhenLeaveTimePassed() {
        val events = listOf(event(1, at(9), at(10), location = "Office"), event(2, at(12), at(13), location = "Cafe"))
        assertEquals(2L, pickLeaveByTarget(events, at(8, 45), 30 * min)?.first?.instanceId)
        assertNull(pickLeaveByTarget(events, at(12), 30 * min))
    }

    @Test
    fun physicalLocation() {
        assertTrue(isPhysicalLocation("Room 4"))
        assertTrue(isPhysicalLocation("Office https://maps.google.com/x"))
        assertFalse(isPhysicalLocation("  "))
        assertFalse(isPhysicalLocation("https://us02web.zoom.us/j/1"))
        assertFalse(isPhysicalLocation("Zoom"))
    }

    // ---- PLAN-3 -----------------------------------------------------------------------------

    @Test
    fun weekGroupsEventsAndTasks() {
        val events = listOf(
            event(1, at(9), at(10), "Standup"),
            event(2, at(22, date = day.plusDays(1)), at(2, date = day.plusDays(2)), "Night"),
            event(3, at(0, date = day.plusDays(2)), at(0, date = day.plusDays(3)), "Holiday", allDay = true),
            event(4, at(9, date = day.plusDays(9)), at(10, date = day.plusDays(9)), "Far")
        )
        val today = day.toEpochDay()
        val tasks = listOf(today - 2, today, today + 2, null, today + 10)
        val week = buildWeek(day, zone, events, tasks)
        assertEquals(7, week.size)
        assertEquals(listOf("Standup"), week[0].events.map { it.title })
        assertEquals(2, week[0].taskCount) // overdue + today
        assertEquals(listOf("Night"), week[1].events.map { it.title })
        assertEquals(listOf("Holiday", "Night"), week[2].events.map { it.title })
        assertEquals(1, week[2].taskCount)
        assertTrue(week.drop(3).all { it.events.isEmpty() })
    }
}
