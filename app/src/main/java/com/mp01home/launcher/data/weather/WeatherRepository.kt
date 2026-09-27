package com.mp01home.launcher.data.weather

import com.mp01home.launcher.data.settings.WeatherUnits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * Thin client for OpenWeatherMap (EXT-1; OWM is used instead of Open-Meteo, see SPEC §10):
 * - "current weather by city name" for temperature + condition;
 * - "5 day / 3 hour forecast" (first [FORECAST_SLOTS] slots ≈ next 6 hours) for rain chance.
 *
 * No polling here: [WeatherSyncable] decides when a refresh is due (see [isStale]).
 */
class WeatherRepository(
    private val client: OkHttpClient = OkHttpClient()
) {
    companion object {
        private const val BASE_URL = "https://api.openweathermap.org/data/2.5/weather"
        private const val FORECAST_URL = "https://api.openweathermap.org/data/2.5/forecast"
        private const val FORECAST_SLOTS = 2
        val CACHE_TTL_MILLIS = 45 * 60 * 1000L
    }

    fun isStale(fetchedAtEpochMillis: Long, now: Long = System.currentTimeMillis()): Boolean =
        now - fetchedAtEpochMillis > CACHE_TTL_MILLIS

    suspend fun fetchCurrentWeather(
        city: String,
        apiKey: String,
        units: WeatherUnits
    ): WeatherResult = withContext(Dispatchers.IO) {
        if (city.isBlank() || apiKey.isBlank()) {
            return@withContext WeatherResult.Error("Set a city and API key in Settings")
        }

        val query = "q=${URLEncoder.encode(city, "UTF-8")}&appid=$apiKey&units=${units.apiValue}"
        val request = Request.Builder().url("$BASE_URL?$query").build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = runCatching { JSONObject(body).optString("message") }.getOrNull()
                    return@withContext WeatherResult.Error(
                        message?.takeIf { it.isNotBlank() } ?: "Weather request failed (${response.code})"
                    )
                }
                val json = JSONObject(body)
                val main = json.getJSONObject("main")
                val weatherArray = json.getJSONArray("weather")
                val condition = if (weatherArray.length() > 0) {
                    weatherArray.getJSONObject(0).optString("main", "")
                } else ""

                WeatherResult.Success(
                    WeatherSnapshot(
                        city = json.optString("name", city),
                        tempRounded = Math.round(main.getDouble("temp")).toInt(),
                        condition = condition,
                        unitsSymbol = units.symbol,
                        fetchedAtEpochMillis = System.currentTimeMillis(),
                        rainChancePercent = fetchRainChancePercent(query)
                    )
                )
            }
        } catch (e: IOException) {
            WeatherResult.Error("Network error: ${e.message ?: "unreachable"}")
        } catch (e: org.json.JSONException) {
            WeatherResult.Error("Unexpected weather response")
        }
    }

    /** Max "pop" (probability of precipitation) over the next forecast slots; null on any failure. */
    private fun fetchRainChancePercent(query: String): Int? = runCatching {
        val request = Request.Builder().url("$FORECAST_URL?$query&cnt=$FORECAST_SLOTS").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val list = JSONObject(response.body?.string().orEmpty()).getJSONArray("list")
            var maxPop = -1.0
            for (i in 0 until list.length()) {
                maxPop = maxOf(maxPop, list.getJSONObject(i).optDouble("pop", -1.0))
            }
            if (maxPop < 0) null else Math.round(maxPop * 100).toInt().coerceIn(0, 100)
        }
    }.getOrNull()
}
