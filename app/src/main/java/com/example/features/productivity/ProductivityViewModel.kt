package com.example.features.productivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.calendar.DeviceCalendarEvent
import com.example.core.database.*
import com.example.core.di.AppContainer
import com.example.core.notifications.RRChannels
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class FocusUiState(
    val isRunning: Boolean = false,
    val phase: String = "FOCUS",
    val label: String = "Deep Focus",
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val remainingMs: Long = 25 * 60_000L,
    val endEpochMs: Long = 0L,
)

class ProductivityViewModel(private val container: AppContainer) : ViewModel() {

    // Tasks
    val tasksWithSubtasks: StateFlow<List<TaskWithSubtasks>> =
        container.taskRepository.tasksWithSubtasks
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Notes
    val notes: StateFlow<List<NoteEntity>> =
        container.noteRepository.notes
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Habits
    val habitsWithLogs: StateFlow<List<HabitWithLogs>> =
        container.habitRepository.habits
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Routines & Focus
    val routines: StateFlow<List<RoutineWithSteps>> =
        container.routineFocusRepository.routines
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val focusSessions: StateFlow<List<FocusSessionEntity>> =
        container.routineFocusRepository.focusSessions
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Reminders & RR Calendar Events
    val reminders: StateFlow<List<ReminderEntity>> =
        container.reminderRepository.reminders
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val calendarEvents: StateFlow<List<CalendarEventEntity>> =
        container.calendarEventRepository.events
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Device Calendar Events
    private val _deviceCalendarEvents = MutableStateFlow<List<DeviceCalendarEvent>>(emptyList())
    val deviceCalendarEvents: StateFlow<List<DeviceCalendarEvent>> = _deviceCalendarEvents.asStateFlow()

