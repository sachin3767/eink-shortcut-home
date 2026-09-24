package com.mp01home.launcher.data.weather

data class WeatherSnapshot(
    val city: String,
    val tempRounded: Int,
    val condition: String,
    val unitsSymbol: String,
    val fetchedAtEpochMillis: Long
)

sealed interface WeatherResult {
    data class Success(val snapshot: WeatherSnapshot) : WeatherResult
    data class Error(val message: String) : WeatherResult
}
