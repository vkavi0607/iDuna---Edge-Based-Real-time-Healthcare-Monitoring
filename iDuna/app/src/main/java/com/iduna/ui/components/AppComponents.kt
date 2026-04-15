package com.iduna.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iduna.domain.model.AnomalyType
import com.iduna.domain.model.HeartRateSample
import com.iduna.ui.theme.AccentBlue
import com.iduna.ui.theme.AccentCyan
import com.iduna.ui.theme.AccentGreen
import com.iduna.ui.theme.AccentRed
import com.iduna.ui.theme.OutlineSoft
import com.iduna.ui.theme.SurfaceRaised
import com.iduna.ui.theme.SurfaceVariant
import com.iduna.ui.theme.TextSecondary
import kotlin.math.max

@Composable
fun ScreenBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        content()
    }
}

@Composable
fun IdunaCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), RoundedCornerShape(28.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
fun SectionTitle(
    title: String,
    subtitle: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun MetricPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(22.dp))
            .border(1.dp, color.copy(alpha = 0.20f), RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, color = color, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun StatusDot(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape),
        )
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector = Icons.Outlined.Bolt,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IdunaCard(
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = "Open",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun HighlightHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        Color.White.copy(alpha = 0.85f),
                    ),
                ),
                RoundedCornerShape(32.dp),
            )
            .border(1.dp, OutlineSoft, RoundedCornerShape(32.dp))
            .padding(22.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.68f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(text = subtitle, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        }
        trailing()
    }
}

@Composable
fun AnimatedBpmDisplay(
    bpm: Int,
    averageBpm: Int,
    anomalyType: AnomalyType,
    modifier: Modifier = Modifier,
) {
    val bpmColor = anomalyColor(anomalyType)
    val pulse by animateFloatAsState(
        targetValue = if (bpm == 0) 0.92f else 1f,
        animationSpec = tween(500),
        label = "bpmScale",
    )
    val backgroundPulse = rememberInfiniteTransition(label = "backgroundPulse")
    val orbScale by backgroundPulse.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orbScale",
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(260.dp)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(bpmColor.copy(alpha = 0.28f), Color.Transparent),
                ),
                radius = size.minDimension * 0.52f * orbScale,
            )
            drawCircle(
                color = bpmColor.copy(alpha = 0.22f),
                radius = size.minDimension * 0.41f * pulse,
                style = Stroke(width = 8.dp.toPx()),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(targetState = bpm, label = "bpmAnimatedContent") { value ->
                Text(
                    text = value.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.displayLarge,
                    color = bpmColor,
                )
            }
            Text(
                text = "BPM",
                style = MaterialTheme.typography.titleLarge,
                color = TextSecondary,
            )
            Text(
                text = "Average $averageBpm",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun LiveLineChart(
    readings: List<HeartRateSample>,
    modifier: Modifier = Modifier,
    lineColor: Color = AccentCyan,
    strokeWidth: Dp = 3.dp,
) {
    if (readings.isEmpty()) {
        Box(
            modifier = modifier
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Waiting for live data",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }

    Canvas(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f), RoundedCornerShape(20.dp))
            .padding(16.dp)
            .height(220.dp)
            .fillMaxWidth(),
    ) {
        val minBpm = readings.minOf { it.bpm }.toFloat()
        val maxBpm = max(readings.maxOf { it.bpm }, 1).toFloat()
        val range = (maxBpm - minBpm).takeIf { it > 0f } ?: 1f
        val stepX = size.width / max(readings.lastIndex, 1)

        repeat(4) { index ->
            val y = size.height / 4 * index
            drawLine(
                color = Color.White.copy(alpha = 0.08f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        val path = Path()
        readings.forEachIndexed { index, sample ->
            val x = stepX * index
            val ratio = (sample.bpm - minBpm) / range
            val y = size.height - (ratio * size.height)
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            brush = Brush.linearGradient(listOf(lineColor, lineColor.copy(alpha = 0.4f))),
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round),
        )
    }
}

@Composable
fun FlashingAlertSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "alertSurface")
    val alpha by transition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alertAlpha",
    )

    Box(
        modifier = modifier.background(
            Brush.radialGradient(
                colors = listOf(AccentRed.copy(alpha = alpha * 0.38f), Color(0xFF2B0810)),
            ),
        ),
    ) {
        content()
    }
}

@Composable
fun CountdownRing(
    remainingSeconds: Int,
    modifier: Modifier = Modifier,
) {
    val progress = remainingSeconds / 10f
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(180.dp)) {
            drawArc(
                color = Color.White.copy(alpha = 0.16f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round),
                size = Size(size.width, size.height),
            )
            drawArc(
                color = AccentRed,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round),
                size = Size(size.width, size.height),
            )
        }
        Text(
            text = remainingSeconds.toString(),
            style = MaterialTheme.typography.displayMedium,
            color = Color.White,
        )
    }
}

fun anomalyColor(anomalyType: AnomalyType): Color = when (anomalyType) {
    AnomalyType.Tachycardia,
    AnomalyType.IrregularRhythm,
    AnomalyType.MissedBeat,
    AnomalyType.Unknown,
    -> AccentRed

    AnomalyType.Bradycardia -> AccentBlue
    AnomalyType.None -> AccentGreen
}
