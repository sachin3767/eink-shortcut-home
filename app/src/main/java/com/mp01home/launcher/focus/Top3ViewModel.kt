package com.mp01home.launcher.focus

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mp01home.launcher.appContainer
import com.mp01home.launcher.data.db.CachedTask
import com.mp01home.launcher.data.db.TopPriority
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class Top3UiState(
    val date: LocalDate = LocalDate.now(),
    val items: List<TopPriority> = emptyList(),
    val carryOver: CarryOverOffer? = null
) {
    val doneCount: Int get() = items.count { it.done }
}

/** FOC-1 home section state. */
class Top3ViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = application.appContainer.topPriorityRepository

    val uiState: StateFlow<Top3UiState> = combine(
        repository.currentDate,
        repository.observeToday(),
        repository.observeCarryOverOffer()
    ) { date, items, offer ->
        Top3UiState(date = date, items = items.filter { it.dateIso == date.toString() }, carryOver = offer)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Top3UiState())

    /** Open cached tasks to link a priority to (only collected while the edit dialog is open). */
    val linkableTasks: StateFlow<List<CachedTask>> = repository.observeLinkableTasks()
        .map { it.take(MAX_LINKABLE_TASKS) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleDone(item: TopPriority) {
        viewModelScope.launch { repository.setDone(item.id, !item.done) }
    }

    fun save(slot: Int, text: String, linkedTaskId: String?) {
        val date = uiState.value.date
        viewModelScope.launch { repository.setPriority(date, slot, text, linkedTaskId) }
    }

    fun clear(slot: Int) {
        val date = uiState.value.date
        viewModelScope.launch { repository.clearSlot(date, slot) }
    }

    fun acceptCarryOver() {
        val date = uiState.value.date
        viewModelScope.launch { repository.acceptCarryOver(date) }
    }

    fun dismissCarryOver() {
        val date = uiState.value.date
        viewModelScope.launch { repository.dismissCarryOver(date) }
    }

    private companion object {
        const val MAX_LINKABLE_TASKS = 5
    }
}
