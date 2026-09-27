package com.mp01home.launcher.ui.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mp01home.launcher.appContainer
import com.mp01home.launcher.data.apps.AppInfo
import com.mp01home.launcher.distraction.formatLimit
import com.mp01home.launcher.distraction.nextLimitStep
import com.mp01home.launcher.ui.settings.ClickableLabel
import com.mp01home.launcher.ui.settings.SettingsNote
import com.mp01home.launcher.ui.settings.SettingsSectionTitle
import com.mp01home.launcher.ui.settings.SettingsViewModel
import com.mp01home.launcher.ui.settings.StepperRow
import com.mp01home.launcher.ui.settings.ToggleRow
import com.mp01home.launcher.ui.theme.einkTappable
import kotlinx.coroutines.launch

/**
 * FEATURE SETTINGS SLOT — CORE-5 hidden apps, DIS-1 friction apps, DIS-2/3 limits + usage access.
 *
 * "Manage apps" expands to a filterable list (plain Column: SettingsScreen already scrolls).
 * Apps with any flag (hidden / friction / limit) are listed first; tapping an app expands its
 * three controls.
 */
@Composable
fun DistractionSettings(viewModel: SettingsViewModel) {
    val state by viewModel.settings.collectAsStateWithLifecycle()
    val container = LocalContext.current.appContainer
    val usage = container.usageStatsRepository
    val limitsRepo = container.appLimitsRepository
    val limits by limitsRepo.limits.collectAsState(initial = emptyMap())
    val scope = rememberCoroutineScope()

    var usageAccess by remember { mutableStateOf(usage.hasAccess()) }
    LifecycleResumeEffect(Unit) {
        usageAccess = usage.hasAccess()
        onPauseOrDispose { }
    }

    SettingsSectionTitle("Distraction")
    if (usageAccess) {
        SettingsNote("Usage access granted: screen time line and daily limits are active.")
    } else {
        ClickableLabel(
            text = "Grant usage access →",
            onClick = {
                val launcher = container.appLauncher
                if (!launcher.startActivitySafely(usage.accessSettingsIntent())) {
                    launcher.startActivitySafely(usage.genericAccessSettingsIntent())
                }
            }
        )
        SettingsNote("Needed for the screen-time line and daily app limits. Friction gates work without it.")
    }
    SettingsNote(
        "${state.hiddenApps.size} hidden · ${state.frictionApps.size} with friction gate · " +
            "${limits.size} with daily limit"
    )

    var expanded by remember { mutableStateOf(false) }
    ClickableLabel(
        text = if (expanded) "▾ Manage apps" else "▸ Manage apps",
        onClick = { expanded = !expanded }
    )
    if (!expanded) return

    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
    LaunchedEffect(Unit) {
        apps = container.installedAppsRepository.getAllLaunchableApps()
            .filter { it.packageName != container.app.packageName }
    }
    var filter by remember { mutableStateOf("") }
    var openPackage by remember { mutableStateOf<String?>(null) }

    OutlinedTextField(
        value = filter,
        onValueChange = { filter = it.take(40) },
        label = { Text("Filter apps") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )

    val loaded = apps
    if (loaded == null) {
        SettingsNote("Loading apps…")
        return
    }
    val query = filter.trim()
    val visible = loaded
        .filter { query.isEmpty() || it.label.contains(query, ignoreCase = true) }
        .sortedWith(
            compareByDescending<AppInfo> {
                it.packageName in state.hiddenApps ||
                    it.packageName in state.frictionApps ||
                    it.packageName in limits
            }.thenBy { it.label.lowercase() }
        )
    if (visible.isEmpty()) SettingsNote("No apps match.")

    visible.forEach { app ->
        val pkg = app.packageName
        val hidden = pkg in state.hiddenApps
        val friction = pkg in state.frictionApps
        val limit = limits[pkg] ?: 0
        val isOpen = openPackage == pkg
        val flags = buildList {
            if (hidden) add("hidden")
            if (friction) add("gate")
            if (limit > 0) add(formatLimit(limit))
        }.joinToString(" · ")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .einkTappable(onClickLabel = "Edit ${app.label}") {
                    openPackage = if (isOpen) null else pkg
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = (if (isOpen) "▾ " else "▸ ") + app.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (flags.isNotEmpty()) {
                Text(
                    text = flags,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        if (isOpen) {
            Column(modifier = Modifier.padding(start = 16.dp)) {
                ToggleRow(
                    label = "Hide from drawer",
                    checked = hidden,
                    onCheckedChange = { value -> viewModel.update { setAppHidden(pkg, value) } }
                )
                ToggleRow(
                    label = "Friction gate (10 s pause + reason)",
                    checked = friction,
                    onCheckedChange = { value -> viewModel.update { setAppFriction(pkg, value) } }
                )
                StepperRow(
                    label = "Daily limit",
                    value = formatLimit(limit),
                    onDecrement = { scope.launch { limitsRepo.setLimit(pkg, nextLimitStep(limit, up = false)) } },
                    onIncrement = { scope.launch { limitsRepo.setLimit(pkg, nextLimitStep(limit, up = true)) } },
                    decrementEnabled = limit > 0,
                    incrementEnabled = limit < 180
                )
                if (limit > 0 && !usageAccess) {
                    SettingsNote("Limit is not enforced until usage access is granted.")
                }
            }
        }
    }
}
