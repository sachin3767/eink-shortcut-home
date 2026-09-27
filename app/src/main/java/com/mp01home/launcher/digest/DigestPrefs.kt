package com.mp01home.launcher.digest

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.digestStore by preferencesDataStore(name = "mp01_digest")

/**
 * FOC-4 feature-private state. Holding (snoozing) other apps' notifications is opt-in because
 * it changes system notification behaviour; the whitelist and times live in LauncherSettings.
 */
class DigestPrefs(private val context: Context) {

    private val holdKey = booleanPreferencesKey("hold_non_whitelisted")

    val holdEnabled: Flow<Boolean> =
        context.digestStore.data.map { it[holdKey] ?: false }.distinctUntilChanged()

    suspend fun isHoldEnabled(): Boolean = holdEnabled.first()

    suspend fun setHoldEnabled(enabled: Boolean) {
        context.digestStore.edit { it[holdKey] = enabled }
    }
}
