package com.mp01home.launcher.ui.settings.sections

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mp01home.launcher.eink.EinkVendor
import com.mp01home.launcher.eink.ExtrasPrefs
import com.mp01home.launcher.eink.ExtrasViewModel
import com.mp01home.launcher.eink.ReadingAppSuggestions
import com.mp01home.launcher.eink.VendorRefreshMode
import com.mp01home.launcher.ui.drawer.DrawerPaging
import com.mp01home.launcher.ui.settings.ClickableLabel
import com.mp01home.launcher.ui.settings.CycleRow
import com.mp01home.launcher.ui.settings.SettingsNote
import com.mp01home.launcher.ui.settings.SettingsSectionTitle
import com.mp01home.launcher.ui.settings.SettingsViewModel
import com.mp01home.launcher.ui.settings.ToggleRow

/**
 * UX-5 sleep-screen agenda, UX-6 e-ink vendor refresh override, EXT-3 reading apps.
 * Text size / density (UX-7), gestures (CORE-6) and "full refresh every N" (EINK-5) live in the
 * general Display and Gestures sections of SettingsScreen.
 */
@Composable
fun ExtrasSettings(viewModel: SettingsViewModel) {
    val extras: ExtrasViewModel = viewModel()
    val prefs by extras.prefs.collectAsStateWithLifecycle()
    val message by extras.message.collectAsStateWithLifecycle()

    // ---- UX-5 ---------------------------------------------------------------------------------
    SettingsSectionTitle("Lock screen")
    ToggleRow(
        label = "Agenda on lock screen",
        checked = prefs.sleepScreenEnabled,
        onCheckedChange = extras::setSleepScreenEnabled
    )
    SettingsNote(
        "Draws today's Top 3, remaining events and due tasks as the lock-screen wallpaper after each " +
            "sync and when the screen turns off. Turning it off restores the default lock screen."
    )
    message?.let { SettingsNote(it) }

    // ---- UX-6 ---------------------------------------------------------------------------------
    SettingsSectionTitle("E-ink refresh")
    CycleRow(
        label = "Refresh method",
        value = vendorModeLabel(prefs.vendorRefreshMode, extras.detectedVendor),
        onClick = {
            val all = VendorRefreshMode.entries
            extras.setVendorRefreshMode(all[(prefs.vendorRefreshMode.ordinal + 1) % all.size])
        }
    )
    ClickableLabel(text = "Test full refresh now", onClick = extras::testFullRefresh)
    SettingsNote(
        "Auto uses the device's refresh API when known (Onyx Boox); otherwise a black flash. " +
            "Bigme and Hisense currently use the flash. Double-tap home also forces a full refresh."
    )

    // ---- EXT-3 --------------------------------------------------------------------------------
    ReadingAppsSettings(extras, prefs)
}

@Composable
private fun ReadingAppsSettings(extras: ExtrasViewModel, prefs: ExtrasPrefs) {
    val configured by extras.readingApps.collectAsStateWithLifecycle()
    val installed by extras.installedApps.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { extras.loadInstalledApps() }

    SettingsSectionTitle("Reading apps")
    if (configured.isEmpty()) {
        SettingsNote("None yet. Added apps appear as a row on the home screen.")
    }
    configured.forEach { app ->
        ClickableLabel(text = "✕  ${app.label}", onClick = { extras.removeReadingApp(app.packageName) })
    }

    val canAdd = prefs.readingApps.size < ExtrasPrefs.MAX_READING_APPS
    if (!canAdd) {
        SettingsNote("Up to ${ExtrasPrefs.MAX_READING_APPS} apps. Tap one above to remove it.")
        return
    }

    val chosen = prefs.readingApps.toSet()
    val suggestions = remember(installed, chosen) {
        val known = ReadingAppSuggestions.PACKAGES.toSet()
        installed.filter { it.packageName in known && it.packageName !in chosen }
    }
    suggestions.forEach { app ->
        ClickableLabel(text = "+  ${app.label}", onClick = { extras.addReadingApp(app.packageName) })
    }

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { Text("Add any app…") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    if (query.isNotBlank()) {
        val matches = remember(installed, chosen, query) {
            DrawerPaging.search(installed.filter { it.packageName !in chosen }, query) { it.label }.take(5)
        }
        if (matches.isEmpty()) SettingsNote("No apps match “$query”")
        matches.forEach { app ->
            ClickableLabel(
                text = "+  ${app.label}",
                onClick = {
                    extras.addReadingApp(app.packageName)
                    query = ""
                }
            )
        }
    }
}

private fun vendorModeLabel(mode: VendorRefreshMode, detected: EinkVendor): String = when (mode) {
    VendorRefreshMode.AUTO -> "Auto (${vendorName(detected)})"
    VendorRefreshMode.ONYX -> "Onyx"
    VendorRefreshMode.BIGME -> "Bigme"
    VendorRefreshMode.HISENSE -> "Hisense"
    VendorRefreshMode.GENERIC -> "Flash only"
}

private fun vendorName(vendor: EinkVendor): String = when (vendor) {
    EinkVendor.ONYX -> "Onyx"
    EinkVendor.BIGME -> "Bigme"
    EinkVendor.HISENSE -> "Hisense"
    EinkVendor.GENERIC -> "flash"
}
