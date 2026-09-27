package com.mp01home.launcher.ui.settings

import android.app.role.RoleManager
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mp01home.launcher.data.settings.DateFormat
import com.mp01home.launcher.data.settings.Gesture
import com.mp01home.launcher.data.settings.GestureAction
import com.mp01home.launcher.data.settings.HomeSection
import com.mp01home.launcher.data.settings.LauncherSettings
import com.mp01home.launcher.data.settings.LayoutDensity
import com.mp01home.launcher.data.settings.TimeFormat
import com.mp01home.launcher.data.settings.WeatherUnits
import com.mp01home.launcher.data.settings.actionFor
import com.mp01home.launcher.data.settings.opposite
import com.mp01home.launcher.ui.settings.sections.BackupSettings
import com.mp01home.launcher.ui.settings.sections.CalendarSettings
import com.mp01home.launcher.ui.settings.sections.DistractionSettings
import com.mp01home.launcher.ui.settings.sections.ExtrasSettings
import com.mp01home.launcher.ui.settings.sections.FocusSettings
import com.mp01home.launcher.ui.settings.sections.MailSettings
import com.mp01home.launcher.ui.settings.sections.ReviewSettings
import com.mp01home.launcher.ui.settings.sections.TasksSettings
import com.mp01home.launcher.ui.theme.EinkTheme
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Settings is the one screen allowed to scroll (it is not glanceable content).
 * General sections live here; feature sections are slots in ui/settings/sections/.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    BackHandler(onBack = onBack)

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { /* nothing to do: the system records the default home app */ }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(EinkTheme.dimens.gutter)
    ) {
        ClickableLabel(text = "← Home", onClick = onBack)

        // ---- Layout ------------------------------------------------------------------------
        SettingsSectionTitle("Layout")
        CycleRow(
            label = "Content alignment",
            value = state.appsAlignment.name,
            onClick = { viewModel.setAppsAlignment(state.appsAlignment.opposite()) }
        )
        ToggleRow(
            label = "Show timeline rail",
            checked = state.timelineEnabled,
            onCheckedChange = { viewModel.setTimelineEnabled(it) }
        )
        SettingsNote("Rail shows on the opposite side (${state.timelineAlignment.name.lowercase()}).")

        // ---- Home sections -----------------------------------------------------------------
        SettingsSectionTitle("Home sections")
        HomeSection.entries.forEach { section ->
            ToggleRow(
                label = sectionLabel(section),
                checked = state.isSectionEnabled(section),
                onCheckedChange = { enabled -> viewModel.update { setSectionEnabled(section, enabled) } }
            )
        }

        // ---- Header ------------------------------------------------------------------------
        SettingsSectionTitle("Header")
        ToggleRow(label = "Show time", checked = state.showTime, onCheckedChange = { viewModel.setShowTime(it) })
        CycleRow(
            label = "Time format",
            value = if (state.timeFormat == TimeFormat.HOUR_24) "24-hour" else "12-hour",
            onClick = {
                val next = if (state.timeFormat == TimeFormat.HOUR_24) TimeFormat.HOUR_12 else TimeFormat.HOUR_24
                viewModel.setTimeFormat(next)
            }
        )
        ToggleRow(label = "Show date", checked = state.showDate, onCheckedChange = { viewModel.setShowDate(it) })
        CycleRow(
            label = "Date format",
            value = state.dateFormat.name,
            onClick = { viewModel.setDateFormat(state.dateFormat.next()) }
        )
        ToggleRow(
            label = "Show battery %",
            checked = state.showBattery,
            onCheckedChange = { viewModel.setShowBattery(it) }
        )

        // ---- Weather (EXT-1, OpenWeatherMap) -----------------------------------------------
        SettingsSectionTitle("Weather")
        ToggleRow(label = "Show weather", checked = state.showWeather, onCheckedChange = { viewModel.setShowWeather(it) })
        OutlinedTextField(
            value = state.weatherCity,
            onValueChange = { viewModel.setWeatherCity(it) },
            label = { Text("City") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        )
        OutlinedTextField(
            value = state.weatherApiKey,
            onValueChange = { viewModel.setWeatherApiKey(it) },
            label = { Text("OpenWeatherMap API key") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        )
        CycleRow(
            label = "Units",
            value = state.weatherUnits.symbol,
            onClick = {
                val next = if (state.weatherUnits == WeatherUnits.CELSIUS) WeatherUnits.FAHRENHEIT else WeatherUnits.CELSIUS
                viewModel.setWeatherUnits(next)
            }
        )

        // ---- Display (UX-7, EINK-5) --------------------------------------------------------
        SettingsSectionTitle("Display")
        StepperRow(
            label = "Text size",
            value = String.format(Locale.US, "%.1f×", state.bodyFontScale),
            decrementEnabled = state.bodyFontScale > LauncherSettings.MIN_FONT_SCALE + 0.01f,
            incrementEnabled = state.bodyFontScale < LauncherSettings.MAX_FONT_SCALE - 0.01f,
            onDecrement = { viewModel.update { setBodyFontScale(stepScale(state.bodyFontScale, -1)) } },
            onIncrement = { viewModel.update { setBodyFontScale(stepScale(state.bodyFontScale, +1)) } }
        )
        SettingsNote("Body text never goes below 18sp.")
        CycleRow(
            label = "Spacing",
            value = state.density.name.lowercase(),
            onClick = { viewModel.update { setDensity(state.density.next()) } }
        )
        StepperRow(
            label = "Full refresh every",
            value = if (state.fullRefreshEveryN == 0) "off" else "${state.fullRefreshEveryN}",
            decrementEnabled = state.fullRefreshEveryN > 0,
            incrementEnabled = state.fullRefreshEveryN < LauncherSettings.MAX_FULL_REFRESH_EVERY_N,
            onDecrement = { viewModel.update { setFullRefreshEveryN(state.fullRefreshEveryN - REFRESH_STEP) } },
            onIncrement = { viewModel.update { setFullRefreshEveryN(state.fullRefreshEveryN + REFRESH_STEP) } }
        )
        SettingsNote("Screen updates between automatic anti-ghosting flashes. Double-tap home to flash now.")

        // ---- Sync (EINK-6) -----------------------------------------------------------------
        SettingsSectionTitle("Sync")
        StepperRow(
            label = "Background sync",
            value = "${state.syncIntervalMinutes} min",
            decrementEnabled = state.syncIntervalMinutes > LauncherSettings.MIN_SYNC_MINUTES,
            incrementEnabled = state.syncIntervalMinutes < LauncherSettings.MAX_SYNC_MINUTES,
            onDecrement = { viewModel.update { setSyncIntervalMinutes(state.syncIntervalMinutes - SYNC_STEP) } },
            onIncrement = { viewModel.update { setSyncIntervalMinutes(state.syncIntervalMinutes + SYNC_STEP) } }
        )
        SettingsNote("Also syncs when the screen turns on (at most every 5 minutes).")

        // ---- Gestures (CORE-6) -------------------------------------------------------------
        SettingsSectionTitle("Gestures")
        Gesture.entries.forEach { gesture ->
            val action = state.gestures.actionFor(gesture)
            CycleRow(
                label = gestureLabel(gesture),
                value = actionLabel(action),
                onClick = { viewModel.update { setGestureAction(gesture, action.next()) } }
            )
        }

        // ---- Feature slots (edit only your own file in ui/settings/sections/) ---------------
        CalendarSettings(viewModel)
        TasksSettings(viewModel)
        MailSettings(viewModel)
        FocusSettings(viewModel)
        ReviewSettings(viewModel)
        DistractionSettings(viewModel)
        BackupSettings(viewModel)
        ExtrasSettings(viewModel)

        // ---- Launcher ----------------------------------------------------------------------
        SettingsSectionTitle("Launcher")
        ClickableLabel(
            text = "Set as default launcher",
            onClick = {
                // minSdk 29 == Q, so RoleManager is always present.
                val roleManager = context.getSystemService(RoleManager::class.java)
                val intent = if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                } else {
                    Intent(AndroidSettings.ACTION_HOME_SETTINGS)
                }
                roleLauncher.launch(intent)
            }
        )
    }
}

