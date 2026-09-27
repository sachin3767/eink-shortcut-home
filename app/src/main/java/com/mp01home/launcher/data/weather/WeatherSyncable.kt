package com.mp01home.launcher.data.weather

import com.mp01home.launcher.data.settings.SettingsRepository
import com.mp01home.launcher.sync.SyncReason
import com.mp01home.launcher.sync.SyncResult
import com.mp01home.launcher.sync.Syncable
import kotlinx.coroutines.flow.first

/** Keeps the weather cache fresh from the background sync (EINK-6) and on settings changes. */
class WeatherSyncable(
    private val settingsRepository: SettingsRepository,
    private val weatherRepository: WeatherRepository,
    private val cacheRepository: WeatherCacheRepository
) : Syncable {

    override val syncKey: String = "weather"

    override suspend fun sync(reason: SyncReason): SyncResult = refresh(force = false)

    /** Fetches if weather is enabled + configured and the cache is stale (or [force]). */
    suspend fun refresh(force: Boolean): SyncResult {
        val settings = settingsRepository.current()
        if (!settings.showWeather || settings.weatherCity.isBlank() || settings.weatherApiKey.isBlank()) {
            return SyncResult.SKIPPED
        }
        val cached = cacheRepository.cachedSnapshot.first()
        if (!force && cached != null && !weatherRepository.isStale(cached.fetchedAtEpochMillis)) {
            return SyncResult.SKIPPED
        }
        return when (
            val result = weatherRepository.fetchCurrentWeather(
                city = settings.weatherCity,
                apiKey = settings.weatherApiKey,
                units = settings.weatherUnits
            )
        ) {
            is WeatherResult.Success -> {
                cacheRepository.save(result.snapshot)
                SyncResult.SUCCESS
            }
            is WeatherResult.Error -> SyncResult.FAILURE
        }
    }
}
