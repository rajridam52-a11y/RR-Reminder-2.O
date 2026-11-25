package com.example.features.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.core.datastore.UserPreferences
import com.example.core.di.AppContainer
import com.example.core.health.HealthScoreCalculator
import com.example.core.health.SystemCheckId
import com.example.core.health.SystemHealthReport
import com.example.core.network.NetworkTimeSyncStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SystemHealthViewModel(private val container: AppContainer) : ViewModel() {

    val report: StateFlow<SystemHealthReport> = container.systemHealthChecker.report

    val preferences: StateFlow<UserPreferences> = container.effectivePreferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    val timeSyncStatus: StateFlow<NetworkTimeSyncStatus> = container.networkTimeProvider.syncStatus

    init {
        viewModelScope.launch {
            runCatching { container.systemHealthChecker.runQuickStartupChecksIfNeeded() }
        }
    }

    fun runFullSystemCheck() {
        viewModelScope.launch {
            container.systemHealthChecker.runFullSystemCheck(includeNetworkApiProbe = true)
        }
    }

    fun refreshQuickStatus() {
        viewModelScope.launch {
            container.systemHealthChecker.runFullSystemCheck(includeNetworkApiProbe = true)
        }
    }

    fun runSingleCheck(id: SystemCheckId) {
        viewModelScope.launch {
            container.systemHealthChecker.runSingleCheck(id)
        }
    }

    fun syncClockAndRecheck() {
        viewModelScope.launch {
            container.syncClockAndEmit()
            container.systemHealthChecker.runSingleCheck(SystemCheckId.LIVE_CLOCK)
        }
    }

    fun buildDiagnosticReportText(): String {
        val zone = preferences.value.primaryClockZone.toZoneId()
        return HealthScoreCalculator.buildDiagnosticReportText(
            report = report.value,
            zoneId = zone,
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        )
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SystemHealthViewModel(container) as T
    }
}
