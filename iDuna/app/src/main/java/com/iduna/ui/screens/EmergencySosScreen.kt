package com.iduna.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SmsManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.iduna.domain.model.SosUiState
import com.iduna.domain.model.UserProfile
import com.iduna.ui.components.CountdownRing
import com.iduna.ui.components.FlashingAlertSurface
import com.iduna.ui.theme.AccentGreen
import com.iduna.ui.theme.AccentRed
import kotlinx.coroutines.delay

@Composable
fun EmergencySosScreen(
    sosState: SosUiState,
    profile: UserProfile,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val remainingSeconds = remember(sosState.bpm, sosState.anomalyType) { mutableIntStateOf(10) }
    // Track whether the auto-call has already fired to prevent double-trigger
    val autoCallFired = remember(sosState.bpm, sosState.anomalyType) { mutableStateOf(false) }

    // Launcher for CALL_PHONE permission — fires and then dismisses SOS after grant/deny
    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        launchCall(context, profile.emergencyContact, directCall = granted)
        // Only cancel SOS after the call intent is fired
        onCancel()
    }

    // Launcher for SEND_SMS permission — fires SMS silently without opening an SMS app
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            sendSilentSms(context, profile.emergencyContact, buildSmsBody(sosState))
        } else {
            // Fall back to SMS app (user presses Send themselves)
            openSmsApp(context, profile.emergencyContact, buildSmsBody(sosState))
        }
    }

    fun performCall() {
        if (profile.emergencyContact.isBlank()) return
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CALL_PHONE,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            launchCall(context, profile.emergencyContact, directCall = true)
            onCancel()
        } else {
            // Permission result will call onCancel after the intent fires
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    fun performSms() {
        if (profile.emergencyContact.isBlank()) return
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.SEND_SMS,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            sendSilentSms(context, profile.emergencyContact, buildSmsBody(sosState))
        } else {
            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
        }
    }

    // Countdown then auto-act
    LaunchedEffect(sosState.bpm, sosState.anomalyType) {
        remainingSeconds.intValue = 10
        while (remainingSeconds.intValue > 0) {
            delay(1_000)
            remainingSeconds.intValue -= 1
        }
        // Guard: fire only once per SOS event
        if (sosState.autoTriggerEnabled && !autoCallFired.value) {
            autoCallFired.value = true
            // Send silent SMS first (no app switch), then call
            performSms()
            delay(500)
            performCall()
        }
    }

    FlashingAlertSurface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "🚨 Emergency SOS",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${sosState.bpm} BPM  ·  ${sosState.conditionLabel}",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White.copy(alpha = 0.85f),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Calling ${profile.emergencyContact.ifBlank { "— no contact set —" }} in…",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.65f),
            )
            if (profile.emergencyContact.isBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "⚠ Set an emergency contact in Profile first!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AccentRed,
                    fontWeight = FontWeight.Bold,
                )
            }
            CountdownRing(
                remainingSeconds = remainingSeconds.intValue,
                modifier = Modifier.padding(vertical = 24.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { performCall() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                ) {
                    Text("📞  Call Now", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { performSms() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                ) {
                    Text("💬  Send SMS", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { onCancel() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("I'm okay — Cancel SOS")
            }
        }
    }
}

private fun buildSmsBody(sosState: SosUiState): String =
    "iDuna Emergency Alert: ${sosState.conditionLabel} detected at ${sosState.bpm} BPM. " +
        "Please check on this patient immediately."

@Suppress("DEPRECATION")
private fun sendSilentSms(context: Context, phoneNumber: String, body: String) {
    if (phoneNumber.isBlank()) return
    try {
        val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }
        smsManager?.sendTextMessage(phoneNumber, null, body, null, null)
    } catch (e: Exception) {
        // If programmatic SMS fails (e.g. no SIM), fall back to the SMS app
        openSmsApp(context, phoneNumber, body)
    }
}

private fun openSmsApp(context: Context, phoneNumber: String, body: String) {
    if (phoneNumber.isBlank()) return
    context.startActivity(
        Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$phoneNumber")
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
}

private fun launchCall(
    context: Context,
    phoneNumber: String,
    directCall: Boolean,
) {
    if (phoneNumber.isBlank()) return
    val action = if (directCall) Intent.ACTION_CALL else Intent.ACTION_DIAL
    context.startActivity(
        Intent(action).apply {
            data = Uri.parse("tel:$phoneNumber")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
}
