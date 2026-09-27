package com.mp01home.launcher.focus

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mp01home.launcher.appContainer
import com.mp01home.launcher.data.settings.LauncherSettings
import com.mp01home.launcher.data.settings.TimeFormat
import com.mp01home.launcher.util.minuteTicker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class FocusUiState(
    val timer: FocusTimerState = FocusTimerState(),
    /** Whole minutes left, rounded up; recomputed once per minute (EINK-4). */
    val minutesLeft: Int = 0,
    val compactText: String = "",
    /** "ends 10:45" for a running phase, else null. */
    val endsAtText: String? = null,
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val dndEnabled: Boolean = false,
    val dndAccess: Boolean = true
)

/** Shared by FocusSection (home line) and FocusScreen (full screen). */
class FocusViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer
    private val timer = container.focusTimerRepository
    private val dnd = container.dndController
    private val dndAccess = MutableStateFlow(dnd.hasAccess())

    /** Home line: redraws on the clock's minute boundary together with the header (EINK-4). */
    val uiState: StateFlow<FocusUiState> = combine(
        timer.state,
        minuteTicker(),
        container.settingsRepository.settings,
        dndAccess
    ) { state, _, settings, access ->
        buildState(state, settings, access, System.currentTimeMillis())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState())

    /**
     * Full screen: redraws once per minute exactly when the remaining-minutes number changes,
     * and triggers the phase change on time instead of waiting for the WorkManager job.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val countdownTicks: Flow<Long> = timer.state.flatMapLatest { state ->
        flow {
            while (true) {
                val now = System.currentTimeMillis()
                emit(now)
                if (state.isRunning && state.endAtMillis <= now) {
                    // Separate job: this flow is cancelled as soon as the new state is written.
                    viewModelScope.launch { timer.reconcile() }
                }
                delay(nextCountdownTickDelayMillis(state, now) + TICK_SLACK_MS)
            }
        }
    }

    val screenState: StateFlow<FocusUiState> = combine(
        timer.state,
        countdownTicks,
        container.settingsRepository.settings,
        dndAccess
    ) { state, now, settings, access ->
        buildState(state, settings, access, now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState())

    private fun buildState(state: FocusTimerState, settings: LauncherSettings, access: Boolean, now: Long) =
        FocusUiState(
            timer = state,
            minutesLeft = remainingMinutesCeil(state, now),
            compactText = formatCompactTimer(state, now),
            endsAtText = if (state.isRunning) "ends " + formatClock(state.endAtMillis, settings.timeFormat) else null,
            focusMinutes = settings.focusMinutes,
            breakMinutes = settings.breakMinutes,
            dndEnabled = settings.focusDndEnabled,
            dndAccess = access
        )

    init {
        // A phase may have ended while nobody was looking (late worker / process death).
        viewModelScope.launch { timer.reconcile() }
    }

    /** Re-check DND access (call on resume, e.g. after returning from system settings). */
    fun refreshAccess() {
        dndAccess.value = dnd.hasAccess()
        viewModelScope.launch { timer.reconcile() }
    }

    fun dndAccessIntent() = dnd.accessSettingsIntent()

    fun startFocus() = act { timer.beginFocus() }
    fun startBreak() = act { timer.beginBreak() }
    fun pause() = act { timer.pauseSession() }
    fun resume() = act { timer.resumeSession() }
    fun stop() = act { timer.stopSession() }
    fun skip() = act { timer.skipPhase() }

    private companion object {
        /** Lands just after the minute boundary so the ceil() has already flipped. */
        const val TICK_SLACK_MS = 50L
    }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun formatClock(millis: Long, format: TimeFormat): String {
        val pattern = if (format == TimeFormat.HOUR_24) "HH:mm" else "h:mm a"
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }
}
