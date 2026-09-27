package com.mp01home.launcher.ui.drawer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mp01home.launcher.appContainer
import com.mp01home.launcher.data.apps.AppInfo
import com.mp01home.launcher.data.settings.LauncherSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppDrawerUiState(
    /** Launchable apps minus [LauncherSettings.hiddenApps] (CORE-5), sorted by label. */
    val apps: List<AppInfo> = emptyList(),
    val dockApps: List<String> = emptyList(),
    val isLoading: Boolean = true
) {
    val isDockFull: Boolean get() = dockApps.size >= LauncherSettings.MAX_DOCK_APPS
}

class AppDrawerViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer
    private val settingsRepository = container.settingsRepository

    private val allApps = MutableStateFlow<List<AppInfo>?>(null)

    val uiState: StateFlow<AppDrawerUiState> = combine(allApps, settingsRepository.settings) { apps, settings ->
        AppDrawerUiState(
            apps = apps.orEmpty().filterNot { it.packageName in settings.hiddenApps },
            dockApps = settings.dockApps,
            isLoading = apps == null
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppDrawerUiState())

    init {
        viewModelScope.launch {
            allApps.value = container.installedAppsRepository.getAllLaunchableApps()
        }
    }

    fun launchApp(packageName: String) {
        container.appLauncher.launch(packageName)
    }

    fun addToDock(packageName: String) {
        viewModelScope.launch { settingsRepository.addDockApp(packageName) }
    }

    fun removeFromDock(packageName: String) {
        viewModelScope.launch { settingsRepository.removeDockApp(packageName) }
    }
}
