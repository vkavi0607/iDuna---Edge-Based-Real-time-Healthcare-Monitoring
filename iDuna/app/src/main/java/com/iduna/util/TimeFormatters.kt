package com.iduna.util

import com.iduna.domain.model.HeartRateSample
import com.iduna.domain.model.HistoryRange
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

object TimeFormatters {
    private val zoneId: ZoneId
        get() = ZoneId.systemDefault()

    private val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")
    private val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
    private val fileFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")

    fun startFor(range: HistoryRange): Long {
        val now = LocalDateTime.now()
        val start = when (range) {
            HistoryRange.Daily -> now.toLocalDate().atStartOfDay()
            HistoryRange.Weekly -> now.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                .toLocalDate()
                .atStartOfDay()

            HistoryRange.Monthly -> now.toLocalDate().withDayOfMonth(1).atStartOfDay()
        }
        return start.atZone(zoneId).toInstant().toEpochMilli()
    }

    fun endNow(): Long = System.currentTimeMillis()

    fun dayRange(date: LocalDate): Pair<Long, Long> {
        val start = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
        return start to end
    }

    fun timeOfDay(timestamp: Long): String =
        Instant.ofEpochMilli(timestamp).atZone(zoneId).format(timeFormatter)

    fun date(timestamp: Long): String =
        Instant.ofEpochMilli(timestamp).atZone(zoneId).format(dateFormatter)

    fun dateTime(timestamp: Long): String =
        "${date(timestamp)} ${timeOfDay(timestamp)}"

    fun reportFileName(): String =
        "iduna_report_${LocalDateTime.now().format(fileFormatter)}.pdf"

    fun chartLabels(readings: List<HeartRateSample>): List<String> =
        readings.map { timeOfDay(it.timestamp) }

    fun nowLocalTime(): LocalTime = LocalTime.now(zoneId)
}
