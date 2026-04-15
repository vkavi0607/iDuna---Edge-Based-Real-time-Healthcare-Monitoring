package com.iduna.ui.screens

import android.app.DatePickerDialog
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iduna.IdunaViewModel
import com.iduna.domain.model.AnomalyType
import com.iduna.ui.components.IdunaCard
import com.iduna.ui.components.LiveLineChart
import com.iduna.ui.components.ScreenBackground
import com.iduna.ui.components.SectionTitle
import java.time.Instant
import java.time.ZoneId

@Composable
fun ReportsScreen(
    viewModel: IdunaViewModel,
) {
    val context = LocalContext.current
    val reportSummary by viewModel.reportSummary.collectAsStateWithLifecycle()
    val reportFile by viewModel.reportFile.collectAsStateWithLifecycle()
    val reportRange by viewModel.reportRange.collectAsStateWithLifecycle()
    val selectedDate = Instant.ofEpochMilli(reportRange.first).atZone(ZoneId.systemDefault()).toLocalDate()

    LaunchedEffect(Unit) {
        viewModel.refreshReportSummary()
    }

    ScreenBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                IdunaCard(modifier = Modifier.fillMaxWidth()) {
                    SectionTitle(
                        title = "Report Builder",
                        subtitle = "Choose a day, preview key metrics, then export a PDF summary",
                    )
                    Text(text = "Selected date: $selectedDate", style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = {
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    viewModel.updateReportDate(java.time.LocalDate.of(year, month + 1, dayOfMonth))
                                },
                                selectedDate.year,
                                selectedDate.monthValue - 1,
                                selectedDate.dayOfMonth,
                            ).show()
                        }) {
                            Text("Select Date")
                        }
                        Button(onClick = viewModel::generateReportPdf) {
                            Text("Generate PDF")
                        }
                    }
                }
            }
            reportSummary?.let { summary ->
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SummaryBlock("Avg", summary.averageBpm.toString(), Modifier.weight(1f))
                        SummaryBlock("Max", summary.maxBpm.toString(), Modifier.weight(1f))
                        SummaryBlock("Min", summary.minBpm.toString(), Modifier.weight(1f))
                    }
                }
                item {
                    IdunaCard(modifier = Modifier.fillMaxWidth()) {
                        SectionTitle(
                            title = "Chart Preview",
                            subtitle = "Preview of the readings that will appear in the report",
                        )
                        LiveLineChart(
                            readings = summary.readings,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    IdunaCard(modifier = Modifier.fillMaxWidth()) {
                        SectionTitle(title = "Anomaly Counts")
                        AnomalyType.entries
                            .filterNot { it == AnomalyType.None || it == AnomalyType.Unknown }
                            .forEach { anomaly ->
                                Text(
                                    text = "${anomaly.title}: ${summary.anomalyCounts[anomaly] ?: 0}",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                    }
                }
            }
            reportFile?.let { file ->
                item {
                    IdunaCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Report ready",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(onClick = {
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file,
                            )
                            startActivity(
                                context,
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                },
                                null,
                            )
                        }) {
                            Text("Share PDF")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    IdunaCard(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(text = value, style = MaterialTheme.typography.headlineSmall)
    }
}
