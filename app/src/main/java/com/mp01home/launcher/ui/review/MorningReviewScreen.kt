package com.mp01home.launcher.ui.review

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mp01home.launcher.data.db.CachedEvent
import com.mp01home.launcher.data.settings.TimeFormat
import com.mp01home.launcher.sync.formatSyncedLabel
import com.mp01home.launcher.ui.home.sections.HomeSectionFrame
import com.mp01home.launcher.ui.theme.EinkTheme
import com.mp01home.launcher.ui.theme.einkTappable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Rows shown per list before collapsing into "+N more" (no scrolling, EINK-1). */
internal const val REVIEW_MAX_ROWS = 4

/**
 * PLAN-1 morning review, in two non-scrolling steps:
 *  1. today's remaining agenda + tasks due (overdue flagged), "Set Top 3 ›"
 *  2. three Top 3 fields (pre-filled from today's saved Top 3) + due tasks as one-tap suggestions.
 * Everything renders from the Room cache with "synced HH:MM" labels (EINK-7).
 */
@Composable
fun MorningReviewScreen(onBack: () -> Unit) {
    val viewModel: ReviewViewModel = viewModel()
    val state by viewModel.morning.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }

    val drafts = remember { mutableStateListOf(Top3Draft(), Top3Draft(), Top3Draft()) }
    var draftsInitialised by remember { mutableStateOf(false) }
    LaunchedEffect(state.loaded) {
        if (state.loaded && !draftsInitialised) {
            state.top3.filter { it.slot in 0 until ReviewViewModel.TOP3_SLOTS }.forEach {
                drafts[it.slot] = Top3Draft(it.text, it.linkedTaskId)
            }
            draftsInitialised = true
        }
    }

    BackHandler { if (step > 0) step = 0 else onBack() }

    ReviewScaffold(
        title = "Good morning — ${formatReviewDate(state.date)}",
        onClose = onBack
    ) {
        if (step == 0) {
            AgendaStep(
                events = state.events,
                timeFormat = state.timeFormat,
                eventsTrailing = formatSyncedLabel(state.eventsSyncedAt, state.timeFormat),
                onEvent = viewModel::openEvent
            )
            val overdue = if (state.overdueCount > 0) " · ${state.overdueCount} overdue" else ""
            HomeSectionFrame(
                title = "DUE TODAY (${state.dueTasks.size})$overdue",
                trailing = formatSyncedLabel(state.tasksSyncedAt, state.timeFormat)
            ) {
                if (state.dueTasks.isEmpty()) {
                    ReviewLine("Nothing due")
                } else {
                    state.dueTasks.take(REVIEW_MAX_ROWS).forEach { task ->
                        val late = (task.dueEpochDay ?: Long.MAX_VALUE) < state.date.toEpochDay()
                        ReviewLine((if (late) "! " else "☐ ") + task.title)
                    }
                    MoreLine(state.dueTasks.size - REVIEW_MAX_ROWS)
                }
            }
            Box(modifier = Modifier.weight(1f))
            ActionBar(
                left = "Skip" to onBack,
                right = "Set Top 3 ›" to { step = 1 }
            )
        } else {
            HomeSectionFrame(title = "TOP 3 FOR TODAY") {
                drafts.forEachIndexed { index, draft ->
                    OutlinedTextField(
                        value = draft.text,
                        onValueChange = { value ->
                            // Editing the text detaches a linked task unless it still matches.
                            drafts[index] = draft.copy(text = value.take(80), linkedTaskId = draft.linkedTaskId.takeIf { value.isNotBlank() })
                        },
                        label = { Text("${index + 1}.") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = EinkTheme.dimens.rowPadding)
                    )
                }
            }
            val usedIds = drafts.mapNotNull { it.linkedTaskId }.toSet()
            val suggestions = state.dueTasks.filter { it.id !in usedIds }
            val firstEmpty = drafts.indexOfFirst { it.text.isBlank() }
            if (suggestions.isNotEmpty() && firstEmpty >= 0) {
                HomeSectionFrame(title = "TAP A TASK TO ADD IT") {
                    suggestions.take(3).forEach { task ->
                        ReviewLine(
                            text = "+ ${task.title}",
                            onClick = { drafts[firstEmpty] = Top3Draft(task.title.take(80), task.id) }
                        )
                    }
                }
            }
            Box(modifier = Modifier.weight(1f))
            ActionBar(
                left = "‹ Back" to { step = 0 },
                right = "Save" to { viewModel.saveTop3(drafts.toList(), onDone = onBack) }
            )
        }
    }
}

@Composable
private fun AgendaStep(
    events: List<CachedEvent>,
    timeFormat: TimeFormat,
    eventsTrailing: String,
    onEvent: (CachedEvent) -> Unit
) {
    HomeSectionFrame(title = "AGENDA (${events.size})", trailing = eventsTrailing) {
        if (events.isEmpty()) {
            ReviewLine("No more events today")
        } else {
            events.take(REVIEW_MAX_ROWS).forEach { event ->
                ReviewLine(text = "${eventTimeLabel(event, timeFormat)}  ${event.title}", onClick = { onEvent(event) })
            }
            MoreLine(events.size - REVIEW_MAX_ROWS)
        }
    }
}

/** Full-screen white page with a close row; content is a non-scrolling column. */
@Composable
internal fun ReviewScaffold(
    title: String,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding()
            .padding(EinkTheme.dimens.gutter)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "✕",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.einkTappable(onClickLabel = "Close review", onClick = onClose)
            )
        }
        content()
    }
}

/** One ellipsized body line; tappable (≥ 56dp) only when [onClick] is given. */
@Composable
internal fun ReviewLine(text: String, onClick: (() -> Unit)? = null) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = if (onClick != null) {
            Modifier.fillMaxWidth().einkTappable(onClick = onClick)
        } else {
            Modifier.fillMaxWidth().padding(vertical = EinkTheme.dimens.rowPadding / 2)
        }
    )
}

@Composable
internal fun MoreLine(hidden: Int) {
    if (hidden > 0) {
        Text(text = "+$hidden more", style = MaterialTheme.typography.labelMedium)
    }
}

/** Bottom row of two large text buttons. */
@Composable
internal fun ActionBar(left: Pair<String, () -> Unit>?, right: Pair<String, () -> Unit>) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (left != null) {
            Text(
                text = left.first,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.einkTappable(onClick = left.second)
            )
        }
        Box(modifier = Modifier.weight(1f))
        Text(
            text = right.first,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.End,
            modifier = Modifier.einkTappable(onClick = right.second)
        )
    }
}

internal fun formatReviewDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))

internal fun eventTimeLabel(event: CachedEvent, timeFormat: TimeFormat, zone: ZoneId = ZoneId.systemDefault()): String {
    if (event.allDay) return "all day"
    val pattern = if (timeFormat == TimeFormat.HOUR_24) "HH:mm" else "h:mm a"
    return Instant.ofEpochMilli(event.beginMillis).atZone(zone)
        .format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}
