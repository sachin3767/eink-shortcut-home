package com.mp01home.launcher.backup

import com.mp01home.launcher.data.settings.GestureMapping
import com.mp01home.launcher.data.settings.HomeSection
import com.mp01home.launcher.data.settings.LauncherSettings
import com.mp01home.launcher.data.settings.SettingsCodec
import com.mp01home.launcher.eink.ExtrasPrefs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * CORE-7: JSON form of a settings + layout backup. Every field is optional on import, so files
 * written by older/newer versions still load (unknown keys are ignored, missing ones fall back to
 * the defaults). Enums and times are stored as their names / "HH:mm" via [SettingsCodec].
 */
@Serializable
data class SettingsBackup(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val appsAlignment: String? = null,
    val timelineEnabled: Boolean? = null,
    val showTime: Boolean? = null,
    val timeFormat: String? = null,
    val showDate: Boolean? = null,
    val dateFormat: String? = null,
    val showBattery: Boolean? = null,
    val showWeather: Boolean? = null,
    val weatherApiKey: String? = null,
    val weatherCity: String? = null,
    val weatherUnits: String? = null,
    val dockApps: List<String>? = null,
    val enabledSections: List<String>? = null,
    val bodyFontScale: Float? = null,
    val density: String? = null,
    val fullRefreshEveryN: Int? = null,
    val syncIntervalMinutes: Int? = null,
    val gestures: Map<String, String>? = null,
    val hiddenApps: List<String>? = null,
    val frictionApps: List<String>? = null,
    val selectedCalendarIds: List<Long>? = null,
    val selectedTaskListIds: List<String>? = null,
    val focusMinutes: Int? = null,
    val breakMinutes: Int? = null,
    val focusDndEnabled: Boolean? = null,
    val digestWhitelist: List<String>? = null,
    val digestTimes: List<String>? = null,
    val morningReviewEnabled: Boolean? = null,
    val morningReviewTime: String? = null,
    val eveningReviewEnabled: Boolean? = null,
    val eveningReviewTime: String? = null,
    // Extras feature DataStore (UX-5, UX-6, EXT-3)
    val sleepScreenEnabled: Boolean? = null,
    val vendorRefreshMode: String? = null,
    val readingApps: List<String>? = null
) {
    companion object {
        const val FORMAT = "mp01-launcher-backup"
        const val VERSION = 1
    }
}

class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Result of decoding a backup. */
data class RestoredBackup(val settings: LauncherSettings, val extras: ExtrasPrefs)

object SettingsBackupCodec {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(settings: LauncherSettings, extras: ExtrasPrefs): String = json.encodeToString(
        SettingsBackup.serializer(),
        SettingsBackup(
            appsAlignment = settings.appsAlignment.name,
            timelineEnabled = settings.timelineEnabled,
            showTime = settings.showTime,
            timeFormat = settings.timeFormat.name,
            showDate = settings.showDate,
            dateFormat = settings.dateFormat.name,
            showBattery = settings.showBattery,
            showWeather = settings.showWeather,
            weatherApiKey = settings.weatherApiKey,
            weatherCity = settings.weatherCity,
            weatherUnits = settings.weatherUnits.name,
            dockApps = settings.dockApps,
            enabledSections = settings.enabledSections.map { it.name }.sorted(),
            bodyFontScale = settings.bodyFontScale,
            density = settings.density.name,
            fullRefreshEveryN = settings.fullRefreshEveryN,
            syncIntervalMinutes = settings.syncIntervalMinutes,
            gestures = mapOf(
                "swipeUp" to settings.gestures.swipeUp.name,
                "swipeDown" to settings.gestures.swipeDown.name,
                "swipeLeft" to settings.gestures.swipeLeft.name,
                "swipeRight" to settings.gestures.swipeRight.name,
                "doubleTap" to settings.gestures.doubleTap.name,
                "longPress" to settings.gestures.longPress.name
            ),
            hiddenApps = settings.hiddenApps.sorted(),
            frictionApps = settings.frictionApps.sorted(),
            selectedCalendarIds = settings.selectedCalendarIds.sorted(),
            selectedTaskListIds = settings.selectedTaskListIds.sorted(),
            focusMinutes = settings.focusMinutes,
            breakMinutes = settings.breakMinutes,
            focusDndEnabled = settings.focusDndEnabled,
            digestWhitelist = settings.digestWhitelist.sorted(),
            digestTimes = settings.digestTimes.map(SettingsCodec::encodeTime),
            morningReviewEnabled = settings.morningReviewEnabled,
            morningReviewTime = SettingsCodec.encodeTime(settings.morningReviewTime),
            eveningReviewEnabled = settings.eveningReviewEnabled,
            eveningReviewTime = SettingsCodec.encodeTime(settings.eveningReviewTime),
            sleepScreenEnabled = extras.sleepScreenEnabled,
            vendorRefreshMode = extras.vendorRefreshMode.name,
            readingApps = extras.readingApps
        )
    )

