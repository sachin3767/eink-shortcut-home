package com.mp01home.launcher.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "mp01_launcher_settings")

/**
 * homeApps order is preserved via a comma-joined string (Preferences string sets are
 * unordered, and slot order matters for the top-to-bottom list).
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val HOME_APPS = stringPreferencesKey("home_apps")
        val APPS_ALIGNMENT = stringPreferencesKey("apps_alignment")
        val TIMELINE_ENABLED = booleanPreferencesKey("timeline_enabled")
        val SHOW_TIME = booleanPreferencesKey("show_time")
        val TIME_FORMAT = stringPreferencesKey("time_format")
        val SHOW_DATE = booleanPreferencesKey("show_date")
        val DATE_FORMAT = stringPreferencesKey("date_format")
        val SHOW_WEATHER = booleanPreferencesKey("show_weather")
        val WEATHER_API_KEY = stringPreferencesKey("weather_api_key")
        val WEATHER_CITY = stringPreferencesKey("weather_city")
        val WEATHER_UNITS = stringPreferencesKey("weather_units")
    }

    val settings: Flow<LauncherSettings> = context.dataStore.data.map { prefs ->
        LauncherSettings(
            homeApps = prefs[Keys.HOME_APPS]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.take(LauncherSettings.MAX_HOME_APPS)
                ?: emptyList(),
            appsAlignment = prefs[Keys.APPS_ALIGNMENT]?.let(HorizontalAlignment::valueOf)
                ?: HorizontalAlignment.LEFT,
            timelineEnabled = prefs[Keys.TIMELINE_ENABLED] ?: true,
            showTime = prefs[Keys.SHOW_TIME] ?: true,
            timeFormat = prefs[Keys.TIME_FORMAT]?.let(TimeFormat::valueOf) ?: TimeFormat.HOUR_24,
            showDate = prefs[Keys.SHOW_DATE] ?: true,
            dateFormat = prefs[Keys.DATE_FORMAT]?.let(DateFormat::valueOf) ?: DateFormat.ISO,
            showWeather = prefs[Keys.SHOW_WEATHER] ?: false,
            weatherApiKey = prefs[Keys.WEATHER_API_KEY] ?: "",
            weatherCity = prefs[Keys.WEATHER_CITY] ?: "",
            weatherUnits = prefs[Keys.WEATHER_UNITS]?.let(WeatherUnits::valueOf) ?: WeatherUnits.CELSIUS
        )
    }

    suspend fun addHomeApp(packageName: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.HOME_APPS]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            if (current.contains(packageName) || current.size >= LauncherSettings.MAX_HOME_APPS) return@edit
            prefs[Keys.HOME_APPS] = (current + packageName).joinToString(",")
        }
    }

    suspend fun removeHomeApp(packageName: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.HOME_APPS]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            prefs[Keys.HOME_APPS] = current.filterNot { it == packageName }.joinToString(",")
        }
    }

    suspend fun setAppsAlignment(alignment: HorizontalAlignment) {
        context.dataStore.edit { it[Keys.APPS_ALIGNMENT] = alignment.name }
    }

    suspend fun setTimelineEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TIMELINE_ENABLED] = enabled }
    }

    suspend fun setShowTime(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_TIME] = show }
    }

    suspend fun setTimeFormat(format: TimeFormat) {
        context.dataStore.edit { it[Keys.TIME_FORMAT] = format.name }
    }

    suspend fun setShowDate(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_DATE] = show }
    }

    suspend fun setDateFormat(format: DateFormat) {
        context.dataStore.edit { it[Keys.DATE_FORMAT] = format.name }
    }

    suspend fun setShowWeather(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_WEATHER] = show }
    }

    suspend fun setWeatherApiKey(key: String) {
        context.dataStore.edit { it[Keys.WEATHER_API_KEY] = key }
    }

    suspend fun setWeatherCity(city: String) {
        context.dataStore.edit { it[Keys.WEATHER_CITY] = city }
    }

    suspend fun setWeatherUnits(units: WeatherUnits) {
        context.dataStore.edit { it[Keys.WEATHER_UNITS] = units.name }
    }
}
