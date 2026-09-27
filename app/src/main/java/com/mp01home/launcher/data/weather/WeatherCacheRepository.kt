package com.mp01home.launcher.data.weather

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.weatherCacheStore by preferencesDataStore(name = "mp01_weather_cache")

/** Persists the last successful weather fetch so the home screen has something to show
 *  immediately on launch, before a fresh network fetch (if due) completes. */
class WeatherCacheRepository(private val context: Context) {

    private object Keys {
        val CITY = stringPreferencesKey("city")
        val TEMP = intPreferencesKey("temp")
        val CONDITION = stringPreferencesKey("condition")
        val UNITS_SYMBOL = stringPreferencesKey("units_symbol")
        val FETCHED_AT = longPreferencesKey("fetched_at")
        val RAIN_CHANCE = intPreferencesKey("rain_chance")
    }

    val cachedSnapshot: Flow<WeatherSnapshot?> = context.weatherCacheStore.data.map { prefs ->
        val fetchedAt = prefs[Keys.FETCHED_AT] ?: return@map null
        WeatherSnapshot(
            city = prefs[Keys.CITY] ?: return@map null,
            tempRounded = prefs[Keys.TEMP] ?: return@map null,
            condition = prefs[Keys.CONDITION] ?: "",
            unitsSymbol = prefs[Keys.UNITS_SYMBOL] ?: "",
            fetchedAtEpochMillis = fetchedAt,
            rainChancePercent = prefs[Keys.RAIN_CHANCE]
        )
    }

    suspend fun save(snapshot: WeatherSnapshot) {
        context.weatherCacheStore.edit { prefs ->
            val rain = snapshot.rainChancePercent
            if (rain != null) {
                prefs[Keys.RAIN_CHANCE] = rain
            } else {
                prefs.remove(Keys.RAIN_CHANCE)
            }
            prefs[Keys.CITY] = snapshot.city
            prefs[Keys.TEMP] = snapshot.tempRounded
            prefs[Keys.CONDITION] = snapshot.condition
            prefs[Keys.UNITS_SYMBOL] = snapshot.unitsSymbol
            prefs[Keys.FETCHED_AT] = snapshot.fetchedAtEpochMillis
        }
    }
}
