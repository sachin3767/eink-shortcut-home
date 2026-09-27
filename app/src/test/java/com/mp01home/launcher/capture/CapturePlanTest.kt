package com.mp01home.launcher.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class CapturePlanTest {

    private val now = LocalDateTime.of(2026, 9, 26, 10, 0)
    private val zone: ZoneId = ZoneOffset.ofHours(2)

    @Test
    fun blankInputIsIgnored() {
        assertNull(planCapture("  ", CaptureTarget.TASK, now, zone))
    }

    @Test
    fun taskGetsParsedTitleAndDueDate() {
        val plan = planCapture("call Sam fri 3pm", CaptureTarget.TASK, now, zone)
        assertEquals(CapturePlan.AddTask("call Sam", LocalDate.of(2026, 10, 2)), plan)
        assertEquals(CapturePlan.AddTask("buy milk", null), planCapture("buy milk", CaptureTarget.TASK, now, zone))
    }

    @Test
    fun keepGetsRawText() {
        assertEquals(
            CapturePlan.KeepNote("idea: fri 3pm retro"),
            planCapture(" idea: fri 3pm retro ", CaptureTarget.KEEP, now, zone)
        )
    }

    @Test
    fun timedEventLastsAnHour() {
        val plan = planCapture("call Sam fri 3pm", CaptureTarget.EVENT, now, zone) as CapturePlan.InsertEvent
        val begin = LocalDateTime.of(2026, 10, 2, 15, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals("call Sam", plan.title)
        assertEquals(begin, plan.beginMillis)
        assertEquals(begin + 3_600_000L, plan.endMillis)
        assertEquals(false, plan.allDay)
    }

    @Test
    fun dateOnlyEventIsAllDayInUtc() {
        val plan = planCapture("holiday tomorrow", CaptureTarget.EVENT, now, zone) as CapturePlan.InsertEvent
        val begin = LocalDate.of(2026, 9, 27).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(true, plan.allDay)
        assertEquals(begin, plan.beginMillis)
        assertEquals(begin + 86_400_000L, plan.endMillis)
    }

    @Test
    fun undatedEventLeavesTimesToCalendarApp() {
        assertEquals(
            CapturePlan.InsertEvent("coffee", null, null, false),
            planCapture("coffee", CaptureTarget.EVENT, now, zone)
        )
    }

    @Test
    fun describe() {
        val today = now.toLocalDate()
        fun d(text: String) = describeParsedWhen(NaturalDateParser.parse(text, now), today, locale = Locale.UK)
        assertEquals("today 12:00", d("x noon"))
        assertEquals("tomorrow", d("x tomorrow"))
        assertEquals("Fri 2 Oct 15:00", d("x fri 3pm"))
        assertEquals("Wed 10 Mar 2027", d("x 3/10"))
        assertNull(d("nothing here"))
        assertEquals(
            "today 3:00 PM",
            describeParsedWhen(ParsedCapture("x", today, LocalTime.of(15, 0)), today, use24h = false, locale = Locale.US)
        )
    }
}
