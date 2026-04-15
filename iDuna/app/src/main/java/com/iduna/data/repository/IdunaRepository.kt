package com.iduna.data.repository

import com.iduna.data.ble.BleHeartRateManager
import com.iduna.data.local.dao.AlertEventDao
import com.iduna.data.local.dao.HeartRateDao
import com.iduna.data.local.dao.UserProfileDao
import com.iduna.data.local.dao.UserSettingsDao
import com.iduna.data.local.entity.AlertEventEntity
import com.iduna.data.local.entity.HeartRateReadingEntity
import com.iduna.data.local.entity.UserProfileEntity
import com.iduna.data.local.entity.UserSettingsEntity
import com.iduna.domain.model.AlertEvent
import com.iduna.domain.model.AnomalyType
import com.iduna.domain.model.BleConnectionState
import com.iduna.domain.model.BleReadingPacket
import com.iduna.domain.model.DashboardUiState
import com.iduna.domain.model.HeartRateSample
import com.iduna.domain.model.HistoryRange
import com.iduna.domain.model.HistorySummary
import com.iduna.domain.model.ReportSummary
import com.iduna.domain.model.SosUiState
import com.iduna.domain.model.UserProfile
import com.iduna.domain.model.UserSettings
import com.iduna.util.AlertNotifier
import com.iduna.util.PdfReportGenerator
import com.iduna.util.TimeFormatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class IdunaRepository(
    private val bleHeartRateManager: BleHeartRateManager,
    private val heartRateDao: HeartRateDao,
    private val alertEventDao: AlertEventDao,
    private val userProfileDao: UserProfileDao,
    private val userSettingsDao: UserSettingsDao,
    private val alertNotifier: AlertNotifier,
    private val pdfReportGenerator: PdfReportGenerator,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _liveReadings = MutableStateFlow<List<HeartRateSample>>(emptyList())
    private val _sosState = MutableStateFlow<SosUiState?>(null)
    private val _currentReading = MutableStateFlow<HeartRateSample?>(null)
    private val _selectedHistoryRange = MutableStateFlow(HistoryRange.Daily)
    private val _sosSuppressed = MutableStateFlow(false)
    private var activeAnomalyType: AnomalyType = AnomalyType.None
    private var anomalyStartTimestamp: Long? = null

    val connectionState: StateFlow<BleConnectionState> = bleHeartRateManager.connectionState
    val profile: StateFlow<UserProfile> = userProfileDao.observeProfile()
        .map { entity -> entity?.toDomain() ?: UserProfile() }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), UserProfile())

    val settings: StateFlow<UserSettings> = userSettingsDao.observeSettings()
        .map { entity -> entity?.toDomain() ?: UserSettings() }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    val alerts: Flow<List<AlertEvent>> = alertEventDao.observeAlerts()
        .map { entities -> entities.map { it.toDomain() } }

    val dashboardState: StateFlow<DashboardUiState> = combine(
        connectionState,
        _currentReading,
        _liveReadings,
    ) { bleState, current, live ->
        DashboardUiState(
            connectionState = bleState,
            currentBpm = current?.bpm ?: 0,
            averageBpm = current?.averageBpm ?: 0,
            anomalyType = current?.anomalyType ?: AnomalyType.None,
            fingerDetected = current?.fingerDetected ?: false,
            liveReadings = live,
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    val historySummary: StateFlow<HistorySummary> = _selectedHistoryRange
        .flatMapLatest { range ->
            heartRateDao.observeReadingsFrom(TimeFormatters.startFor(range))
                .map { entities -> entities.map { it.toDomain() }.toSummary() }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), HistorySummary())

    val sosState: StateFlow<SosUiState?> = _sosState.asStateFlow()

    init {
        scope.launch { ensureDefaults() }
        scope.launch {
            settings.collect { userSettings ->
                bleHeartRateManager.setAutoReconnect(userSettings.bleAutoConnectEnabled)
            }
        }
        scope.launch {
            bleHeartRateManager.latestPacket
                .filterNotNull()
                .collect(::handleIncomingPacket)
        }
    }

    suspend fun generatePdfReport(start: Long, end: Long): File {
        val summary = buildReportSummary(start, end)
        return pdfReportGenerator.generate(
            profile = profile.value,
            summary = summary,
            start = start,
            end = end,
        )
    }

    fun setHistoryRange(range: HistoryRange) {
        _selectedHistoryRange.value = range
    }

    fun startScan() = bleHeartRateManager.startScan()

    fun stopScan() = bleHeartRateManager.stopScan()

    fun reconnect() = bleHeartRateManager.connectToLastDevice()

    fun disconnect() = bleHeartRateManager.disconnect()

    suspend fun updateProfile(profile: UserProfile) {
        userProfileDao.upsertProfile(profile.toEntity())
    }

    suspend fun updateSettings(settings: UserSettings) {
        userSettingsDao.upsertSettings(settings.toEntity())
    }

    fun dismissSos() {
        _sosSuppressed.value = true
        _sosState.value = null
    }

    suspend fun buildReportSummary(start: Long, end: Long): ReportSummary {
        val readings = heartRateDao.getReadingsBetween(start, end).map { it.toDomain() }
        val alerts = alertEventDao.getAlertsBetween(start, end)
        return ReportSummary(
            averageBpm = readings.map { it.bpm }.average().toInt(),
            maxBpm = readings.maxOfOrNull { it.bpm } ?: 0,
            minBpm = readings.minOfOrNull { it.bpm } ?: 0,
            anomalyCounts = alerts.groupingBy { AnomalyType.fromCode(it.anomalyCode) }.eachCount(),
            readings = readings,
        )
    }

    private suspend fun ensureDefaults() {
        if (userProfileDao.getProfile() == null) {
            userProfileDao.upsertProfile(UserProfile().toEntity())
        }
        if (userSettingsDao.getSettings() == null) {
            userSettingsDao.upsertSettings(UserSettings().toEntity())
        }
    }

    private suspend fun handleIncomingPacket(packet: BleReadingPacket) {
        val sample = HeartRateSample(
            timestamp = packet.timestamp,
            bpm = packet.bpm,
            averageBpm = packet.averageBpm,
            anomalyType = packet.anomalyType,
            fingerDetected = packet.fingerDetected,
        )
        _currentReading.value = sample
        _liveReadings.value = (_liveReadings.value + sample)
            .filter { packet.timestamp - it.timestamp <= 60_000L }
            .takeLast(180)

        heartRateDao.insertReading(
            HeartRateReadingEntity(
                timestamp = sample.timestamp,
                bpm = sample.bpm,
                averageBpm = sample.averageBpm,
                anomalyCode = sample.anomalyType.code,
                fingerDetected = sample.fingerDetected,
            ),
        )

        if (sample.anomalyType != AnomalyType.None && sample.anomalyType != activeAnomalyType) {
            val alertEntity = AlertEventEntity(
                timestamp = sample.timestamp,
                bpm = sample.bpm,
                anomalyCode = sample.anomalyType.code,
                message = sample.anomalyType.detail,
            )
            alertEventDao.insertAlert(alertEntity)
            alertNotifier.sendAlert(
                alert = alertEntity.toDomain(),
                shouldNotify = settings.value.notificationsEnabled,
                shouldVibrate = settings.value.vibrationEnabled,
            )
        }

        updateSosState(sample)
    }

    private fun updateSosState(sample: HeartRateSample) {
        if (sample.anomalyType == AnomalyType.None) {
            activeAnomalyType = AnomalyType.None
            anomalyStartTimestamp = null
            _sosState.value = null
            _sosSuppressed.value = false
            return
        }

        if (sample.anomalyType != activeAnomalyType) {
            activeAnomalyType = sample.anomalyType
            anomalyStartTimestamp = sample.timestamp
            return
        }

        val startedAt = anomalyStartTimestamp ?: sample.timestamp
        val persistedLongEnough = sample.timestamp - startedAt >= 10_000L
        if (
            persistedLongEnough &&
            !_sosSuppressed.value &&
            settings.value.autoSosEnabled
        ) {
            _sosState.value = SosUiState(
                bpm = sample.bpm,
                anomalyType = sample.anomalyType,
                autoTriggerEnabled = settings.value.autoSosEnabled,
            )
        }
    }

    private fun List<HeartRateSample>.toSummary(): HistorySummary = HistorySummary(
        averageBpm = map { it.bpm }.average().toInt(),
        maxBpm = maxOfOrNull { it.bpm } ?: 0,
        minBpm = minOfOrNull { it.bpm } ?: 0,
        readings = this.sortedByDescending { it.timestamp },
    )

    private fun HeartRateReadingEntity.toDomain(): HeartRateSample = HeartRateSample(
        id = id,
        timestamp = timestamp,
        bpm = bpm,
        averageBpm = averageBpm,
        anomalyType = AnomalyType.fromCode(anomalyCode),
        fingerDetected = fingerDetected,
    )

    private fun AlertEventEntity.toDomain(): AlertEvent = AlertEvent(
        id = id,
        timestamp = timestamp,
        bpm = bpm,
        anomalyType = AnomalyType.fromCode(anomalyCode),
        message = message,
    )

    private fun UserProfileEntity.toDomain(): UserProfile = UserProfile(
        name = name,
        age = age,
        emergencyContact = emergencyContact,
    )

    private fun UserSettingsEntity.toDomain(): UserSettings = UserSettings(
        notificationsEnabled = notificationsEnabled,
        vibrationEnabled = vibrationEnabled,
        autoSosEnabled = autoSosEnabled,
        bleAutoConnectEnabled = bleAutoConnectEnabled,
        darkModeEnabled = darkModeEnabled,
    )

    private fun UserProfile.toEntity(): UserProfileEntity = UserProfileEntity(
        name = name,
        age = age,
        emergencyContact = emergencyContact,
    )

    private fun UserSettings.toEntity(): UserSettingsEntity = UserSettingsEntity(
        notificationsEnabled = notificationsEnabled,
        vibrationEnabled = vibrationEnabled,
        autoSosEnabled = autoSosEnabled,
        bleAutoConnectEnabled = bleAutoConnectEnabled,
        darkModeEnabled = darkModeEnabled,
    )
}
