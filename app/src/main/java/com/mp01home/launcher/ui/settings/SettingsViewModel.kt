package com.mp01home.launcher.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mp01home.launcher.appContainer
import com.mp01home.launcher.data.settings.DateFormat
import com.mp01home.launcher.data.settings.HorizontalAlignment
import com.mp01home.launcher.data.settings.LauncherSettings
import com.mp01home.launcher.data.settings.SettingsRepository
import com.mp01home.launcher.data.settings.TimeFormat
import com.mp01home.launcher.data.settings.WeatherUnits
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Shared by the whole settings screen, including the feature slots in ui/settings/sections/.
 * Feature slots write through [update], e.g.
 * `viewModel.update { setSelectedCalendarIds(ids) }`, so they never need to edit this class.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = application.appContainer.settingsRepository

    val settings: StateFlow<LauncherSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherSettings())

    /** Runs any [SettingsRepository] write in the ViewModel scope. */
    fun update(block: suspend SettingsRepository.() -> Unit) {
        viewModelScope.launch { settingsRepository.block() }
    }

    fun setAppsAlignment(alignment: HorizontalAlignment) = update { setAppsAlignment(alignment) }
    fun setTimelineEnabled(enabled: Boolean) = update { setTimelineEnabled(enabled) }
    fun setShowTime(show: Boolean) = update { setShowTime(show) }
    fun setTimeFormat(format: TimeFormat) = update { setTimeFormat(format) }
    fun setShowDate(show: Boolean) = update { setShowDate(show) }
    fun setDateFormat(format: DateFormat) = update { setDateFormat(format) }
    fun setShowBattery(show: Boolean) = update { setShowBattery(show) }
    fun setShowWeather(show: Boolean) = update { setShowWeather(show) }
    fun setWeatherApiKey(key: String) = update { setWeatherApiKey(key) }
    fun setWeatherCity(city: String) = update { setWeatherCity(city) }
    fun setWeatherUnits(units: WeatherUnits) = update { setWeatherUnits(units) }
}
