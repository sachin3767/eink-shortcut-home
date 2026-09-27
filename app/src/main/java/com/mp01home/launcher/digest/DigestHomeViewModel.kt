package com.mp01home.launcher.digest

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mp01home.launcher.appContainer
import com.mp01home.launcher.data.settings.TimeFormat
import com.mp01home.launcher.sync.formatSyncedLabel
import com.mp01home.launcher.util.minuteTicker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DigestUiState(
    val view: DigestView = DigestView.EMPTY,
    val holdEnabled: Boolean = false,
    val hasWhitelist: Boolean = false,
    /** "18:00" or "6:00 PM"; null when no digest times are set. */
    val nextReleaseLabel: String? = null,
    val syncedLabel: String = "not synced"
)

/** Backs DigestSection (FOC-4). Recomputed per minute so held items release on time. */
class DigestHomeViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer
    private val repository = container.digestRepository

    val state: StateFlow<DigestUiState> = combine(
        repository.rows,
        container.settingsRepository.settings,
        repository.prefs.holdEnabled,
        repository.lastUpdateMillis,
        minuteTicker()
    ) { rows, settings, hold, lastMillis, _ ->
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val view = DigestSummarizer.summarize(rows, now, zone, settings.digestTimes)
        DigestUiState(
            view = view,
            holdEnabled = hold,
            hasWhitelist = settings.digestWhitelist.isNotEmpty(),
            nextReleaseLabel = view.nextReleaseMillis?.let { formatClock(it, settings.timeFormat, zone) },
            syncedLabel = formatSyncedLabel(lastMillis, settings.timeFormat)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DigestUiState())

    /** Opens the app behind a digest group and clears its rows. */
    fun openGroup(group: DigestGroup) {
        repository.dismiss(group.keys)
        container.appLauncher.launch(group.packageName)
    }

    fun clearAll() = repository.clearAll()
}

/** "HH:mm" or "h:mm a" in [zone]. */
fun formatClock(epochMillis: Long, timeFormat: TimeFormat, zone: ZoneId, locale: Locale = Locale.getDefault()): String {
    val pattern = if (timeFormat == TimeFormat.HOUR_24) "HH:mm" else "h:mm a"
    return Instant.ofEpochMilli(epochMillis).atZone(zone).format(DateTimeFormatter.ofPattern(pattern, locale))
}
