package com.mp01home.launcher.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mp01home.launcher.appContainer
import com.mp01home.launcher.data.settings.TimeFormat
import com.mp01home.launcher.sync.formatSyncedLabel
import com.mp01home.launcher.util.minuteTicker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** Everything the home CalendarSection renders; computed once per minute or on data change. */
data class CalendarUiState(
    val hasPermission: Boolean = true,
    val nowMillis: Long = System.currentTimeMillis(),
    val zone: ZoneId = ZoneId.systemDefault(),
    val use24h: Boolean = true,
    val next: CalEvent? = null,
    /** Remaining events today after [next], already limited to the agenda row setting. */
    val agenda: List<CalEvent> = emptyList(),
    /** Remaining events today that didn't fit in [agenda]. */
    val moreCount: Int = 0,
    val stripWindow: TimeRange? = null,
    val stripSegments: List<StripSegment> = emptyList(),
    val freeLabel: String? = null,
    val prefs: CalendarPrefs = CalendarPrefs(),
    val syncedLabel: String = "not synced"
)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer
    private val repository = container.calendarRepository
    private val dao = container.database.cachedEventDao()

    private val permission = MutableStateFlow(repository.hasPermission())

    private val ticker = minuteTicker()
        .map { System.currentTimeMillis() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    private val today = ticker.map { LocalDate.now(ZoneId.systemDefault()) }.distinctUntilChanged()

    private val todayEvents = today.flatMapLatest { date ->
        val zone = ZoneId.systemDefault()
        dao.observeBetween(
            date.atStartOfDay(zone).toInstant().toEpochMilli(),
            date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        )
    }.map { list -> list.map { it.toCalEvent() } }

    private val next = ticker.flatMapLatest { now -> dao.observeNext(now) }
        .map { it?.toCalEvent() }
        .distinctUntilChanged()

    private val syncedAndFormat = combine(
        container.syncStatusRepository.lastSuccessMillis(CalendarRepository.SYNC_KEY),
        container.settingsRepository.settings.map { it.timeFormat }.distinctUntilChanged(),
        ticker
    ) { synced, format, _ -> formatSyncedLabel(synced, format) to (format == TimeFormat.HOUR_24) }

    private val inputs = combine(todayEvents, next, repository.prefsRepository.prefs) { e, n, p -> Triple(e, n, p) }

    val state: StateFlow<CalendarUiState> = combine(
        ticker, inputs, syncedAndFormat, permission
    ) { now, (events, nextEvent, prefs), (synced, use24h), granted ->
        buildState(now, events, nextEvent, prefs, synced, use24h, granted)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    /** Called on resume and after the permission dialog. */
    fun refreshPermission() {
        val granted = repository.hasPermission()
        val was = permission.value
        permission.value = granted
        if (granted && !was) repository.onPermissionGranted()
    }

    fun onPermissionResult(granted: Boolean) {
        permission.value = granted
        if (granted) repository.onPermissionGranted()
    }

    fun scheduleBlock(title: String, minutes: Int) {
        viewModelScope.launch {
            CalendarActions.scheduleIntoFreeSlot(getApplication(), title, minutes)
        }
    }

    private fun buildState(
        now: Long,
        events: List<CalEvent>,
        nextEvent: CalEvent?,
        prefs: CalendarPrefs,
        synced: String,
        use24h: Boolean,
        granted: Boolean
    ): CalendarUiState {
        val zone = ZoneId.systemDefault()
        val date = LocalDate.now(zone)
        val remaining = events.filter { it.endMillis > now && it.instanceId != nextEvent?.instanceId }
        val agenda = remaining.take(prefs.agendaRows)

        val busy = busyRanges(events)
        val window = stripWindow(date, zone, busy)
        val segments = if (prefs.showFreeBusy) stripSegments(busy, window.startMillis, window.endMillis) else emptyList()
        val freeLabel = if (prefs.showFreeBusy) {
            val gaps = freeGaps(busy, maxOf(now, window.startMillis), window.endMillis, MIN_FREE_GAP_MILLIS)
            if (now >= window.endMillis) null else freeGapsLabel(gaps, now, zone, use24h)
        } else {
            null
        }
        return CalendarUiState(
            hasPermission = granted,
            nowMillis = now,
            zone = zone,
            use24h = use24h,
            next = nextEvent,
            agenda = agenda,
            moreCount = remaining.size - agenda.size,
            stripWindow = window,
            stripSegments = segments,
            freeLabel = freeLabel,
            prefs = prefs,
            syncedLabel = synced
        )
    }

    private companion object {
        const val MIN_FREE_GAP_MILLIS = 15 * 60_000L
    }
}
