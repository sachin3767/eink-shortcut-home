package com.mp01home.launcher.ui.home

import com.mp01home.launcher.data.settings.Gesture
import kotlin.math.abs

/**
 * Classifies a finished drag by its total offset (screen coordinates: +y is down).
 * Returns null when the drag is shorter than [threshold] px on its dominant axis.
 */
fun classifySwipe(dx: Float, dy: Float, threshold: Float): Gesture? {
    val ax = abs(dx)
    val ay = abs(dy)
    return when {
        ax < threshold && ay < threshold -> null
        ay >= ax -> if (dy < 0f) Gesture.SWIPE_UP else Gesture.SWIPE_DOWN
        else -> if (dx < 0f) Gesture.SWIPE_LEFT else Gesture.SWIPE_RIGHT
    }
}
