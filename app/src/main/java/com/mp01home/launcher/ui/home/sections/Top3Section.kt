package com.mp01home.launcher.ui.home.sections

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mp01home.launcher.data.db.TopPriority
import com.mp01home.launcher.focus.TOP3_SLOTS
import com.mp01home.launcher.focus.Top3ViewModel
import com.mp01home.launcher.focus.slotMarker
import com.mp01home.launcher.ui.theme.einkTappable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * FOC-1: daily Top 3 priorities with carry-over prompt.
 *
 *     TOP 3                         1/3 done
 *     Carry over 2 from yesterday?  Yes  No
 *     ☑ ① Send invoice
 *     ☐ ② Call Sam
 *     + add
 *
 * Tap a priority to tick it, long-press to edit (text / linked task / clear).
 * Renders from Room only; never scrolls (at most 5 rows).
 */
@Composable
fun Top3Section(host: HomeSectionHost, modifier: Modifier = Modifier) {
    val viewModel: Top3ViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editingSlot by remember { mutableStateOf<Int?>(null) }

    val trailing = if (state.items.isNotEmpty()) "${state.doneCount}/${state.items.size} done" else null
    HomeSectionFrame(title = "TOP 3", modifier = modifier, trailing = trailing) {
        state.carryOver?.let { offer ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Carry over ${offer.items.size} from ${dayLabel(offer.fromDateIso, state.date)}?",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Yes",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.einkTappable(onClickLabel = "Carry over") { viewModel.acceptCarryOver() }
                        .padding(horizontal = 8.dp)
                )
                Text(
                    text = "No",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.einkTappable(onClickLabel = "Dismiss") { viewModel.dismissCarryOver() }
                        .padding(horizontal = 8.dp)
                )
            }
        }

        if (state.items.isEmpty()) {
            Text(
                text = "+ Set today's Top 3",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().einkTappable { editingSlot = 0 }
            )
        } else {
            state.items.forEach { item ->
                PriorityRow(
                    item = item,
                    onToggle = { viewModel.toggleDone(item) },
                    onEdit = { editingSlot = item.slot }
                )
            }
            val used = state.items.map { it.slot }.toSet()
            val nextFree = (0 until TOP3_SLOTS).firstOrNull { it !in used }
            if (nextFree != null) {
                Text(
                    text = "+ add",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().einkTappable { editingSlot = nextFree }
                )
            }
        }
    }

    editingSlot?.let { slot ->
        val existing = state.items.firstOrNull { it.slot == slot }
        val tasks by viewModel.linkableTasks.collectAsStateWithLifecycle()
        PriorityEditDialog(
            slot = slot,
            initialText = existing?.text.orEmpty(),
            initialLinkedTaskId = existing?.linkedTaskId,
            tasks = tasks.map { it.id to it.title },
            canClear = existing != null,
            onSave = { text, link ->
                viewModel.save(slot, text, link)
                editingSlot = null
            },
            onClear = {
                viewModel.clear(slot)
                editingSlot = null
            },
            onDismiss = { editingSlot = null }
        )
    }
}

@Composable
private fun PriorityRow(item: TopPriority, onToggle: () -> Unit, onEdit: () -> Unit) {
    val box = if (item.done) "☑" else "☐"
    val carried = if (item.carriedOver) " ↻" else ""
    Text(
        text = "$box ${slotMarker(item.slot)} ${item.text}$carried",
        style = MaterialTheme.typography.bodyLarge.copy(
            textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .einkTappable(
                onClickLabel = if (item.done) "Mark not done" else "Mark done",
                onLongClick = onEdit,
                onClick = onToggle
            )
    )
}

@Composable
private fun PriorityEditDialog(
    slot: Int,
    initialText: String,
    initialLinkedTaskId: String?,
    tasks: List<Pair<String, String>>,
    canClear: Boolean,
    onSave: (String, String?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    var linked by remember { mutableStateOf(initialLinkedTaskId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Priority ${slotMarker(slot)}") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(80) },
                    label = { Text("What matters today?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (tasks.isNotEmpty()) {
                    Text(
                        text = "Link a task (optional):",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    tasks.forEach { (id, title) ->
                        val selected = id == linked
                        Text(
                            text = (if (selected) "● " else "○ ") + title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (selected) Modifier.border(1.dp, Color.Black) else Modifier)
                                .einkTappable {
                                    if (selected) {
                                        linked = null
                                    } else {
                                        linked = id
                                        if (text.isBlank()) text = title
                                    }
                                }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text, linked) }, enabled = text.isNotBlank()) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (canClear) TextButton(onClick = onClear) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

/** "yesterday" or "Thu 24 Sep" for the carry-over prompt. */
private fun dayLabel(dateIso: String, today: LocalDate): String {
    val date = runCatching { LocalDate.parse(dateIso) }.getOrNull() ?: return dateIso
    return if (date == today.minusDays(1)) "yesterday"
    else date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
}
