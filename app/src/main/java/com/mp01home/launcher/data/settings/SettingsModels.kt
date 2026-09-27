package com.mp01home.launcher.data.settings

import java.time.LocalTime

enum class HorizontalAlignment { LEFT, RIGHT }

fun HorizontalAlignment.opposite(): HorizontalAlignment =
    if (this == HorizontalAlignment.LEFT) HorizontalAlignment.RIGHT else HorizontalAlignment.LEFT

enum class TimeFormat { HOUR_24, HOUR_12 }

enum class DateFormat(val pattern: String) {
    ISO("yyyy-MM-dd"),
    US("MM/dd/yyyy"),
    EU("dd/MM/yyyy"),
    LONG("EEE, d MMM")
}

enum class WeatherUnits(val apiValue: String, val symbol: String) {
    CELSIUS("metric", "°C"),
    FAHRENHEIT("imperial", "°F")
}

/** UX-7: vertical spacing density. [spacingScale] multiplies paddings/gaps. */
enum class LayoutDensity(val spacingScale: Float) {
    COMPACT(0.7f),
    NORMAL(1.0f),
    SPACIOUS(1.4f)
}

/** CORE-6: what a home gesture does. */
enum class GestureAction {
    NONE,
    OPEN_DRAWER,
    QUICK_CAPTURE,
    FULL_REFRESH,
    OPEN_SETTINGS,
    NEXT_PAGE,
    PREVIOUS_PAGE
}

/** CORE-6: configurable home gestures. */
data class GestureMapping(
    val swipeUp: GestureAction = GestureAction.OPEN_DRAWER,
    val swipeDown: GestureAction = GestureAction.QUICK_CAPTURE,
    val swipeLeft: GestureAction = GestureAction.NEXT_PAGE,
    val swipeRight: GestureAction = GestureAction.PREVIOUS_PAGE,
    val doubleTap: GestureAction = GestureAction.FULL_REFRESH,
    val longPress: GestureAction = GestureAction.OPEN_SETTINGS
)

enum class Gesture { SWIPE_UP, SWIPE_DOWN, SWIPE_LEFT, SWIPE_RIGHT, DOUBLE_TAP, LONG_PRESS }

fun GestureMapping.actionFor(gesture: Gesture): GestureAction = when (gesture) {
    Gesture.SWIPE_UP -> swipeUp
    Gesture.SWIPE_DOWN -> swipeDown
    Gesture.SWIPE_LEFT -> swipeLeft
    Gesture.SWIPE_RIGHT -> swipeRight
    Gesture.DOUBLE_TAP -> doubleTap
    Gesture.LONG_PRESS -> longPress
}

fun GestureMapping.with(gesture: Gesture, action: GestureAction): GestureMapping = when (gesture) {
    Gesture.SWIPE_UP -> copy(swipeUp = action)
    Gesture.SWIPE_DOWN -> copy(swipeDown = action)
    Gesture.SWIPE_LEFT -> copy(swipeLeft = action)
    Gesture.SWIPE_RIGHT -> copy(swipeRight = action)
    Gesture.DOUBLE_TAP -> copy(doubleTap = action)
    Gesture.LONG_PRESS -> copy(longPress = action)
}

/**
 * Toggleable home-screen section slots (see ui/home/sections/). HEADER is always shown.
 * [defaultEnabled] applies only when the user has never touched the section toggles.
 */
enum class HomeSection(val defaultEnabled: Boolean) {
    TOP3(true),          // FOC-1
    CALENDAR(true),      // CAL-1/2/4/5
    TASKS(true),         // TASK-2/3
    MAIL(true),          // MAIL-1/2/3
    DIGEST(false),       // FOC-4
    FOCUS(false),        // FOC-2 (running timer line)
    HABITS(false),       // EXT-2
    READING(false),      // EXT-3
    SCREEN_TIME(false),  // DIS-3
    CAPTURE(true),       // CAP-1
    DOCK(true),          // CORE-3
    WEEK_PAGE(false);    // PLAN-3 (second home page)

