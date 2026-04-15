package com.iduna

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.iduna.data.repository.IdunaRepository
import com.iduna.domain.model.AlertEvent
import com.iduna.domain.model.DashboardUiState
import com.iduna.domain.model.HistoryRange
import com.iduna.domain.model.HistorySummary
import com.iduna.domain.model.ReportSummary
import com.iduna.domain.model.SosUiState
import com.iduna.domain.model.UserProfile
import com.iduna.domain.model.UserSettings
import com.iduna.util.TimeFormatters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

class IdunaViewModel(
    application: Application,
    private val repository: IdunaRepository,
) : AndroidViewModel(application) {
    private val _reportRange = MutableStateFlow(TimeFormatters.dayRange(LocalDate.now()))
    private val _reportFile = MutableStateFlow<File?>(null)
    private val _reportSummary = MutableStateFlow<ReportSummary?>(null)

    val dashboardState: StateFlow<DashboardUiState> = repository.dashboardState
    val historySummary: StateFlow<HistorySummary> = repository.historySummary
    val profile: StateFlow<UserProfile> = repository.profile
    val settings: StateFlow<UserSettings> = repository.settings
    val sosState: StateFlow<SosUiState?> = repository.sosState
    val alerts: StateFlow<List<AlertEvent>> = repository.alerts.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val reportFile: StateFlow<File?> = _reportFile.asStateFlow()
    val reportSummary: StateFlow<ReportSummary?> = _reportSummary.asStateFlow()
    val reportRange: StateFlow<Pair<Long, Long>> = _reportRange.asStateFlow()

    init {
        refreshReportSummary()
    }

    fun startScan() = repository.startScan()

    fun stopScan() = repository.stopScan()

    fun reconnect() = repository.reconnect()

    fun disconnect() = repository.disconnect()

    fun setHistoryRange(range: HistoryRange) = repository.setHistoryRange(range)

    fun updateProfile(name: String, age: String, emergencyContact: String) {
        viewModelScope.launch {
            repository.updateProfile(
                UserProfile(
                    name = name.trim(),
                    age = age.toIntOrNull() ?: 0,
                    emergencyContact = emergencyContact.trim(),
                ),
            )
        }
    }

    fun updateSetting(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch {
            repository.updateSettings(transform(settings.value))
        }
    }

    fun cancelSos() = repository.dismissSos()

    fun updateReportDate(date: LocalDate) {
        _reportRange.value = TimeFormatters.dayRange(date)
        refreshReportSummary()
    }

    fun refreshReportSummary() {
        viewModelScope.launch {
            val (start, end) = _reportRange.value
            _reportSummary.value = repository.buildReportSummary(start, end)
        }
    }

    fun generateReportPdf() {
        viewModelScope.launch {
            val (start, end) = _reportRange.value
            _reportFile.value = repository.generatePdfReport(start, end)
            _reportSummary.value = repository.buildReportSummary(start, end)
        }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    val app = application as IdunaApplication
                    return IdunaViewModel(application, app.appContainer.repository) as T
                }
            }
    }
}
