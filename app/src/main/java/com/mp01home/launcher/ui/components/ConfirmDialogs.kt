package com.mp01home.launcher.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun RemoveFromHomeDialog(
    appLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove from Home?") },
        text = { Text(appLabel) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Remove") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AddToHomeDialog(
    appLabel: String,
    homeIsFull: Boolean,
    alreadyOnHome: Boolean,
    onConfirmAdd: () -> Unit,
    onConfirmRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(appLabel) },
        text = {
            Text(
                when {
                    alreadyOnHome -> "Already on Home."
                    homeIsFull -> "Home is full — remove an app first."
                    else -> "Add this app to Home?"
                }
            )
        },
        confirmButton = {
            when {
                alreadyOnHome -> TextButton(onClick = onConfirmRemove) { Text("Remove from Home") }
                !homeIsFull -> TextButton(onClick = onConfirmAdd) { Text("Add to Home") }
                else -> {}
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
