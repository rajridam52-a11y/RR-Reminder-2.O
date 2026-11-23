package com.example.features.alarms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.database.AlarmEntity
import com.example.core.database.AlarmHistoryEntity
import com.example.core.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmsViewModel(private val container: AppContainer) : ViewModel() {

    val alarms: StateFlow<List<AlarmEntity>> = container.alarmRepository.alarms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<AlarmHistoryEntity>> = container.alarmRepository.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveAlarm(alarm: AlarmEntity) {
        viewModelScope.launch { container.alarmRepository.saveAlarm(alarm) }
    }

    fun toggleAlarm(alarm: AlarmEntity, enabled: Boolean) {
        viewModelScope.launch { container.alarmRepository.toggleAlarm(alarm, enabled) }
    }

    fun deleteAlarm(id: Long) {
        viewModelScope.launch { container.alarmRepository.deleteAlarm(id) }
    }

    fun clearHistory() {
        viewModelScope.launch { container.alarmRepository.clearHistory() }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AlarmsViewModel(container) as T
    }
}
