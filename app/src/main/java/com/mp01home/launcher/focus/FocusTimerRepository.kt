package com.mp01home.launcher.focus

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mp01home.launcher.appContainer
import com.mp01home.launcher.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * FOC-2/3: Pomodoro timer persisted as an absolute end time in [FocusStateStore], so it
 * survives process death. Phase ends are handled by [reconcile], which runs
 *  - from [FocusTimerWorker], scheduled by WorkManager at the phase end, and
 *  - whenever the UI or app start touches the timer (so a late worker never shows stale state).
 *
 * FOCUS → BREAK happens automatically; BREAK → IDLE ends the cycle. DND (if enabled in
 * settings and access is granted) is on for the FOCUS phase only.
 */
class FocusTimerRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val store: FocusStateStore,
    private val dnd: DndController,
    private val notifier: FocusNotifier,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val lock = Mutex()

    /** Raw persisted state; combine with a minute ticker and [remainingMinutesCeil] to render. */
    val state: Flow<FocusTimerState> = store.timerState

    private fun todayIso() = LocalDate.now().toString()

    suspend fun beginFocus() = mutate { state, now ->
        val settings = settingsRepository.current()
        val next = startFocus(state, settings.focusMinutes, now)
        if (settings.focusDndEnabled) dnd.enable()
        next
    }

    suspend fun beginBreak() = mutate { state, now ->
        dnd.restore()
        startBreak(state, settingsRepository.current().breakMinutes, now)
    }

    suspend fun stopSession() = mutate { state, _ ->
        dnd.restore()
        stopTimer(state)
    }

    suspend fun pauseSession() = mutate { state, now -> pauseTimer(state, now) }

    suspend fun resumeSession() = mutate { state, now -> resumeTimer(state, now) }

    /** Ends the current phase now (focus → break, break → idle). */
    suspend fun skipPhase() = mutate { state, now ->
        val ended = when {
            state.isPaused -> resumeTimer(state, now)
            else -> state
        }.copy(endAtMillis = now)
        val (next, transitions) = advanceTimer(ended, settingsRepository.current().breakMinutes, now, todayIso())
        applyEffects(transitions, notify = false)
        // advanceTimer would also end a just-started break if its end <= now; it can't here.
        next
    }

    /** Brings the persisted state up to date with the clock; safe to call any time. */
    suspend fun reconcile() {
        lock.withLock {
            val now = clock()
            val current = store.readTimer()
            val breakMinutes = settingsRepository.current().breakMinutes
            val (next, transitions) = advanceTimer(current, breakMinutes, now, todayIso())
            if (next != current) store.writeTimer(next)
            applyEffects(transitions, notify = true)
            if (next.phase != FocusPhase.FOCUS) dnd.restore()
            schedule(next, now)
        }
    }

    private suspend fun mutate(block: suspend (FocusTimerState, Long) -> FocusTimerState) {
        lock.withLock {
            val now = clock()
            val breakMinutes = settingsRepository.current().breakMinutes
            val (current, transitions) = advanceTimer(store.readTimer(), breakMinutes, now, todayIso())
            applyEffects(transitions, notify = false)
            val next = block(current, now)
            store.writeTimer(next)
            schedule(next, now)
        }
    }

    private suspend fun applyEffects(transitions: List<PhaseTransition>, notify: Boolean) {
        if (transitions.isEmpty()) return
        if (transitions.any { it.from == FocusPhase.FOCUS }) dnd.restore()
        if (notify) {
            val breakMinutes = settingsRepository.current().breakMinutes
            // Only the latest transition matters to the user.
            notifier.notifyTransition(transitions.last(), breakMinutes)
        }
    }

    private fun schedule(state: FocusTimerState, now: Long) {
        val workManager = WorkManager.getInstance(context)
        if (!state.isRunning) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val delay = (state.endAtMillis - now).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<FocusTimerWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(WORK_NAME)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        const val WORK_NAME = "focus_timer_phase_end"
    }
}

/** Fires at a phase end (FOC-2): advances the timer, restores DND, notifies. */
class FocusTimerWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        applicationContext.appContainer.focusTimerRepository.reconcile()
        return Result.success()
    }
}
