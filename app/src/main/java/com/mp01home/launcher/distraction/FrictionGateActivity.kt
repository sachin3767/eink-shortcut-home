package com.mp01home.launcher.distraction

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mp01home.launcher.appContainer
import com.mp01home.launcher.data.settings.LauncherSettings
import com.mp01home.launcher.ui.theme.EinkTheme
import com.mp01home.launcher.ui.theme.MP01HomeTheme
import com.mp01home.launcher.ui.theme.einkTappable
import kotlinx.coroutines.delay

/**
 * DIS-1 friction gate / DIS-2 limit block, shown by [DistractionGate] instead of the app.
 *
 * Friction: the "Open" button reads "Wait…" for [PAUSE_MILLIS] (a single redraw when it
 * becomes available, no per-second countdown: EINK-4), and a typed reason is required.
 * Limit reached: a message and a way back; the app is not opened today.
 *
 * Manifest: declared with `noHistory` + `excludeFromRecents`, so leaving it cancels the launch.
 */
class FrictionGateActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE)
        if (packageName.isNullOrBlank()) {
            finish()
            return
        }
        val limitMinutes = intent.getIntExtra(EXTRA_LIMIT_MINUTES, 0)
        val usedMillis = intent.getLongExtra(EXTRA_USED_MILLIS, 0L)
        val container = appContainer

        setContent {
            val settings by container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = LauncherSettings())
            val label by produceState(initialValue = packageName, packageName) {
                value = container.installedAppsRepository.resolveLabel(packageName) ?: packageName
            }
            MP01HomeTheme(fontScale = settings.bodyFontScale, density = settings.density) {
                if (limitMinutes > 0) {
                    LimitReachedScreen(
                        appLabel = label,
                        usedMillis = usedMillis,
                        limitMinutes = limitMinutes,
                        onBack = { finish() }
                    )
                } else {
                    FrictionScreen(
                        appLabel = label,
                        onCancel = { finish() },
                        onOpen = {
                            container.appLauncher.launch(packageName, bypassInterceptor = true)
                            finish()
                        }
                    )
                }
            }
        }
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0) // EINK-1: no exit animation
    }

    companion object {
        const val EXTRA_PACKAGE = "com.mp01home.launcher.extra.GATE_PACKAGE"
        const val EXTRA_LIMIT_MINUTES = "com.mp01home.launcher.extra.GATE_LIMIT_MINUTES"
        const val EXTRA_USED_MILLIS = "com.mp01home.launcher.extra.GATE_USED_MILLIS"
        const val PAUSE_MILLIS = 10_000L
    }
}

@Composable
private fun GateScaffold(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding()
            .imePadding()
            .padding(EinkTheme.dimens.gutter)
    ) {
        content()
    }
}

@Composable
private fun FrictionScreen(appLabel: String, onCancel: () -> Unit, onOpen: () -> Unit) {
    var waited by rememberSaveable { mutableStateOf(false) }
    var reason by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) {
        if (!waited) {
            delay(FrictionGateActivity.PAUSE_MILLIS)
            waited = true
        }
    }
    val canOpen = waited && isReasonAcceptable(reason)

    GateScaffold {
        Text(
            text = "← Not now",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.einkTappable(onClick = onCancel)
        )
        Spacer(Modifier.height(24.dp))
        Text(text = "Pause.", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(8.dp))
        Text(text = "Why open $appLabel now?", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = reason,
            onValueChange = { reason = it.take(120) },
            label = { Text("Reason") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = when {
                !waited -> "Wait…"
                !isReasonAcceptable(reason) -> "Type a reason to open"
                else -> "Open $appLabel"
            },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = if (canOpen) Color.White else Color.Black,
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, Color.Black)
                .background(if (canOpen) Color.Black else Color.White)
                .einkTappable(enabled = canOpen, onClick = onOpen)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Not now",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .einkTappable(onClick = onCancel)
        )
    }
}

@Composable
private fun LimitReachedScreen(appLabel: String, usedMillis: Long, limitMinutes: Int, onBack: () -> Unit) {
    GateScaffold {
        Text(
            text = "← Home",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.einkTappable(onClick = onBack)
        )
        Spacer(Modifier.height(24.dp))
        Text(text = "Limit reached", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "$appLabel: ${formatDuration(usedMillis)} used today of your ${formatLimit(limitMinutes)} limit.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "It opens again tomorrow. Change limits in Settings → Distraction.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Back to home",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, Color.Black)
                .einkTappable(onClick = onBack)
        )
    }
}
