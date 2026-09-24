package com.mp01home.launcher.data.settings

enum class HorizontalAlignment { LEFT, RIGHT }

fun HorizontalAlignment.opposite(): HorizontalAlignment =
    if (this == HorizontalAlignment.LEFT) HorizontalAlignment.RIGHT else HorizontalAlignment.LEFT

enum class TimeFormat { HOUR_24, HOUR_12 }

enum class DateFormat(val pattern: String) {
    ISO("yyyy-MM-dd"),
    US("MM/dd/yyyy"),
    EU("dd/MM/yyyy"),
    LONG("EEE, d MMM")
}

enum class WeatherUnits(val apiValue: String, val symbol: String) {
    CELSIUS("metric", "°C"),
    FAHRENHEIT("imperial", "°F")
}

data class LauncherSettings(
    val homeApps: List<String> = emptyList(),
    val appsAlignment: HorizontalAlignment = HorizontalAlignment.LEFT,
    val timelineEnabled: Boolean = true,
    val showTime: Boolean = true,
    val timeFormat: TimeFormat = TimeFormat.HOUR_24,
    val showDate: Boolean = true,
    val dateFormat: DateFormat = DateFormat.ISO,
    val showWeather: Boolean = false,
    val weatherApiKey: String = "",
    val weatherCity: String = "",
    val weatherUnits: WeatherUnits = WeatherUnits.CELSIUS
) {
    val timelineAlignment: HorizontalAlignment get() = appsAlignment.opposite()

    companion object {
        const val MAX_HOME_APPS = 8
    }
}
