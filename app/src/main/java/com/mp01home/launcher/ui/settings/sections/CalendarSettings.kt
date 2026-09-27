package com.mp01home.launcher.ui.settings.sections

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mp01home.launcher.appContainer
import com.mp01home.launcher.calendar.CalendarInfo
import com.mp01home.launcher.calendar.CalendarPermissionPrompt
import com.mp01home.launcher.calendar.CalendarPrefs
import com.mp01home.launcher.calendar.formatDuration
import com.mp01home.launcher.ui.settings.SettingsNote
import com.mp01home.launcher.ui.settings.SettingsSectionTitle
import com.mp01home.launcher.ui.settings.SettingsViewModel
import com.mp01home.launcher.ui.settings.StepperRow
import com.mp01home.launcher.ui.settings.ToggleRow
import kotlinx.coroutines.launch

/**
 * CAL-7 calendar selection (all device accounts), READ_CALENDAR prompt, and the calendar
 * feature's own options: agenda rows (CAL-2), free/busy strip (CAL-5), leave-by alert and lead
 * time (CAL-6), default time-block length (PLAN-4).
 */
@Composable
fun CalendarSettings(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val repository = remember(context) { context.appContainer.calendarRepository }
    val prefsRepository = repository.prefsRepository
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val prefs by prefsRepository.prefs.collectAsStateWithLifecycle(initialValue = CalendarPrefs())

    var granted by remember { mutableStateOf(repository.hasPermission()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val now = repository.hasPermission()
        if (now && !granted) repository.onPermissionGranted()
        granted = now
    }

    SettingsSectionTitle("Calendar")

    if (!granted) {
        CalendarPermissionPrompt(
            onResult = { ok ->
                granted = ok
                if (ok) repository.onPermissionGranted()
            },
            text = "Allow calendar access"
        )
        SettingsNote("Events are read from the calendars on this phone (every account). Nothing leaves the device.")
    } else {
        val calendars by produceState(initialValue = emptyList<CalendarInfo>(), granted) {
            value = repository.listCalendars()
        }
        val selected = settings.selectedCalendarIds
        if (calendars.isEmpty()) {
            SettingsNote("No calendars found on this phone.")
        } else {
            SettingsNote("Calendars shown on home" + if (selected.isEmpty()) " (default: those visible in your calendar app)" else "")
        }
        calendars.forEach { cal ->
            val checked = if (selected.isEmpty()) cal.visible else cal.id in selected
            val label = if (cal.accountName.isBlank() || cal.accountName == cal.displayName) {
                cal.displayName
            } else {
                "${cal.displayName} · ${cal.accountName}"
            }
            ToggleRow(
                label = label,
                checked = checked,
                onCheckedChange = { on ->
                    val defaults = calendars.filter { it.visible }.map { it.id }.toSet()
                    val base = if (selected.isEmpty()) defaults else selected.intersect(calendars.map { it.id }.toSet())
                    val next = if (on) base + cal.id else base - cal.id
                    // Keep at least one calendar; the default set is stored as "empty" (= follow visibility).
                    if (next.isNotEmpty()) {
                        viewModel.update { setSelectedCalendarIds(if (next == defaults) emptySet() else next) }
                    }
                }
            )
        }
    }

    StepperRow(
        label = "Agenda rows on home",
        value = prefs.agendaRows.toString(),
        onDecrement = { scope.launch { prefsRepository.setAgendaRows(prefs.agendaRows - 1) } },
        onIncrement = { scope.launch { prefsRepository.setAgendaRows(prefs.agendaRows + 1) } },
        decrementEnabled = prefs.agendaRows > 0,
        incrementEnabled = prefs.agendaRows < CalendarPrefs.MAX_AGENDA_ROWS
    )
    ToggleRow(
        label = "Free/busy strip",
        checked = prefs.showFreeBusy,
        onCheckedChange = { on -> scope.launch { prefsRepository.setShowFreeBusy(on) } }
    )
    StepperRow(
        label = "Time block length",
        value = formatDuration(prefs.blockMinutes.toLong()),
        onDecrement = { scope.launch { prefsRepository.setBlockMinutes(prefs.blockMinutes - CalendarPrefs.BLOCK_STEP) } },
        onIncrement = { scope.launch { prefsRepository.setBlockMinutes(prefs.blockMinutes + CalendarPrefs.BLOCK_STEP) } },
        decrementEnabled = prefs.blockMinutes > CalendarPrefs.MIN_BLOCK,
        incrementEnabled = prefs.blockMinutes < CalendarPrefs.MAX_BLOCK
    )
    SettingsNote("\"Block\" on home schedules a task into the next free slot (08–20).")

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        // Enabled either way; the note below explains when notifications are blocked.
        scope.launch { prefsRepository.setLeaveByEnabled(true) }
    }
    ToggleRow(
        label = "Leave-by alerts",
        checked = prefs.leaveByEnabled,
        onCheckedChange = { on ->
            val needsAsk = on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            if (needsAsk) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                scope.launch { prefsRepository.setLeaveByEnabled(on) }
            }
        }
    )
    if (prefs.leaveByEnabled) {
        StepperRow(
            label = "Leave before start",
            value = "${prefs.leaveByLeadMinutes} min",
            onDecrement = { scope.launch { prefsRepository.setLeaveByLeadMinutes(prefs.leaveByLeadMinutes - CalendarPrefs.LEAD_STEP) } },
            onIncrement = { scope.launch { prefsRepository.setLeaveByLeadMinutes(prefs.leaveByLeadMinutes + CalendarPrefs.LEAD_STEP) } },
            decrementEnabled = prefs.leaveByLeadMinutes > CalendarPrefs.MIN_LEAD,
            incrementEnabled = prefs.leaveByLeadMinutes < CalendarPrefs.MAX_LEAD
        )
        val notificationsBlocked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        SettingsNote(
            if (notificationsBlocked) {
                "Notifications are blocked for this app — allow them in system settings to get alerts."
            } else {
                "One notification per event that has a place as its location (video-call links are skipped)."
            }
        )
    }
}
