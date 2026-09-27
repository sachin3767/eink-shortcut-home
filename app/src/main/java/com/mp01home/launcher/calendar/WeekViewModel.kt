package com.mp01home.launcher.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mp01home.launcher.appContainer
import com.mp01home.launcher.data.settings.TimeFormat
import com.mp01home.launcher.sync.formatSyncedLabel
import com.mp01home.launcher.util.minuteTicker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

data class WeekUiState(
    val days: List<WeekDay> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val zone: ZoneId = ZoneId.systemDefault(),
    val use24h: Boolean = true,
    val syncedLabel: String = "not synced",
    val hasPermission: Boolean = true
)

/** PLAN-3: seven days from today, event titles from the calendar cache, open-task counts. */
@OptIn(ExperimentalCoroutinesApi::class)
class WeekViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer
    private val eventDao = container.database.cachedEventDao()
    private val taskDao = container.database.cachedTaskDao()

    private val ticker = minuteTicker()

    private val today = ticker.map { LocalDate.now(ZoneId.systemDefault()) }.distinctUntilChanged()

    private val weekData = today.flatMapLatest { date ->
        val zone = ZoneId.systemDefault()
        combine(
            eventDao.observeBetween(
                date.atStartOfDay(zone).toInstant().toEpochMilli(),
                date.plusDays(DAYS.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
            ),
            taskDao.observeOpen()
        ) { events, tasks ->
            val days = buildWeek(date, zone, events.map { it.toCalEvent() }, tasks.map { it.dueEpochDay }, DAYS)
            Triple(date, zone, days)
        }
    }

    private val labelAndFormat = combine(
        container.syncStatusRepository.lastSuccessMillis(CalendarRepository.SYNC_KEY),
        container.settingsRepository.settings.map { it.timeFormat }.distinctUntilChanged(),
        ticker
    ) { synced, format, _ -> formatSyncedLabel(synced, format) to (format == TimeFormat.HOUR_24) }

    val state: StateFlow<WeekUiState> = combine(weekData, labelAndFormat) { (date, zone, days), (label, use24h) ->
        WeekUiState(
            days = days,
            today = date,
            zone = zone,
            use24h = use24h,
            syncedLabel = label,
            hasPermission = container.calendarRepository.hasPermission()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekUiState())

    private companion object {
        const val DAYS = 7
    }
}
