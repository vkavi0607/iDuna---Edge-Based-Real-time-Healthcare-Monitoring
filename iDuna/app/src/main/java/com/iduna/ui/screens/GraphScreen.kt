package com.iduna.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iduna.domain.model.DashboardUiState
import com.iduna.ui.components.IdunaCard
import com.iduna.ui.components.LiveLineChart
import com.iduna.ui.components.ScreenBackground
import com.iduna.ui.components.SectionTitle
import com.iduna.ui.components.anomalyColor

@Composable
fun GraphScreen(
    dashboardState: DashboardUiState,
) {
    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IdunaCard(modifier = Modifier.fillMaxWidth()) {
                SectionTitle(
                    title = "Smooth Real-Time Chart",
                    subtitle = "BPM vs time for the current live session",
                )
                LiveLineChart(
                    readings = dashboardState.liveReadings.sortedBy { it.timestamp },
                    modifier = Modifier.fillMaxWidth(),
                    lineColor = anomalyColor(dashboardState.anomalyType),
                )
                Text(
                    text = "Current: ${dashboardState.currentBpm} BPM | Average: ${dashboardState.averageBpm} BPM | Condition: ${dashboardState.anomalyType.title}",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}
