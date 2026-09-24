package com.mp01home.launcher.ui.drawer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mp01home.launcher.data.apps.AppInfo
import com.mp01home.launcher.data.apps.InstalledAppsRepository
import com.mp01home.launcher.data.settings.LauncherSettings
import com.mp01home.launcher.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppDrawerUiState(
    val apps: List<AppInfo> = emptyList(),
    val homeApps: List<String> = emptyList(),
    val isLoading: Boolean = true
)

class AppDrawerViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val appsRepository = InstalledAppsRepository(application)

    private val _uiState = MutableStateFlow(AppDrawerUiState())
    val uiState: StateFlow<AppDrawerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val apps = appsRepository.getAllLaunchableApps()
            _uiState.value = _uiState.value.copy(apps = apps, isLoading = false)
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { settings: LauncherSettings ->
                _uiState.value = _uiState.value.copy(homeApps = settings.homeApps)
            }
        }
    }

    val isHomeFull: Boolean get() = _uiState.value.homeApps.size >= LauncherSettings.MAX_HOME_APPS

    fun launchApp(packageName: String) = appsRepository.launchApp(packageName)

    fun addToHome(packageName: String) {
        viewModelScope.launch { settingsRepository.addHomeApp(packageName) }
    }

    fun removeFromHome(packageName: String) {
        viewModelScope.launch { settingsRepository.removeHomeApp(packageName) }
    }
}
