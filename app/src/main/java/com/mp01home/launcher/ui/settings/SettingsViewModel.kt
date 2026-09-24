package com.mp01home.launcher.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)

    val settings: StateFlow<LauncherSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LauncherSettings())

    fun setAppsAlignment(alignment: HorizontalAlignment) =
        viewModelScope.launch { settingsRepository.setAppsAlignment(alignment) }

    fun setTimelineEnabled(enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setTimelineEnabled(enabled) }

    fun setShowTime(show: Boolean) =
        viewModelScope.launch { settingsRepository.setShowTime(show) }

    fun setTimeFormat(format: TimeFormat) =
        viewModelScope.launch { settingsRepository.setTimeFormat(format) }

    fun setShowDate(show: Boolean) =
        viewModelScope.launch { settingsRepository.setShowDate(show) }

    fun setDateFormat(format: DateFormat) =
        viewModelScope.launch { settingsRepository.setDateFormat(format) }

    fun setShowWeather(show: Boolean) =
        viewModelScope.launch { settingsRepository.setShowWeather(show) }

    fun setWeatherApiKey(key: String) =
        viewModelScope.launch { settingsRepository.setWeatherApiKey(key) }

    fun setWeatherCity(city: String) =
        viewModelScope.launch { settingsRepository.setWeatherCity(city) }

    fun setWeatherUnits(units: WeatherUnits) =
        viewModelScope.launch { settingsRepository.setWeatherUnits(units) }
}
