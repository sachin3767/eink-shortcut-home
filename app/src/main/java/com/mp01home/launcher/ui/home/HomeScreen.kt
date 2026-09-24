package com.mp01home.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mp01home.launcher.data.apps.AppInfo
import com.mp01home.launcher.data.settings.HorizontalAlignment
import com.mp01home.launcher.ui.components.HeaderInfo
import com.mp01home.launcher.ui.components.RemoveFromHomeDialog
import com.mp01home.launcher.ui.components.TimelineRail

@Composable
fun HomeScreen(
    onOpenAppDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings = uiState.settings
    var appPendingRemoval by remember { mutableStateOf<AppInfo?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding()
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onOpenSettings() })
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -24f) onOpenAppDrawer()
                }
            }
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HeaderInfo(
                now = uiState.now,
                showTime = settings.showTime,
                timeFormat = settings.timeFormat,
                showDate = settings.showDate,
                dateFormat = settings.dateFormat,
                showWeather = settings.showWeather,
                weather = uiState.weather,
                alignment = settings.appsAlignment,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            val appList: @Composable () -> Unit = {
                AppList(
                    apps = uiState.homeAppInfos,
                    alignment = settings.appsAlignment,
                    onTap = { pkg -> viewModel.launchApp(pkg) },
                    onLongPress = { app -> appPendingRemoval = app },
                    modifier = Modifier.fillMaxHeight()
                )
            }
            val rail: @Composable () -> Unit = {
                if (settings.timelineEnabled) {
                    TimelineRail(
                        currentTime = uiState.now.toLocalTime(),
                        modifier = Modifier.fillMaxHeight()
                    )
                }
            }

            Row(modifier = Modifier.fillMaxSize()) {
                if (settings.appsAlignment == HorizontalAlignment.LEFT) {
                    Box(modifier = Modifier.weight(1f)) { appList() }
                    rail()
                } else {
                    rail()
                    Box(modifier = Modifier.weight(1f)) { appList() }
                }
            }
        }
    }

    appPendingRemoval?.let { app ->
        RemoveFromHomeDialog(
            appLabel = app.label,
            onConfirm = {
                viewModel.removeFromHome(app.packageName)
                appPendingRemoval = null
            },
            onDismiss = { appPendingRemoval = null }
        )
    }
}

@Composable
private fun AppList(
    apps: List<AppInfo>,
    alignment: HorizontalAlignment,
    onTap: (String) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val textAlign = if (alignment == HorizontalAlignment.LEFT) TextAlign.Left else TextAlign.Right
    val horizontalAlignment = if (alignment == HorizontalAlignment.LEFT) Alignment.Start else Alignment.End

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = horizontalAlignment
    ) {
        apps.forEach { app ->
            val interactionSource = remember { MutableInteractionSource() }
            Text(
                text = app.label,
                textAlign = textAlign,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onTap(app.packageName) },
                        onLongClick = { onLongPress(app) }
                    )
            )
        }
    }
}
