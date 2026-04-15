package com.iduna.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.iduna.R
import com.iduna.domain.model.AlertEvent

class AlertNotifier(
    private val context: Context,
) {
    fun createChannels() {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Health Alerts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Critical heart rate anomaly alerts"
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun sendAlert(alert: AlertEvent, shouldNotify: Boolean, shouldVibrate: Boolean) {
        if (shouldNotify && canPostNotifications()) {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(alert.anomalyType.title)
                .setContentText("${alert.message} at ${TimeFormatters.timeOfDay(alert.timestamp)}")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.notify(alert.id.toInt().coerceAtLeast(1), notification)
        }

        if (shouldVibrate) {
            val vibrator = context.getSystemService(Vibrator::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 300), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 200, 100, 300), -1)
            }
        }
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val CHANNEL_ID = "iduna_alerts"
    }
}
