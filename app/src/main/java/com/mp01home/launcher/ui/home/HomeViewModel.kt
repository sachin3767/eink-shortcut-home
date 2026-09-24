package com.mp01home.launcher.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mp01home.launcher.data.apps.AppInfo
import com.mp01home.launcher.data.apps.InstalledAppsRepository
import com.mp01home.launcher.data.settings.LauncherSettings
import com.mp01home.launcher.data.settings.SettingsRepository
import com.mp01home.launcher.data.weather.WeatherCacheRepository
import com.mp01home.launcher.data.weather.WeatherRepository
import com.mp01home.launcher.data.weather.WeatherResult
import com.mp01home.launcher.data.weather.WeatherSnapshot
import com.mp01home.launcher.util.minuteTicker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

data class HomeUiState(
    val settings: LauncherSettings = LauncherSettings(),
    val homeAppInfos: List<AppInfo> = emptyList(),
    val now: LocalDateTime = LocalDateTime.now(),
    val weather: WeatherSnapshot? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val appsRepository = InstalledAppsRepository(application)
    private val weatherRepository = WeatherRepository()
    private val weatherCacheRepository = WeatherCacheRepository(application)

    private val homeAppInfos = MutableStateFlow<List<AppInfo>>(emptyList())

    val uiState: StateFlow<HomeUiState> = combine(
        settingsRepository.settings,
        minuteTicker(),
        weatherCacheRepository.cachedSnapshot,
        homeAppInfos
    ) { settings, now, weather, appInfos ->
        HomeUiState(settings = settings, homeAppInfos = appInfos, now = now, weather = weather)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    init {
        viewModelScope.launch {
            settingsRepository.settings
                .distinctUntilChanged { old, new -> old.homeApps == new.homeApps }
                .collect { settings -> resolveHomeAppLabels(settings.homeApps) }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                maybeRefreshWeather(settings)
            }
        }
    }

    private suspend fun resolveHomeAppLabels(packages: List<String>) {
        homeAppInfos.value = packages.mapNotNull { pkg ->
            appsRepository.resolveLabel(pkg)?.let { label -> AppInfo(pkg, label) }
        }
    }

    private fun maybeRefreshWeather(settings: LauncherSettings) {
        if (!settings.showWeather || settings.weatherCity.isBlank() || settings.weatherApiKey.isBlank()) return
        val cached = uiState.value.weather
        if (cached != null && !weatherRepository.isStale(cached.fetchedAtEpochMillis)) return

        viewModelScope.launch {
            val result = weatherRepository.fetchCurrentWeather(
                city = settings.weatherCity,
                apiKey = settings.weatherApiKey,
                units = settings.weatherUnits
            )
            if (result is WeatherResult.Success) {
                weatherCacheRepository.save(result.snapshot)
            }
        }
    }

    fun launchApp(packageName: String) = appsRepository.launchApp(packageName)

    fun removeFromHome(packageName: String) {
        viewModelScope.launch { settingsRepository.removeHomeApp(packageName) }
    }
}
