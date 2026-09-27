package com.mp01home.launcher.focus

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.focusStateStore by preferencesDataStore(name = "mp01_focus_state")

/**
 * Feature-private persisted state for FOC-1/2/3 (not user settings):
 * the running timer (absolute end time, so it survives process death), the DND filter to
 * restore, and the day the Top-3 carry-over prompt was last answered.
 */
class FocusStateStore(private val context: Context) {

    private object Keys {
        val PHASE = stringPreferencesKey("timer_phase")
        val END_AT = longPreferencesKey("timer_end_at")
        val PAUSED_REMAINING = longPreferencesKey("timer_paused_remaining") // absent = not paused
        val COMPLETED = intPreferencesKey("timer_completed_sessions")
        val SESSIONS_DATE = stringPreferencesKey("timer_sessions_date")
        val DND_APPLIED = booleanPreferencesKey("dnd_applied")
        val DND_PREVIOUS_FILTER = intPreferencesKey("dnd_previous_filter")
        val CARRY_ANSWERED_DATE = stringPreferencesKey("top3_carry_answered_date")
    }

    private fun Preferences.toTimerState() = FocusTimerState(
        phase = this[Keys.PHASE]?.let { runCatching { FocusPhase.valueOf(it) }.getOrNull() } ?: FocusPhase.IDLE,
        endAtMillis = this[Keys.END_AT] ?: 0L,
        pausedRemainingMillis = this[Keys.PAUSED_REMAINING],
        completedFocusSessions = this[Keys.COMPLETED] ?: 0,
        sessionsDateIso = this[Keys.SESSIONS_DATE].orEmpty()
    )

    val timerState: Flow<FocusTimerState> =
        context.focusStateStore.data.map { it.toTimerState() }.distinctUntilChanged()

    suspend fun readTimer(): FocusTimerState = timerState.first()

    suspend fun writeTimer(state: FocusTimerState) {
        context.focusStateStore.edit { prefs ->
            prefs[Keys.PHASE] = state.phase.name
            prefs[Keys.END_AT] = state.endAtMillis
            val paused = state.pausedRemainingMillis
            if (paused != null) prefs[Keys.PAUSED_REMAINING] = paused else prefs.remove(Keys.PAUSED_REMAINING)
            prefs[Keys.COMPLETED] = state.completedFocusSessions
            prefs[Keys.SESSIONS_DATE] = state.sessionsDateIso
        }
    }

    // ---- FOC-3 DND bookkeeping ----

    /** Previous interruption filter if we switched DND on, else null. */
    suspend fun dndPreviousFilter(): Int? {
        val prefs = context.focusStateStore.data.first()
        return if (prefs[Keys.DND_APPLIED] == true) prefs[Keys.DND_PREVIOUS_FILTER] else null
    }

    suspend fun setDndApplied(previousFilter: Int) {
        context.focusStateStore.edit {
            it[Keys.DND_APPLIED] = true
            it[Keys.DND_PREVIOUS_FILTER] = previousFilter
        }
    }

    suspend fun clearDndApplied() {
        context.focusStateStore.edit {
            it.remove(Keys.DND_APPLIED)
            it.remove(Keys.DND_PREVIOUS_FILTER)
        }
    }

    // ---- FOC-1 carry-over prompt ----

    val carryOverAnsweredDate: Flow<String?> =
        context.focusStateStore.data.map { it[Keys.CARRY_ANSWERED_DATE] }.distinctUntilChanged()

    suspend fun setCarryOverAnswered(dateIso: String) {
        context.focusStateStore.edit { it[Keys.CARRY_ANSWERED_DATE] = dateIso }
    }
}
