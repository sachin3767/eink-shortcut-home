package com.mp01home.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import java.time.LocalTime

private val RAIL_WIDTH = 28.dp
private val HOURS_IN_DAY = 24

/**
 * A vertical 24-hour rail with a tick per hour and a marker for [currentTime].
 * Hours run top (00:00) to bottom (24:00) so it reads like a day timeline.
 */
@androidx.compose.runtime.Composable
fun TimelineRail(currentTime: LocalTime, modifier: Modifier = Modifier) {
    val fractionOfDay = (currentTime.hour * 60 + currentTime.minute) / (HOURS_IN_DAY * 60f)

    Canvas(modifier = modifier.width(RAIL_WIDTH)) {
        val centerX = size.width / 2f
        val lineWidth = 2.dp.toPx()

        // Spine
        drawLine(
            color = Color.Black,
            start = Offset(centerX, 0f),
            end = Offset(centerX, size.height),
            strokeWidth = lineWidth
        )

        // Hour ticks: a short tick every hour, a longer one every 6 hours.
        for (hour in 0..HOURS_IN_DAY) {
            val y = size.height * (hour / HOURS_IN_DAY.toFloat())
            val tickLength = if (hour % 6 == 0) size.width * 0.8f else size.width * 0.4f
            drawLine(
                color = Color.Black,
                start = Offset(centerX - tickLength / 2f, y),
                end = Offset(centerX + tickLength / 2f, y),
                strokeWidth = lineWidth
            )
        }

        // Current-time marker: a filled diamond straddling the spine.
        val markerY = size.height * fractionOfDay
        val markerRadius = size.width * 0.55f
        drawCircle(
            color = Color.Black,
            radius = markerRadius,
            center = Offset(centerX, markerY),
            style = Stroke(width = lineWidth * 1.5f)
        )
    }
}
