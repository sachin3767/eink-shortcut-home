package com.mp01home.launcher.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mp01home.launcher.appContainer
import com.mp01home.launcher.data.apps.AppInfo
import com.mp01home.launcher.data.settings.LauncherSettings
import com.mp01home.launcher.data.weather.WeatherSnapshot
import com.mp01home.launcher.util.batteryPercentFlow
import com.mp01home.launcher.util.minuteTicker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Foundation-owned state for the home frame (header + dock). Feature sections own their own state. */
data class HomeUiState(
    val settings: LauncherSettings = LauncherSettings(),
    /** Resolved dock entries, in dock order; uninstalled packages are skipped. */
    val dock: List<AppInfo> = emptyList(),
    val now: LocalDateTime = LocalDateTime.now(),
    val weather: WeatherSnapshot? = null,
    val batteryPercent: Int? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer
    private val settingsRepository = container.settingsRepository
    private val appsRepository = container.installedAppsRepository

    private val dock = MutableStateFlow<List<AppInfo>>(emptyList())

    /** Emits null first so the header renders before the sticky battery broadcast arrives. */
    private val battery: Flow<Int?> = flow {
        emit(null)
        emitAll(batteryPercentFlow(application))
    }

    val uiState: StateFlow<HomeUiState> = combine(
        settingsRepository.settings,
        minuteTicker(),
        container.weatherCacheRepository.cachedSnapshot,
        dock,
        battery
    ) { settings, now, weather, dockApps, batteryPercent ->
        HomeUiState(
            settings = settings,
            dock = dockApps,
            now = now,
            weather = weather,
            batteryPercent = batteryPercent
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch {
            settingsRepository.settings
                .map { it.dockApps }
                .distinctUntilChanged()
                .collect { packages -> resolveDock(packages) }
        }
        viewModelScope.launch {
            // First emission: refresh only if stale. Later weather-setting edits: force a refetch.
            settingsRepository.settings
                .map { WeatherConfig(it.showWeather, it.weatherCity, it.weatherApiKey, it.weatherUnits.name) }
                .distinctUntilChanged()
                .withIndex()
                .collectLatest { indexed ->
                    if (indexed.index == 0) {
                        container.weatherSyncable.refresh(force = false)
                    } else {
                        // Settings text fields save per keystroke: wait until typing settles.
                        delay(WEATHER_EDIT_SETTLE_MILLIS)
                        container.weatherSyncable.refresh(force = true)
                    }
                }
        }
    }

    private data class WeatherConfig(val show: Boolean, val city: String, val apiKey: String, val units: String)

    private suspend fun resolveDock(packages: List<String>) {
        dock.value = packages.mapNotNull { pkg ->
            appsRepository.resolveLabel(pkg)?.let { label -> AppInfo(pkg, label) }
        }
    }

    fun launchApp(packageName: String) {
        container.appLauncher.launch(packageName)
    }

    fun removeFromDock(packageName: String) {
        viewModelScope.launch { settingsRepository.removeDockApp(packageName) }
    }

    fun requestFullRefresh() = container.einkRefresh.requestFullRefresh()

    private companion object {
        const val WEATHER_EDIT_SETTLE_MILLIS = 2_000L
    }
}
