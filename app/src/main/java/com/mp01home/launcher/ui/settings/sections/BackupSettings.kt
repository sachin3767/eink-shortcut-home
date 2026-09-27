package com.mp01home.launcher.ui.settings.sections

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.mp01home.launcher.appContainer
import com.mp01home.launcher.backup.BackupFormatException
import com.mp01home.launcher.backup.SettingsBackupManager
import com.mp01home.launcher.ui.settings.ClickableLabel
import com.mp01home.launcher.ui.settings.SettingsNote
import com.mp01home.launcher.ui.settings.SettingsSectionTitle
import com.mp01home.launcher.ui.settings.SettingsViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** CORE-7: export / import settings + home layout as a JSON file (Storage Access Framework). */
@Composable
fun BackupSettings(viewModel: SettingsViewModel) {
    val context = LocalContext.current.applicationContext
    val container = context.appContainer
    val manager = remember(container) {
        SettingsBackupManager(context, container.settingsRepository, container.extrasStore)
    }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun runBackup(uri: Uri?, work: suspend (Uri) -> Unit, success: String, failure: String) {
        if (uri == null || busy) return
        busy = true
        status = "Working…"
        scope.launch {
            status = try {
                work(uri)
                success
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupFormatException) {
                "$failure: ${e.message}"
            } catch (e: Exception) {
                "$failure (${e.javaClass.simpleName})"
            } finally {
                busy = false
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(SettingsBackupManager.MIME_TYPE)
    ) { uri -> runBackup(uri, manager::exportTo, "Settings exported", "Export failed") }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        runBackup(uri, manager::importFrom, "Settings imported", "Import failed")
    }

    SettingsSectionTitle("Backup")
    ClickableLabel(
        text = "Export settings to file…",
        onClick = { if (!busy) exportLauncher.launch(SettingsBackupManager.suggestedFileName()) }
    )
    ClickableLabel(
        text = "Import settings from file…",
        onClick = { if (!busy) importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
    )
    SettingsNote(
        "Saves layout, sections, dock, gestures, display and feature settings as JSON. " +
            "Accounts, caches and history are not included. Importing replaces current settings."
    )
    status?.let { SettingsNote(it) }
}
