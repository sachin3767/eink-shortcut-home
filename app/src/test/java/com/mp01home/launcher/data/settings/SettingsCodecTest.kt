package com.mp01home.launcher.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class SettingsCodecTest {

    @Test
    fun orderedListRoundTripKeepsOrderAndDropsBlanksAndDuplicates() {
        val encoded = SettingsCodec.encodeOrderedList(listOf("b.pkg", " a.pkg ", "", "b.pkg", "bad,pkg"))
        assertEquals("b.pkg,a.pkg", encoded)
        assertEquals(listOf("b.pkg", "a.pkg"), SettingsCodec.decodeOrderedList(encoded))
    }

    @Test
    fun decodeOrderedListHandlesNullAndEmpty() {
        assertEquals(emptyList<String>(), SettingsCodec.decodeOrderedList(null))
        assertEquals(emptyList<String>(), SettingsCodec.decodeOrderedList(""))
    }

    @Test
    fun timeRoundTrip() {
        assertEquals("07:05", SettingsCodec.encodeTime(LocalTime.of(7, 5)))
        assertEquals(LocalTime.of(20, 0), SettingsCodec.decodeTime("20:00"))
        assertNull(SettingsCodec.decodeTime("25:99"))
        assertNull(SettingsCodec.decodeTime(null))
    }

    @Test
    fun timesAreSortedAndDeduplicated() {
        val times = listOf(LocalTime.of(18, 0), LocalTime.of(12, 0), LocalTime.of(18, 0))
        val encoded = SettingsCodec.encodeTimes(times)
        assertEquals("12:00,18:00", encoded)
        assertEquals(listOf(LocalTime.of(12, 0), LocalTime.of(18, 0)), SettingsCodec.decodeTimes(encoded))
        assertNull(SettingsCodec.decodeTimes(null))
    }

    @Test
    fun enumsFallBackToDefault() {
        assertEquals(LayoutDensity.COMPACT, SettingsCodec.decodeEnum("COMPACT", LayoutDensity.NORMAL))
        assertEquals(LayoutDensity.NORMAL, SettingsCodec.decodeEnum("nope", LayoutDensity.NORMAL))
        assertEquals(
            setOf(HomeSection.TOP3, HomeSection.DOCK),
            SettingsCodec.decodeEnumSet(setOf("TOP3", "DOCK", "REMOVED_SECTION"), HomeSection.DEFAULTS)
        )
        assertEquals(HomeSection.DEFAULTS, SettingsCodec.decodeEnumSet<HomeSection>(null, HomeSection.DEFAULTS))
    }

    @Test
    fun longSetIgnoresGarbage() {
        assertEquals(setOf(1L, 42L), SettingsCodec.decodeLongSet(setOf("1", "42", "x")))
    }

    @Test
    fun clamps() {
        assertEquals(15, SettingsCodec.clampSyncMinutes(5))
        assertEquals(30, SettingsCodec.clampSyncMinutes(90))
        assertEquals(20, SettingsCodec.clampSyncMinutes(20))
        assertEquals(LauncherSettings.MIN_FONT_SCALE, SettingsCodec.clampFontScale(0.1f), 0f)
        assertEquals(LauncherSettings.MAX_FONT_SCALE, SettingsCodec.clampFontScale(9f), 0f)
    }

    @Test
    fun gestureMappingRoundTrip() {
        val mapping = GestureMapping().with(Gesture.SWIPE_UP, GestureAction.FULL_REFRESH)
        assertEquals(GestureAction.FULL_REFRESH, mapping.actionFor(Gesture.SWIPE_UP))
        assertEquals(GestureAction.QUICK_CAPTURE, mapping.actionFor(Gesture.SWIPE_DOWN))
    }
}
