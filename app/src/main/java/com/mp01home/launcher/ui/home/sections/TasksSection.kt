package com.mp01home.launcher.ui.home.sections

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mp01home.launcher.tasks.HomeTaskRow
import com.mp01home.launcher.tasks.TasksHomeViewModel
import com.mp01home.launcher.ui.theme.einkTappable

/**
 * TASK-2/3 home widget:
 *
 *     TASKS · 2 overdue          synced 10:42
 *     [ ]  Send invoice
 *     [x]  Call Sam              (ticked a moment ago; tap again to undo)
 *     [ ]  Renew passport     !  (overdue)
 *     +3 more
 *
 * Renders from the Room cache only (EINK-7); the checkbox completes optimistically and the change
 * is queued until it reaches Google (TASK-3). Tapping a title opens Google Tasks.
 */
@Composable
fun TasksSection(host: HomeSectionHost, modifier: Modifier = Modifier) {
    val vm: TasksHomeViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    val title = if (state.overdueCount > 0) "TASKS · ${state.overdueCount} overdue" else "TASKS"
    HomeSectionFrame(
        title = title,
        modifier = modifier,
        trailing = if (state.signedIn) state.syncedLabel else null
    ) {
        when {
            !state.loaded -> Unit
            !state.signedIn && state.rows.isEmpty() -> {
                Text(
                    text = if (state.configured) "Sign in to Google Tasks ›" else "Tasks: set up OAuth in settings ›",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().einkTappable(onClick = host.openSettings)
                )
            }
            state.rows.isEmpty() -> {
                Text(
                    text = "Nothing due today",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().einkTappable(onClickLabel = "Open Tasks") { vm.openTasksApp() }
                )
            }
            else -> {
                state.rows.forEach { row ->
                    TaskRowView(row = row, onToggle = { vm.toggle(row) }, onOpen = { vm.openTasksApp() })
                }
                if (state.hiddenCount > 0) {
                    Text(
                        text = "+${state.hiddenCount} more",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().einkTappable(onClickLabel = "Open Tasks") { vm.openTasksApp() }
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskRowView(row: HomeTaskRow, onToggle: () -> Unit, onOpen: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (row.checked) "[x]" else "[ ]",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.einkTappable(
                onClickLabel = if (row.checked) "Mark not done" else "Mark done",
                role = Role.Checkbox,
                onClick = onToggle
            )
        )
        Text(
            text = row.task.title.ifBlank { "(untitled)" },
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (row.checked) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .einkTappable(onClickLabel = "Open Tasks", onClick = onOpen)
        )
        if (row.overdue && !row.checked) {
            Text(text = "!", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
    }
}
