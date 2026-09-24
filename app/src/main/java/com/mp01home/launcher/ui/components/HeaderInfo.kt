package com.mp01home.launcher.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.mp01home.launcher.data.settings.DateFormat
import com.mp01home.launcher.data.settings.HorizontalAlignment
import com.mp01home.launcher.data.settings.TimeFormat
import com.mp01home.launcher.data.weather.WeatherSnapshot
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Top-of-screen block: current time / date / weather, each independently toggleable. */
@Composable
fun HeaderInfo(
    now: LocalDateTime,
    showTime: Boolean,
    timeFormat: TimeFormat,
    showDate: Boolean,
    dateFormat: DateFormat,
    showWeather: Boolean,
    weather: WeatherSnapshot?,
    alignment: HorizontalAlignment,
    modifier: Modifier = Modifier
) {
    if (!showTime && !showDate && !showWeather) return

    val textAlign = if (alignment == HorizontalAlignment.LEFT) TextAlign.Left else TextAlign.Right

    Column(modifier = modifier.fillMaxWidth()) {
        if (showTime) {
            val pattern = if (timeFormat == TimeFormat.HOUR_24) "HH:mm" else "h:mm a"
            Text(
                text = now.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault())),
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (showDate) {
            Text(
                text = now.format(DateTimeFormatter.ofPattern(dateFormat.pattern, Locale.getDefault())),
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (showWeather) {
            val text = when {
                weather == null -> "Weather unavailable"
                else -> "${weather.tempRounded}${weather.unitsSymbol} ${weather.condition}".trim()
            }
            Text(text = text, textAlign = textAlign, modifier = Modifier.fillMaxWidth())
        }
    }
}
