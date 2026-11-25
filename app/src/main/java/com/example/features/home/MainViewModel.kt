package com.example.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.ai.AssistantUiState
import com.example.core.database.*
import com.example.core.datastore.ClockFaceStyle
import com.example.core.datastore.DashboardWidgetId
import com.example.core.datastore.UserPreferences
import com.example.core.di.AppContainer
import com.example.core.firebase.GlobalAppConfig
import com.example.core.health.SystemHealthReport
import com.example.core.network.NetworkTimeSyncStatus
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

data class SmartSearchResults(
    val reminders: List<ReminderEntity> = emptyList(),
    val tasks: List<TaskWithSubtasks> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val alarms: List<AlarmEntity> = emptyList(),
    val habits: List<HabitWithLogs> = emptyList(),
    val routines: List<RoutineWithSteps> = emptyList(),
    val calendarEvents: List<CalendarEventEntity> = emptyList(),
) {
    val isEmpty: Boolean
        get() = reminders.isEmpty() && tasks.isEmpty() && notes.isEmpty() &&
            alarms.isEmpty() && habits.isEmpty() && routines.isEmpty() &&
            calendarEvents.isEmpty()
}

data class HomeUiState(
    val nowMillis: Long = System.currentTimeMillis(),
    val upcomingReminders: List<ReminderEntity> = emptyList(),
    val nextAlarm: AlarmEntity? = null,
    val featuredAlarm: AlarmEntity? = null,
    val activeTimers: List<TimerEntity> = emptyList(),
    val weather: WeatherCacheEntity? = null,
    val worldClockCities: List<WorldClockCityEntity> = emptyList(),
    val todayTasks: List<TaskWithSubtasks> = emptyList(),
    val recentCompleted: List<String> = emptyList(),
    val healthReport: SystemHealthReport = SystemHealthReport(),
    val activeHabitsCount: Int = 0,
    val unreadNotificationsCount: Int = 0,
    val totalRemindersCount: Int = 0,
    val activeRemindersCount: Int = 0,
    val overdueRemindersCount: Int = 0,
    val pendingTasksCount: Int = 0,
    val completedTasksCount: Int = 0,
    val todayCompletedCount: Int = 0,
    val todayTotalCount: Int = 0,
    val todayProgressFraction: Float = 0f,
)

