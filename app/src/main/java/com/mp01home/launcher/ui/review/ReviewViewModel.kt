package com.mp01home.launcher.ui.review

import android.app.Application
import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mp01home.launcher.appContainer
import com.mp01home.launcher.data.db.CachedEvent
import com.mp01home.launcher.data.db.CachedTask
import com.mp01home.launcher.data.db.TopPriority
import com.mp01home.launcher.data.settings.TimeFormat
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** One editable Top 3 slot (FOC-1 data, edited from the morning review). */
data class Top3Draft(val text: String = "", val linkedTaskId: String? = null)

data class MorningReviewState(
    val date: LocalDate,
    val events: List<CachedEvent> = emptyList(),
    val dueTasks: List<CachedTask> = emptyList(),
    val overdueCount: Int = 0,
    val top3: List<TopPriority> = emptyList(),
    val eventsSyncedAt: Long? = null,
    val tasksSyncedAt: Long? = null,
    val timeFormat: TimeFormat = TimeFormat.HOUR_24,
    val loaded: Boolean = false
)

data class EveningReviewState(
    val date: LocalDate,
    val completedToday: List<CachedTask> = emptyList(),
    /** Open tasks due today or earlier: they roll over to tomorrow. */
    val rollOver: List<CachedTask> = emptyList(),
    val top3: List<TopPriority> = emptyList(),
    val tomorrowFirstEvent: CachedEvent? = null,
    val tomorrowTop3Count: Int = 0,
    val tasksSyncedAt: Long? = null,
    val timeFormat: TimeFormat = TimeFormat.HOUR_24
) {
    val unfinishedTop3: List<TopPriority> get() = top3.filter { !it.done && it.text.isNotBlank() }
}

/**
 * PLAN-1 / PLAN-2. Reads only the Room cache (EINK-7). Top 3 is written straight through
 * [com.mp01home.launcher.data.db.TopPriorityDao] (unique per date + slot).
 */
class ReviewViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer
    private val db get() = container.database
    private val zone: ZoneId = ZoneId.systemDefault()

    /** Fixed when the screen opens; a review is a snapshot of "today". */
    val today: LocalDate = LocalDate.now(zone)
    private val tomorrow: LocalDate = today.plusDays(1)
    private val todayIso = today.toString()
    private val tomorrowIso = tomorrow.toString()
    private val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
    private val dayEnd = tomorrow.atStartOfDay(zone).toInstant().toEpochMilli()
    private val tomorrowEnd = tomorrow.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    private val timeFormat = container.settingsRepository.settings.map { it.timeFormat }

    val morning: StateFlow<MorningReviewState> = combine(
        db.cachedEventDao().observeBetween(dayStart, dayEnd),
        db.cachedTaskDao().observeDueOnOrBefore(today.toEpochDay()),
        db.topPriorityDao().observeForDate(todayIso),
        combine(db.cachedEventDao().observeLastSyncedAt(), db.cachedTaskDao().observeLastSyncedAt()) { e, t -> e to t },
        timeFormat
    ) { events, due, top3, synced, format ->
        val now = System.currentTimeMillis()
        MorningReviewState(
            date = today,
            events = events.filter { it.allDay || it.endMillis > now },
            dueTasks = due,
            overdueCount = due.count { (it.dueEpochDay ?: Long.MAX_VALUE) < today.toEpochDay() },
            top3 = top3,
            eventsSyncedAt = synced.first,
            tasksSyncedAt = synced.second,
            timeFormat = format,
            loaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MorningReviewState(today))

    val evening: StateFlow<EveningReviewState> = combine(
        db.cachedTaskDao().observeCompletedSince(dayStart),
        db.cachedTaskDao().observeDueOnOrBefore(today.toEpochDay()),
        db.topPriorityDao().observeForDate(todayIso),
        combine(
            db.cachedEventDao().observeBetween(dayEnd, tomorrowEnd),
            db.topPriorityDao().observeForDate(tomorrowIso)
        ) { events, tomorrowTop3 -> events to tomorrowTop3.size },
        combine(db.cachedTaskDao().observeLastSyncedAt(), timeFormat) { s, f -> s to f }
    ) { completed, open, top3, tomorrowData, meta ->
        val (tomorrowEvents, tomorrowTop3Count) = tomorrowData
        EveningReviewState(
            date = today,
            completedToday = completed,
            rollOver = open,
            top3 = top3,
            // First timed event that starts tomorrow; otherwise the first all-day one.
            tomorrowFirstEvent = tomorrowEvents.filter { !it.allDay && it.beginMillis >= dayEnd }
                .minByOrNull { it.beginMillis }
                ?: tomorrowEvents.firstOrNull { it.allDay },
            tomorrowTop3Count = tomorrowTop3Count,
            tasksSyncedAt = meta.first,
            timeFormat = meta.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EveningReviewState(today))

    /** Writes the three slots for today. Blank drafts delete their slot. */
    fun saveTop3(drafts: List<Top3Draft>, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val dao = db.topPriorityDao()
            val existing = dao.getForDate(todayIso).associateBy { it.slot }
            drafts.take(TOP3_SLOTS).forEachIndexed { slot, draft ->
                val text = draft.text.trim()
                val old = existing[slot]
                when {
                    text.isEmpty() -> old?.let { dao.deleteById(it.id) }
                    old == null -> dao.upsert(
                        TopPriority(dateIso = todayIso, slot = slot, text = text, linkedTaskId = draft.linkedTaskId)
                    )
                    old.text != text || old.linkedTaskId != draft.linkedTaskId -> dao.upsert(
                        old.copy(text = text, linkedTaskId = draft.linkedTaskId, done = false)
                    )
                }
            }
            onDone()
        }
    }

    /** PLAN-2: copies today's unfinished Top 3 into tomorrow's free slots (marked carried over). */
    fun carryUnfinishedTop3ToTomorrow() {
        viewModelScope.launch {
            val dao = db.topPriorityDao()
            val unfinished = dao.getForDate(todayIso).filter { !it.done && it.text.isNotBlank() }
            val tomorrowItems = dao.getForDate(tomorrowIso)
            val existingTexts = tomorrowItems.map { it.text.trim().lowercase() }.toSet()
            val freeSlots = (0 until TOP3_SLOTS).filter { slot -> tomorrowItems.none { it.slot == slot } }.toMutableList()
            unfinished
                .filter { it.text.trim().lowercase() !in existingTexts }
                .forEach { item ->
                    val slot = freeSlots.removeFirstOrNull() ?: return@launch
                    dao.upsert(
                        TopPriority(
                            dateIso = tomorrowIso,
                            slot = slot,
                            text = item.text,
                            linkedTaskId = item.linkedTaskId,
                            carriedOver = true
                        )
                    )
                }
        }
    }

    fun openEvent(event: CachedEvent) {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId)
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.beginMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endMillis)
        container.appLauncher.startActivitySafely(intent)
    }

    companion object {
        const val TOP3_SLOTS = 3
    }
}
