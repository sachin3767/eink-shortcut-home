package com.mp01home.launcher.ui.settings

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mp01home.launcher.data.settings.DateFormat
import com.mp01home.launcher.data.settings.HorizontalAlignment
import com.mp01home.launcher.data.settings.TimeFormat
import com.mp01home.launcher.data.settings.WeatherUnits

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current

    BackHandler(onBack = onBack)

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { /* result ignored: settings.homeApps already reflects the real default via the system */ }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        ClickableLabel(text = "← Home", onClick = onBack, modifier = Modifier.padding(bottom = 16.dp))

        SectionTitle("Apps")
        CycleRow(
            label = "Alignment",
            value = settings.appsAlignment.name,
            onClick = {
                val next = if (settings.appsAlignment == HorizontalAlignment.LEFT) {
                    HorizontalAlignment.RIGHT
                } else {
                    HorizontalAlignment.LEFT
                }
                viewModel.setAppsAlignment(next)
            }
        )

        SectionTitle("Timeline")
        ToggleRow(
            label = "Show timeline rail",
            checked = settings.timelineEnabled,
            onCheckedChange = viewModel::setTimelineEnabled
        )
        Text(
            text = "Rail shows on the opposite side of the apps (${settings.timelineAlignment.name.lowercase()}).",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        SectionTitle("Time")
        ToggleRow(label = "Show time", checked = settings.showTime, onCheckedChange = viewModel::setShowTime)
        CycleRow(
            label = "Format",
            value = if (settings.timeFormat == TimeFormat.HOUR_24) "24-hour" else "12-hour",
            onClick = {
                val next = if (settings.timeFormat == TimeFormat.HOUR_24) TimeFormat.HOUR_12 else TimeFormat.HOUR_24
                viewModel.setTimeFormat(next)
            }
        )

        SectionTitle("Date")
        ToggleRow(label = "Show date", checked = settings.showDate, onCheckedChange = viewModel::setShowDate)
        CycleRow(
            label = "Format",
            value = settings.dateFormat.name,
            onClick = {
                val values = DateFormat.entries
                val next = values[(values.indexOf(settings.dateFormat) + 1) % values.size]
                viewModel.setDateFormat(next)
            }
        )

        SectionTitle("Weather")
        ToggleRow(label = "Show weather", checked = settings.showWeather, onCheckedChange = viewModel::setShowWeather)
        OutlinedTextField(
            value = settings.weatherCity,
            onValueChange = viewModel::setWeatherCity,
            label = { Text("City") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        )
        OutlinedTextField(
            value = settings.weatherApiKey,
            onValueChange = viewModel::setWeatherApiKey,
            label = { Text("OpenWeatherMap API key") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        )
        CycleRow(
            label = "Units",
            value = settings.weatherUnits.symbol,
            onClick = {
                val next = if (settings.weatherUnits == WeatherUnits.CELSIUS) {
                    WeatherUnits.FAHRENHEIT
                } else {
                    WeatherUnits.CELSIUS
                }
                viewModel.setWeatherUnits(next)
            }
        )

        SectionTitle("Launcher")
        ClickableLabel(
            text = "Set as Default Launcher",
            onClick = {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val roleManager = context.getSystemService(RoleManager::class.java)
                    if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                        roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    } else {
                        Intent(AndroidSettings.ACTION_HOME_SETTINGS)
                    }
                } else {
                    Intent(AndroidSettings.ACTION_HOME_SETTINGS)
                }
                roleLauncher.launch(intent)
            },
            modifier = Modifier.padding(vertical = 12.dp)
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
    HorizontalDivider(color = Color.Black)
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
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

@Composable
private fun CycleRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = {}
            ),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ClickableLabel(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
            onLongClick = {}
        )
    )
}
