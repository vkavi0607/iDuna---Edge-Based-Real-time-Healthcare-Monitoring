package com.iduna.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.iduna.domain.model.AnomalyType
import com.iduna.domain.model.BleConnectionState
import com.iduna.domain.model.DashboardUiState
import com.iduna.ui.components.AnimatedBpmDisplay
import com.iduna.ui.components.HighlightHeader
import com.iduna.ui.components.IdunaCard
import com.iduna.ui.components.LiveLineChart
import com.iduna.ui.components.MetricPill
import com.iduna.ui.components.QuickActionCard
import com.iduna.ui.components.ScreenBackground
import com.iduna.ui.components.SectionTitle
import com.iduna.ui.components.StatusDot
import com.iduna.ui.components.anomalyColor
import com.iduna.ui.theme.AccentBlue
import com.iduna.ui.theme.AccentGreen
import com.iduna.ui.theme.AccentRed

@Composable
fun DashboardScreen(
    dashboardState: DashboardUiState,
    onGraphClick: () -> Unit,
    onReportsClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val breathingTransition = rememberInfiniteTransition(label = "breathingOrb")
    val breathingScale by breathingTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathingScale",
    )

    ScreenBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                HighlightHeader(
                    title = greetingTitle(dashboardState.connectionState),
                    subtitle = "Live cardiac overview with anomaly awareness, average BPM, and streaming status.",
                    modifier = Modifier.padding(top = 12.dp),
                    trailing = {
                        Box(modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd)) {
                            StatusDot(
                                label = dashboardState.connectionState.name,
                                color = when (dashboardState.connectionState) {
                                    BleConnectionState.Connected -> AccentGreen
                                    BleConnectionState.Scanning, BleConnectionState.Connecting -> AccentBlue
                                    else -> AccentRed
                                },
                            )
                        }
                    },
                )
            }
            item {
                IdunaCard(modifier = Modifier.fillMaxWidth()) {
                    AnimatedBpmDisplay(
                        bpm = dashboardState.currentBpm,
                        averageBpm = dashboardState.averageBpm,
                        anomalyType = dashboardState.anomalyType,
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetricPill(
                        label = "Average",
                        value = "${dashboardState.averageBpm} BPM",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    MetricPill(
                        label = "Condition",
                        value = dashboardState.anomalyType.title,
                        color = anomalyColor(dashboardState.anomalyType),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                IdunaCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle(title = "Live Status", subtitle = "Connection, finger placement, and anomaly summary")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatusDot(
                                label = "BLE ${dashboardState.connectionState.name}",
                                color = when (dashboardState.connectionState) {
                                    BleConnectionState.Connected -> AccentGreen
                                    BleConnectionState.Scanning, BleConnectionState.Connecting -> AccentBlue
                                    else -> AccentRed
                                },
                            )
                            StatusDot(
                                label = if (dashboardState.fingerDetected) "Finger detected" else "Finger not detected",
                                color = if (dashboardState.fingerDetected) AccentGreen else AccentRed,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Anomaly",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = dashboardState.anomalyType.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = anomalyColor(dashboardState.anomalyType),
                            )
                        }
                    }
                    Text(
                        text = anomalyDescription(dashboardState.anomalyType),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            item {
                IdunaCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle(
                        title = "Last 60 Seconds",
                        subtitle = "Live BPM feed from the wearable",
                    )
                    LiveLineChart(
                        readings = dashboardState.liveReadings.sortedBy { it.timestamp },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                IdunaCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle(
                        title = "Breathing Focus",
                        subtitle = "A calm rhythm cue while readings stream in",
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Outer glow ring
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF3CE3FF).copy(alpha = 0.18f),
                                        Color.Transparent,
                                    ),
                                ),
                                radius = size.minDimension * 0.46f * breathingScale,
                            )
                            // Inner orb
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF3CE3FF).copy(alpha = 0.55f),
                                        Color(0xFF3CE3FF).copy(alpha = 0.08f),
                                    ),
                                ),
                                radius = size.minDimension * 0.30f * breathingScale,
                            )
                        }
                        androidx.compose.material3.Text(
                            text = if (breathingScale > 0.97f) "Exhale slowly…" else "Inhale…",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF3CE3FF).copy(alpha = 0.85f),
                        )
                    }
                    Text(
                        text = "Inhale 4s · Hold 1s · Exhale 6s. Focus on the orb expanding and settling.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            item {
                SectionTitle(title = "Shortcuts")
            }
            item {
                QuickActionCard(
                    title = "Open Live Graph",
                    subtitle = "See the extended real-time trend view",
                    icon = Icons.Outlined.BarChart,
                    onClick = onGraphClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                QuickActionCard(
                    title = "Build Report",
                    subtitle = "Generate a downloadable PDF summary",
                    icon = Icons.Outlined.Description,
                    onClick = onReportsClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                QuickActionCard(
                    title = "Adjust Settings",
                    subtitle = "Tune notifications, BLE, and SOS behavior",
                    icon = Icons.Outlined.Settings,
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 18.dp),
                )
            }
        }
    }
}

private fun greetingTitle(state: BleConnectionState): String = when (state) {
    BleConnectionState.Connected -> "Patient Dashboard"
    BleConnectionState.Scanning -> "Searching for iDuna"
    BleConnectionState.Connecting -> "Connecting to wearable"
    else -> "Heart Monitoring"
}

private fun anomalyDescription(anomalyType: AnomalyType): String = when (anomalyType) {
    AnomalyType.Tachycardia -> "High heart rate detected. Monitor exertion and prepare for emergency escalation if it persists."
    AnomalyType.Bradycardia -> "Low heart rate detected. Stay seated and observe symptoms closely."
    AnomalyType.IrregularRhythm -> "Irregular rhythm detected. This may indicate skipped or uneven beats."
    AnomalyType.MissedBeat -> "Possible missed beat or pause detected. Keep the wearable steady."
    AnomalyType.Unknown -> "The device reported an unknown anomaly code."
    AnomalyType.None -> "Heart rhythm appears stable."
}