    companion object {
        val DEFAULTS: Set<HomeSection> = entries.filter { it.defaultEnabled }.toSet()
    }
}

data class LauncherSettings(
    // ---- Layout / header (existing) ----
    /** Alignment of home content; the timeline rail renders on the opposite side. */
    val appsAlignment: HorizontalAlignment = HorizontalAlignment.LEFT,
    val timelineEnabled: Boolean = true,
    val showTime: Boolean = true,
    val timeFormat: TimeFormat = TimeFormat.HOUR_24,
    val showDate: Boolean = true,
    val dateFormat: DateFormat = DateFormat.LONG,
    val showBattery: Boolean = true,

    // ---- Weather (EXT-1, OpenWeatherMap) ----
    val showWeather: Boolean = false,
    val weatherApiKey: String = "",
    val weatherCity: String = "",
    val weatherUnits: WeatherUnits = WeatherUnits.CELSIUS,

    // ---- Dock (CORE-3): ordered package names, max [MAX_DOCK_APPS] ----
    val dockApps: List<String> = emptyList(),

    // ---- Home sections ----
    val enabledSections: Set<HomeSection> = HomeSection.DEFAULTS,

    // ---- Display (UX-7, EINK-3, EINK-5) ----
    /** Multiplier for body text; body text never goes below 18sp (EINK-3). */
    val bodyFontScale: Float = 1.0f,
    val density: LayoutDensity = LayoutDensity.NORMAL,
    /** EINK-5: force a full refresh after this many partial screen updates. 0 = off. */
    val fullRefreshEveryN: Int = 20,

    // ---- Sync (EINK-6) ----
    /** Periodic background sync interval, clamped to [MIN_SYNC_MINUTES]..[MAX_SYNC_MINUTES]. */
    val syncIntervalMinutes: Int = 30,

    // ---- Gestures (CORE-6) ----
    val gestures: GestureMapping = GestureMapping(),

    // ---- App management ----
    /** CORE-5: packages hidden from the drawer. */
    val hiddenApps: Set<String> = emptySet(),
    /** DIS-1: packages that require the friction gate before opening. */
    val frictionApps: Set<String> = emptySet(),

    // ---- Integration filters ----
    /** CAL-7: CalendarContract calendar ids to show. Empty = all calendars. */
    val selectedCalendarIds: Set<Long> = emptySet(),
    /** TASK-5: Google Tasks list ids feeding home. Empty = default list only. */
    val selectedTaskListIds: Set<String> = emptySet(),

    // ---- Focus (FOC-2/3/4) ----
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val focusDndEnabled: Boolean = false,
    /** FOC-4: packages whose notifications appear in the digest. */
    val digestWhitelist: Set<String> = emptySet(),
    /** FOC-4: times of day when the digest of non-whitelisted apps is released. Sorted. */
    val digestTimes: List<LocalTime> = listOf(LocalTime.of(12, 0), LocalTime.of(18, 0)),

    // ---- Planning (PLAN-1/2) ----
    val morningReviewEnabled: Boolean = true,
    val morningReviewTime: LocalTime = LocalTime.of(7, 0),
    val eveningReviewEnabled: Boolean = true,
    val eveningReviewTime: LocalTime = LocalTime.of(20, 0)
) {
    val timelineAlignment: HorizontalAlignment get() = appsAlignment.opposite()

    fun isSectionEnabled(section: HomeSection): Boolean = section in enabledSections

    companion object {
        const val MAX_DOCK_APPS = 6
        const val MIN_SYNC_MINUTES = 15
        const val MAX_SYNC_MINUTES = 30
        const val MIN_FONT_SCALE = 0.8f
        const val MAX_FONT_SCALE = 1.6f
        const val MAX_FULL_REFRESH_EVERY_N = 100
    }
}
