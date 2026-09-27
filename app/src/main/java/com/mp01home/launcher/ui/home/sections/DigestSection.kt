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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mp01home.launcher.digest.DigestGroup
import com.mp01home.launcher.digest.DigestHomeViewModel
import com.mp01home.launcher.mail.openNotificationAccessSettings
import com.mp01home.launcher.mail.rememberNotificationAccessGranted
import com.mp01home.launcher.ui.theme.einkTappable

private const val MAX_GROUPS = 3

/**
 * FOC-4: batched notifications grouped by app ("Slack 4 · Re: deploy"). Whitelisted apps show
 * as they arrive; other apps (when holding is on) are snoozed and appear at the digest times.
 * Tap a group to open that app and clear it; long-press to clear the whole digest.
 */
@Composable
fun DigestSection(host: HomeSectionHost, modifier: Modifier = Modifier) {
    val viewModel: DigestHomeViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val granted = rememberNotificationAccessGranted()
    val context = LocalContext.current
    val view = state.view

    HomeSectionFrame(
        title = if (view.totalVisible > 0) "DIGEST · ${view.totalVisible}" else "DIGEST",
        modifier = modifier,
        trailing = if (granted) state.syncedLabel else null
    ) {
        when {
            !granted -> Text(
                text = "Grant notification access →",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .einkTappable(onClickLabel = "Open notification access settings") {
                        openNotificationAccessSettings(context)
                    }
            )

            !state.hasWhitelist && !state.holdEnabled -> Text(
                text = "Choose digest apps in settings →",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .einkTappable { host.openSettings() }
            )

            else -> {
                view.groups.take(MAX_GROUPS).forEach { group ->
                    DigestGroupRow(group, onClick = { viewModel.openGroup(group) }, onLongClick = { viewModel.clearAll() })
                }
                val hiddenGroups = (view.groups.size - MAX_GROUPS).coerceAtLeast(0)
                val footer = buildList {
                    if (hiddenGroups > 0) add("+$hiddenGroups apps")
                    if (view.heldCount > 0) {
                        val until = state.nextReleaseLabel?.let { " until $it" }.orEmpty()
                        add("${view.heldCount} held$until")
                    } else if (state.holdEnabled && state.nextReleaseLabel != null) {
                        add("next digest ${state.nextReleaseLabel}")
                    }
                    if (view.groups.isEmpty() && isEmpty()) add("nothing new")
                }
                if (footer.isNotEmpty()) {
                    Text(
                        text = footer.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun DigestGroupRow(group: DigestGroup, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .einkTappable(onClickLabel = "Open ${group.appLabel}", onLongClick = onLongClick, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${group.appLabel} ${group.count}",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(0.45f, fill = false)
                .padding(end = 8.dp)
        )
        Text(
            text = group.latestTitle,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.55f)
        )
    }
}
