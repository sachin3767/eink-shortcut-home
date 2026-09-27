package com.mp01home.launcher.backup

import com.mp01home.launcher.data.settings.DateFormat
import com.mp01home.launcher.data.settings.GestureAction
import com.mp01home.launcher.data.settings.GestureMapping
import com.mp01home.launcher.data.settings.HomeSection
import com.mp01home.launcher.data.settings.HorizontalAlignment
import com.mp01home.launcher.data.settings.LauncherSettings
import com.mp01home.launcher.data.settings.LayoutDensity
import com.mp01home.launcher.data.settings.TimeFormat
import com.mp01home.launcher.eink.ExtrasPrefs
import com.mp01home.launcher.eink.VendorRefreshMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class SettingsBackupCodecTest {

    private val custom = LauncherSettings(
        appsAlignment = HorizontalAlignment.RIGHT,
        timelineEnabled = false,
        timeFormat = TimeFormat.HOUR_12,
        dateFormat = DateFormat.ISO,
        weatherCity = "Berlin",
        dockApps = listOf("b.pkg", "a.pkg"),
        enabledSections = setOf(HomeSection.TOP3, HomeSection.WEEK_PAGE),
        bodyFontScale = 1.3f,
        density = LayoutDensity.COMPACT,
        fullRefreshEveryN = 35,
        syncIntervalMinutes = 20,
        gestures = GestureMapping(swipeUp = GestureAction.OPEN_SETTINGS, doubleTap = GestureAction.NONE),
        hiddenApps = setOf("x.pkg"),
        frictionApps = setOf("y.pkg"),
        selectedCalendarIds = setOf(3L, 7L),
        selectedTaskListIds = setOf("list1"),
        focusMinutes = 50,
        breakMinutes = 10,
        focusDndEnabled = true,
        digestWhitelist = setOf("z.pkg"),
        digestTimes = listOf(LocalTime.of(9, 30), LocalTime.of(17, 0)),
        morningReviewEnabled = false,
        morningReviewTime = LocalTime.of(6, 45),
        eveningReviewTime = LocalTime.of(21, 15)
    )
    private val extras = ExtrasPrefs(
        sleepScreenEnabled = true,
        vendorRefreshMode = VendorRefreshMode.ONYX,
        readingApps = listOf("com.amazon.kindle")
    )

    @Test
    fun roundTripRestoresEverything() {
        val json = SettingsBackupCodec.encode(custom, extras)
        assertTrue(json.contains("\"format\": \"mp01-launcher-backup\""))
        val restored = SettingsBackupCodec.decode(json)
        assertEquals(custom, restored.settings)
        assertEquals(extras, restored.extras)
    }

    @Test
    fun missingAndUnknownFieldsFallBackToDefaults() {
        val restored = SettingsBackupCodec.decode(
            """{"format":"mp01-launcher-backup","version":99,"futureField":{"a":1},"density":"BOGUS","fullRefreshEveryN":5000,"dockApps":["a","a","b","c","d","e","f","g"]}"""
        )
        val d = LauncherSettings()
        assertEquals(d.density, restored.settings.density)
        assertEquals(LauncherSettings.MAX_FULL_REFRESH_EVERY_N, restored.settings.fullRefreshEveryN)
        assertEquals(listOf("a", "b", "c", "d", "e", "f"), restored.settings.dockApps)
        assertEquals(d.gestures, restored.settings.gestures)
        assertEquals(ExtrasPrefs(), restored.extras)
    }

    @Test(expected = BackupFormatException::class)
    fun rejectsGarbage() {
        SettingsBackupCodec.decode("not json")
    }

    @Test(expected = BackupFormatException::class)
    fun rejectsOtherJson() {
        SettingsBackupCodec.decode("""{"format":"something-else"}""")
    }
}
