package com.mp01home.launcher.ui.home.sections

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mp01home.launcher.calendar.CalEvent
import com.mp01home.launcher.calendar.CalendarActions
import com.mp01home.launcher.calendar.CalendarPermissionPrompt
import com.mp01home.launcher.calendar.CalendarUiState
import com.mp01home.launcher.calendar.CalendarViewModel
import com.mp01home.launcher.calendar.FreeBusyStrip
import com.mp01home.launcher.calendar.MeetingLinks
import com.mp01home.launcher.calendar.TimeBlockDialog
import com.mp01home.launcher.calendar.countdownLabel
import com.mp01home.launcher.calendar.eventTimeLabel
import com.mp01home.launcher.calendar.formatClock
import com.mp01home.launcher.calendar.isPhysicalLocation
import com.mp01home.launcher.ui.theme.einkTappable
import java.time.LocalDate

/**
 * CAL-1/2/3/4/5 + PLAN-4 entry:
 *
 *     CALENDAR                         synced 10:42
 *     Standup                              [JOIN]
 *     in 8 min · 10:30–10:45 · Room 4
 *     11:00 Design review
 *     14:00 1:1 Anna
 *     [########   ##    |          ####       ]
 *     08:00                                 20:00
 *     free now–10:30 · 12:00–14:00
 *     + Event          Block             2 more
 *
 * Renders from the Room cache only (EINK-7); updates once per minute (EINK-4); no scrolling.
 */
@Composable
fun CalendarSection(host: HomeSectionHost, modifier: Modifier = Modifier) {
    val vm: CalendarViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showBlockDialog by rememberSaveable { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshPermission() }

    HomeSectionFrame(title = "CALENDAR", modifier = modifier, trailing = state.syncedLabel) {
        if (!state.hasPermission) {
            CalendarPermissionPrompt(onResult = vm::onPermissionResult)
        } else {
            NextEventCard(state)
            state.agenda.forEach { event -> AgendaRow(event, state) }
            val window = state.stripWindow
            if (state.prefs.showFreeBusy && window != null && state.stripSegments.isNotEmpty()) {
                FreeBusyStrip(
                    window = window,
                    segments = state.stripSegments,
                    nowMillis = state.nowMillis,
                    startLabel = formatClock(window.startMillis, state.zone, state.use24h),
                    endLabel = formatClock(window.endMillis, state.zone, state.use24h),
                    modifier = Modifier.padding(top = 4.dp)
                )
                state.freeLabel?.let {
                    Text(text = it, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FooterAction("+ Event", Modifier.weight(1f), TextAlign.Start) { CalendarActions.insertEvent(context) }
            FooterAction("Block", Modifier.weight(1f), TextAlign.Center) { showBlockDialog = true }
            if (state.hasPermission && state.moreCount > 0) {
                FooterAction("${state.moreCount} more", Modifier.weight(1f), TextAlign.End) {
                    CalendarActions.openDay(context, LocalDate.now(state.zone), state.zone)
                }
            }
        }
    }

    if (showBlockDialog) {
        TimeBlockDialog(
            defaultMinutes = state.prefs.blockMinutes,
            onSchedule = { title, minutes -> vm.scheduleBlock(title, minutes) },
            onDismiss = { showBlockDialog = false }
        )
    }
}

@Composable
private fun NextEventCard(state: CalendarUiState) {
    val context = LocalContext.current
    val next = state.next
    if (next == null) {
        Text(text = "No upcoming events", style = MaterialTheme.typography.bodyMedium)
        return
    }
    val showJoin = MeetingLinks.shouldShowJoin(state.nowMillis, next.beginMillis, next.endMillis, next.meetingUrl)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier
                .weight(1f)
                .einkTappable(onClickLabel = "Open event") {
                    CalendarActions.openEvent(context, next.eventId, next.beginMillis, next.endMillis)
                }
        ) {
            Text(
                text = next.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = nextDetailLine(next, state),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (showJoin) {
            val url = next.meetingUrl
            Text(
                text = "JOIN",
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .border(2.dp, Color.Black)
                    .einkTappable(onClickLabel = "Join meeting") {
                        if (url != null) CalendarActions.openUrl(context, url)
                    }
                    .padding(horizontal = 12.dp)
            )
        }
    }
}

/** "in 8 min · 10:30–10:45 · Room 4 · leave 10:00" */
private fun nextDetailLine(next: CalEvent, state: CalendarUiState): String {
    val parts = mutableListOf(
        countdownLabel(state.nowMillis, next.beginMillis, next.endMillis),
        eventTimeLabel(next.beginMillis, next.endMillis, next.allDay, state.nowMillis, state.zone, state.use24h)
    )
    val location = next.location
    if (!location.isNullOrBlank() && next.meetingUrl?.let { location.contains(it) } != true) parts += location.trim()
    if (state.prefs.leaveByEnabled && isPhysicalLocation(location)) {
        val leaveAt = next.beginMillis - state.prefs.leaveByLeadMinutes * 60_000L
        if (leaveAt > state.nowMillis) parts += "leave " + formatClock(leaveAt, state.zone, state.use24h)
    }
    return parts.joinToString(" · ")
}

@Composable
private fun AgendaRow(event: CalEvent, state: CalendarUiState) {
    val context = LocalContext.current
    val time = when {
        event.allDay -> "all day"
        event.beginMillis <= state.nowMillis -> "now"
        else -> formatClock(event.beginMillis, state.zone, state.use24h)
    }
    Text(
        text = "$time ${event.title}",
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .einkTappable(onClickLabel = "Open event") {
                CalendarActions.openEvent(context, event.eventId, event.beginMillis, event.endMillis)
            }
    )
}

@Composable
private fun FooterAction(label: String, modifier: Modifier, align: TextAlign, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        textAlign = align,
        maxLines = 1,
        modifier = modifier.einkTappable(onClick = onClick)
    )
}
