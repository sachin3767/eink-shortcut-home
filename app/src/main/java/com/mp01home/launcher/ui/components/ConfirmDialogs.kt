package com.mp01home.launcher.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.mp01home.launcher.data.settings.LauncherSettings

@Composable
fun RemoveFromDockDialog(
    appLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove from dock?") },
        text = { Text(appLabel) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Remove") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Drawer long-press menu: add to / remove from the dock (CORE-3). Feature agents may add more
 * actions (hide app CORE-5, friction DIS-1) through [extraActions].
 */
@Composable
fun AddToDockDialog(
    appLabel: String,
    dockIsFull: Boolean,
    alreadyInDock: Boolean,
    onConfirmAdd: () -> Unit,
    onConfirmRemove: () -> Unit,
    onDismiss: () -> Unit,
    extraActions: @Composable ColumnScope.() -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(appLabel) },
        text = {
            Column {
                Text(
                    when {
                        alreadyInDock -> "In the dock."
                        dockIsFull -> "Dock is full (${LauncherSettings.MAX_DOCK_APPS} apps) — remove one first."
                        else -> "Add this app to the dock?"
                    }
                )
                extraActions()
            }
        },
        confirmButton = {
            when {
                alreadyInDock -> TextButton(onClick = onConfirmRemove) { Text("Remove from dock") }
                !dockIsFull -> TextButton(onClick = onConfirmAdd) { Text("Add to dock") }
                else -> {}
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
