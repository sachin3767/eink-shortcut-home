package com.mp01home.launcher.distraction

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mp01home.launcher.appContainer
import com.mp01home.launcher.util.minuteTicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.withIndex

data class ScreenTimeUiState(
    val loaded: Boolean = false,
    val hasAccess: Boolean = true,
    /** "today: 1h12m, top: Chrome". */
    val line: String = ""
)

/**
 * DIS-3 state. Recomputed when home becomes visible again (the flow restarts) and then every
 * [REFRESH_EVERY_MINUTES] minutes while visible — usage queries are not free, and the line
 * only needs minute precision (EINK-4).
 */
class ScreenTimeViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer
    private val usage = container.usageStatsRepository

    val uiState: StateFlow<ScreenTimeUiState> = minuteTicker()
        .withIndex()
        .filter { (index, now) -> index == 0 || now.minute % REFRESH_EVERY_MINUTES == 0 }
        .map { compute() }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenTimeUiState())

    private suspend fun compute(): ScreenTimeUiState {
        val summary = usage.summaryToday() ?: return ScreenTimeUiState(loaded = true, hasAccess = false)
        val label = summary.topPackage?.let { container.installedAppsRepository.resolveLabel(it) }
        return ScreenTimeUiState(loaded = true, hasAccess = true, line = formatScreenTimeLine(summary, label))
    }

    fun accessIntent(): Intent = usage.accessSettingsIntent()

    fun genericAccessIntent(): Intent = usage.genericAccessSettingsIntent()

    private companion object {
        const val REFRESH_EVERY_MINUTES = 5
    }
}
