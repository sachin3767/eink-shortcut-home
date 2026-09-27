package com.mp01home.launcher.ui.settings.sections

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mp01home.launcher.appContainer
import com.mp01home.launcher.tasks.TaskListCatalog
import com.mp01home.launcher.tasks.TasksAuthException
import com.mp01home.launcher.tasks.TasksMapping
import com.mp01home.launcher.ui.settings.ClickableLabel
import com.mp01home.launcher.ui.settings.SettingsNote
import com.mp01home.launcher.ui.settings.SettingsSectionTitle
import com.mp01home.launcher.ui.settings.SettingsViewModel
import com.mp01home.launcher.ui.settings.ToggleRow
import kotlinx.coroutines.launch

/**
 * TASK-1 Google sign-in / sign-out and TASK-5 task list selection.
 */
@Composable
fun TasksSettings(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val repo = remember(context) { context.appContainer.tasksRepository }
    val auth = repo.auth
    val scope = rememberCoroutineScope()

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val account by auth.accountName.collectAsStateWithLifecycle()
    val consentIntent by auth.consentIntent.collectAsStateWithLifecycle()
    val catalog by repo.catalog.collectAsStateWithLifecycle(initialValue = TaskListCatalog())
    val pendingCount by repo.observePendingCount().collectAsStateWithLifecycle(initialValue = 0)
    var message by remember { mutableStateOf<String?>(null) }

    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        scope.launch {
            message = try {
                val name = auth.completeSignIn(result.data)
                repo.requestSyncNow()
                "Signed in as $name"
            } catch (e: TasksAuthException) {
                e.message
            }
        }
    }
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        repo.requestSyncNow()
        message = "Syncing…"
    }

    SettingsSectionTitle("Google Tasks")

    if (!auth.isConfigured) {
        SettingsNote(
            "Google sign-in is not configured in this build. Add " +
                "GOOGLE_OAUTH_WEB_CLIENT_ID=<web client id>.apps.googleusercontent.com to local.properties " +
                "(Google Cloud project with the Tasks API enabled, plus an Android OAuth client for this " +
                "app's package and signing SHA-1), then rebuild."
        )
        return
    }

    val signedIn = account
    if (signedIn == null) {
        ClickableLabel(text = "Sign in with Google", onClick = {
            message = null
            try {
                signInLauncher.launch(auth.signInIntent())
            } catch (e: Exception) {
                message = "Google Play services unavailable: ${e.message}"
            }
        })
        SettingsNote("Shows tasks due today and overdue on home; quick capture adds to your default list.")
    } else {
        SettingsNote("Signed in as $signedIn")
        consentIntent?.let { intent ->
            ClickableLabel(text = "Grant Google Tasks access", onClick = { consentLauncher.launch(intent) })
        }
        ClickableLabel(text = "Sync now", onClick = {
            repo.requestSyncNow()
            message = "Syncing…"
        })
        if (pendingCount > 0) {
            SettingsNote("$pendingCount change(s) waiting to sync")
        }
        ClickableLabel(text = "Sign out", onClick = {
            scope.launch {
                repo.signOut()
                message = "Signed out"
            }
        })

        // ---- TASK-5: which lists feed home ----
        if (catalog.lists.isEmpty()) {
            SettingsNote("Task lists appear here after the first sync.")
        } else {
            SettingsNote("Lists shown on home (none selected = default list)")
            val listIds = catalog.lists.map { it.id }
            val effective = TasksMapping.effectiveListIds(settings.selectedTaskListIds, listIds, catalog.defaultListId).toSet()
            catalog.lists.forEach { list ->
                val checked = list.id in effective
                val label = list.title.ifBlank { "(untitled list)" } +
                    if (list.id == catalog.defaultListId) " (default)" else ""
                ToggleRow(
                    label = label,
                    checked = checked,
                    onCheckedChange = { on ->
                        val next = if (on) effective + list.id else effective - list.id
                        viewModel.update {
                            setSelectedTaskListIds(next)
                            repo.requestSyncNow()
                        }
                    }
                )
            }
        }
    }

    message?.let { SettingsNote(it) }
}
