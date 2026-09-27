package com.mp01home.launcher.habits

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mp01home.launcher.appContainer
import com.mp01home.launcher.data.db.Habit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** EXT-2 state for the home section and the habit settings block. */
class HabitViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = application.appContainer.habitRepository

    val rows: StateFlow<List<HabitRow>> = repository.observeRows()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val habits: StateFlow<List<Habit>> = repository.observeHabits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleToday(row: HabitRow) {
        viewModelScope.launch { repository.toggleToday(row) }
    }

    fun add(name: String) {
        viewModelScope.launch { repository.addHabit(name) }
    }

    fun remove(habitId: Long) {
        viewModelScope.launch { repository.removeHabit(habitId) }
    }
}
