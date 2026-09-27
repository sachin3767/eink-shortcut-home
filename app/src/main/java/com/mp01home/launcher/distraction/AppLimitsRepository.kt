package com.mp01home.launcher.distraction

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.distractionStore by preferencesDataStore(name = "mp01_distraction")

/**
 * DIS-2: per-app daily minute limits, {packageName → minutes}. Stored in this feature's own
 * DataStore (encoded by [encodeLimits]) because LauncherSettings has no field for it yet.
 */
class AppLimitsRepository(private val context: Context) {

    private val key = stringPreferencesKey("app_limits")

    val limits: Flow<Map<String, Int>> =
        context.distractionStore.data.map { decodeLimits(it[key]) }.distinctUntilChanged()

    /** [minutes] ≤ 0 removes the limit. */
    suspend fun setLimit(packageName: String, minutes: Int) {
        context.distractionStore.edit { prefs ->
            val current = decodeLimits(prefs[key]).toMutableMap()
            if (minutes > 0) current[packageName] = minutes else current.remove(packageName)
            prefs[key] = encodeLimits(current)
        }
    }
}
