package com.mp01home.launcher.ui.home.sections

import androidx.compose.foundation.layout.Column
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
import com.mp01home.launcher.mail.MailHomeViewModel
import com.mp01home.launcher.mail.MailItem
import com.mp01home.launcher.mail.openNotificationAccessSettings
import com.mp01home.launcher.mail.rememberNotificationAccessGranted
import com.mp01home.launcher.ui.theme.einkTappable

/**
 * MAIL-1/2/3 + KEEP-1: unread count and latest 3 Gmail messages (from Gmail notifications,
 * decision D1), inbox/compose actions and one-tap Keep note/list. Renders from Room only.
 */
@Composable
fun MailSection(host: HomeSectionHost, modifier: Modifier = Modifier) {
    val viewModel: MailHomeViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val granted = rememberNotificationAccessGranted()
    val context = LocalContext.current

    val unread = state.summary.unreadCount
    val title = when {
        !granted -> "MAIL"
        unread == 0 -> "MAIL · no new"
        else -> "MAIL · $unread unread"
    }

    HomeSectionFrame(
        title = title,
        modifier = modifier,
        trailing = if (granted) state.syncedLabel else null
    ) {
        if (!granted) {
            Text(
                text = "Grant notification access →",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .einkTappable(onClickLabel = "Open notification access settings") {
                        openNotificationAccessSettings(context)
                    }
            )
        } else if (state.summary.latest.isNotEmpty()) {
            // One tap target for the message list: opens the inbox.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .einkTappable(onClickLabel = "Open inbox") { viewModel.openInbox() }
            ) {
                state.summary.latest.forEach { MailRow(it) }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ActionText("Inbox", Modifier.weight(1f)) { viewModel.openInbox() }
            ActionText("Compose", Modifier.weight(1f)) { viewModel.compose() }
        }
        if (state.keepInstalled) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Keep:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 8.dp)
                )
                ActionText("+ note", Modifier.weight(1f)) { viewModel.newKeepNote() }
                ActionText("+ list", Modifier.weight(1f)) { viewModel.newKeepList() }
            }
        }
    }
}

@Composable
private fun MailRow(item: MailItem) {
    val text = when {
        item.sender.isBlank() -> item.subject
        item.subject.isBlank() -> item.sender
        else -> "${item.sender} — ${item.subject}"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ActionText(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        modifier = modifier.einkTappable(onClick = onClick)
    )
}