    // Focus Timer State
    private val _focusState = MutableStateFlow(FocusUiState())
    val focusState: StateFlow<FocusUiState> = _focusState.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = container.preferencesRepository.getSnapshot()
            val now = System.currentTimeMillis()
            if (prefs.focusRunning && prefs.focusEndEpochMs > now) {
                _focusState.value = FocusUiState(
                    isRunning = true,
                    phase = prefs.focusPhase,
                    label = prefs.focusLabel,
                    focusMinutes = prefs.focusMinutes,
                    breakMinutes = prefs.breakMinutes,
                    remainingMs = prefs.focusEndEpochMs - now,
                    endEpochMs = prefs.focusEndEpochMs,
                )
            } else if (prefs.focusRunning && prefs.focusEndEpochMs in 1L..now) {
                completeFocusPhase(prefs.focusPhase, prefs.focusLabel, prefs.focusMinutes, prefs.breakMinutes)
            } else {
                _focusState.value = FocusUiState(
                    isRunning = false,
                    phase = prefs.focusPhase,
                    label = prefs.focusLabel,
                    focusMinutes = prefs.focusMinutes,
                    breakMinutes = prefs.breakMinutes,
                    remainingMs = prefs.focusRemainingMs,
                    endEpochMs = 0L,
                )
            }
        }

        viewModelScope.launch {
            while (isActive) {
                delay(250L)
                val cur = _focusState.value
                if (cur.isRunning && cur.endEpochMs > 0L) {
                    val rem = (cur.endEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
                    if (rem == 0L) {
                        completeFocusPhase(cur.phase, cur.label, cur.focusMinutes, cur.breakMinutes)
                    } else {
                        _focusState.value = cur.copy(remainingMs = rem)
                    }
                }
            }
        }
    }

    private suspend fun completeFocusPhase(phase: String, label: String, focusMin: Int, breakMin: Int) {
        val durationSec = if (phase == "FOCUS") focusMin * 60 else breakMin * 60
        container.routineFocusRepository.recordFocusSession(
            FocusSessionEntity(
                label = label,
                focusMinutes = focusMin,
                breakMinutes = breakMin,
                actualDurationSeconds = durationSec,
                phase = phase,
            ),
        )
        val nextPhase = if (phase == "FOCUS") "BREAK" else "FOCUS"
        val nextDuration = (if (nextPhase == "FOCUS") focusMin else breakMin) * 60_000L
        container.preferencesRepository.saveFocusState(
            running = false,
            phase = nextPhase,
            label = label,
            focusMinutes = focusMin,
            breakMinutes = breakMin,
            remainingMs = nextDuration,
            endEpochMs = 0L,
        )
        _focusState.value = FocusUiState(
            isRunning = false,
            phase = nextPhase,
            label = label,
            focusMinutes = focusMin,
            breakMinutes = breakMin,
            remainingMs = nextDuration,
            endEpochMs = 0L,
        )
        container.notificationManager.postSimpleNotification(
            id = 7501,
            channelId = RRChannels.ROUTINES,
            title = if (phase == "FOCUS") "Focus Session Complete" else "Break Complete",
            body = if (phase == "FOCUS") "Great work on $label! Ready for a ${breakMin}m break." else "Break finished. Ready to resume focus.",
            sourceType = "ROUTINE",
        )
    }

    fun configureFocus(label: String, focusMinutes: Int, breakMinutes: Int) {
        viewModelScope.launch {
            val fMin = focusMinutes.coerceIn(1, 180)
            val bMin = breakMinutes.coerceIn(1, 60)
            val rem = fMin * 60_000L
            container.preferencesRepository.saveFocusState(
                running = false,
                phase = "FOCUS",
                label = label.ifBlank { "Deep Focus" },
                focusMinutes = fMin,
                breakMinutes = bMin,
                remainingMs = rem,
                endEpochMs = 0L,
            )
            _focusState.value = FocusUiState(
                isRunning = false,
                phase = "FOCUS",
                label = label.ifBlank { "Deep Focus" },
                focusMinutes = fMin,
                breakMinutes = bMin,
                remainingMs = rem,
                endEpochMs = 0L,
            )
        }
    }

    fun startOrResumeFocus() {
        viewModelScope.launch {
            val cur = _focusState.value
            val endEpoch = System.currentTimeMillis() + cur.remainingMs
            container.preferencesRepository.saveFocusState(
                running = true,
                phase = cur.phase,
                label = cur.label,
                focusMinutes = cur.focusMinutes,
                breakMinutes = cur.breakMinutes,
                remainingMs = cur.remainingMs,
                endEpochMs = endEpoch,
            )
            _focusState.value = cur.copy(isRunning = true, endEpochMs = endEpoch)
        }
    }

    fun pauseFocus() {
        viewModelScope.launch {
            val cur = _focusState.value
            val exactRem = if (cur.isRunning && cur.endEpochMs > 0L) {
                (cur.endEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
            } else {
                cur.remainingMs
            }
            container.preferencesRepository.saveFocusState(
                running = false,
                phase = cur.phase,
                label = cur.label,
                focusMinutes = cur.focusMinutes,
                breakMinutes = cur.breakMinutes,
                remainingMs = exactRem,
                endEpochMs = 0L,
            )
            _focusState.value = cur.copy(isRunning = false, remainingMs = exactRem, endEpochMs = 0L)
        }
    }

    fun resetFocus() {
        val cur = _focusState.value
        configureFocus(cur.label, cur.focusMinutes, cur.breakMinutes)
    }

    fun loadDeviceCalendarForMonth(year: Int, month: Int) {
        viewModelScope.launch {
            val start = LocalDate.of(year, month, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val end = LocalDate.of(year, month, 1).plusMonths(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            _deviceCalendarEvents.value = container.calendarReader.getEventsInRange(start, end)
        }
    }

    // Calendar Events mutations
    fun saveCalendarEvent(event: CalendarEventEntity) {
        viewModelScope.launch { container.calendarEventRepository.saveEvent(event) }
    }

    fun deleteCalendarEvent(id: Long) {
        viewModelScope.launch { container.calendarEventRepository.deleteEvent(id) }
    }

    // Tasks mutations
    fun saveTask(task: TaskEntity, subtasks: List<String>) {
        viewModelScope.launch { container.taskRepository.saveTask(task, subtasks) }
    }

    fun toggleTaskComplete(task: TaskEntity) {
        viewModelScope.launch { container.taskRepository.toggleTaskComplete(task) }
    }

    fun toggleSubtask(subtask: SubtaskEntity) {
        viewModelScope.launch { container.taskRepository.toggleSubtask(subtask) }
    }

    fun deleteTask(id: Long) {
        viewModelScope.launch { container.taskRepository.deleteTask(id) }
    }

    // Notes mutations
    fun saveNote(note: NoteEntity, onSavedId: ((Long) -> Unit)? = null) {
        viewModelScope.launch {
            val id = container.noteRepository.saveNote(note)
            onSavedId?.invoke(id)
        }
    }

    fun togglePinNote(note: NoteEntity) {
        viewModelScope.launch { container.noteRepository.togglePin(note) }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch { container.noteRepository.deleteNote(id) }
    }

    // Habits mutations
    fun saveHabit(habit: HabitEntity) {
        viewModelScope.launch { container.habitRepository.saveHabit(habit) }
    }

    fun toggleHabitCheckIn(habitId: Long, epochDay: Long, currentlyDone: Boolean) {
        viewModelScope.launch { container.habitRepository.toggleCheckIn(habitId, epochDay, currentlyDone) }
    }

    fun setHabitPaused(habit: HabitEntity, paused: Boolean) {
        viewModelScope.launch { container.habitRepository.setPaused(habit, paused) }
    }

    fun setHabitArchived(habit: HabitEntity, archived: Boolean) {
        viewModelScope.launch { container.habitRepository.setArchived(habit, archived) }
    }

    fun deleteHabit(id: Long) {
        viewModelScope.launch { container.habitRepository.deleteHabit(id) }
    }

    // Routines mutations
    fun saveRoutine(routine: RoutineEntity, steps: List<Pair<String, Int>>) {
        viewModelScope.launch { container.routineFocusRepository.saveRoutine(routine, steps) }
    }

    fun toggleRoutineStepToday(step: RoutineStepEntity, todayEpochDay: Long) {
        viewModelScope.launch { container.routineFocusRepository.toggleStepDoneToday(step, todayEpochDay) }
    }

    fun deleteRoutine(id: Long) {
        viewModelScope.launch { container.routineFocusRepository.deleteRoutine(id) }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ProductivityViewModel(container) as T
    }
}
