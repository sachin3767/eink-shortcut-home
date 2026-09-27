package com.mp01home.launcher.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mp01home.launcher.data.settings.TimeFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Context.syncStatusStore by preferencesDataStore(name = "mp01_sync_status")

/** EINK-7: remembers when each [Syncable] last succeeded. */
class SyncStatusRepository(private val context: Context) {

    private fun keyFor(syncKey: String) = longPreferencesKey("last_success_$syncKey")

    /** Epoch millis of the last successful sync for [syncKey], or null if never. */
    fun lastSuccessMillis(syncKey: String): Flow<Long?> =
        context.syncStatusStore.data.map { it[keyFor(syncKey)] }.distinctUntilChanged()

    suspend fun markSuccess(syncKey: String, atMillis: Long = System.currentTimeMillis()) {
        context.syncStatusStore.edit { it[keyFor(syncKey)] = atMillis }
    }
}

/**
 * EINK-7 label: "synced 10:42" (today), "synced 24 Sep 10:42" (another day) or "not synced".
 * Pure function so it is unit-testable.
 */
fun formatSyncedLabel(
    epochMillis: Long?,
    timeFormat: TimeFormat = TimeFormat.HOUR_24,
    now: ZonedDateTime = ZonedDateTime.now(),
    locale: Locale = Locale.getDefault()
): String {
    if (epochMillis == null || epochMillis <= 0L) return "not synced"
    val zone: ZoneId = now.zone
    val at = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val timePattern = if (timeFormat == TimeFormat.HOUR_24) "HH:mm" else "h:mm a"
    val pattern = if (at.toLocalDate() == now.toLocalDate()) timePattern else "d MMM $timePattern"
    return "synced " + at.format(DateTimeFormatter.ofPattern(pattern, locale))
}
