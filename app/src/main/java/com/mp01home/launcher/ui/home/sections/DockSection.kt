package com.mp01home.launcher.ui.home.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.mp01home.launcher.data.apps.AppInfo
import com.mp01home.launcher.data.settings.LauncherSettings
import com.mp01home.launcher.ui.theme.einkTappable

/**
 * CORE-3 dock: up to [LauncherSettings.MAX_DOCK_APPS] text shortcuts at the bottom of home.
 * ≤ 4 apps render as one row; 5–6 as rows of 3. Long-press asks to remove from the dock.
 */
@Composable
fun DockSection(
    apps: List<AppInfo>,
    onTap: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    HomeSectionFrame(title = null, modifier = modifier) {
        if (apps.isEmpty()) {
            Text(
                text = "Dock is empty — long-press an app in the drawer to add it.",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            DockGrid(apps = apps, onTap = onTap, onLongPress = onLongPress)
        }
    }
}

@Composable
private fun DockGrid(apps: List<AppInfo>, onTap: (AppInfo) -> Unit, onLongPress: (AppInfo) -> Unit) {
    val visible = apps.take(LauncherSettings.MAX_DOCK_APPS)
    val perRow = dockColumns(visible.size)
    Column(modifier = Modifier.fillMaxWidth()) {
        visible.chunked(perRow).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { app ->
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .einkTappable(
                                onClickLabel = "Open ${app.label}",
                                onLongClick = { onLongPress(app) },
                                onClick = { onTap(app) }
                            )
                    )
                }
                // Keep column widths stable on a short last row.
                repeat(perRow - row.size) { Box(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/** Items per dock row: one row of up to 4, otherwise rows of 3. */
internal fun dockColumns(count: Int): Int = if (count <= 4) maxOf(count, 1) else 3
