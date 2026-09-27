package com.mp01home.launcher.ui.home.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.mp01home.launcher.data.settings.DateFormat
import com.mp01home.launcher.data.settings.LauncherSettings
import com.mp01home.launcher.data.settings.TimeFormat
import com.mp01home.launcher.data.weather.WeatherSnapshot
import com.mp01home.launcher.ui.theme.EinkTheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * CORE-2 header (foundation-owned, always shown):
 *
 *     14:05                    87%
 *     Sat, 26 Sep
 *     12°C Clouds · rain 40%
 *
 * Each part respects its settings toggle (showTime / showDate / showBattery / showWeather).
 * Updates at most once per minute (the time) or when the battery level changes.
 */
@Composable
fun HeaderSection(
    now: LocalDateTime,
    settings: LauncherSettings,
    batteryPercent: Int?,
    weather: WeatherSnapshot?,
    modifier: Modifier = Modifier
) {
    val showBattery = settings.showBattery && batteryPercent != null
    if (!settings.showTime && !settings.showDate && !showBattery && !settings.showWeather) return

    Column(modifier = modifier.fillMaxWidth().padding(bottom = EinkTheme.dimens.sectionGap)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val primary = when {
                settings.showTime -> formatTime(now, settings.timeFormat)
                settings.showDate -> formatDate(now, settings.dateFormat)
                else -> ""
            }
            Text(
                text = primary,
                style = if (settings.showTime) MaterialTheme.typography.displayLarge else MaterialTheme.typography.titleLarge,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (showBattery) {
                Text(text = "$batteryPercent%", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
        }
        if (settings.showTime && settings.showDate) {
            Text(
                text = formatDate(now, settings.dateFormat),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1
            )
        }
        if (settings.showWeather) {
            Text(
                text = formatWeatherLine(weather),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

internal fun formatTime(now: LocalDateTime, format: TimeFormat): String {
    val pattern = if (format == TimeFormat.HOUR_24) "HH:mm" else "h:mm a"
    return now.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

internal fun formatDate(now: LocalDateTime, format: DateFormat): String =
    now.format(DateTimeFormatter.ofPattern(format.pattern, Locale.getDefault()))

/** "12°C Clouds · rain 40%", or a short note when nothing is cached yet. */
internal fun formatWeatherLine(weather: WeatherSnapshot?): String {
    if (weather == null) return "Weather unavailable"
    val base = "${weather.tempRounded}${weather.unitsSymbol} ${weather.condition}".trim()
    val rain = weather.rainChancePercent
    return if (rain != null) "$base · rain $rain%" else base
}
