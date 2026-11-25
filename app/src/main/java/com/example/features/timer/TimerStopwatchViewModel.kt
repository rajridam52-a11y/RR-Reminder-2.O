package com.example.features.timer

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.database.StopwatchSessionEntity
import com.example.core.database.TimerEntity
import com.example.core.database.TimerHistoryEntity
import com.example.core.database.TimerPresetEntity
import com.example.core.di.AppContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class StopwatchUiState(
    val isRunning: Boolean = false,
    val totalElapsedMillis: Long = 0L,
    val lapsMillis: List<Long> = emptyList(),
    val bestLapIndex: Int? = null,
    val slowestLapIndex: Int? = null,
)

class TimerStopwatchViewModel(private val container: AppContainer) : ViewModel() {

    val timers: StateFlow<List<TimerEntity>> = container.timerRepository.timers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val presets: StateFlow<List<TimerPresetEntity>> = container.timerRepository.presets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val timerHistory: StateFlow<List<TimerHistoryEntity>> = container.timerRepository.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stopwatchSessions: StateFlow<List<StopwatchSessionEntity>> =
        container.database.stopwatchDao().observeSessions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _timerTickNow = MutableStateFlow(System.currentTimeMillis())
    val timerTickNow: StateFlow<Long> = _timerTickNow.asStateFlow()

    private val _stopwatchState = MutableStateFlow(StopwatchUiState())
    val stopwatchState: StateFlow<StopwatchUiState> = _stopwatchState.asStateFlow()

    private var swRunning = false
    private var swStartNanos = 0L
    private var swAccumNanos = 0L
    private var swLaps = mutableListOf<Long>()

    init {
        viewModelScope.launch {
            val prefs = container.preferencesRepository.getSnapshot()
            swRunning = prefs.stopwatchRunning
            swStartNanos = prefs.stopwatchStartElapsedNanos
            swAccumNanos = prefs.stopwatchAccumulatedNanos
            swLaps = prefs.stopwatchLapsCsv
                .split(",")
                .mapNotNull { it.toLongOrNull() }
                .toMutableList()
            // Guard against reboot resetting elapsedRealtimeNanos below stored start
            val nowMono = SystemClock.elapsedRealtimeNanos()
            if (swRunning && nowMono < swStartNanos) {
                swStartNanos = nowMono
            }
            emitStopwatchState()
        }

        // High-precision UI ticker while stopwatch runs; 1s cadence when only countdown timers are active
        viewModelScope.launch {
            while (true) {
                _timerTickNow.value = System.currentTimeMillis()
                if (swRunning) {
                    emitStopwatchState()
                    delay(40L)
                } else {
                    delay(1000L)
                }
            }
        }
    }

    private fun currentStopwatchTotalNanos(): Long {
        val live = if (swRunning) {
            (SystemClock.elapsedRealtimeNanos() - swStartNanos).coerceAtLeast(0L)
        } else {
            0L
        }
        return swAccumNanos + live
    }

    private fun emitStopwatchState() {
        val totalMs = currentStopwatchTotalNanos() / 1_000_000L
        val lapsSnapshot = swLaps.toList()
        val bestIdx = if (lapsSnapshot.size >= 2) {
            lapsSnapshot.indices.minByOrNull { lapsSnapshot[it] }
        } else {
            null
        }
        val slowIdx = if (lapsSnapshot.size >= 2) {
            lapsSnapshot.indices.maxByOrNull { lapsSnapshot[it] }
        } else {
            null
        }
        _stopwatchState.value = StopwatchUiState(
            isRunning = swRunning,
            totalElapsedMillis = totalMs,
            lapsMillis = lapsSnapshot,
            bestLapIndex = bestIdx,
            slowestLapIndex = slowIdx,
        )
    }

    private fun persistStopwatch() {
        val csv = swLaps.joinToString(",")
        viewModelScope.launch {
            container.preferencesRepository.saveStopwatchState(
                running = swRunning,
                startElapsedNanos = swStartNanos,
                accumulatedNanos = swAccumNanos,
                lapsCsv = csv,
            )
        }
    }

    fun startStopwatch() {
        if (swRunning) return
        swStartNanos = SystemClock.elapsedRealtimeNanos()
        swRunning = true
        emitStopwatchState()
        persistStopwatch()
    }

    fun pauseStopwatch() {
        if (!swRunning) return
        swAccumNanos = currentStopwatchTotalNanos()
        swRunning = false
        swStartNanos = 0L
        emitStopwatchState()
        persistStopwatch()
    }

    fun recordLap() {
        if (!swRunning && swAccumNanos == 0L) return
        val totalMs = currentStopwatchTotalNanos() / 1_000_000L
        val prevSum = swLaps.sum()
        val lapDuration = (totalMs - prevSum).coerceAtLeast(1L)
        swLaps.add(lapDuration)
        emitStopwatchState()
        persistStopwatch()
    }

    fun resetStopwatch(saveSessionLabel: String? = null) {
        val totalMs = currentStopwatchTotalNanos() / 1_000_000L
        val lapsCopy = swLaps.toList()
        if (!saveSessionLabel.isNullOrBlank() && totalMs > 0L) {
            viewModelScope.launch {
                container.database.stopwatchDao().insertSession(
                    StopwatchSessionEntity(
                        label = saveSessionLabel.trim(),
                        totalElapsedMillis = totalMs,
                        lapsCsvMillis = lapsCopy.joinToString(","),
                    ),
                )
            }
        }
        swRunning = false
        swStartNanos = 0L
        swAccumNanos = 0L
        swLaps.clear()
        emitStopwatchState()
        persistStopwatch()
    }

    fun deleteStopwatchSession(id: Long) {
        viewModelScope.launch { container.database.stopwatchDao().deleteSession(id) }
    }

    fun createTimer(label: String, durationMillis: Long, repeat: Boolean) {
        if (durationMillis <= 0L) return
        viewModelScope.launch {
            container.timerRepository.createAndStartTimer(label, durationMillis, repeat)
        }
    }

    fun pauseTimer(timer: TimerEntity) {
        viewModelScope.launch { container.timerRepository.pauseTimer(timer) }
    }

    fun resumeTimer(timer: TimerEntity) {
        viewModelScope.launch { container.timerRepository.resumeTimer(timer) }
    }

    fun resetTimer(timer: TimerEntity) {
        viewModelScope.launch { container.timerRepository.resetTimer(timer) }
    }

    fun deleteTimer(id: Long) {
        viewModelScope.launch { container.timerRepository.deleteTimer(id) }
    }

    fun addPreset(label: String, durationMillis: Long) {
        viewModelScope.launch { container.timerRepository.addPreset(label, durationMillis) }
    }

    fun deletePreset(id: Long) {
        viewModelScope.launch { container.timerRepository.deletePreset(id) }
    }

    fun clearTimerHistory() {
        viewModelScope.launch { container.timerRepository.clearHistory() }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TimerStopwatchViewModel(container) as T
    }
}
