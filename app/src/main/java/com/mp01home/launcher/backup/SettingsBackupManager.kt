package com.mp01home.launcher.backup

import android.content.Context
import android.net.Uri
import com.mp01home.launcher.data.settings.Gesture
import com.mp01home.launcher.data.settings.HomeSection
import com.mp01home.launcher.data.settings.SettingsRepository
import com.mp01home.launcher.data.settings.actionFor
import com.mp01home.launcher.eink.ExtrasStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * CORE-7: exports/imports settings + home layout as JSON through the Storage Access Framework
 * (the caller obtains [Uri]s from CreateDocument / OpenDocument). Only user preferences are
 * backed up — caches and tokens are not.
 */
class SettingsBackupManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val extrasStore: ExtrasStore = ExtrasStore(context)
) {

    suspend fun exportTo(uri: Uri) {
        val json = SettingsBackupCodec.encode(settingsRepository.current(), extrasStore.current())
        withContext(Dispatchers.IO) {
            val out = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IOException("Cannot open $uri for writing")
            out.bufferedWriter(Charsets.UTF_8).use { it.write(json) }
        }
    }

    /** Reads, validates and applies a backup. @throws BackupFormatException / IOException. */
    suspend fun importFrom(uri: Uri) {
        val text = withContext(Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Cannot open $uri for reading")
            input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
        if (text.length > MAX_BACKUP_CHARS) throw BackupFormatException("File too large for a settings backup")
        apply(SettingsBackupCodec.decode(text))
    }

    private suspend fun apply(backup: RestoredBackup) {
        val r = settingsRepository
        val s = backup.settings
        val current = r.current()

        r.setAppsAlignment(s.appsAlignment)
        r.setTimelineEnabled(s.timelineEnabled)
        r.setShowTime(s.showTime)
        r.setTimeFormat(s.timeFormat)
        r.setShowDate(s.showDate)
        r.setDateFormat(s.dateFormat)
        r.setShowBattery(s.showBattery)
        r.setShowWeather(s.showWeather)
        r.setWeatherApiKey(s.weatherApiKey)
        r.setWeatherCity(s.weatherCity)
        r.setWeatherUnits(s.weatherUnits)

        r.setDockApps(s.dockApps)
        HomeSection.entries.forEach { r.setSectionEnabled(it, it in s.enabledSections) }

        r.setBodyFontScale(s.bodyFontScale)
        r.setDensity(s.density)
        r.setFullRefreshEveryN(s.fullRefreshEveryN)
        r.setSyncIntervalMinutes(s.syncIntervalMinutes)
        Gesture.entries.forEach { r.setGestureAction(it, s.gestures.actionFor(it)) }

        syncSet(current.hiddenApps, s.hiddenApps) { pkg, on -> r.setAppHidden(pkg, on) }
        syncSet(current.frictionApps, s.frictionApps) { pkg, on -> r.setAppFriction(pkg, on) }
        syncSet(current.digestWhitelist, s.digestWhitelist) { pkg, on -> r.setDigestWhitelisted(pkg, on) }

        r.setSelectedCalendarIds(s.selectedCalendarIds)
        r.setSelectedTaskListIds(s.selectedTaskListIds)

        r.setFocusMinutes(s.focusMinutes)
        r.setBreakMinutes(s.breakMinutes)
        r.setFocusDndEnabled(s.focusDndEnabled)
        r.setDigestTimes(s.digestTimes)

        r.setMorningReviewEnabled(s.morningReviewEnabled)
        r.setMorningReviewTime(s.morningReviewTime)
        r.setEveningReviewEnabled(s.eveningReviewEnabled)
        r.setEveningReviewTime(s.eveningReviewTime)

        extrasStore.replaceAll(backup.extras)
    }

    private suspend fun syncSet(
        current: Set<String>,
        target: Set<String>,
        set: suspend (String, Boolean) -> Unit
    ) {
        (current - target).forEach { set(it, false) }
        (target - current).forEach { set(it, true) }
    }

    companion object {
        const val MIME_TYPE = "application/json"
        const val MAX_BACKUP_CHARS = 1_000_000
        fun suggestedFileName(date: java.time.LocalDate = java.time.LocalDate.now()) =
            "mp01-launcher-backup-$date.json"
    }
}
