package com.mp01home.launcher.data.weather

data class WeatherSnapshot(
    val city: String,
    val tempRounded: Int,
    val condition: String,
    val unitsSymbol: String,
    val fetchedAtEpochMillis: Long,
    /** EXT-1: max probability of precipitation over the next ~6 hours (0–100), if known. */
    val rainChancePercent: Int? = null
)

sealed interface WeatherResult {
    data class Success(val snapshot: WeatherSnapshot) : WeatherResult
    data class Error(val message: String) : WeatherResult
}
