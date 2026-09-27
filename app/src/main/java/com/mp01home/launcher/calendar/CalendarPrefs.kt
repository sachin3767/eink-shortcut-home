package com.mp01home.launcher.calendar

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.calendarPrefsStore by preferencesDataStore(name = "mp01_calendar")

/** Calendar feature-private preferences (CAL-5/6, PLAN-4). CAL-7 selection lives in LauncherSettings. */
data class CalendarPrefs(
    /** CAL-2: agenda rows on the home screen (0..[MAX_AGENDA_ROWS]). */
    val agendaRows: Int = 3,
    /** CAL-5: show the free/busy strip. */
    val showFreeBusy: Boolean = true,
    /** CAL-6: "leave now" notification for events with a location. */
    val leaveByEnabled: Boolean = false,
    /** CAL-6: minutes before the start at which the notification fires. */
    val leaveByLeadMinutes: Int = 30,
    /** PLAN-4: default length of a time block. */
    val blockMinutes: Int = 30
) {
    companion object {
        const val MAX_AGENDA_ROWS = 5
        const val MIN_LEAD = 5
        const val MAX_LEAD = 120
        const val LEAD_STEP = 5
        const val MIN_BLOCK = 15
        const val MAX_BLOCK = 180
        const val BLOCK_STEP = 15
    }
}

class CalendarPrefsRepository(private val context: Context) {

    private object Keys {
        val AGENDA_ROWS = intPreferencesKey("agenda_rows")
        val SHOW_FREE_BUSY = booleanPreferencesKey("show_free_busy")
        val LEAVE_BY_ENABLED = booleanPreferencesKey("leave_by_enabled")
        val LEAVE_BY_LEAD = intPreferencesKey("leave_by_lead_minutes")
        val BLOCK_MINUTES = intPreferencesKey("block_minutes")
        val LAST_LEAVE_BY_NOTIFIED = longPreferencesKey("last_leave_by_instance")
    }

    val prefs: Flow<CalendarPrefs> = context.calendarPrefsStore.data
        .map { it.toPrefs() }
        .distinctUntilChanged()

    suspend fun current(): CalendarPrefs = prefs.first()

    private fun Preferences.toPrefs(): CalendarPrefs {
        val d = CalendarPrefs()
        return CalendarPrefs(
            agendaRows = (this[Keys.AGENDA_ROWS] ?: d.agendaRows).coerceIn(0, CalendarPrefs.MAX_AGENDA_ROWS),
            showFreeBusy = this[Keys.SHOW_FREE_BUSY] ?: d.showFreeBusy,
            leaveByEnabled = this[Keys.LEAVE_BY_ENABLED] ?: d.leaveByEnabled,
            leaveByLeadMinutes = (this[Keys.LEAVE_BY_LEAD] ?: d.leaveByLeadMinutes)
                .coerceIn(CalendarPrefs.MIN_LEAD, CalendarPrefs.MAX_LEAD),
            blockMinutes = (this[Keys.BLOCK_MINUTES] ?: d.blockMinutes)
                .coerceIn(CalendarPrefs.MIN_BLOCK, CalendarPrefs.MAX_BLOCK)
        )
    }

    suspend fun setAgendaRows(rows: Int) = context.calendarPrefsStore.edit {
        it[Keys.AGENDA_ROWS] = rows.coerceIn(0, CalendarPrefs.MAX_AGENDA_ROWS)
    }

    suspend fun setShowFreeBusy(show: Boolean) = context.calendarPrefsStore.edit { it[Keys.SHOW_FREE_BUSY] = show }

    suspend fun setLeaveByEnabled(enabled: Boolean) = context.calendarPrefsStore.edit { it[Keys.LEAVE_BY_ENABLED] = enabled }

    suspend fun setLeaveByLeadMinutes(minutes: Int) = context.calendarPrefsStore.edit {
        it[Keys.LEAVE_BY_LEAD] = minutes.coerceIn(CalendarPrefs.MIN_LEAD, CalendarPrefs.MAX_LEAD)
    }

    suspend fun setBlockMinutes(minutes: Int) = context.calendarPrefsStore.edit {
        it[Keys.BLOCK_MINUTES] = minutes.coerceIn(CalendarPrefs.MIN_BLOCK, CalendarPrefs.MAX_BLOCK)
    }

    /** CAL-6 de-duplication: the instance id last notified. */
    suspend fun lastLeaveByNotified(): Long? = context.calendarPrefsStore.data.first()[Keys.LAST_LEAVE_BY_NOTIFIED]

    suspend fun setLastLeaveByNotified(instanceId: Long) = context.calendarPrefsStore.edit {
        it[Keys.LAST_LEAVE_BY_NOTIFIED] = instanceId
    }
}
