package com.mp01home.launcher.ui.home.sections

import android.app.Activity
import android.content.ActivityNotFoundException
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mp01home.launcher.appContainer
import com.mp01home.launcher.capture.CaptureExecutor
import com.mp01home.launcher.capture.CaptureIntents
import com.mp01home.launcher.capture.CaptureTarget
import com.mp01home.launcher.capture.NaturalDateParser
import com.mp01home.launcher.capture.describeParsedWhen
import com.mp01home.launcher.capture.planCapture
import com.mp01home.launcher.data.settings.TimeFormat
import com.mp01home.launcher.ui.theme.EinkMinTouchTarget
import com.mp01home.launcher.ui.theme.einkTappable
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * CAP-1/2/3, KEEP-2 universal quick capture (always at the bottom of home):
 *
 *     [ call Sam fri 3pm           ] Mic Add
 *     (T) K  E    → Fri 2 Oct 15:00
 *
 * Type, then "Add" (or the keyboard's Done key): one tap. Switching target first: two taps.
 * T = Google Tasks default list (date parsed, CAP-2), K = share raw text to Keep (KEEP-2),
 * E = calendar new-event editor with the parsed start time. Swipe down focuses the input.
 */
@Composable
fun CaptureSection(host: HomeSectionHost, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val container = remember(context) { context.appContainer }
    val executor = remember(container) {
        CaptureExecutor(context.applicationContext, container.appLauncher, container.tasksActions, container.tasksAuth)
    }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var text by rememberSaveable { mutableStateOf("") }
    var targetName by rememberSaveable { mutableStateOf(CaptureTarget.TASK.name) }
    val target = CaptureTarget.entries.firstOrNull { it.name == targetName } ?: CaptureTarget.TASK
    var status by remember { mutableStateOf<String?>(null) }

    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    val use24h = settings?.timeFormat != TimeFormat.HOUR_12
    val dayFirst = remember { NaturalDateParser.isDayFirst(Locale.getDefault()) }

    // CORE-6 swipe-down → focus the input and raise the keyboard.
    LaunchedEffect(host.captureRequests) {
        if (host.captureRequests > 0) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    // CAP-3 voice input.
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !spoken.isNullOrBlank()) {
            text = if (text.isBlank()) spoken else "${text.trimEnd()} $spoken"
            status = null
        }
    }

    fun submit() {
        val plan = planCapture(text, target, LocalDateTime.now(), ZoneId.systemDefault(), dayFirst) ?: return
        scope.launch {
            val outcome = executor.execute(plan)
            status = outcome.message
            if (outcome.success) {
                text = ""
                focusManager.clearFocus()
                keyboard?.hide()
            }
        }
    }

    // CAP-2 preview of the parsed date/time (recomputed only as the user types).
    val preview = remember(text, target, use24h, dayFirst) {
        if (target == CaptureTarget.KEEP || text.isBlank()) {
            null
        } else {
            val parsed = NaturalDateParser.parse(text, LocalDateTime.now(), dayFirst)
            describeParsedWhen(parsed, LocalDate.now(), use24h)
        }
    }

    HomeSectionFrame(title = null, modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = text,
                onValueChange = {
                    text = it
                    status = null
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
                cursorBrush = SolidColor(Color.Black),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = EinkMinTouchTarget)
                            .border(1.dp, Color.Black)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (text.isEmpty()) {
                            Text(
                                text = "+ capture…",
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1
                            )
                        }
                        inner()
                    }
                }
            )
            Text(
                text = "Mic",
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.einkTappable(onClickLabel = "Voice input") {
                    try {
                        speechLauncher.launch(CaptureIntents.speech())
                    } catch (e: ActivityNotFoundException) {
                        status = "No speech recogniser installed"
                    }
                }
            )
            Text(
                text = "Add",
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.einkTappable(
                    enabled = text.isNotBlank(),
                    onClickLabel = "Add ${target.label.lowercase()}"
                ) { submit() }
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CaptureTarget.entries.forEach { t ->
                TargetToggle(target = t, selected = t == target) {
                    targetName = t.name
                    status = null
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = status ?: preview?.let { "→ $it" } ?: target.label,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** One of T / K / E; the selected target is shown inverted (EINK-2: no greys). */
@Composable
private fun TargetToggle(target: CaptureTarget, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = target.short,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) Color.White else Color.Black,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .semantics { this.selected = selected }
            .border(1.dp, Color.Black)
            .background(if (selected) Color.Black else Color.White)
            .einkTappable(onClickLabel = target.label, role = Role.RadioButton, onClick = onClick)
    )
}
