package com.mp01home.launcher.tasks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mp01home.launcher.appContainer
import com.mp01home.launcher.data.db.CachedTask
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** What the TASK-2 home widget renders. */
data class TasksHomeState(
    val signedIn: Boolean = false,
    val configured: Boolean = true,
    val overdueCount: Int = 0,
    val rows: List<HomeTaskRow> = emptyList(),
    val hiddenCount: Int = 0,
    val syncedLabel: String = "not synced",
    val loaded: Boolean = false
)

/**
 * TASK-2/3 home widget state, straight from the Room cache (EINK-7). Recomputed when the cache
 * changes and once per minute (to roll over at midnight and to age out ticked rows).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TasksHomeViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer
    private val repo = container.tasksRepository

    /** Ticked this session: id → (snapshot before ticking, tick time). Shown checked ~1 min. */
    private val recent = MutableStateFlow<Map<String, Pair<CachedTask, Long>>>(emptyMap())

    private val ticker = minuteTicker()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDateTime.now())

    val state: StateFlow<TasksHomeState> = ticker
        .map { it.toLocalDate() }
        .distinctUntilChanged()
        .flatMapLatest { today ->
            val epochDay = today.toEpochDay()
            val cache = combine(
                repo.observeDueOnOrBefore(epochDay),
                repo.observeOverdueCount(epochDay),
                recent
            ) { due, overdue, recentMap -> Triple(due, overdue, recentMap) }
            val meta = combine(
                container.syncStatusRepository.lastSuccessMillis(TasksRepository.SYNC_KEY),
                container.settingsRepository.settings.map { it.timeFormat }.distinctUntilChanged(),
                repo.accountName,
                ticker
            ) { synced, timeFormat, account, _ -> Triple(synced, timeFormat, account) }
            combine(cache, meta) { (due, overdue, recentMap), (synced, timeFormat, account) ->
                val selection = selectHomeTasks(
                    open = due,
                    recentlyCompleted = recentMap.values.map { it.first },
                    todayEpochDay = epochDay,
                    max = MAX_ROWS
                )
                TasksHomeState(
                    signedIn = account != null,
                    configured = repo.auth.isConfigured,
                    overdueCount = overdue,
                    rows = selection.rows,
                    hiddenCount = selection.hiddenCount,
                    syncedLabel = formatSyncedLabel(synced, timeFormat),
                    loaded = true
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksHomeState())

    init {
        // Age out ticked rows once per minute (EINK-4: no finer-grained updates).
        viewModelScope.launch {
            ticker.collect {
                val cutoff = System.currentTimeMillis() - KEEP_TICKED_MILLIS
                recent.update { map -> map.filterValues { it.second >= cutoff } }
            }
        }
    }

    /** TASK-3: optimistic toggle. Unticking a just-ticked row re-opens the task. */
    fun toggle(row: HomeTaskRow) {
        val id = row.task.id
        if (row.checked) {
            recent.update { it - id }
            viewModelScope.launch { repo.setCompleted(id, completed = false) }
        } else {
            recent.update { it + (id to (row.task to System.currentTimeMillis())) }
            viewModelScope.launch { repo.setCompleted(id, completed = true) }
        }
    }

    fun openTasksApp() {
        if (!container.appLauncher.launch(GOOGLE_TASKS_PACKAGE)) {
            // Not installed: fall back to the web app.
            container.appLauncher.startActivitySafely(
                android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://tasks.google.com/")
                )
            )
        }
    }

    companion object {
        const val MAX_ROWS = 4
        const val GOOGLE_TASKS_PACKAGE = "com.google.android.apps.tasks"
        private const val KEEP_TICKED_MILLIS = 60_000L
    }
}
