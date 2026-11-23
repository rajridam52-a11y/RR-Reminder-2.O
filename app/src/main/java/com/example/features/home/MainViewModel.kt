package com.example.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.database.*
import com.example.core.datastore.DashboardWidgetId
import com.example.core.datastore.UserPreferences
import com.example.core.datastore.UserPreferencesRepository
import com.example.core.di.AppContainer
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SmartSearchResults(
    val reminders: List<ReminderEntity> = emptyList(),
    val tasks: List<TaskWithSubtasks> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val alarms: List<AlarmEntity> = emptyList(),
    val habits: List<HabitWithLogs> = emptyList(),
    val routines: List<RoutineWithSteps> = emptyList(),
) {
    val isEmpty: Boolean
        get() = reminders.isEmpty() && tasks.isEmpty() && notes.isEmpty() &&
            alarms.isEmpty() && habits.isEmpty() && routines.isEmpty()
}

data class HomeUiState(
    val nowMillis: Long = System.currentTimeMillis(),
    val upcomingReminders: List<ReminderEntity> = emptyList(),
    val nextAlarm: AlarmEntity? = null,
    val weather: WeatherCacheEntity? = null,
    val todayTasks: List<TaskWithSubtasks> = emptyList(),
    val recentCompleted: List<String> = emptyList(),
    val activeHabitsCount: Int = 0,
    val unreadNotificationsCount: Int = 0,
)

