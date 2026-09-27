package com.mp01home.launcher.ui.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mp01home.launcher.appContainer
import com.mp01home.launcher.data.apps.AppInfo
import com.mp01home.launcher.data.settings.TimeFormat
import com.mp01home.launcher.mail.openNotificationAccessSettings
import com.mp01home.launcher.mail.rememberNotificationAccessGranted
import com.mp01home.launcher.ui.settings.ClickableLabel
import com.mp01home.launcher.ui.settings.SettingsNote
import com.mp01home.launcher.ui.settings.SettingsSectionTitle
import com.mp01home.launcher.ui.settings.SettingsViewModel
import com.mp01home.launcher.ui.settings.StepperRow
import com.mp01home.launcher.ui.settings.ToggleRow
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MAX_DIGEST_TIMES = 6
private const val TIME_STEP_MINUTES = 30L

/**
 * MAIL-1 notification access, and FOC-4 digest: hold toggle, release times, app whitelist.
 */
@Composable
fun MailSettings(viewModel: SettingsViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()
    val granted = rememberNotificationAccessGranted()
    val holdEnabled by container.digestPrefs.holdEnabled.collectAsState(initial = false)

    SettingsSectionTitle("Mail & notifications")
    SettingsNote(
        if (granted) "Notification access: granted. Gmail unread count and the digest read notifications only."
        else "Notification access: not granted. Needed for the Gmail count and the digest."
    )
    ClickableLabel(
        text = if (granted) "Notification access settings" else "Grant notification access",
        onClick = { openNotificationAccessSettings(context) }
    )

    // ---- FOC-4 digest ----
    SettingsSectionTitle("Notification digest")
    SettingsNote("Digest apps are summarised on home. Enable the Digest section under home sections.")
    ToggleRow(
        label = "Hold other apps until digest times",
        checked = holdEnabled,
        onCheckedChange = { enabled -> scope.launch { container.digestPrefs.setHoldEnabled(enabled) } },
        enabled = granted
    )
    SettingsNote("Calls, alarms, reminders, media, Gmail and ongoing notifications are never held.")

    val times = settings.digestTimes
    times.forEachIndexed { index, time ->
        StepperRow(
            label = "Digest ${index + 1}",
            value = formatTime(time, settings.timeFormat),
            onDecrement = {
                val updated = times.toMutableList().also { it[index] = time.minusMinutes(TIME_STEP_MINUTES) }
                viewModel.update { setDigestTimes(updated) }
            },
            onIncrement = {
                val updated = times.toMutableList().also { it[index] = time.plusMinutes(TIME_STEP_MINUTES) }
                viewModel.update { setDigestTimes(updated) }
            }
        )
    }
    if (times.size < MAX_DIGEST_TIMES) {
        ClickableLabel(
            text = "+ Add digest time",
            onClick = {
                val candidate = (times.lastOrNull()?.plusHours(3) ?: LocalTime.of(12, 0))
                val next = generateSequence(candidate) { it.plusMinutes(TIME_STEP_MINUTES) }
                    .take(48)
                    .firstOrNull { it !in times } ?: candidate
                viewModel.update { setDigestTimes(times + next) }
            }
        )
    }
    if (times.isNotEmpty()) {
        ClickableLabel(
            text = "− Remove last digest time",
            onClick = { viewModel.update { setDigestTimes(times.dropLast(1)) } }
        )
    }

    // Whitelist app picker
    var expanded by rememberSaveable { mutableStateOf(false) }
    val whitelist = settings.digestWhitelist
    ClickableLabel(
        text = (if (expanded) "▾ " else "▸ ") + "Digest apps (${whitelist.size} selected)",
        onClick = { expanded = !expanded }
    )
    if (expanded) {
        var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
        LaunchedEffect(Unit) {
            apps = container.installedAppsRepository.getAllLaunchableApps()
        }
        val loaded = apps
        if (loaded == null) {
            SettingsNote("Loading apps…")
        } else {
            // Selected apps first (order frozen on load so rows don't jump when toggled).
            val ordered = remember(loaded) {
                loaded.sortedWith(compareBy<AppInfo>({ it.packageName !in whitelist }, { it.label.lowercase() }))
            }
            ordered.forEach { app ->
                ToggleRow(
                    label = app.label,
                    checked = app.packageName in whitelist,
                    onCheckedChange = { checked ->
                        viewModel.update { setDigestWhitelisted(app.packageName, checked) }
                    }
                )
            }
        }
    }
}

private fun formatTime(time: LocalTime, format: TimeFormat): String {
    val pattern = if (format == TimeFormat.HOUR_24) "HH:mm" else "h:mm a"
    return time.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}
