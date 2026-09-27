package com.mp01home.launcher.ui.home.sections

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mp01home.launcher.focus.FocusPhase
import com.mp01home.launcher.focus.FocusViewModel
import com.mp01home.launcher.ui.navigation.Routes
import com.mp01home.launcher.ui.theme.einkTappable

/**
 * FOC-2: one-line focus timer state ("Focus · 18 min left"); tap opens Routes.FOCUS,
 * long-press starts a focus session straight from home when idle.
 * Updates on the minute boundary only (EINK-4); state is persisted, so it survives restarts.
 */
@Composable
fun FocusSection(host: HomeSectionHost, modifier: Modifier = Modifier) {
    val viewModel: FocusViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val idle = state.timer.phase == FocusPhase.IDLE

    HomeSectionFrame(title = "FOCUS", modifier = modifier, trailing = state.endsAtText) {
        Text(
            text = if (idle) "Start ${state.focusMinutes} min focus" else state.compactText,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .einkTappable(
                    onClickLabel = "Open focus timer",
                    onLongClick = if (idle) ({ viewModel.startFocus() }) else null,
                    onClick = { host.navigate(Routes.FOCUS) }
                )
        )
    }
}
