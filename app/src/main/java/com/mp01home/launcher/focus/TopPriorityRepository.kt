package com.mp01home.launcher.focus

import com.mp01home.launcher.data.db.AppDatabase
import com.mp01home.launcher.data.db.CachedTask
import com.mp01home.launcher.data.db.TopPriority
import com.mp01home.launcher.data.db.TopPriorityDao
import com.mp01home.launcher.util.minuteTicker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/**
 * FOC-1 Daily Top 3 (also used by PLAN-1/2 review screens to set tomorrow's / today's Top 3).
 *
 * Priorities are stored per day in `top_priorities` (unique dateIso+slot), so they "reset"
 * daily by construction: a new day simply has no rows. Unfinished items from the most recent
 * earlier day are offered through [observeCarryOverOffer]; the answer is remembered per day.
 *
 * Usage from other features:
 * ```
 * val top3 = context.appContainer.topPriorityRepository
 * top3.setTop3(LocalDate.now(), listOf("Ship spec" to null, "Call Sam" to cachedTaskId))
 * top3.observeForDate(date)   // Flow<List<TopPriority>>
 * ```
 */
class TopPriorityRepository(
    private val databaseProvider: () -> AppDatabase,
    private val store: FocusStateStore,
    private val today: () -> LocalDate = { LocalDate.now() }
) {
    private val dao: TopPriorityDao get() = databaseProvider().topPriorityDao()
    private val writeLock = Mutex()

    /** Today's date, re-evaluated every minute so the list switches at midnight. */
    val currentDate: Flow<LocalDate> =
        minuteTicker().map { it.toLocalDate() }.distinctUntilChanged()

    fun observeForDate(date: LocalDate): Flow<List<TopPriority>> =
        dao.observeForDate(date.toString())

    /** Today's priorities (slot order); follows the date across midnight. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeToday(): Flow<List<TopPriority>> = currentDate.flatMapLatest { observeForDate(it) }

    suspend fun getForDate(date: LocalDate): List<TopPriority> = dao.getForDate(date.toString())

    /**
     * Sets (or clears, when [text] is blank) one slot for [date].
     * @param linkedTaskId optional [CachedTask.id] this priority mirrors.
     */
    suspend fun setPriority(date: LocalDate, slot: Int, text: String, linkedTaskId: String? = null) {
        require(slot in 0 until TOP3_SLOTS) { "slot must be 0..2" }
        writeLock.withLock { setPriorityLocked(date, slot, text, linkedTaskId, carriedOver = false) }
    }

    private suspend fun setPriorityLocked(
        date: LocalDate,
        slot: Int,
        text: String,
        linkedTaskId: String?,
        carriedOver: Boolean
    ) {
        val dateIso = date.toString()
        val existing = dao.getForDate(dateIso).firstOrNull { it.slot == slot }
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            existing?.let { dao.deleteById(it.id) }
            return
        }
        // Keep the row id so the (dateIso, slot) unique index is updated, not violated.
        val keepDone = existing != null && existing.text == trimmed && existing.done
        dao.upsert(
            TopPriority(
                id = existing?.id ?: 0,
                dateIso = dateIso,
                slot = slot,
                text = trimmed,
                linkedTaskId = linkedTaskId,
                done = keepDone,
                carriedOver = carriedOver || (existing?.carriedOver == true && existing.text == trimmed)
            )
        )
    }

    /**
     * Replaces the whole Top 3 for [date] (review screens). Blank entries are dropped and the
     * rest compacted into slots 0..2; extra entries beyond 3 are ignored.
     */
    suspend fun setTop3(date: LocalDate, items: List<Pair<String, String?>>) {
        val normalized = normalizeTop3(items)
        writeLock.withLock {
            for (slot in 0 until TOP3_SLOTS) {
                val item = normalized.firstOrNull { it.slot == slot }
                setPriorityLocked(date, slot, item?.text.orEmpty(), item?.linkedTaskId, carriedOver = false)
            }
        }
    }

    suspend fun setDone(id: Long, done: Boolean) = dao.setDone(id, done)

    suspend fun clearSlot(date: LocalDate, slot: Int) = setPriority(date, slot, "")

    /**
     * Unfinished items of the latest earlier day, if the prompt has not been answered today
     * and today still has room; null otherwise.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeCarryOverOffer(): Flow<CarryOverOffer?> =
        currentDate.flatMapLatest { date ->
            combine(observeForDate(date), store.carryOverAnsweredDate) { todayRows, answered ->
                todayRows to answered
            }.mapLatest { (todayRows, answered) -> computeOffer(date, todayRows, answered) }
        }

    private suspend fun computeOffer(date: LocalDate, todayRows: List<TopPriority>, answered: String?): CarryOverOffer? {
        val todayIso = date.toString()
        if (answered == todayIso) return null
        val previousIso = dao.latestDateBefore(todayIso) ?: return null
        val candidates = carryOverCandidates(dao.getForDate(previousIso).map { it.toItem() })
        val todayItems = todayRows.map { it.toItem() }
        if (!shouldOfferCarryOver(candidates, todayItems, answered, todayIso)) return null
        return CarryOverOffer(previousIso, planCarryOver(candidates, todayItems))
            .takeIf { it.items.isNotEmpty() }
    }

    /** "Yes": copies the unfinished items into today's free slots. */
    suspend fun acceptCarryOver(date: LocalDate = today()) {
        writeLock.withLock {
            val todayIso = date.toString()
            val previousIso = dao.latestDateBefore(todayIso)
            if (previousIso != null) {
                val candidates = carryOverCandidates(dao.getForDate(previousIso).map { it.toItem() })
                val todayItems = dao.getForDate(todayIso).map { it.toItem() }
                for (item in planCarryOver(candidates, todayItems)) {
                    setPriorityLocked(date, item.slot, item.text, item.linkedTaskId, carriedOver = true)
                }
            }
            store.setCarryOverAnswered(todayIso)
        }
    }

    /** "No": hide the prompt for the rest of today. */
    suspend fun dismissCarryOver(date: LocalDate = today()) {
        store.setCarryOverAnswered(date.toString())
    }

    /** Housekeeping: drop priorities older than [keepDays] days. */
    suspend fun prune(keepDays: Long = 60) {
        dao.deleteBefore(today().minusDays(keepDays).toString())
    }

    /** Open cached tasks the user can link a priority to (TASK-* cache, may be empty). */
    fun observeLinkableTasks(): Flow<List<CachedTask>> = databaseProvider().cachedTaskDao().observeOpen()

    private fun TopPriority.toItem() = PriorityItem(slot, text, linkedTaskId, done)
}
