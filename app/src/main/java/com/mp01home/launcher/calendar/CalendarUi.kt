package com.mp01home.launcher.calendar

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.mp01home.launcher.ui.theme.einkTappable

/**
 * READ_CALENDAR prompt: a tappable line that shows the system permission dialog. After two
 * denials Android stops showing the dialog, so the line then opens the app's settings page.
 */
@Composable
fun CalendarPermissionPrompt(
    onResult: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Allow calendar access to see events"
) {
    val context = LocalContext.current
    var denials by rememberSaveable { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) denials++
        onResult(granted)
    }
    val label = if (denials >= 2) "Calendar access denied · open settings" else text
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
            .fillMaxWidth()
            .einkTappable(onClickLabel = "Allow calendar access") {
                if (denials >= 2) openAppSettings(context) else launcher.launch(Manifest.permission.READ_CALENDAR)
            }
    )
}

fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // No settings activity: nothing else to offer.
    }
}

/**
 * CAL-5 block strip: an outlined bar over [window] with busy time filled black and a
 * "now" tick (inverted over busy blocks), with the window's start/end times underneath.
 */
@Composable
fun FreeBusyStrip(
    window: TimeRange,
    segments: List<StripSegment>,
    nowMillis: Long,
    startLabel: String,
    endLabel: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
        ) {
            val total = (window.endMillis - window.startMillis).toFloat()
            if (total <= 0f) return@Canvas
            val w = size.width
            val h = size.height
            fun x(millis: Long) = ((millis - window.startMillis) / total * w).coerceIn(0f, w)
            segments.filter { it.busy }.forEach { seg ->
                val x0 = x(seg.range.startMillis)
                val x1 = x(seg.range.endMillis)
                drawRect(Color.Black, topLeft = Offset(x0, 0f), size = Size((x1 - x0).coerceAtLeast(1f), h))
            }
            val strokePx = 1.dp.toPx()
            drawRect(Color.Black, topLeft = Offset.Zero, size = Size(w, h), style = Stroke(width = strokePx))
            if (nowMillis in window.startMillis until window.endMillis) {
                val nx = x(nowMillis)
                val overBusy = segments.any { it.busy && nowMillis >= it.range.startMillis && nowMillis < it.range.endMillis }
                val tickW = 3.dp.toPx()
                drawRect(
                    color = if (overBusy) Color.White else Color.Black,
                    topLeft = Offset((nx - tickW / 2).coerceIn(0f, w - tickW), 0f),
                    size = Size(tickW, h)
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text(text = startLabel, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text(text = endLabel, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * PLAN-4 entry point: asks for a title and length, then [onSchedule] finds the next free slot
 * and opens the calendar editor prefilled (see [CalendarActions.scheduleIntoFreeSlot]).
 */
@Composable
fun TimeBlockDialog(
    defaultMinutes: Int,
    onSchedule: (title: String, minutes: Int) -> Unit,
    onDismiss: () -> Unit,
    initialTitle: String = ""
) {
    var title by remember { mutableStateOf(initialTitle) }
    var minutes by remember { mutableIntStateOf(defaultMinutes) }
    val submit = {
        if (title.isNotBlank()) {
            onSchedule(title.trim(), minutes)
            onDismiss()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Block time") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Length: ${formatDuration(minutes.toLong())}  (tap to change)",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .einkTappable {
                            minutes = BLOCK_CHOICES.firstOrNull { it > minutes } ?: BLOCK_CHOICES.first()
                        }
                )
                Text(
                    text = "Opens your calendar at the next free slot (08–20, next 7 days).",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = title.isNotBlank()) { Text("Schedule") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private val BLOCK_CHOICES = listOf(15, 30, 45, 60, 90, 120)
