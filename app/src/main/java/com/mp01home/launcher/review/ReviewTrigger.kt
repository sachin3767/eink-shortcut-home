package com.mp01home.launcher.review

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** PLAN-1 / PLAN-2. */
enum class ReviewKind { MORNING, EVENING }

/** The user's review settings, decoupled from [com.mp01home.launcher.data.settings.LauncherSettings]. */
data class ReviewSchedule(
    val morningEnabled: Boolean,
    val morningTime: LocalTime,
    val eveningEnabled: Boolean,
    val eveningTime: LocalTime
)

/**
 * Pure "should a review open now?" rule, evaluated whenever the home screen is resumed (which is
 * what happens on the first unlock / screen-on while the launcher is in front).
 *
 * - Each review opens at most once per calendar day.
 * - The evening review is due from its time until midnight.
 * - The morning review is due from its time for [MORNING_WINDOW_MINUTES] (clamped to midnight),
 *   and never once the evening review time has been reached, so a first unlock at 21:00 does not
 *   show a stale morning review.
 * - If both are due the evening review wins (it is the later, more relevant one).
 */
object ReviewTrigger {

    const val MORNING_WINDOW_MINUTES = 6 * 60

    fun reviewToShow(
        now: LocalDateTime,
        schedule: ReviewSchedule,
        lastMorningShown: LocalDate?,
        lastEveningShown: LocalDate?
    ): ReviewKind? {
        val today = now.toLocalDate()
        val minute = minuteOfDay(now.toLocalTime())

        if (schedule.eveningEnabled && lastEveningShown != today &&
            minute >= minuteOfDay(schedule.eveningTime)
        ) {
            return ReviewKind.EVENING
        }

        if (schedule.morningEnabled && lastMorningShown != today) {
            val start = minuteOfDay(schedule.morningTime)
            var end = minOf(start + MORNING_WINDOW_MINUTES, MINUTES_PER_DAY)
            val evening = minuteOfDay(schedule.eveningTime)
            if (schedule.eveningEnabled && evening > start) end = minOf(end, evening)
            if (minute in start until end) return ReviewKind.MORNING
        }
        return null
    }

    private fun minuteOfDay(time: LocalTime): Int = time.hour * 60 + time.minute

    private const val MINUTES_PER_DAY = 24 * 60
}
