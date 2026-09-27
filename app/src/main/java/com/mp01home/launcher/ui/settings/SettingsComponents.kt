package com.mp01home.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mp01home.launcher.ui.theme.EinkMinTouchTarget
import com.mp01home.launcher.ui.theme.einkTappable

/*
 * Building blocks for settings rows. Public so feature settings slots
 * (ui/settings/sections/) look consistent. All rows are ≥ 56dp tall (EINK-3).
 */

@Composable
fun SettingsSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp)
    )
    HorizontalDivider(color = Color.Black)
}

/** Small explanatory text under a row. */
@Composable
fun SettingsNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier.padding(bottom = 8.dp)
    )
}

/** Whole row toggles; the Switch is display-only so there's a single, large target. */
@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .einkTappable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color.Black,
                uncheckedThumbColor = Color.Black,
                uncheckedTrackColor = Color.White,
                uncheckedBorderColor = Color.Black
            )
        )
    }
}

/** "Label ........ value"; tapping advances to the next value. */
@Composable
fun CycleRow(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .einkTappable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

/** "Label   [−] value [+]" for bounded numbers. */
@Composable
fun StepperRow(
    label: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    decrementEnabled: Boolean = true,
    incrementEnabled: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = "−",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            color = if (decrementEnabled) Color.Black else Color.Gray,
            modifier = Modifier.einkTappable(enabled = decrementEnabled, onClickLabel = "Decrease", onClick = onDecrement)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            text = "+",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            color = if (incrementEnabled) Color.Black else Color.Gray,
            modifier = Modifier.einkTappable(enabled = incrementEnabled, onClickLabel = "Increase", onClick = onIncrement)
        )
    }
}

/** A tappable line of text (actions such as "Grant notification access"). */
@Composable
fun ClickableLabel(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier
            .fillMaxWidth()
            .einkTappable(onClick = onClick)
    )
}

/** Minimum row height, exposed for custom rows in feature slots. */
val SettingsRowMinHeight = EinkMinTouchTarget
