package com.mp01home.launcher.habits

import com.mp01home.launcher.data.db.AppDatabase
import com.mp01home.launcher.data.db.Habit
import com.mp01home.launcher.data.db.HabitDao
import com.mp01home.launcher.data.db.HabitEntry
import com.mp01home.launcher.util.minuteTicker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** One habit as shown on home: today's checkbox, streak and the last days' marks. */
data class HabitRow(
    val habit: Habit,
    val doneToday: Boolean,
    val streak: Int,
    /** Previous 6 days, oldest first ("■" done / "□" missed). */
    val history: String
)

/** EXT-2 habit tracker on top of `habits` / `habit_entries` (habitDao). */
class HabitRepository(private val databaseProvider: () -> AppDatabase) {

    private val dao: HabitDao get() = databaseProvider().habitDao()

    private val currentDate: Flow<LocalDate> =
        minuteTicker().map { it.toLocalDate() }.distinctUntilChanged()

    fun observeHabits(): Flow<List<Habit>> = dao.observeActiveHabits()

    /** Active habits with today's state; entries of the last [STREAK_WINDOW_DAYS] days feed streaks. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeRows(): Flow<List<HabitRow>> = currentDate.flatMapLatest { today ->
        combine(
            dao.observeActiveHabits(),
            dao.observeEntriesBetween(today.minusDays(STREAK_WINDOW_DAYS).toString(), today.toString())
        ) { habits, entries ->
            val doneByHabit: Map<Long, Set<LocalDate>> = entries
                .filter { it.done }
                .groupBy { it.habitId }
                .mapValues { (_, list) -> parseIsoDates(list.map { it.dateIso }) }
            habits.map { habit ->
                val done = doneByHabit[habit.id].orEmpty()
                HabitRow(
                    habit = habit,
                    doneToday = today in done,
                    streak = currentStreak(done, today),
                    history = historyStrip(done, today)
                )
            }
        }
    }

    suspend fun setDone(habitId: Long, date: LocalDate, done: Boolean) {
        if (done) dao.upsertEntry(HabitEntry(habitId = habitId, dateIso = date.toString(), done = true))
        else dao.deleteEntry(habitId, date.toString())
    }

    suspend fun toggleToday(row: HabitRow) = setDone(row.habit.id, LocalDate.now(), !row.doneToday)

    suspend fun addHabit(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val position = (dao.observeActiveHabits().first().maxOfOrNull { it.position } ?: -1) + 1
        dao.upsertHabit(Habit(name = trimmed, position = position))
    }

    /** Archives (keeps history, hides everywhere). */
    suspend fun removeHabit(habitId: Long) = dao.archive(habitId)

    suspend fun renameHabit(habit: Habit, name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) dao.upsertHabit(habit.copy(name = trimmed))
    }

    companion object {
        const val STREAK_WINDOW_DAYS = 400L
        /** Rows shown on home before "+N more" (EINK-1: no scrolling). */
        const val MAX_HOME_ROWS = 4
    }
}
