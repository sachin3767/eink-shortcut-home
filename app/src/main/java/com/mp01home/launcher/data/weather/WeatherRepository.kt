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
 * Thin client for OpenWeatherMap's "current weather by city name" endpoint.
 * No background polling: callers decide when a refresh is due (see [isStale]).
 */
class WeatherRepository(
    private val client: OkHttpClient = OkHttpClient()
) {
    companion object {
        private const val BASE_URL = "https://api.openweathermap.org/data/2.5/weather"
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

        val url = "$BASE_URL?q=${URLEncoder.encode(city, "UTF-8")}" +
            "&appid=$apiKey&units=${units.apiValue}"
        val request = Request.Builder().url(url).build()

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
                        fetchedAtEpochMillis = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: IOException) {
            WeatherResult.Error("Network error: ${e.message ?: "unreachable"}")
        }
    }
}
