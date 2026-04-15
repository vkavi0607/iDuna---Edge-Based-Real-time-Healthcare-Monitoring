package com.iduna.domain.model

enum class BleConnectionState {
    BluetoothUnavailable,
    BluetoothDisabled,
    Idle,
    Scanning,
    Connecting,
    Connected,
    Disconnected,
    Error,
}

enum class HistoryRange {
    Daily,
    Weekly,
    Monthly,
}

enum class AnomalyType(
    val code: Int,
    val title: String,
    val detail: String,
) {
    None(0, "Normal", "Stable rhythm"),
    Tachycardia(1, "Tachycardia", "Heart rate is elevated"),
    Bradycardia(2, "Bradycardia", "Heart rate is lower than normal"),
    IrregularRhythm(4, "Irregular rhythm", "Rhythm variation detected"),
    MissedBeat(8, "Missed beat", "Potential pause detected"),
    Unknown(-1, "Unknown anomaly", "Sensor reported an unknown code"),
    ;

    companion object {
        fun fromCode(code: Int): AnomalyType = entries.firstOrNull { it.code == code } ?: when {
            code == 0 -> None
            code and 1 == 1 -> Tachycardia
            code and 2 == 2 -> Bradycardia
            code and 4 == 4 -> IrregularRhythm
            code and 8 == 8 -> MissedBeat
            else -> Unknown
        }
    }
}

data class HeartRateSample(
    val id: Long = 0,
    val timestamp: Long,
    val bpm: Int,
    val averageBpm: Int,
    val anomalyType: AnomalyType,
    val fingerDetected: Boolean,
)

data class AlertEvent(
    val id: Long = 0,
    val timestamp: Long,
    val bpm: Int,
    val anomalyType: AnomalyType,
    val message: String,
)

data class UserProfile(
    val name: String = "",
    val age: Int = 30,
    val emergencyContact: String = "",
)

data class UserSettings(
    val notificationsEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val autoSosEnabled: Boolean = true,
    val bleAutoConnectEnabled: Boolean = true,
    val darkModeEnabled: Boolean = false,
)

data class HistorySummary(
    val averageBpm: Int = 0,
    val maxBpm: Int = 0,
    val minBpm: Int = 0,
    val readings: List<HeartRateSample> = emptyList(),
)

data class ReportSummary(
    val averageBpm: Int,
    val maxBpm: Int,
    val minBpm: Int,
    val anomalyCounts: Map<AnomalyType, Int>,
    val readings: List<HeartRateSample>,
)

data class BleReadingPacket(
    val bpm: Int,
    val averageBpm: Int,
    val anomalyType: AnomalyType,
    val fingerDetected: Boolean,
    val timestamp: Long,
)

data class DashboardUiState(
    val connectionState: BleConnectionState = BleConnectionState.Idle,
    val currentBpm: Int = 0,
    val averageBpm: Int = 0,
    val anomalyType: AnomalyType = AnomalyType.None,
    val fingerDetected: Boolean = false,
    val liveReadings: List<HeartRateSample> = emptyList(),
    val deviceName: String = "iDuna",
)

data class SosUiState(
    val bpm: Int,
    val anomalyType: AnomalyType,
    val remainingSeconds: Int = 10,
    val conditionLabel: String = anomalyType.title,
    val autoTriggerEnabled: Boolean = true,
)
