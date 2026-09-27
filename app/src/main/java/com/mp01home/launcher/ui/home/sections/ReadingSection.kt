package com.mp01home.launcher.ui.home.sections

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mp01home.launcher.eink.ExtrasViewModel
import com.mp01home.launcher.ui.theme.einkTappable

/**
 * EXT-3: one row of up to 4 reading-app shortcuts (Kindle, KOReader, Libby, …).
 *
 *     READ
 *     Kindle   KOReader   Libby
 *
 * Apps are chosen in Settings → Reading apps; launches go through AppLauncher (DIS-1 gate).
 */
@Composable
fun ReadingSection(host: HomeSectionHost, modifier: Modifier = Modifier) {
    val viewModel: ExtrasViewModel = viewModel()
    val apps by viewModel.readingApps.collectAsStateWithLifecycle()

    HomeSectionFrame(title = "READ", modifier = modifier) {
        if (apps.isEmpty()) {
            Text(
                text = "+ Choose reading apps in settings",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().einkTappable(onClick = host.openSettings)
            )
        } else {
            Row(modifier = Modifier.fillMaxWidth()) {
                apps.forEach { app ->
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .einkTappable(onClickLabel = "Open ${app.label}") { viewModel.launch(app.packageName) }
                    )
                }
            }
        }
    }
}
