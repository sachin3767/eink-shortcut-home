package com.mp01home.launcher.focus

/*
 * FOC-2 pure timer model (no Android types). The timer is persisted as an absolute end time,
 * so it survives process death: whatever process reads the state recomputes the remaining
 * time from the wall clock. The UI only redraws once per minute (EINK-4).
 */

enum class FocusPhase { IDLE, FOCUS, BREAK }

/**
 * @property endAtMillis wall-clock end of the running phase (0 when idle or paused).
 * @property pausedRemainingMillis remaining time while paused, null when not paused.
 * @property completedFocusSessions focus sessions finished today (informational).
 * @property sessionsDateIso the day [completedFocusSessions] counts for.
 */
data class FocusTimerState(
    val phase: FocusPhase = FocusPhase.IDLE,
    val endAtMillis: Long = 0L,
    val pausedRemainingMillis: Long? = null,
    val completedFocusSessions: Int = 0,
    val sessionsDateIso: String = ""
) {
    val isRunning: Boolean get() = phase != FocusPhase.IDLE && pausedRemainingMillis == null
    val isPaused: Boolean get() = phase != FocusPhase.IDLE && pausedRemainingMillis != null
}

private const val MINUTE_MS = 60_000L

fun startFocus(state: FocusTimerState, focusMinutes: Int, nowMillis: Long): FocusTimerState =
    state.copy(
        phase = FocusPhase.FOCUS,
        endAtMillis = nowMillis + focusMinutes.coerceAtLeast(1) * MINUTE_MS,
        pausedRemainingMillis = null
    )

fun startBreak(state: FocusTimerState, breakMinutes: Int, nowMillis: Long): FocusTimerState =
    state.copy(
        phase = FocusPhase.BREAK,
        endAtMillis = nowMillis + breakMinutes.coerceAtLeast(1) * MINUTE_MS,
        pausedRemainingMillis = null
    )

fun stopTimer(state: FocusTimerState): FocusTimerState =
    state.copy(phase = FocusPhase.IDLE, endAtMillis = 0L, pausedRemainingMillis = null)

fun pauseTimer(state: FocusTimerState, nowMillis: Long): FocusTimerState {
    if (!state.isRunning) return state
    return state.copy(pausedRemainingMillis = (state.endAtMillis - nowMillis).coerceAtLeast(0L), endAtMillis = 0L)
}

fun resumeTimer(state: FocusTimerState, nowMillis: Long): FocusTimerState {
    val remaining = state.pausedRemainingMillis ?: return state
    return state.copy(endAtMillis = nowMillis + remaining, pausedRemainingMillis = null)
}

/** Remaining milliseconds of the current phase (paused value when paused, 0 when idle). */
fun remainingMillis(state: FocusTimerState, nowMillis: Long): Long = when {
    state.phase == FocusPhase.IDLE -> 0L
    state.pausedRemainingMillis != null -> state.pausedRemainingMillis
    else -> (state.endAtMillis - nowMillis).coerceAtLeast(0L)
}

/**
 * Whole minutes shown on the countdown, rounded UP so "25" shows for the whole first minute
 * and "1" for the last one (the display never reads 0 while a phase is running).
 */
fun remainingMinutesCeil(state: FocusTimerState, nowMillis: Long): Int {
    val ms = remainingMillis(state, nowMillis)
    if (ms <= 0L) return 0
    return ((ms + MINUTE_MS - 1) / MINUTE_MS).toInt()
}

/** "25 min", "1 h 05 min". */
fun formatRemaining(minutes: Int): String =
    if (minutes >= 60) "${minutes / 60} h ${(minutes % 60).toString().padStart(2, '0')} min" else "$minutes min"

/** Effect of a phase ending, so callers can restore DND, notify, etc. */
data class PhaseTransition(val from: FocusPhase, val to: FocusPhase, val state: FocusTimerState)

/**
 * Advances a running timer whose end time has passed: FOCUS → BREAK (starting at the focus
 * end time, so late processing does not lengthen the break), BREAK → IDLE. Paused or idle
 * timers are returned unchanged. Applied repeatedly until the state is current.
 *
 * @return the up-to-date state and the transitions that happened (possibly empty).
 */
fun advanceTimer(
    state: FocusTimerState,
    breakMinutes: Int,
    nowMillis: Long,
    todayIso: String
): Pair<FocusTimerState, List<PhaseTransition>> {
    var current = if (state.sessionsDateIso != todayIso) {
        state.copy(completedFocusSessions = 0, sessionsDateIso = todayIso)
    } else state
    val transitions = mutableListOf<PhaseTransition>()
    while (current.isRunning && current.endAtMillis <= nowMillis) {
        val next = when (current.phase) {
            FocusPhase.FOCUS -> startBreak(current, breakMinutes, current.endAtMillis)
                .copy(completedFocusSessions = current.completedFocusSessions + 1)
            FocusPhase.BREAK -> stopTimer(current)
            FocusPhase.IDLE -> current
        }
        transitions += PhaseTransition(current.phase, next.phase, next)
        current = next
    }
    return current to transitions
}

/** One-line compact home text: "Focus · 18 min left", "Break · 4 min left", "Paused · 12 min". */
fun formatCompactTimer(state: FocusTimerState, nowMillis: Long): String {
    val minutes = remainingMinutesCeil(state, nowMillis)
    val label = when (state.phase) {
        FocusPhase.FOCUS -> "Focus"
        FocusPhase.BREAK -> "Break"
        FocusPhase.IDLE -> return "Idle · tap to start"
    }
    return if (state.isPaused) "$label paused · ${formatRemaining(minutes)} left"
    else "$label · ${formatRemaining(minutes)} left"
}

/**
 * Delay until the displayed minute count of a running phase next changes (i.e. the remaining
 * time crosses a whole minute), so the full-screen countdown redraws exactly once per minute
 * and flips to the next phase on time. Non-running timers tick on a plain minute cadence.
 */
fun nextCountdownTickDelayMillis(state: FocusTimerState, nowMillis: Long): Long {
    if (!state.isRunning) return MINUTE_MS
    val remaining = state.endAtMillis - nowMillis
    if (remaining <= 0L) return MINUTE_MS
    val toBoundary = remaining % MINUTE_MS
    return if (toBoundary == 0L) MINUTE_MS else toBoundary
}
