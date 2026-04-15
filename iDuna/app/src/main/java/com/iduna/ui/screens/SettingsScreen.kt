package com.iduna.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iduna.domain.model.UserSettings
import com.iduna.ui.components.IdunaCard
import com.iduna.ui.components.ScreenBackground
import com.iduna.ui.components.SectionTitle

@Composable
fun SettingsScreen(
    settings: UserSettings,
    onSettingsChanged: ((UserSettings) -> UserSettings) -> Unit,
) {
    ScreenBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SectionTitle(
                    title = "App Settings",
                    subtitle = "Tune notifications, vibration, SOS behavior, BLE reconnects, and theme mode",
                )
            }
            item {
                SettingToggleCard(
                    title = "Notifications",
                    checked = settings.notificationsEnabled,
                    onCheckedChange = { checked -> onSettingsChanged { it.copy(notificationsEnabled = checked) } },
                )
            }
            item {
                SettingToggleCard(
                    title = "Vibration",
                    checked = settings.vibrationEnabled,
                    onCheckedChange = { checked -> onSettingsChanged { it.copy(vibrationEnabled = checked) } },
                )
            }
            item {
                SettingToggleCard(
                    title = "Auto SOS",
                    checked = settings.autoSosEnabled,
                    onCheckedChange = { checked -> onSettingsChanged { it.copy(autoSosEnabled = checked) } },
                )
            }
            item {
                SettingToggleCard(
                    title = "BLE Auto Connect",
                    checked = settings.bleAutoConnectEnabled,
                    onCheckedChange = { checked -> onSettingsChanged { it.copy(bleAutoConnectEnabled = checked) } },
                )
            }
            item {
                SettingToggleCard(
                    title = "Dark Mode",
                    checked = settings.darkModeEnabled,
                    onCheckedChange = { checked -> onSettingsChanged { it.copy(darkModeEnabled = checked) } },
                )
            }
        }
    }
}

@Composable
private fun SettingToggleCard(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    IdunaCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
