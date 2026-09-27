package com.mp01home.launcher.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ReviewTriggerTest {

    private val day = LocalDate.of(2026, 9, 26)
    private val schedule = ReviewSchedule(
        morningEnabled = true,
        morningTime = LocalTime.of(7, 0),
        eveningEnabled = true,
        eveningTime = LocalTime.of(20, 0)
    )

    private fun at(h: Int, m: Int = 0) = LocalDateTime.of(day, LocalTime.of(h, m))

    @Test
    fun nothingBeforeMorningTime() {
        assertNull(ReviewTrigger.reviewToShow(at(6, 59), schedule, null, null))
    }

    @Test
    fun morningAtAndAfterItsTime() {
        assertEquals(ReviewKind.MORNING, ReviewTrigger.reviewToShow(at(7, 0), schedule, null, null))
        assertEquals(ReviewKind.MORNING, ReviewTrigger.reviewToShow(at(12, 59), schedule, null, null))
    }

    @Test
    fun morningOnlyOncePerDay() {
        assertNull(ReviewTrigger.reviewToShow(at(8), schedule, day, null))
        // Yesterday's review does not count.
        assertEquals(ReviewKind.MORNING, ReviewTrigger.reviewToShow(at(8), schedule, day.minusDays(1), null))
    }

    @Test
    fun morningWindowExpires() {
        assertNull(ReviewTrigger.reviewToShow(at(13, 0), schedule, null, null))
    }

    @Test
    fun morningWindowEndsAtEveningTime() {
        val s = schedule.copy(morningTime = LocalTime.of(16, 0), eveningTime = LocalTime.of(18, 0))
        assertEquals(ReviewKind.MORNING, ReviewTrigger.reviewToShow(at(17, 59), s, null, null))
        assertEquals(ReviewKind.EVENING, ReviewTrigger.reviewToShow(at(18, 0), s, null, null))
    }

    @Test
    fun eveningFromItsTimeUntilMidnight() {
        assertNull(ReviewTrigger.reviewToShow(at(19, 59), schedule, day, null))
        assertEquals(ReviewKind.EVENING, ReviewTrigger.reviewToShow(at(20, 0), schedule, day, null))
        assertEquals(ReviewKind.EVENING, ReviewTrigger.reviewToShow(at(23, 59), schedule, day, null))
        assertNull(ReviewTrigger.reviewToShow(at(23, 59), schedule, day, day))
    }

    @Test
    fun eveningWinsWhenMorningWasMissed() {
        assertEquals(ReviewKind.EVENING, ReviewTrigger.reviewToShow(at(21), schedule, null, null))
        // ...and the missed morning review is not shown after the evening one.
        assertNull(ReviewTrigger.reviewToShow(at(21), schedule, null, day))
    }

    @Test
    fun disabledReviewsNeverShow() {
        val off = schedule.copy(morningEnabled = false, eveningEnabled = false)
        assertNull(ReviewTrigger.reviewToShow(at(7), off, null, null))
        assertNull(ReviewTrigger.reviewToShow(at(21), off, null, null))
    }

    @Test
    fun morningWindowWithoutEveningClampsAtMidnight() {
        val s = schedule.copy(morningTime = LocalTime.of(22, 0), eveningEnabled = false)
        assertEquals(ReviewKind.MORNING, ReviewTrigger.reviewToShow(at(23, 30), s, null, null))
        assertNull(ReviewTrigger.reviewToShow(LocalDateTime.of(day.plusDays(1), LocalTime.of(0, 30)), s, day, null))
    }
}