private const val REFRESH_STEP = 5
private const val SYNC_STEP = 5

private fun stepScale(state: Float, direction: Int): Float =
    ((state * 10f).roundToInt() + direction) / 10f

private fun DateFormat.next(): DateFormat = DateFormat.entries[(ordinal + 1) % DateFormat.entries.size]
private fun LayoutDensity.next(): LayoutDensity = LayoutDensity.entries[(ordinal + 1) % LayoutDensity.entries.size]
private fun GestureAction.next(): GestureAction = GestureAction.entries[(ordinal + 1) % GestureAction.entries.size]

private fun sectionLabel(section: HomeSection): String = when (section) {
    HomeSection.TOP3 -> "Top 3 priorities"
    HomeSection.CALENDAR -> "Calendar"
    HomeSection.TASKS -> "Tasks"
    HomeSection.MAIL -> "Mail"
    HomeSection.DIGEST -> "Notification digest"
    HomeSection.FOCUS -> "Focus timer"
    HomeSection.HABITS -> "Habits"
    HomeSection.READING -> "Currently reading"
    HomeSection.SCREEN_TIME -> "Screen time"
    HomeSection.CAPTURE -> "Quick capture"
    HomeSection.DOCK -> "Dock"
    HomeSection.WEEK_PAGE -> "Week page (swipe left)"
}

private fun gestureLabel(gesture: Gesture): String = when (gesture) {
    Gesture.SWIPE_UP -> "Swipe up"
    Gesture.SWIPE_DOWN -> "Swipe down"
    Gesture.SWIPE_LEFT -> "Swipe left"
    Gesture.SWIPE_RIGHT -> "Swipe right"
    Gesture.DOUBLE_TAP -> "Double-tap"
    Gesture.LONG_PRESS -> "Long-press"
}

private fun actionLabel(action: GestureAction): String = when (action) {
    GestureAction.NONE -> "nothing"
    GestureAction.OPEN_DRAWER -> "app drawer"
    GestureAction.QUICK_CAPTURE -> "capture"
    GestureAction.FULL_REFRESH -> "full refresh"
    GestureAction.OPEN_SETTINGS -> "settings"
    GestureAction.NEXT_PAGE -> "next page"
    GestureAction.PREVIOUS_PAGE -> "previous page"
}
