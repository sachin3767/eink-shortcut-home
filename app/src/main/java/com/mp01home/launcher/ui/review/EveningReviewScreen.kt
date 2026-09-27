package com.mp01home.launcher.ui.review

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mp01home.launcher.sync.formatSyncedLabel
import com.mp01home.launcher.ui.home.sections.HomeSectionFrame

/** Rows per evening list; three lists share one screen without scrolling. */
private const val EVENING_MAX_ROWS = 3

/**
 * PLAN-2 evening review (one non-scrolling screen): tasks completed today, open tasks that roll
 * over, tomorrow's first event, and an offer to carry unfinished Top 3 items to tomorrow.
 */
@Composable
fun EveningReviewScreen(onBack: () -> Unit) {
    val viewModel: ReviewViewModel = viewModel()
    val state by viewModel.evening.collectAsStateWithLifecycle()
    var carried by rememberSaveable { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    ReviewScaffold(title = "Evening — ${formatReviewDate(state.date)}", onClose = onBack) {
        val synced = formatSyncedLabel(state.tasksSyncedAt, state.timeFormat)

        HomeSectionFrame(title = "DONE TODAY (${state.completedToday.size})", trailing = synced) {
            if (state.completedToday.isEmpty()) {
                ReviewLine("No tasks completed")
            } else {
                state.completedToday.take(EVENING_MAX_ROWS).forEach { ReviewLine("☑ ${it.title}") }
                MoreLine(state.completedToday.size - EVENING_MAX_ROWS)
            }
        }

        HomeSectionFrame(title = "ROLLS OVER (${state.rollOver.size})") {
            if (state.rollOver.isEmpty()) {
                ReviewLine("Nothing left open")
            } else {
                state.rollOver.take(EVENING_MAX_ROWS).forEach { ReviewLine("☐ ${it.title}") }
                MoreLine(state.rollOver.size - EVENING_MAX_ROWS)
            }
        }

        HomeSectionFrame(title = "TOMORROW") {
            val first = state.tomorrowFirstEvent
            if (first == null) {
                ReviewLine("No events")
            } else {
                ReviewLine(
                    text = "${eventTimeLabel(first, state.timeFormat)}  ${first.title}",
                    onClick = { viewModel.openEvent(first) }
                )
            }
            val unfinished = state.unfinishedTop3
            val freeSlots = ReviewViewModel.TOP3_SLOTS - state.tomorrowTop3Count
            when {
                carried -> ReviewLine("Top 3 carried to tomorrow ✓")
                unfinished.isNotEmpty() && freeSlots > 0 -> ReviewLine(
                    text = "↻ Carry ${minOf(unfinished.size, freeSlots)} unfinished Top 3 to tomorrow",
                    onClick = {
                        viewModel.carryUnfinishedTop3ToTomorrow()
                        carried = true
                    }
                )
                state.top3.isNotEmpty() && unfinished.isEmpty() -> ReviewLine("Top 3 all done ✓")
            }
        }

        Box(modifier = Modifier.weight(1f))
        ActionBar(left = null, right = "Done" to onBack)
    }
}
