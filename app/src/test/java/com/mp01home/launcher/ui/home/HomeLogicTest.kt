package com.mp01home.launcher.ui.home

import com.mp01home.launcher.data.settings.Gesture
import com.mp01home.launcher.data.weather.WeatherSnapshot
import com.mp01home.launcher.ui.home.sections.dockColumns
import com.mp01home.launcher.ui.home.sections.formatWeatherLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeLogicTest {

    @Test
    fun shortDragsAreIgnored() {
        assertNull(classifySwipe(10f, -20f, threshold = 48f))
    }

    @Test
    fun dominantAxisWins() {
        assertEquals(Gesture.SWIPE_UP, classifySwipe(30f, -200f, 48f))
        assertEquals(Gesture.SWIPE_DOWN, classifySwipe(-30f, 200f, 48f))
        assertEquals(Gesture.SWIPE_LEFT, classifySwipe(-200f, 60f, 48f))
        assertEquals(Gesture.SWIPE_RIGHT, classifySwipe(200f, -60f, 48f))
    }

    @Test
    fun dockLayout() {
        assertEquals(1, dockColumns(0))
        assertEquals(3, dockColumns(3))
        assertEquals(4, dockColumns(4))
        assertEquals(3, dockColumns(5))
        assertEquals(3, dockColumns(6))
    }

    @Test
    fun weatherLine() {
        val snapshot = WeatherSnapshot("Berlin", 12, "Clouds", "°C", 0L, rainChancePercent = 40)
        assertEquals("12°C Clouds · rain 40%", formatWeatherLine(snapshot))
        assertEquals("12°C Clouds", formatWeatherLine(snapshot.copy(rainChancePercent = null)))
        assertEquals("Weather unavailable", formatWeatherLine(null))
    }
}
