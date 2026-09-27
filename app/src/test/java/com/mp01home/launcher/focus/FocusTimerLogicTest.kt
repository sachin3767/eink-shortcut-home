package com.mp01home.launcher.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusTimerLogicTest {

    private val min = 60_000L
    private val t0 = 1_000_000_000L
    private val today = "2026-09-26"

    @Test
    fun countdownRoundsUpToWholeMinutes() {
        val s = startFocus(FocusTimerState(sessionsDateIso = today), 25, t0)
        assertEquals(25, remainingMinutesCeil(s, t0))
        assertEquals(25, remainingMinutesCeil(s, t0 + 30_000))
        assertEquals(24, remainingMinutesCeil(s, t0 + min))
        assertEquals(1, remainingMinutesCeil(s, t0 + 24 * min + 59_000))
        assertEquals(0, remainingMinutesCeil(s, t0 + 25 * min))
    }

    @Test
    fun pauseFreezesAndResumeShiftsEnd() {
        val s = startFocus(FocusTimerState(), 25, t0)
        val paused = pauseTimer(s, t0 + 5 * min)
        assertTrue(paused.isPaused)
        assertEquals(20, remainingMinutesCeil(paused, t0 + 60 * min))
        val resumed = resumeTimer(paused, t0 + 60 * min)
        assertEquals(t0 + 80 * min, resumed.endAtMillis)
    }

    @Test
    fun focusEndStartsBreakFromEndTimeThenIdle() {
        val s = startFocus(FocusTimerState(sessionsDateIso = today), 25, t0)
        val (afterFocus, tr1) = advanceTimer(s, 5, t0 + 26 * min, today)
        assertEquals(FocusPhase.BREAK, afterFocus.phase)
        assertEquals(t0 + 30 * min, afterFocus.endAtMillis)
        assertEquals(1, afterFocus.completedFocusSessions)
        assertEquals(listOf(FocusPhase.FOCUS to FocusPhase.BREAK), tr1.map { it.from to it.to })

        val (idle, tr2) = advanceTimer(s, 5, t0 + 40 * min, today)
        assertEquals(FocusPhase.IDLE, idle.phase)
        assertEquals(2, tr2.size)
    }

    @Test
    fun pausedOrFutureTimersAreUnchanged() {
        val s = startFocus(FocusTimerState(sessionsDateIso = today), 25, t0)
        assertEquals(s, advanceTimer(s, 5, t0 + min, today).first)
        val p = pauseTimer(s, t0 + min)
        assertEquals(p, advanceTimer(p, 5, t0 + 99 * min, today).first)
    }

    @Test
    fun sessionCounterResetsOnNewDay() {
        val s = FocusTimerState(completedFocusSessions = 4, sessionsDateIso = "2026-09-25")
        assertEquals(0, advanceTimer(s, 5, t0, today).first.completedFocusSessions)
    }

    @Test
    fun formatting() {
        assertEquals("25 min", formatRemaining(25))
        assertEquals("1 h 05 min", formatRemaining(65))
        val s = startFocus(FocusTimerState(), 25, t0)
        assertEquals("Focus · 18 min left", formatCompactTimer(s, t0 + 7 * min))
        assertEquals("Focus paused · 18 min left", formatCompactTimer(pauseTimer(s, t0 + 7 * min), t0))
        assertEquals("Idle · tap to start", formatCompactTimer(FocusTimerState(), t0))
    }

    @Test
    fun countdownTickAlignsToRemainingMinuteBoundary() {
        val s = startFocus(FocusTimerState(), 25, t0)
        assertEquals(min, nextCountdownTickDelayMillis(s, t0))
        assertEquals(37_000L, nextCountdownTickDelayMillis(s, t0 + 23_000))
        assertEquals(min, nextCountdownTickDelayMillis(FocusTimerState(), t0))
        assertEquals(min, nextCountdownTickDelayMillis(pauseTimer(s, t0 + 5_000), t0 + 5_000))
    }
}