class MainViewModel(private val container: AppContainer) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = container.effectivePreferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    val remoteConfig: StateFlow<GlobalAppConfig?> = container.remoteGlobalConfig

    val nowMillis: StateFlow<Long> = container.sharedNowMillis

    val timeSyncStatus: StateFlow<NetworkTimeSyncStatus> = container.networkTimeProvider.syncStatus

    private val _assistantState = MutableStateFlow<AssistantUiState>(AssistantUiState.Idle)
    val assistantState: StateFlow<AssistantUiState> = _assistantState.asStateFlow()
    private var assistantJob: Job? = null

    // Coarse 15s pulse for dashboard overdue/today date bucket calculations; UI live seconds read `nowMillis` directly.
    private val coarseRefreshPulse: Flow<Long> = flow {
        while (currentCoroutineContext().isActive) {
            emit(container.networkTimeProvider.currentEpochMillis())
            delay(15_000L)
        }
    }

    val homeState: StateFlow<HomeUiState> = combine(
        coarseRefreshPulse,
        preferences,
        container.reminderRepository.reminders,
        container.alarmRepository.alarms,
        container.timerRepository.timers,
        container.weatherRepository.weatherCache,
        container.database.worldClockDao().observeCities(),
        container.taskRepository.tasksWithSubtasks,
        container.habitRepository.habits,
        container.database.notificationLogDao().observeUnreadCount(),
        container.systemHealthChecker.report,
    ) { values ->
        val now = container.sharedNowMillis.value
        val prefs = values[1] as UserPreferences
        @Suppress("UNCHECKED_CAST")
        val reminders = values[2] as List<ReminderEntity>
        @Suppress("UNCHECKED_CAST")
        val alarms = values[3] as List<AlarmEntity>
        @Suppress("UNCHECKED_CAST")
        val timers = values[4] as List<TimerEntity>
        val weather = values[5] as WeatherCacheEntity?
        @Suppress("UNCHECKED_CAST")
        val worldCities = values[6] as List<WorldClockCityEntity>
        @Suppress("UNCHECKED_CAST")
        val tasks = values[7] as List<TaskWithSubtasks>
        @Suppress("UNCHECKED_CAST")
        val habits = values[8] as List<HabitWithLogs>
        val unread = values[9] as Int
        val health = values[10] as SystemHealthReport

        val zone = prefs.primaryClockZone.toZoneId()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val todayEpochDay = today.toEpochDay()

        val activeReminders = reminders.filter { !it.isCompleted }
            .sortedBy { it.snoozedUntilMillis ?: it.triggerAtMillis }
        val overdueCount = activeReminders.count { (it.snoozedUntilMillis ?: it.triggerAtMillis) < now }

        val nextAlarm = alarms.filter { it.isEnabled && it.nextTriggerAtMillis != null }
            .minByOrNull { it.nextTriggerAtMillis ?: Long.MAX_VALUE }
        val featuredAlarm = nextAlarm ?: alarms.firstOrNull()

        val activeTimers = timers.filter { it.isRunning || it.remainingMillis in 1 until it.durationMillis }
            .sortedByDescending { it.isRunning }

        val openTasks = tasks.filter { !it.task.isCompleted }
        val completedTasksCount = tasks.count { it.task.isCompleted }

        val activeHabits = habits.filter { !it.habit.isArchived && !it.habit.isPaused }
        val habitsDoneToday = activeHabits.count { hw -> hw.logs.any { it.dateEpochDay == todayEpochDay } }

        val remindersToday = reminders.filter {
            val ms = it.snoozedUntilMillis ?: it.triggerAtMillis
            Instant.ofEpochMilli(ms).atZone(zone).toLocalDate() == today
        }
        val remindersDoneToday = remindersToday.count { it.isCompleted }

        val tasksDueTodayOrDoneToday = tasks.filter { tw ->
            val due = tw.task.dueDateMillis
            val doneMs = tw.task.completedAtMillis
            (due != null && Instant.ofEpochMilli(due).atZone(zone).toLocalDate() == today) ||
                (doneMs != null && Instant.ofEpochMilli(doneMs).atZone(zone).toLocalDate() == today)
        }
        val tasksDoneToday = tasksDueTodayOrDoneToday.count { it.task.isCompleted }

        val todayTotal = remindersToday.size + tasksDueTodayOrDoneToday.size + activeHabits.size
        val todayDone = remindersDoneToday + tasksDoneToday + habitsDoneToday
        val progress = if (todayTotal > 0) (todayDone.toFloat() / todayTotal.toFloat()).coerceIn(0f, 1f) else 0f

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
            upcomingReminders = activeReminders.take(4),
            nextAlarm = nextAlarm,
            featuredAlarm = featuredAlarm,
            activeTimers = activeTimers.take(3),
            weather = weather,
            worldClockCities = worldCities.take(4),
            todayTasks = openTasks.take(4),
            recentCompleted = recentDone,
            healthReport = health,
            activeHabitsCount = activeHabits.size,
            unreadNotificationsCount = unread,
            totalRemindersCount = reminders.size,
            activeRemindersCount = activeReminders.size,
            overdueRemindersCount = overdueCount,
            pendingTasksCount = openTasks.size,
            completedTasksCount = completedTasksCount,
            todayCompletedCount = todayDone,
            todayTotalCount = todayTotal,
            todayProgressFraction = progress,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

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
        container.calendarEventRepository.events,
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
        @Suppress("UNCHECKED_CAST")
        val events = (values[7] as List<CalendarEventEntity>).filter {
            it.title.lowercase().contains(q) || it.description.lowercase().contains(q) ||
                it.location.lowercase().contains(q)
        }
        SmartSearchResults(reminders, tasks, notes, alarms, habits, routines, events)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SmartSearchResults())

    init {
        viewModelScope.launch {
            runCatching { container.syncClockAndEmit() }
        }
        viewModelScope.launch {
            container.ownerConfigRepository.observeGlobalConfig()
                .catch { /* Keep local preferences when remote listener errors */ }
                .collect { res ->
                    res.onSuccess { cfg ->
                        runCatching { container.applyRemoteGlobalConfig(cfg) }
                    }
                }
        }
        viewModelScope.launch {
            runCatching { container.systemHealthChecker.runQuickStartupChecksIfNeeded() }
        }
    }

    fun syncNetworkTime() {
        viewModelScope.launch {
            container.syncClockAndEmit()
        }
    }

    fun refreshClockNow() {
        container.emitClockTick()
    }

    fun cycleClockFaceStyle() {
        viewModelScope.launch {
            val all = ClockFaceStyle.entries
            val currentIdx = all.indexOf(preferences.value.clockFaceStyle).coerceAtLeast(0)
            val next = all[(currentIdx + 1) % all.size]
            container.preferencesRepository.setClockFaceStyle(next)
        }
    }

    fun setClockFaceStyle(style: ClockFaceStyle) {
        viewModelScope.launch { container.preferencesRepository.setClockFaceStyle(style) }
    }

    fun askProductivityAssistant(userPrompt: String) {
        if (userPrompt.isBlank()) return
        assistantJob?.cancel()
        assistantJob = viewModelScope.launch {
            val state = homeState.value
            val upcomingList = state.upcomingReminders.take(5).joinToString("; ") {
                "${it.title} (${it.category}, ${it.priority})"
            }.ifBlank { "None" }
            val tasksList = state.todayTasks.take(5).joinToString("; ") {
                "${it.task.title} (${it.task.priority})"
            }.ifBlank { "None" }
            val weatherDesc = state.weather?.let {
                "${it.cityName}: ${it.currentTempC.toInt()}°C, Wind ${it.windKmh.toInt()} km/h"
            } ?: "Unavailable"

            val summary = buildString {
                appendLine("- Overdue reminders: ${state.overdueRemindersCount}, Active reminders: ${state.activeRemindersCount}")
                appendLine("- Upcoming reminders: $upcomingList")
                appendLine("- Open tasks (${state.pendingTasksCount}): $tasksList")
                appendLine("- Active habits: ${state.activeHabitsCount}, Today progress: ${state.todayCompletedCount}/${state.todayTotalCount}")
                appendLine("- Weather: $weatherDesc")
            }

            container.aiAssistantRepository.streamProductivityAdvice(userPrompt, summary)
                .collect { uiState ->
                    _assistantState.value = uiState
                }
        }
    }

    fun clearAssistantResponse() {
        assistantJob?.cancel()
        _assistantState.value = AssistantUiState.Idle
    }

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
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

    fun quickToggleAlarm(alarm: AlarmEntity, enabled: Boolean) {
        viewModelScope.launch { container.alarmRepository.toggleAlarm(alarm, enabled) }
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
