package com.mp01home.launcher.ui.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mp01home.launcher.appContainer
import com.mp01home.launcher.habits.HabitSettings
import com.mp01home.launcher.ui.settings.ClickableLabel
import com.mp01home.launcher.ui.settings.SettingsNote
import com.mp01home.launcher.ui.settings.SettingsSectionTitle
import com.mp01home.launcher.ui.settings.SettingsViewModel
import com.mp01home.launcher.ui.settings.StepperRow
import com.mp01home.launcher.ui.settings.ToggleRow

/**
 * FEATURE SETTINGS SLOT — FOC-2/3 focus + break minutes and DND during focus;
 * EXT-2 habits (add/remove).
 */
@Composable
fun FocusSettings(viewModel: SettingsViewModel) {
    val state by viewModel.settings.collectAsStateWithLifecycle()
    val container = LocalContext.current.appContainer
    val dnd = container.dndController
    var dndAccess by remember { mutableStateOf(dnd.hasAccess()) }
    // Re-check after returning from the system "Do Not Disturb access" screen.
    LifecycleResumeEffect(Unit) {
        dndAccess = dnd.hasAccess()
        onPauseOrDispose { }
    }

    SettingsSectionTitle("Focus timer")
    StepperRow(
        label = "Focus",
        value = "${state.focusMinutes} min",
        onDecrement = { viewModel.update { setFocusMinutes(stepMinutes(state.focusMinutes, up = false)) } },
        onIncrement = { viewModel.update { setFocusMinutes(stepMinutes(state.focusMinutes, up = true)) } },
        decrementEnabled = state.focusMinutes > 1,
        incrementEnabled = state.focusMinutes < 180
    )
    StepperRow(
        label = "Break",
        value = "${state.breakMinutes} min",
        onDecrement = { viewModel.update { setBreakMinutes(state.breakMinutes - 1) } },
        onIncrement = { viewModel.update { setBreakMinutes(state.breakMinutes + 1) } },
        decrementEnabled = state.breakMinutes > 1,
        incrementEnabled = state.breakMinutes < 60
    )
    ToggleRow(
        label = "Do Not Disturb during focus",
        checked = state.focusDndEnabled,
        onCheckedChange = { enabled ->
            viewModel.update { setFocusDndEnabled(enabled) }
            if (enabled && !dnd.hasAccess()) container.appLauncher.startActivitySafely(dnd.accessSettingsIntent())
        }
    )
    if (state.focusDndEnabled && !dndAccess) {
        ClickableLabel(
            text = "Grant Do Not Disturb access →",
            onClick = { container.appLauncher.startActivitySafely(dnd.accessSettingsIntent()) }
        )
        SettingsNote("Without it the timer runs, but notifications are not silenced.")
    } else {
        SettingsNote("Priority-only mode while focusing; your previous mode is restored afterwards.")
    }

    HabitSettings()
}

/** 1-minute steps up to 5, then 5-minute steps (25 → 30 → 35 …). */
private fun stepMinutes(current: Int, up: Boolean): Int = when {
    up && current < 5 -> current + 1
    up -> (current / 5 + 1) * 5
    current <= 5 -> current - 1
    current % 5 != 0 -> current / 5 * 5
    else -> current - 5
}
