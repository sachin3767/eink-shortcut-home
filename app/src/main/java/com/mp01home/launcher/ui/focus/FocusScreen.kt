package com.mp01home.launcher.ui.focus

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mp01home.launcher.appContainer
import com.mp01home.launcher.focus.FocusPhase
import com.mp01home.launcher.focus.FocusUiState
import com.mp01home.launcher.focus.FocusViewModel
import com.mp01home.launcher.ui.theme.EinkTheme
import com.mp01home.launcher.ui.theme.einkTappable

/**
 * FOC-2/3 full-screen Pomodoro timer: a large minutes-left number that redraws once per
 * minute (never per second, EINK-4), start/pause/stop/skip controls, and the DND state.
 * Route: Routes.FOCUS.
 */
@Composable
fun FocusScreen(onBack: () -> Unit) {
    val viewModel: FocusViewModel = viewModel()
    val state by viewModel.screenState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    BackHandler(onBack = onBack)
    // Returning from the DND-access settings screen: re-check the grant.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshAccess()
        onPauseOrDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding()
            .padding(EinkTheme.dimens.gutter)
    ) {
        Text(
            text = "← Back",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.einkTappable(onClick = onBack)
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = phaseTitle(state),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            if (state.timer.completedFocusSessions > 0) {
                Text(
                    text = "${state.timer.completedFocusSessions} done today",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val minutes = if (state.timer.phase == FocusPhase.IDLE) state.focusMinutes else state.minutesLeft
            Text(
                text = minutes.toString(),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 140.sp, fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(
                text = when {
                    state.timer.phase == FocusPhase.IDLE -> "min focus"
                    state.timer.isPaused -> "min left · paused"
                    else -> "min left" + (state.endsAtText?.let { " · $it" } ?: "")
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
        }

        DndLine(
            state = state,
            onGrant = { context.appContainer.appLauncher.startActivitySafely(viewModel.dndAccessIntent()) }
        )
        Spacer(Modifier.height(12.dp))
        Controls(state = state, viewModel = viewModel)
    }
}

private fun phaseTitle(state: FocusUiState): String = when (state.timer.phase) {
    FocusPhase.IDLE -> "Focus"
    FocusPhase.FOCUS -> "Focusing"
    FocusPhase.BREAK -> "Break"
}

@Composable
private fun DndLine(state: FocusUiState, onGrant: () -> Unit) {
    if (!state.dndEnabled) return
    if (!state.dndAccess) {
        Text(
            text = "Do Not Disturb needs access — tap to grant",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .einkTappable(onClick = onGrant)
        )
    } else {
        Text(
            text = if (state.timer.phase == FocusPhase.FOCUS) "Do Not Disturb: on" else "Do Not Disturb during focus",
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun Controls(state: FocusUiState, viewModel: FocusViewModel) {
    val timer = state.timer
    when {
        timer.phase == FocusPhase.IDLE -> {
            BigButton("Start focus · ${state.focusMinutes} min", primary = true, onClick = viewModel::startFocus)
            Spacer(Modifier.height(8.dp))
            BigButton("Start break · ${state.breakMinutes} min", onClick = viewModel::startBreak)
        }
        else -> {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (timer.isPaused) {
                    BigButton("Resume", primary = true, onClick = viewModel::resume, modifier = Modifier.weight(1f))
                } else {
                    BigButton("Pause", onClick = viewModel::pause, modifier = Modifier.weight(1f))
                }
                BigButton("Stop", onClick = viewModel::stop, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            BigButton(
                text = if (timer.phase == FocusPhase.FOCUS) "Skip to break" else "End break",
                onClick = viewModel::skip
            )
        }
    }
}

@Composable
private fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth(), primary: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        color = if (primary) Color.White else Color.Black,
        maxLines = 1,
        modifier = modifier
            .border(2.dp, Color.Black)
            .background(if (primary) Color.Black else Color.White)
            .einkTappable(onClick = onClick)
    )
}
