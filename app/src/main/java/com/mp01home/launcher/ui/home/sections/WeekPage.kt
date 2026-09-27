package com.mp01home.launcher.ui.home.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mp01home.launcher.calendar.CalendarActions
import com.mp01home.launcher.calendar.WeekDay
import com.mp01home.launcher.calendar.WeekUiState
import com.mp01home.launcher.calendar.WeekViewModel
import com.mp01home.launcher.calendar.formatClock
import com.mp01home.launcher.ui.theme.einkTappable
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * PLAN-3: second home page — seven days from today, each with its event titles and the number
 * of open tasks due that day (today also counts overdue). Tapping a day opens the calendar app
 * on that date. Seven equal rows fill the page body; nothing scrolls (EINK-1).
 */
@Composable
fun WeekPage(host: HomeSectionHost, modifier: Modifier = Modifier) {
    val vm: WeekViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        HomeSectionFrame(title = "WEEK", trailing = state.syncedLabel) {
            if (!state.hasPermission) {
                Text(text = "Calendar access needed (see page 1 or Settings)", style = MaterialTheme.typography.labelMedium)
            }
        }
        state.days.forEachIndexed { index, day ->
            if (index > 0) HorizontalDivider(color = Color.Black, thickness = 1.dp)
            DayRow(day = day, state = state, isToday = index == 0, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun DayRow(day: WeekDay, state: WeekUiState, isToday: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val dateLabel = day.date.format(DateTimeFormatter.ofPattern("EEE d", Locale.getDefault())) +
        if (isToday) " · today" else ""
    val tasks = when (day.taskCount) {
        0 -> ""
        1 -> "1 task"
        else -> "${day.taskCount} tasks"
    }
    val events = if (day.events.isEmpty()) {
        "—"
    } else {
        day.events.joinToString(" · ") { e ->
            if (e.allDay || e.beginMillis < day.date.atStartOfDay(state.zone).toInstant().toEpochMilli()) {
                e.title
            } else {
                formatClock(e.beginMillis, state.zone, state.use24h) + " " + e.title
            }
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .einkTappable(onClickLabel = "Open day") { CalendarActions.openDay(context, day.date, state.zone) }
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = dateLabel, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.weight(1f))
            if (tasks.isNotEmpty()) Text(text = tasks, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
        Text(
            text = events,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
