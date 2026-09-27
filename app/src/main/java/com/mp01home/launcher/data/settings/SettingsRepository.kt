package com.mp01home.launcher.data.settings

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime

private val Context.dataStore by preferencesDataStore(name = "mp01_launcher_settings")

/**
 * User-facing launcher settings, backed by Preferences DataStore.
 *
 * Feature-private state (tokens, sync cursors, caches) does NOT belong here — give it its own
 * DataStore / Room table. Add a field here only if the user can change it in Settings.
 *
 * Ordered lists (dock, digest times) are stored comma-joined because Preferences string sets
 * are unordered.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        /** Legacy (pre-dock) "up to 8 apps" list. Migrated into [DOCK_APPS] once, then removed. */
        val LEGACY_HOME_APPS = stringPreferencesKey("home_apps")

        val APPS_ALIGNMENT = stringPreferencesKey("apps_alignment")
        val TIMELINE_ENABLED = booleanPreferencesKey("timeline_enabled")
        val SHOW_TIME = booleanPreferencesKey("show_time")
        val TIME_FORMAT = stringPreferencesKey("time_format")
        val SHOW_DATE = booleanPreferencesKey("show_date")
        val DATE_FORMAT = stringPreferencesKey("date_format")
        val SHOW_BATTERY = booleanPreferencesKey("show_battery")
        val SHOW_WEATHER = booleanPreferencesKey("show_weather")
        val WEATHER_API_KEY = stringPreferencesKey("weather_api_key")
        val WEATHER_CITY = stringPreferencesKey("weather_city")
        val WEATHER_UNITS = stringPreferencesKey("weather_units")

        val DOCK_APPS = stringPreferencesKey("dock_apps")
        val ENABLED_SECTIONS = stringSetPreferencesKey("enabled_sections")
        val BODY_FONT_SCALE = floatPreferencesKey("body_font_scale")
        val DENSITY = stringPreferencesKey("layout_density")
        val FULL_REFRESH_EVERY_N = intPreferencesKey("full_refresh_every_n")
        val SYNC_INTERVAL_MINUTES = intPreferencesKey("sync_interval_minutes")

        val GESTURE_SWIPE_UP = stringPreferencesKey("gesture_swipe_up")
        val GESTURE_SWIPE_DOWN = stringPreferencesKey("gesture_swipe_down")
        val GESTURE_SWIPE_LEFT = stringPreferencesKey("gesture_swipe_left")
        val GESTURE_SWIPE_RIGHT = stringPreferencesKey("gesture_swipe_right")
        val GESTURE_DOUBLE_TAP = stringPreferencesKey("gesture_double_tap")
        val GESTURE_LONG_PRESS = stringPreferencesKey("gesture_long_press")

        val HIDDEN_APPS = stringSetPreferencesKey("hidden_apps")
        val FRICTION_APPS = stringSetPreferencesKey("friction_apps")
        val SELECTED_CALENDAR_IDS = stringSetPreferencesKey("selected_calendar_ids")
        val SELECTED_TASK_LIST_IDS = stringSetPreferencesKey("selected_task_list_ids")

        val FOCUS_MINUTES = intPreferencesKey("focus_minutes")
        val BREAK_MINUTES = intPreferencesKey("break_minutes")
        val FOCUS_DND = booleanPreferencesKey("focus_dnd")
        val DIGEST_WHITELIST = stringSetPreferencesKey("digest_whitelist")
        val DIGEST_TIMES = stringPreferencesKey("digest_times")

        val MORNING_REVIEW_ENABLED = booleanPreferencesKey("morning_review_enabled")
        val MORNING_REVIEW_TIME = stringPreferencesKey("morning_review_time")
        val EVENING_REVIEW_ENABLED = booleanPreferencesKey("evening_review_enabled")
        val EVENING_REVIEW_TIME = stringPreferencesKey("evening_review_time")

        fun forGesture(gesture: Gesture) = when (gesture) {
            Gesture.SWIPE_UP -> GESTURE_SWIPE_UP
            Gesture.SWIPE_DOWN -> GESTURE_SWIPE_DOWN
            Gesture.SWIPE_LEFT -> GESTURE_SWIPE_LEFT
            Gesture.SWIPE_RIGHT -> GESTURE_SWIPE_RIGHT
            Gesture.DOUBLE_TAP -> GESTURE_DOUBLE_TAP
            Gesture.LONG_PRESS -> GESTURE_LONG_PRESS
        }
    }

    val settings: Flow<LauncherSettings> = context.dataStore.data
        .map { toSettings(it) }
        .distinctUntilChanged()

    /** One-shot read of the current settings (for workers / non-UI code). */
    suspend fun current(): LauncherSettings = settings.first()

    private fun toSettings(prefs: Preferences): LauncherSettings {
        val d = LauncherSettings()
        val g = d.gestures
        return LauncherSettings(
            appsAlignment = SettingsCodec.decodeEnum(prefs[Keys.APPS_ALIGNMENT], d.appsAlignment),
            timelineEnabled = prefs[Keys.TIMELINE_ENABLED] ?: d.timelineEnabled,
            showTime = prefs[Keys.SHOW_TIME] ?: d.showTime,
            timeFormat = SettingsCodec.decodeEnum(prefs[Keys.TIME_FORMAT], d.timeFormat),
            showDate = prefs[Keys.SHOW_DATE] ?: d.showDate,
            dateFormat = SettingsCodec.decodeEnum(prefs[Keys.DATE_FORMAT], d.dateFormat),
            showBattery = prefs[Keys.SHOW_BATTERY] ?: d.showBattery,
            showWeather = prefs[Keys.SHOW_WEATHER] ?: d.showWeather,
            weatherApiKey = prefs[Keys.WEATHER_API_KEY] ?: d.weatherApiKey,
            weatherCity = prefs[Keys.WEATHER_CITY] ?: d.weatherCity,
            weatherUnits = SettingsCodec.decodeEnum(prefs[Keys.WEATHER_UNITS], d.weatherUnits),
            dockApps = SettingsCodec.decodeOrderedList(prefs[Keys.DOCK_APPS])
                .take(LauncherSettings.MAX_DOCK_APPS),
            enabledSections = SettingsCodec.decodeEnumSet(prefs[Keys.ENABLED_SECTIONS], d.enabledSections),
            bodyFontScale = SettingsCodec.clampFontScale(prefs[Keys.BODY_FONT_SCALE] ?: d.bodyFontScale),
            density = SettingsCodec.decodeEnum(prefs[Keys.DENSITY], d.density),
            fullRefreshEveryN = (prefs[Keys.FULL_REFRESH_EVERY_N] ?: d.fullRefreshEveryN)
                .coerceIn(0, LauncherSettings.MAX_FULL_REFRESH_EVERY_N),
            syncIntervalMinutes = SettingsCodec.clampSyncMinutes(
                prefs[Keys.SYNC_INTERVAL_MINUTES] ?: d.syncIntervalMinutes
            ),
            gestures = GestureMapping(
                swipeUp = SettingsCodec.decodeEnum(prefs[Keys.GESTURE_SWIPE_UP], g.swipeUp),
                swipeDown = SettingsCodec.decodeEnum(prefs[Keys.GESTURE_SWIPE_DOWN], g.swipeDown),
                swipeLeft = SettingsCodec.decodeEnum(prefs[Keys.GESTURE_SWIPE_LEFT], g.swipeLeft),
                swipeRight = SettingsCodec.decodeEnum(prefs[Keys.GESTURE_SWIPE_RIGHT], g.swipeRight),
                doubleTap = SettingsCodec.decodeEnum(prefs[Keys.GESTURE_DOUBLE_TAP], g.doubleTap),
                longPress = SettingsCodec.decodeEnum(prefs[Keys.GESTURE_LONG_PRESS], g.longPress)
            ),
            hiddenApps = prefs[Keys.HIDDEN_APPS] ?: d.hiddenApps,
            frictionApps = prefs[Keys.FRICTION_APPS] ?: d.frictionApps,
            selectedCalendarIds = SettingsCodec.decodeLongSet(prefs[Keys.SELECTED_CALENDAR_IDS]),
            selectedTaskListIds = prefs[Keys.SELECTED_TASK_LIST_IDS] ?: d.selectedTaskListIds,
            focusMinutes = (prefs[Keys.FOCUS_MINUTES] ?: d.focusMinutes).coerceIn(1, 180),
            breakMinutes = (prefs[Keys.BREAK_MINUTES] ?: d.breakMinutes).coerceIn(1, 60),
            focusDndEnabled = prefs[Keys.FOCUS_DND] ?: d.focusDndEnabled,
            digestWhitelist = prefs[Keys.DIGEST_WHITELIST] ?: d.digestWhitelist,
            digestTimes = SettingsCodec.decodeTimes(prefs[Keys.DIGEST_TIMES]) ?: d.digestTimes,
            morningReviewEnabled = prefs[Keys.MORNING_REVIEW_ENABLED] ?: d.morningReviewEnabled,
            morningReviewTime = SettingsCodec.decodeTime(prefs[Keys.MORNING_REVIEW_TIME]) ?: d.morningReviewTime,
            eveningReviewEnabled = prefs[Keys.EVENING_REVIEW_ENABLED] ?: d.eveningReviewEnabled,
            eveningReviewTime = SettingsCodec.decodeTime(prefs[Keys.EVENING_REVIEW_TIME]) ?: d.eveningReviewTime
        )
    }

    // ---------------------------------------------------------------------------------------
    // Dock (CORE-3)
    // ---------------------------------------------------------------------------------------

    /**
     * Initialises the dock exactly once:
     * - if a dock is already stored, does nothing;
     * - else migrates the legacy "home_apps" list (first 6 entries);
     * - else stores [defaults] (Calendar, Tasks, Gmail, Keep — whichever are installed).
     */
    suspend fun ensureDockInitialized(defaults: suspend () -> List<String>) {
        val prefs = context.dataStore.data.first()
        if (prefs[Keys.DOCK_APPS] != null) {
            if (prefs[Keys.LEGACY_HOME_APPS] != null) context.dataStore.edit { it.remove(Keys.LEGACY_HOME_APPS) }
            return
        }
        val legacy = SettingsCodec.decodeOrderedList(prefs[Keys.LEGACY_HOME_APPS])
        val initial = (if (legacy.isNotEmpty()) legacy else defaults()).take(LauncherSettings.MAX_DOCK_APPS)
        context.dataStore.edit { p ->
            if (p[Keys.DOCK_APPS] == null) p[Keys.DOCK_APPS] = SettingsCodec.encodeOrderedList(initial)
            p.remove(Keys.LEGACY_HOME_APPS)
        }
    }

    suspend fun setDockApps(packages: List<String>) = edit {
        it[Keys.DOCK_APPS] = SettingsCodec.encodeOrderedList(packages.take(LauncherSettings.MAX_DOCK_APPS))
    }

    /** No-op if already docked or the dock is full. */
    suspend fun addDockApp(packageName: String) = edit { prefs ->
        val current = SettingsCodec.decodeOrderedList(prefs[Keys.DOCK_APPS])
        if (packageName in current || current.size >= LauncherSettings.MAX_DOCK_APPS) return@edit
        prefs[Keys.DOCK_APPS] = SettingsCodec.encodeOrderedList(current + packageName)
    }

    suspend fun removeDockApp(packageName: String) = edit { prefs ->
        val current = SettingsCodec.decodeOrderedList(prefs[Keys.DOCK_APPS])
        prefs[Keys.DOCK_APPS] = SettingsCodec.encodeOrderedList(current - packageName)
    }

    /** Moves a docked app by [delta] positions (negative = left). */
    suspend fun moveDockApp(packageName: String, delta: Int) = edit { prefs ->
        val current = SettingsCodec.decodeOrderedList(prefs[Keys.DOCK_APPS]).toMutableList()
        val from = current.indexOf(packageName)
        if (from < 0) return@edit
        val to = (from + delta).coerceIn(0, current.lastIndex)
        current.add(to, current.removeAt(from))
        prefs[Keys.DOCK_APPS] = SettingsCodec.encodeOrderedList(current)
    }

    // ---------------------------------------------------------------------------------------
    // Layout / header / weather (existing)
    // ---------------------------------------------------------------------------------------

    suspend fun setAppsAlignment(alignment: HorizontalAlignment) = edit { it[Keys.APPS_ALIGNMENT] = alignment.name }
    suspend fun setTimelineEnabled(enabled: Boolean) = edit { it[Keys.TIMELINE_ENABLED] = enabled }
    suspend fun setShowTime(show: Boolean) = edit { it[Keys.SHOW_TIME] = show }
    suspend fun setTimeFormat(format: TimeFormat) = edit { it[Keys.TIME_FORMAT] = format.name }
    suspend fun setShowDate(show: Boolean) = edit { it[Keys.SHOW_DATE] = show }
    suspend fun setDateFormat(format: DateFormat) = edit { it[Keys.DATE_FORMAT] = format.name }
    suspend fun setShowBattery(show: Boolean) = edit { it[Keys.SHOW_BATTERY] = show }
    suspend fun setShowWeather(show: Boolean) = edit { it[Keys.SHOW_WEATHER] = show }
    suspend fun setWeatherApiKey(key: String) = edit { it[Keys.WEATHER_API_KEY] = key.trim() }
    suspend fun setWeatherCity(city: String) = edit { it[Keys.WEATHER_CITY] = city }
    suspend fun setWeatherUnits(units: WeatherUnits) = edit { it[Keys.WEATHER_UNITS] = units.name }

    // ---------------------------------------------------------------------------------------
    // Home sections, display, sync, gestures
    // ---------------------------------------------------------------------------------------

    suspend fun setSectionEnabled(section: HomeSection, enabled: Boolean) = edit { prefs ->
        val current = SettingsCodec.decodeEnumSet(prefs[Keys.ENABLED_SECTIONS], HomeSection.DEFAULTS)
        val next = if (enabled) current + section else current - section
        prefs[Keys.ENABLED_SECTIONS] = next.map { it.name }.toSet()
    }

    suspend fun setBodyFontScale(scale: Float) = edit { it[Keys.BODY_FONT_SCALE] = SettingsCodec.clampFontScale(scale) }
    suspend fun setDensity(density: LayoutDensity) = edit { it[Keys.DENSITY] = density.name }
    suspend fun setFullRefreshEveryN(n: Int) = edit {
        it[Keys.FULL_REFRESH_EVERY_N] = n.coerceIn(0, LauncherSettings.MAX_FULL_REFRESH_EVERY_N)
    }
    suspend fun setSyncIntervalMinutes(minutes: Int) = edit {
        it[Keys.SYNC_INTERVAL_MINUTES] = SettingsCodec.clampSyncMinutes(minutes)
    }
    suspend fun setGestureAction(gesture: Gesture, action: GestureAction) = edit {
        it[Keys.forGesture(gesture)] = action.name
    }

    // ---------------------------------------------------------------------------------------
    // App management (CORE-5, DIS-1)
    // ---------------------------------------------------------------------------------------

    suspend fun setAppHidden(packageName: String, hidden: Boolean) = toggleInSet(Keys.HIDDEN_APPS, packageName, hidden)
    suspend fun setAppFriction(packageName: String, flagged: Boolean) = toggleInSet(Keys.FRICTION_APPS, packageName, flagged)

    // ---------------------------------------------------------------------------------------
    // Integration filters (CAL-7, TASK-5)
    // ---------------------------------------------------------------------------------------

    suspend fun setSelectedCalendarIds(ids: Set<Long>) = edit { it[Keys.SELECTED_CALENDAR_IDS] = ids.map { id -> id.toString() }.toSet() }
    suspend fun setSelectedTaskListIds(ids: Set<String>) = edit { it[Keys.SELECTED_TASK_LIST_IDS] = ids }

    // ---------------------------------------------------------------------------------------
    // Focus / digest (FOC-2/3/4)
    // ---------------------------------------------------------------------------------------

    suspend fun setFocusMinutes(minutes: Int) = edit { it[Keys.FOCUS_MINUTES] = minutes.coerceIn(1, 180) }
    suspend fun setBreakMinutes(minutes: Int) = edit { it[Keys.BREAK_MINUTES] = minutes.coerceIn(1, 60) }
    suspend fun setFocusDndEnabled(enabled: Boolean) = edit { it[Keys.FOCUS_DND] = enabled }
    suspend fun setDigestWhitelisted(packageName: String, whitelisted: Boolean) =
        toggleInSet(Keys.DIGEST_WHITELIST, packageName, whitelisted)
    suspend fun setDigestTimes(times: List<LocalTime>) = edit { it[Keys.DIGEST_TIMES] = SettingsCodec.encodeTimes(times) }

    // ---------------------------------------------------------------------------------------
    // Planning (PLAN-1/2)
    // ---------------------------------------------------------------------------------------

    suspend fun setMorningReviewEnabled(enabled: Boolean) = edit { it[Keys.MORNING_REVIEW_ENABLED] = enabled }
    suspend fun setMorningReviewTime(time: LocalTime) = edit { it[Keys.MORNING_REVIEW_TIME] = SettingsCodec.encodeTime(time) }
    suspend fun setEveningReviewEnabled(enabled: Boolean) = edit { it[Keys.EVENING_REVIEW_ENABLED] = enabled }
    suspend fun setEveningReviewTime(time: LocalTime) = edit { it[Keys.EVENING_REVIEW_TIME] = SettingsCodec.encodeTime(time) }

    // ---------------------------------------------------------------------------------------

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    private suspend fun toggleInSet(key: Preferences.Key<Set<String>>, value: String, present: Boolean) = edit { prefs ->
        val current = prefs[key] ?: emptySet()
        prefs[key] = if (present) current + value else current - value
    }
}