class MainViewModel(private val container: AppContainer) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = container.preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    private val _splashFinished = MutableStateFlow(false)
    val splashFinished: StateFlow<Boolean> = _splashFinished.asStateFlow()

    private val _welcomeDismissed = MutableStateFlow(false)
    val welcomeDismissed: StateFlow<Boolean> = _welcomeDismissed.asStateFlow()

    private val _nowMillis = MutableStateFlow(System.currentTimeMillis())

    val homeState: StateFlow<HomeUiState> = combine(
        _nowMillis,
        container.reminderRepository.reminders,
        container.alarmRepository.alarms,
        container.weatherRepository.weatherCache,
        container.taskRepository.tasksWithSubtasks,
        container.habitRepository.habits,
        container.database.notificationLogDao().observeUnreadCount(),
    ) { values ->
        val now = values[0] as Long
        @Suppress("UNCHECKED_CAST")
        val reminders = values[1] as List<ReminderEntity>
        @Suppress("UNCHECKED_CAST")
        val alarms = values[2] as List<AlarmEntity>
        val weather = values[3] as WeatherCacheEntity?
        @Suppress("UNCHECKED_CAST")
        val tasks = values[4] as List<TaskWithSubtasks>
        @Suppress("UNCHECKED_CAST")
        val habits = values[5] as List<HabitWithLogs>
        val unread = values[6] as Int

        val upcoming = reminders.filter { !it.isCompleted }.sortedBy { it.snoozedUntilMillis ?: it.triggerAtMillis }
        val nextAlarm = alarms.filter { it.isEnabled && it.nextTriggerAtMillis != null }
            .minByOrNull { it.nextTriggerAtMillis ?: Long.MAX_VALUE }
        val openTasks = tasks.filter { !it.task.isCompleted }

        val recentDone = buildList {
            reminders.filter { it.isCompleted && it.completedAtMillis != null }
                .sortedByDescending { it.completedAtMillis }
                .take(3)
                .forEach { add("Reminder: ${it.title}") }
            tasks.filter { it.task.isCompleted && it.task.completedAtMillis != null }
                .sortedByDescending { it.task.completedAtMillis }
                .take(3)
                .forEach { add("Task: ${it.task.title}") }
        }.take(5)

        HomeUiState(
            nowMillis = now,
            upcomingReminders = upcoming.take(4),
            nextAlarm = nextAlarm,
            weather = weather,
            todayTasks = openTasks.take(4),
            recentCompleted = recentDone,
            activeHabitsCount = habits.count { !it.habit.isArchived && !it.habit.isPaused },
            unreadNotificationsCount = unread,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    val notificationLogs: StateFlow<List<NotificationLogEntity>> =
        container.database.notificationLogDao().observeLogs()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val smartSearchResults: StateFlow<SmartSearchResults> = combine(
        _searchQuery,
        container.reminderRepository.reminders,
        container.taskRepository.tasksWithSubtasks,
        container.noteRepository.notes,
        container.alarmRepository.alarms,
        container.habitRepository.habits,
        container.routineFocusRepository.routines,
    ) { values ->
        val q = (values[0] as String).trim().lowercase()
        if (q.isBlank()) return@combine SmartSearchResults()

        @Suppress("UNCHECKED_CAST")
        val reminders = (values[1] as List<ReminderEntity>).filter {
            it.title.lowercase().contains(q) || it.description.lowercase().contains(q) ||
                it.category.lowercase().contains(q) || it.tagsCsv.lowercase().contains(q)
        }
        @Suppress("UNCHECKED_CAST")
        val tasks = (values[2] as List<TaskWithSubtasks>).filter {
            it.task.title.lowercase().contains(q) || it.task.description.lowercase().contains(q) ||
                it.task.category.lowercase().contains(q)
        }
        @Suppress("UNCHECKED_CAST")
        val notes = (values[3] as List<NoteEntity>).filter {
            it.title.lowercase().contains(q) || it.body.lowercase().contains(q) ||
                it.category.lowercase().contains(q)
        }
        @Suppress("UNCHECKED_CAST")
        val alarms = (values[4] as List<AlarmEntity>).filter {
            it.label.lowercase().contains(q)
        }
        @Suppress("UNCHECKED_CAST")
        val habits = (values[5] as List<HabitWithLogs>).filter {
            it.habit.name.lowercase().contains(q) || it.habit.description.lowercase().contains(q)
        }
        @Suppress("UNCHECKED_CAST")
        val routines = (values[6] as List<RoutineWithSteps>).filter {
            it.routine.name.lowercase().contains(q)
        }
        SmartSearchResults(reminders, tasks, notes, alarms, habits, routines)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SmartSearchResults())

    init {
        viewModelScope.launch {
            delay(1150L)
            _splashFinished.value = true
        }
        viewModelScope.launch {
            while (true) {
                delay(1000L)
                _nowMillis.value = System.currentTimeMillis()
            }
        }
    }

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun dismissWelcomePreview() {
        _welcomeDismissed.value = true
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            container.preferencesRepository.setOnboardingCompleted(true)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { container.preferencesRepository.setThemeMode(mode) }
    }

    fun setAccentPreset(preset: AccentPreset) {
        viewModelScope.launch { container.preferencesRepository.setAccentPreset(preset) }
    }

    fun moveDashboardWidget(index: Int, delta: Int) {
        viewModelScope.launch {
            val current = preferences.value
            val list = current.dashboardWidgetOrder.toMutableList()
            val newIndex = (index + delta).coerceIn(0, list.lastIndex)
            if (index in list.indices && newIndex != index) {
                val item = list.removeAt(index)
                list.add(newIndex, item)
                container.preferencesRepository.setDashboardLayout(list, current.hiddenDashboardWidgets)
            }
        }
    }

    fun toggleDashboardWidgetVisibility(widgetKey: String) {
        viewModelScope.launch {
            val current = preferences.value
            val hidden = current.hiddenDashboardWidgets.toMutableSet()
            if (widgetKey in hidden) hidden.remove(widgetKey) else hidden.add(widgetKey)
            container.preferencesRepository.setDashboardLayout(current.dashboardWidgetOrder, hidden)
        }
    }

    fun resetDashboardLayout() {
        viewModelScope.launch {
            container.preferencesRepository.setDashboardLayout(DashboardWidgetId.DEFAULT_ORDER, emptySet())
        }
    }

    fun quickCompleteReminder(reminder: ReminderEntity) {
        viewModelScope.launch { container.reminderRepository.toggleComplete(reminder) }
    }

    fun quickToggleTask(task: TaskEntity) {
        viewModelScope.launch { container.taskRepository.toggleTaskComplete(task) }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch { container.database.notificationLogDao().markAllRead() }
    }

    fun clearNotificationLogs() {
        viewModelScope.launch { container.database.notificationLogDao().clearAll() }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(container) as T
    }
}
