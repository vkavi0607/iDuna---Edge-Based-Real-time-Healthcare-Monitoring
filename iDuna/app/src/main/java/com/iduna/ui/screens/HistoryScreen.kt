package com.iduna.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iduna.domain.model.AnomalyType
import com.iduna.domain.model.HistoryRange
import com.iduna.domain.model.HistorySummary
import com.iduna.ui.components.IdunaCard
import com.iduna.ui.components.MetricPill
import com.iduna.ui.components.ScreenBackground
import com.iduna.ui.components.SectionTitle
import com.iduna.ui.components.anomalyColor
import com.iduna.util.TimeFormatters

@Composable
fun HistoryScreen(
    historySummary: HistorySummary,
    onRangeSelected: (HistoryRange) -> Unit,
) {
    val selectedRange = remember { mutableStateOf(HistoryRange.Daily) }

    ScreenBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HistoryRange.entries.forEach { range ->
                        FilterChip(
                            selected = selectedRange.value == range,
                            onClick = {
                                selectedRange.value = range
                                onRangeSelected(range)
                            },
                            label = { Text(range.name) },
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetricPill("Average", "${historySummary.averageBpm} BPM", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    MetricPill("Max", "${historySummary.maxBpm} BPM", anomalyColor(AnomalyType.Tachycardia), Modifier.weight(1f))
                    MetricPill("Min", "${historySummary.minBpm} BPM", anomalyColor(AnomalyType.Bradycardia), Modifier.weight(1f))
                }
            }
            item {
                SectionTitle(
                    title = "Saved Readings",
                    subtitle = "Daily, weekly, or monthly heart-rate history",
                )
            }
            items(historySummary.readings) { reading ->
                IdunaCard(modifier = Modifier.fillMaxWidth()) {
                    Text(text = "${reading.bpm} BPM", style = MaterialTheme.typography.titleLarge, color = anomalyColor(reading.anomalyType))
                    Text(text = "Average ${reading.averageBpm} BPM", style = MaterialTheme.typography.bodyLarge)
                    Text(text = TimeFormatters.dateTime(reading.timestamp), style = MaterialTheme.typography.bodyMedium)
                    Text(text = reading.anomalyType.title, style = MaterialTheme.typography.bodyMedium, color = anomalyColor(reading.anomalyType))
                }
            }
        }
    }
}
