package com.mp01home.launcher.habits

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mp01home.launcher.ui.settings.ClickableLabel
import com.mp01home.launcher.ui.settings.SettingsNote
import com.mp01home.launcher.ui.settings.SettingsSectionTitle
import com.mp01home.launcher.ui.theme.einkTappable

/**
 * EXT-2 settings block: add / remove habits. Rendered from FocusSettings; can be moved to
 * ExtrasSettings by calling `HabitSettings()` there instead.
 * Removing archives the habit (history kept, hidden everywhere); needs a second tap to confirm.
 */
@Composable
fun HabitSettings(viewModel: HabitViewModel = viewModel()) {
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }
    var confirmRemoveId by remember { mutableStateOf<Long?>(null) }

    SettingsSectionTitle("Habits")
    SettingsNote("Shown on home when the Habits section is on. Tap a habit there to tick today.")
    habits.forEach { habit ->
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = habit.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            val confirming = confirmRemoveId == habit.id
            Text(
                text = if (confirming) "Remove?" else "Remove",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .einkTappable(onClickLabel = "Remove ${habit.name}") {
                        if (confirming) {
                            viewModel.remove(habit.id)
                            confirmRemoveId = null
                        } else {
                            confirmRemoveId = habit.id
                        }
                    }
            )
        }
    }
    OutlinedTextField(
        value = newName,
        onValueChange = { newName = it.take(40) },
        label = { Text("New habit") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
    if (newName.isNotBlank()) {
        ClickableLabel(
            text = "+ Add \"${newName.trim()}\"",
            onClick = {
                viewModel.add(newName)
                newName = ""
            }
        )
    }
}
