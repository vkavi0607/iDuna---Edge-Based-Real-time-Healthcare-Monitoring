package com.iduna.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.iduna.domain.model.AnomalyType
import com.iduna.domain.model.ReportSummary
import com.iduna.domain.model.UserProfile
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

class PdfReportGenerator(
    private val context: Context,
) {
    fun generate(
        profile: UserProfile,
        summary: ReportSummary,
        start: Long,
        end: Long,
    ): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(1080, 1440, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 48f
            isFakeBoldText = true
        }
        val headingPaint = Paint().apply {
            color = Color.parseColor("#3CE3FF")
            textSize = 28f
            isFakeBoldText = true
        }
        val bodyPaint = Paint().apply {
            color = Color.LTGRAY
            textSize = 24f
        }
        val accentPaint = Paint().apply {
            color = Color.parseColor("#FF355D")
            strokeWidth = 6f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        canvas.drawColor(Color.parseColor("#090B12"))
        canvas.drawText("iDuna Heart Health Report", 60f, 90f, titlePaint)
        canvas.drawText("Patient: ${profile.name.ifBlank { "Unknown" }}", 60f, 160f, bodyPaint)
        canvas.drawText("Age: ${profile.age}", 60f, 200f, bodyPaint)
        canvas.drawText("Emergency Contact: ${profile.emergencyContact.ifBlank { "Not set" }}", 60f, 240f, bodyPaint)
        canvas.drawText(
            "Period: ${TimeFormatters.date(start)} to ${TimeFormatters.date(end)}",
            60f,
            280f,
            bodyPaint,
        )

        canvas.drawText("Vitals Summary", 60f, 360f, headingPaint)
        canvas.drawText("Average BPM: ${summary.averageBpm}", 60f, 410f, bodyPaint)
        canvas.drawText("Max BPM: ${summary.maxBpm}", 60f, 450f, bodyPaint)
        canvas.drawText("Min BPM: ${summary.minBpm}", 60f, 490f, bodyPaint)

        canvas.drawText("Anomaly Count", 60f, 580f, headingPaint)
        var anomalyY = 630f
        AnomalyType.entries.filterNot { it == AnomalyType.None || it == AnomalyType.Unknown }.forEach { anomaly ->
            val count = summary.anomalyCounts[anomaly] ?: 0
            canvas.drawText("${anomaly.title}: $count", 60f, anomalyY, bodyPaint)
            anomalyY += 40f
        }

        canvas.drawText("Heart Rate Trend", 60f, 860f, headingPaint)
        drawMiniChart(
            left = 60f,
            top = 900f,
            width = 960f,
            height = 360f,
            summary = summary,
            canvas = canvas,
            paint = accentPaint,
        )

        document.finishPage(page)

        val outputDir = File(context.filesDir, "reports").apply { mkdirs() }
        val reportFile = File(outputDir, TimeFormatters.reportFileName())
        FileOutputStream(reportFile).use { output ->
            document.writeTo(output)
        }
        document.close()
        return reportFile
    }

    fun uriFor(file: File) = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )

    private fun drawMiniChart(
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        summary: ReportSummary,
        canvas: android.graphics.Canvas,
        paint: Paint,
    ) {
        val readings = summary.readings
        if (readings.size < 2) return

        val maxBpm = max(summary.maxBpm, 1)
        val stepX = width / (readings.size - 1)
        var previousX = left
        var previousY = top + height - (readings.first().bpm / maxBpm.toFloat()) * height

        readings.drop(1).forEachIndexed { index, sample ->
            val x = left + stepX * (index + 1)
            val y = top + height - (sample.bpm / maxBpm.toFloat()) * height
            canvas.drawLine(previousX, previousY, x, y, paint)
            previousX = x
            previousY = y
        }
    }
}
