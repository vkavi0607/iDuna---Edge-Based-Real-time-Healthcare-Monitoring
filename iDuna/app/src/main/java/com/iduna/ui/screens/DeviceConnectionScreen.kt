package com.iduna.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.iduna.R
import com.iduna.domain.model.BleConnectionState
import com.iduna.domain.model.DashboardUiState
import com.iduna.ui.components.HighlightHeader
import com.iduna.ui.components.IdunaCard
import com.iduna.ui.components.ScreenBackground
import com.iduna.ui.components.StatusDot
import com.iduna.ui.theme.AccentBlue
import com.iduna.ui.theme.AccentGreen
import com.iduna.ui.theme.AccentRed

@Composable
fun DeviceConnectionScreen(
    dashboardState: DashboardUiState,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onReconnect: () -> Unit,
) {
    val context = LocalContext.current
    val permissionStates = remember { mutableStateMapOf<String, Boolean>() }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        permissionStates.putAll(result)
    }
    val requiredPermissions = remember { requiredBlePermissions() }

    LaunchedEffect(Unit) {
        permissionStates.putAll(requiredPermissions.associateWith { hasPermission(context, it) })
        permissionLauncher.launch(requiredPermissions)
    }

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top),
        ) {
            HighlightHeader(
                title = "Connect iDuna",
                subtitle = "Allow Bluetooth and location access, then scan and connect to your wearable from inside the app.",
                trailing = {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(92.dp)
                            .background(
                                Brush.radialGradient(listOf(AccentRed.copy(alpha = 0.18f), Color.Transparent)),
                                shape = androidx.compose.foundation.shape.CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_iduna_logo),
                            contentDescription = "iDuna logo",
                            modifier = Modifier.size(62.dp),
                        )
                    }
                },
            )
            IdunaCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Connection Status", style = MaterialTheme.typography.titleLarge)
                StatusDot(label = "BLE status: ${dashboardState.connectionState.name}", color = connectionColor(dashboardState.connectionState))
                Text(
                    text = "Expected device name: iDuna",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = when (dashboardState.connectionState) {
                        BleConnectionState.Connected -> "Wearable connected. Live data will appear on the dashboard."
                        BleConnectionState.Scanning -> "Scanning for nearby iDuna BLE advertisements."
                        BleConnectionState.Connecting -> "Connection in progress. Keep the device close to the phone."
                        else -> "Turn on the wearable and keep it nearby before starting a scan."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            IdunaCard(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Permissions", style = MaterialTheme.typography.titleLarge)
                requiredPermissions.forEach { permission ->
                    PermissionRow(
                        label = permissionLabel(permission),
                        granted = permissionStates[permission] == true || hasPermission(context, permission),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { permissionLauncher.launch(requiredPermissions) }) {
                        Text("Allow Access")
                    }
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                        })
                    }) {
                        Text("App Settings")
                    }
                }
            }

            IdunaCard(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Quick Actions", style = MaterialTheme.typography.titleLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(onClick = onScan, modifier = Modifier.weight(1f)) {
                        Text("Start Scan")
                    }
                    OutlinedButton(onClick = onStopScan, modifier = Modifier.weight(1f)) {
                        Text("Stop Scan")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = onReconnect, modifier = Modifier.weight(1f)) {
                        Text("Reconnect")
                    }
                    OutlinedButton(
                        onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Bluetooth")
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(connectionColor(dashboardState.connectionState), androidx.compose.foundation.shape.CircleShape),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Pair from the app, not from the phone Bluetooth settings page.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        StatusDot(
            label = if (granted) "Allowed" else "Needed",
            color = if (granted) AccentGreen else AccentRed,
        )
    }
}

private fun connectionColor(state: BleConnectionState) = when (state) {
    BleConnectionState.Connected -> AccentGreen
    BleConnectionState.Scanning,
    BleConnectionState.Connecting,
    -> AccentBlue

    else -> AccentRed
}

private fun permissionLabel(permission: String): String = when (permission) {
    Manifest.permission.BLUETOOTH_SCAN -> "Bluetooth scan"
    Manifest.permission.BLUETOOTH_CONNECT -> "Bluetooth connect"
    Manifest.permission.ACCESS_FINE_LOCATION -> "Location access"
    Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
    else -> permission.substringAfterLast('.')
}

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun requiredBlePermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // API 31+ — BLUETOOTH_SCAN declared with neverForLocation, no location permission needed
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        // API <= 30 — classic BLE scanning requires location
        add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()
