package com.iduna.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iduna.domain.model.AlertEvent
import com.iduna.ui.components.IdunaCard
import com.iduna.ui.components.ScreenBackground
import com.iduna.ui.components.SectionTitle
import com.iduna.ui.components.anomalyColor
import com.iduna.util.TimeFormatters

@Composable
fun AlertsScreen(
    alerts: List<AlertEvent>,
) {
    ScreenBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SectionTitle(
                    title = "Anomaly Timeline",
                    subtitle = "Every anomaly event is logged with its timestamp and detected BPM",
                )
            }
            items(alerts) { alert ->
                IdunaCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = alert.anomalyType.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = anomalyColor(alert.anomalyType),
                    )
                    Text(text = "${alert.bpm} BPM", style = MaterialTheme.typography.headlineSmall)
                    Text(text = alert.message, style = MaterialTheme.typography.bodyLarge)
                    Text(text = TimeFormatters.dateTime(alert.timestamp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
