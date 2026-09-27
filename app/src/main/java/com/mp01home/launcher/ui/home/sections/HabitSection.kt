package com.mp01home.launcher.ui.home.sections

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mp01home.launcher.habits.HabitRepository
import com.mp01home.launcher.habits.HabitRow
import com.mp01home.launcher.habits.HabitViewModel
import com.mp01home.launcher.habits.formatStreak
import com.mp01home.launcher.ui.theme.einkTappable

/**
 * EXT-2: one row per habit with today's checkbox, the previous days and the streak:
 *
 *     HABITS
 *     ☑ Read         ■■□■■■  5d
 *     ☐ Stretch      □■■■■■  6d
 *
 * Tap a row to tick/untick today. At most [HabitRepository.MAX_HOME_ROWS] rows (no scrolling);
 * habits are added/removed in Settings → Focus → Habits (long-press opens settings).
 */
@Composable
fun HabitSection(host: HomeSectionHost, modifier: Modifier = Modifier) {
    val viewModel: HabitViewModel = viewModel()
    val rows by viewModel.rows.collectAsStateWithLifecycle()

    HomeSectionFrame(title = "HABITS", modifier = modifier) {
        if (rows.isEmpty()) {
            Text(
                text = "+ Add habits in settings",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().einkTappable(onClick = host.openSettings)
            )
        } else {
            HabitRows(rows = rows, host = host, viewModel = viewModel)
        }
    }
}

@Composable
private fun HabitRows(rows: List<HabitRow>, host: HomeSectionHost, viewModel: HabitViewModel) {
    rows.take(HabitRepository.MAX_HOME_ROWS).forEach { row ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .einkTappable(
                    onClickLabel = if (row.doneToday) "Uncheck ${row.habit.name}" else "Check ${row.habit.name}",
                    onLongClick = host.openSettings,
                    onClick = { viewModel.toggleToday(row) }
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = (if (row.doneToday) "☑ " else "☐ ") + row.habit.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = row.history,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Text(
                text = formatStreak(row.streak).ifEmpty { "—" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
        }
    }
    val hidden = rows.size - HabitRepository.MAX_HOME_ROWS
    if (hidden > 0) {
        Text(text = "+$hidden more", style = MaterialTheme.typography.labelMedium)
    }
}