    /** @throws BackupFormatException if [text] is not a launcher backup. */
    fun decode(text: String): RestoredBackup {
        val b = try {
            json.decodeFromString(SettingsBackup.serializer(), text)
        } catch (e: Exception) {
            throw BackupFormatException("Not a valid backup file", e)
        }
        if (b.format != SettingsBackup.FORMAT) throw BackupFormatException("Not a launcher backup (format '${b.format}')")

        val d = LauncherSettings()
        val g = d.gestures
        val gestures = b.gestures.orEmpty()
        val settings = LauncherSettings(
            appsAlignment = SettingsCodec.decodeEnum(b.appsAlignment, d.appsAlignment),
            timelineEnabled = b.timelineEnabled ?: d.timelineEnabled,
            showTime = b.showTime ?: d.showTime,
            timeFormat = SettingsCodec.decodeEnum(b.timeFormat, d.timeFormat),
            showDate = b.showDate ?: d.showDate,
            dateFormat = SettingsCodec.decodeEnum(b.dateFormat, d.dateFormat),
            showBattery = b.showBattery ?: d.showBattery,
            showWeather = b.showWeather ?: d.showWeather,
            weatherApiKey = b.weatherApiKey ?: d.weatherApiKey,
            weatherCity = b.weatherCity ?: d.weatherCity,
            weatherUnits = SettingsCodec.decodeEnum(b.weatherUnits, d.weatherUnits),
            dockApps = (b.dockApps ?: d.dockApps).distinct().take(LauncherSettings.MAX_DOCK_APPS),
            enabledSections = SettingsCodec.decodeEnumSet<HomeSection>(b.enabledSections?.toSet(), d.enabledSections),
            bodyFontScale = SettingsCodec.clampFontScale(b.bodyFontScale ?: d.bodyFontScale),
            density = SettingsCodec.decodeEnum(b.density, d.density),
            fullRefreshEveryN = (b.fullRefreshEveryN ?: d.fullRefreshEveryN)
                .coerceIn(0, LauncherSettings.MAX_FULL_REFRESH_EVERY_N),
            syncIntervalMinutes = SettingsCodec.clampSyncMinutes(b.syncIntervalMinutes ?: d.syncIntervalMinutes),
            gestures = GestureMapping(
                swipeUp = SettingsCodec.decodeEnum(gestures["swipeUp"], g.swipeUp),
                swipeDown = SettingsCodec.decodeEnum(gestures["swipeDown"], g.swipeDown),
                swipeLeft = SettingsCodec.decodeEnum(gestures["swipeLeft"], g.swipeLeft),
                swipeRight = SettingsCodec.decodeEnum(gestures["swipeRight"], g.swipeRight),
                doubleTap = SettingsCodec.decodeEnum(gestures["doubleTap"], g.doubleTap),
                longPress = SettingsCodec.decodeEnum(gestures["longPress"], g.longPress)
            ),
            hiddenApps = b.hiddenApps?.toSet() ?: d.hiddenApps,
            frictionApps = b.frictionApps?.toSet() ?: d.frictionApps,
            selectedCalendarIds = b.selectedCalendarIds?.toSet() ?: d.selectedCalendarIds,
            selectedTaskListIds = b.selectedTaskListIds?.toSet() ?: d.selectedTaskListIds,
            focusMinutes = (b.focusMinutes ?: d.focusMinutes).coerceIn(1, 180),
            breakMinutes = (b.breakMinutes ?: d.breakMinutes).coerceIn(1, 60),
            focusDndEnabled = b.focusDndEnabled ?: d.focusDndEnabled,
            digestWhitelist = b.digestWhitelist?.toSet() ?: d.digestWhitelist,
            digestTimes = b.digestTimes?.mapNotNull(SettingsCodec::decodeTime)?.distinct()?.sorted() ?: d.digestTimes,
            morningReviewEnabled = b.morningReviewEnabled ?: d.morningReviewEnabled,
            morningReviewTime = SettingsCodec.decodeTime(b.morningReviewTime) ?: d.morningReviewTime,
            eveningReviewEnabled = b.eveningReviewEnabled ?: d.eveningReviewEnabled,
            eveningReviewTime = SettingsCodec.decodeTime(b.eveningReviewTime) ?: d.eveningReviewTime
        )
        val e = ExtrasPrefs()
        val extras = ExtrasPrefs(
            sleepScreenEnabled = b.sleepScreenEnabled ?: e.sleepScreenEnabled,
            vendorRefreshMode = SettingsCodec.decodeEnum(b.vendorRefreshMode, e.vendorRefreshMode),
            readingApps = (b.readingApps ?: e.readingApps).distinct().take(ExtrasPrefs.MAX_READING_APPS)
        )
        return RestoredBackup(settings, extras)
    }
}
