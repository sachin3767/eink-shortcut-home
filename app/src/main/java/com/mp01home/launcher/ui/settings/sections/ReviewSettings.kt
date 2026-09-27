package com.mp01home.launcher.ui.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mp01home.launcher.data.settings.TimeFormat
import com.mp01home.launcher.ui.settings.SettingsNote
import com.mp01home.launcher.ui.settings.SettingsSectionTitle
import com.mp01home.launcher.ui.settings.SettingsViewModel
import com.mp01home.launcher.ui.settings.StepperRow
import com.mp01home.launcher.ui.settings.ToggleRow
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val REVIEW_TIME_STEP_MINUTES = 15L

/** PLAN-1 / PLAN-2: morning and evening review toggles and trigger times. */
@Composable
fun ReviewSettings(viewModel: SettingsViewModel) {
    val state by viewModel.settings.collectAsStateWithLifecycle()
    val fmt = DateTimeFormatter.ofPattern(
        if (state.timeFormat == TimeFormat.HOUR_24) "HH:mm" else "h:mm a",
        Locale.getDefault()
    )

    SettingsSectionTitle("Daily review")
    ToggleRow(
        label = "Morning review",
        checked = state.morningReviewEnabled,
        onCheckedChange = { viewModel.update { setMorningReviewEnabled(it) } }
    )
    if (state.morningReviewEnabled) {
        TimeStepper(
            label = "Show from",
            time = state.morningReviewTime,
            format = fmt,
            onChange = { t -> viewModel.update { setMorningReviewTime(t) } }
        )
    }
    ToggleRow(
        label = "Evening review",
        checked = state.eveningReviewEnabled,
        onCheckedChange = { viewModel.update { setEveningReviewEnabled(it) } }
    )
    if (state.eveningReviewEnabled) {
        TimeStepper(
            label = "Show from",
            time = state.eveningReviewTime,
            format = fmt,
            onChange = { t -> viewModel.update { setEveningReviewTime(t) } }
        )
    }
    SettingsNote(
        "Each review opens once a day, the first time you return to the home screen after its time " +
            "(morning: within 6 hours, and before the evening review). Close it with ✕ or Back."
    )
}

@Composable
private fun TimeStepper(label: String, time: LocalTime, format: DateTimeFormatter, onChange: (LocalTime) -> Unit) {
    StepperRow(
        label = label,
        value = time.format(format),
        // LocalTime wraps around midnight, so both directions are always available.
        onDecrement = { onChange(snap(time).minusMinutes(REVIEW_TIME_STEP_MINUTES)) },
        onIncrement = { onChange(snap(time).plusMinutes(REVIEW_TIME_STEP_MINUTES)) }
    )
}

/** Aligns odd stored times (e.g. 07:10) onto the 15-minute grid before stepping. */
private fun snap(time: LocalTime): LocalTime =
    time.withSecond(0).withNano(0).withMinute((time.minute / REVIEW_TIME_STEP_MINUTES.toInt()) * REVIEW_TIME_STEP_MINUTES.toInt())
