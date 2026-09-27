package com.mp01home.launcher.ui.home.sections

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mp01home.launcher.appContainer
import com.mp01home.launcher.distraction.ScreenTimeViewModel
import com.mp01home.launcher.ui.theme.einkTappable

/**
 * DIS-3: one line "today: 1h12m, top: Chrome" from UsageStatsManager (this launcher excluded).
 * Without usage access it shows a tappable prompt that opens the system "Usage access" screen.
 */
@Composable
fun ScreenTimeLine(host: HomeSectionHost, modifier: Modifier = Modifier) {
    val viewModel: ScreenTimeViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    HomeSectionFrame(title = null, modifier = modifier) {
        when {
            !state.loaded -> Text(text = "today: …", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            !state.hasAccess -> Text(
                text = "Screen time: tap to allow usage access",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .einkTappable {
                        val launcher = context.appContainer.appLauncher
                        if (!launcher.startActivitySafely(viewModel.accessIntent())) {
                            launcher.startActivitySafely(viewModel.genericAccessIntent())
                        }
                    }
            )
            else -> Text(
                text = state.line,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
