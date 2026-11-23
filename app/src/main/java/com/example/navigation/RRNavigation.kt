package com.example.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.database.ReminderEntity
import com.example.core.database.TaskEntity
import com.example.core.di.AppContainer
import com.example.features.alarms.AlarmsTabContent
import com.example.features.alarms.AlarmsViewModel
import com.example.features.calendar.CalendarTabContent
import com.example.features.focus.FocusAndRoutinesTabContent
import com.example.features.habits.HabitsTabContent
import com.example.features.home.HomeDashboardScreen
import com.example.features.home.MainViewModel
import com.example.features.notes.NotesTabContent
import com.example.features.onboarding.OnboardingScreen
import com.example.features.onboarding.SplashScreen
import com.example.features.onboarding.WelcomePreviewScreen
import com.example.features.productivity.ProductivityViewModel
import com.example.features.reminders.RemindersScreen
import com.example.features.reminders.RemindersViewModel
import com.example.features.search.NotificationCenterScreen
import com.example.features.search.SmartSearchScreen
import com.example.features.settings.SettingsScreen
import com.example.features.settings.SettingsViewModel
import com.example.features.tasks.TasksTabContent
import com.example.features.timer.StopwatchTabContent
import com.example.features.timer.TimerStopwatchViewModel
import com.example.features.timer.TimerTabContent
import com.example.features.weather.WeatherScreen
import com.example.features.weather.WeatherWorldClockViewModel
import com.example.features.weather.WorldClockTabContent
import kotlinx.coroutines.launch

object RRRoutes {
    const val HOME = "home"
    const val REMINDERS = "reminders"
    const val CLOCK_HUB = "clock_hub"
    const val WORKSPACE_HUB = "workspace_hub"
    const val WEATHER = "weather"
    const val SEARCH = "search"
    const val NOTIFICATIONS = "notifications"
    const val SETTINGS = "settings"
}

enum class TopNavTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val testTag: String,
) {
    HOME(RRRoutes.HOME, "Home", Icons.Rounded.SpaceDashboard, "nav_home"),
    REMINDERS(RRRoutes.REMINDERS, "Reminders", Icons.Rounded.NotificationsActive, "nav_reminders"),
    CLOCK(RRRoutes.CLOCK_HUB, "Clock", Icons.Rounded.AccessTimeFilled, "nav_clock"),
    WORKSPACE(RRRoutes.WORKSPACE_HUB, "Planner", Icons.Rounded.GridView, "nav_workspace"),
    WEATHER(RRRoutes.WEATHER, "Weather", Icons.Rounded.WbSunny, "nav_weather"),
}

@Composable
fun RRAppRoot(
    container: AppContainer,
    initialDestination: String? = null,
) {
    val mainViewModel: MainViewModel = viewModel(factory = MainViewModel.Factory(container))
    val preferences by mainViewModel.preferences.collectAsStateWithLifecycle()
    val splashDone by mainViewModel.splashFinished.collectAsStateWithLifecycle()
    val welcomeDismissed by mainViewModel.welcomeDismissed.collectAsStateWithLifecycle()
    val homeState by mainViewModel.homeState.collectAsStateWithLifecycle()

    if (!splashDone) {
        SplashScreen()
        return
    }

    if (!welcomeDismissed && initialDestination == null) {
        WelcomePreviewScreen(
            homeState = homeState,
            preferences = preferences,
            isFirstLaunch = !preferences.onboardingCompleted,
            onContinue = mainViewModel::dismissWelcomePreview,
        )
        return
    }

    if (!preferences.onboardingCompleted) {
        OnboardingScreen(
            themeMode = preferences.themeMode,
            accentPreset = preferences.accentPreset,
            onSelectThemeMode = mainViewModel::setThemeMode,
            onSelectAccent = mainViewModel::setAccentPreset,
            onFinish = mainViewModel::completeOnboarding,
        )
        return
    }

    RRMainShell(
        container = container,
        mainViewModel = mainViewModel,
        initialDestination = initialDestination,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RRMainShell(
    container: AppContainer,
    mainViewModel: MainViewModel,
    initialDestination: String?,
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    val remindersVm: RemindersViewModel = viewModel(factory = RemindersViewModel.Factory(container))
    val alarmsVm: AlarmsViewModel = viewModel(factory = AlarmsViewModel.Factory(container))
    val weatherClockVm: WeatherWorldClockViewModel = viewModel(factory = WeatherWorldClockViewModel.Factory(container))
    val timerSwVm: TimerStopwatchViewModel = viewModel(factory = TimerStopwatchViewModel.Factory(container))
    val prodVm: ProductivityViewModel = viewModel(factory = ProductivityViewModel.Factory(container))
    val settingsVm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(container))

    val preferences by mainViewModel.preferences.collectAsStateWithLifecycle()
    val homeState by mainViewModel.homeState.collectAsStateWithLifecycle()

    var clockSubTab by remember { mutableIntStateOf(0) } // 0=Alarms, 1=World Clock, 2=Timer, 3=Stopwatch
    var workspaceSubTab by remember { mutableIntStateOf(0) } // 0=Calendar, 1=Tasks, 2=Notes, 3=Habits, 4=Focus & Routines

    LaunchedEffect(initialDestination) {
        when (initialDestination) {
            "reminders" -> navController.navigateSingleTop(RRRoutes.REMINDERS)
            "weather" -> navController.navigateSingleTop(RRRoutes.WEATHER)
            "worldclock" -> {
                clockSubTab = 1
                navController.navigateSingleTop(RRRoutes.CLOCK_HUB)
            }
            "timer" -> {
                clockSubTab = 2
                navController.navigateSingleTop(RRRoutes.CLOCK_HUB)
            }
            "quick_add" -> navController.navigateSingleTop(RRRoutes.REMINDERS)
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: RRRoutes.HOME
    val showPrimaryNav = currentRoute in TopNavTab.entries.map { it.route }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useRail && showPrimaryNav) {
                NavigationRail(modifier = Modifier.safeDrawingPadding()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TopNavTab.entries.forEach { tab ->
                        NavigationRailItem(
                            selected = currentRoute == tab.route,
                            onClick = { navController.navigateSingleTop(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            modifier = Modifier.testTag(tab.testTag),
                        )
                    }
                }
            }

            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (!useRail && showPrimaryNav) {
                        NavigationBar {
                            TopNavTab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentRoute == tab.route,
                                    onClick = { navController.navigateSingleTop(tab.route) },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag(tab.testTag),
                                )
                            }
                        }
                    }
                },
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = RRRoutes.HOME,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    composable(RRRoutes.HOME) {
                        HomeDashboardScreen(
                            homeState = homeState,
                            preferences = preferences,
                            onNavigateReminders = { navController.navigateSingleTop(RRRoutes.REMINDERS) },
                            onNavigateAlarms = {
                                clockSubTab = 0
                                navController.navigateSingleTop(RRRoutes.CLOCK_HUB)
                            },
                            onNavigateWeather = { navController.navigateSingleTop(RRRoutes.WEATHER) },
                            onNavigateWorldClock = {
                                clockSubTab = 1
                                navController.navigateSingleTop(RRRoutes.CLOCK_HUB)
                            },
                            onNavigateTimer = {
                                clockSubTab = 2
                                navController.navigateSingleTop(RRRoutes.CLOCK_HUB)
                            },
                            onNavigateStopwatch = {
                                clockSubTab = 3
                                navController.navigateSingleTop(RRRoutes.CLOCK_HUB)
                            },
                            onNavigateCalendar = {
                                workspaceSubTab = 0
                                navController.navigateSingleTop(RRRoutes.WORKSPACE_HUB)
                            },
                            onNavigateTasks = {
                                workspaceSubTab = 1
                                navController.navigateSingleTop(RRRoutes.WORKSPACE_HUB)
                            },
                            onNavigateNotes = {
                                workspaceSubTab = 2
                                navController.navigateSingleTop(RRRoutes.WORKSPACE_HUB)
                            },
                            onNavigateHabits = {
                                workspaceSubTab = 3
                                navController.navigateSingleTop(RRRoutes.WORKSPACE_HUB)
                            },
                            onNavigateFocus = {
                                workspaceSubTab = 4
                                navController.navigateSingleTop(RRRoutes.WORKSPACE_HUB)
                            },
                            onNavigateSearch = { navController.navigate(RRRoutes.SEARCH) },
                            onNavigateNotifications = { navController.navigate(RRRoutes.NOTIFICATIONS) },
                            onNavigateSettings = { navController.navigate(RRRoutes.SETTINGS) },
                            onQuickCompleteReminder = mainViewModel::quickCompleteReminder,
                            onQuickToggleTask = mainViewModel::quickToggleTask,
                            onQuickSaveReminder = { rem: ReminderEntity ->
                                scope.launch { container.reminderRepository.saveReminder(rem) }
                            },
                            onQuickSaveTask = { task: TaskEntity ->
                                scope.launch { container.taskRepository.saveTask(task, emptyList()) }
                            },
                            onMoveWidget = mainViewModel::moveDashboardWidget,
                            onToggleWidgetVisibility = mainViewModel::toggleDashboardWidgetVisibility,
                            onResetDashboard = mainViewModel::resetDashboardLayout,
                        )
                    }

                    composable(RRRoutes.REMINDERS) {
                        val reminders by remindersVm.filteredReminders.collectAsStateWithLifecycle()
                        val query by remindersVm.searchQuery.collectAsStateWithLifecycle()
                        val filter by remindersVm.selectedFilter.collectAsStateWithLifecycle()
                        val cat by remindersVm.selectedCategory.collectAsStateWithLifecycle()
                        val capturedLoc by remindersVm.capturedLocation.collectAsStateWithLifecycle()

                        RemindersScreen(
                            reminders = reminders,
                            searchQuery = query,
                            selectedFilter = filter,
                            selectedCategory = cat,
                            preferences = preferences,
                            capturedLocation = capturedLoc,
                            onSearchQueryChange = remindersVm::setSearchQuery,
                            onSelectFilter = remindersVm::setFilter,
                            onSelectCategory = remindersVm::setCategory,
                            onSaveReminder = remindersVm::saveReminder,
                            onToggleComplete = remindersVm::toggleComplete,
                            onSnooze = remindersVm::snoozeReminder,
                            onDeleteReminder = remindersVm::deleteReminder,
                            onCaptureLocation = remindersVm::captureCurrentLocation,
                            onClearCapturedLocation = remindersVm::clearCapturedLocation,
                        )
                    }

                    composable(RRRoutes.CLOCK_HUB) {
                        val alarms by alarmsVm.alarms.collectAsStateWithLifecycle()
                        val alarmHistory by alarmsVm.history.collectAsStateWithLifecycle()
                        val worldCities by weatherClockVm.worldCities.collectAsStateWithLifecycle()
                        val availCities by weatherClockVm.availableCitiesToAdd.collectAsStateWithLifecycle()
                        val zoneQuery by weatherClockVm.zoneQuery.collectAsStateWithLifecycle()
                        val clockNow by weatherClockVm.nowMillis.collectAsStateWithLifecycle()
                        val timers by timerSwVm.timers.collectAsStateWithLifecycle()
                        val presets by timerSwVm.presets.collectAsStateWithLifecycle()
                        val timerHistory by timerSwVm.timerHistory.collectAsStateWithLifecycle()
                        val timerTick by timerSwVm.timerTickNow.collectAsStateWithLifecycle()
                        val swState by timerSwVm.stopwatchState.collectAsStateWithLifecycle()
                        val swSessions by timerSwVm.stopwatchSessions.collectAsStateWithLifecycle()

                        val tabTitles = listOf("Alarms", "World Clock", "Timer", "Stopwatch")
                        Scaffold(
                            topBar = {
                                Column {
                                    TopAppBar(title = { Text("Clock & Precision Time") })
                                    PrimaryTabRow(selectedTabIndex = clockSubTab) {
                                        tabTitles.forEachIndexed { idx, title ->
                                            Tab(
                                                selected = clockSubTab == idx,
                                                onClick = { clockSubTab = idx },
                                                text = { Text(title, maxLines = 1) },
                                                modifier = Modifier.testTag("clock_tab_$idx"),
                                            )
                                        }
                                    }
                                }
                            },
                        ) { hubPad ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(hubPad),
                            ) {
                                when (clockSubTab) {
                                    0 -> AlarmsTabContent(
                                        alarms = alarms,
                                        history = alarmHistory,
                                        preferences = preferences,
                                        onSaveAlarm = alarmsVm::saveAlarm,
                                        onToggleAlarm = alarmsVm::toggleAlarm,
                                        onDeleteAlarm = alarmsVm::deleteAlarm,
                                        onClearHistory = alarmsVm::clearHistory,
                                    )
                                    1 -> WorldClockTabContent(
                                        cities = worldCities,
                                        availableToAdd = availCities,
                                        zoneQuery = zoneQuery,
                                        nowMillis = clockNow,
                                        preferences = preferences,
                                        onZoneQueryChange = weatherClockVm::setZoneSearchQuery,
                                        onAddCity = weatherClockVm::addWorldCity,
                                        onRemoveCity = weatherClockVm::removeWorldCity,
                                    )
                                    2 -> TimerTabContent(
                                        timers = timers,
                                        presets = presets,
                                        history = timerHistory,
                                        tickNowMillis = timerTick,
                                        preferences = preferences,
                                        onCreateTimer = timerSwVm::createTimer,
                                        onPauseTimer = timerSwVm::pauseTimer,
                                        onResumeTimer = timerSwVm::resumeTimer,
                                        onResetTimer = timerSwVm::resetTimer,
                                        onDeleteTimer = timerSwVm::deleteTimer,
                                        onAddPreset = timerSwVm::addPreset,
                                        onClearHistory = timerSwVm::clearTimerHistory,
                                    )
                                    else -> StopwatchTabContent(
                                        state = swState,
                                        sessions = swSessions,
                                        preferences = preferences,
                                        onStart = timerSwVm::startStopwatch,
                                        onPause = timerSwVm::pauseStopwatch,
                                        onLap = timerSwVm::recordLap,
                                        onReset = timerSwVm::resetStopwatch,
                                        onDeleteSession = timerSwVm::deleteStopwatchSession,
                                    )
                                }
                            }
                        }
                    }

                    composable(RRRoutes.WORKSPACE_HUB) {
                        val reminders by prodVm.reminders.collectAsStateWithLifecycle()
                        val tasks by prodVm.tasksWithSubtasks.collectAsStateWithLifecycle()
                        val notes by prodVm.notes.collectAsStateWithLifecycle()
                        val habits by prodVm.habitsWithLogs.collectAsStateWithLifecycle()
                        val routines by prodVm.routines.collectAsStateWithLifecycle()
                        val focusState by prodVm.focusState.collectAsStateWithLifecycle()
                        val focusSessions by prodVm.focusSessions.collectAsStateWithLifecycle()
                        val deviceEvents by prodVm.deviceCalendarEvents.collectAsStateWithLifecycle()

                        val tabTitles = listOf("Calendar", "Tasks", "Notes", "Habits", "Focus")
                        Scaffold(
                            topBar = {
                                Column {
                                    TopAppBar(title = { Text("Productivity Workspace") })
                                    ScrollableTabRow(
                                        selectedTabIndex = workspaceSubTab,
                                        edgePadding = 12.dp,
                                    ) {
                                        tabTitles.forEachIndexed { idx, title ->
                                            Tab(
                                                selected = workspaceSubTab == idx,
                                                onClick = { workspaceSubTab = idx },
                                                text = { Text(title) },
                                                modifier = Modifier.testTag("workspace_tab_$idx"),
                                            )
                                        }
                                    }
                                }
                            },
                        ) { hubPad ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(hubPad),
                            ) {
                                when (workspaceSubTab) {
                                    0 -> CalendarTabContent(
                                        reminders = reminders,
                                        tasks = tasks,
                                        deviceEvents = deviceEvents,
                                        preferences = preferences,
                                        onLoadDeviceCalendarMonth = prodVm::loadDeviceCalendarForMonth,
                                    )
                                    1 -> TasksTabContent(
                                        tasks = tasks,
                                        preferences = preferences,
                                        onSaveTask = prodVm::saveTask,
                                        onToggleTask = prodVm::toggleTaskComplete,
                                        onToggleSubtask = prodVm::toggleSubtask,
                                        onDeleteTask = prodVm::deleteTask,
                                    )
                                    2 -> NotesTabContent(
                                        notes = notes,
                                        reminders = reminders,
                                        onSaveNote = prodVm::saveNote,
                                        onTogglePin = prodVm::togglePinNote,
                                        onDeleteNote = prodVm::deleteNote,
                                    )
                                    3 -> HabitsTabContent(
                                        habits = habits,
                                        onSaveHabit = prodVm::saveHabit,
                                        onToggleCheckIn = prodVm::toggleHabitCheckIn,
                                        onSetPaused = prodVm::setHabitPaused,
                                        onSetArchived = prodVm::setHabitArchived,
                                        onDeleteHabit = prodVm::deleteHabit,
                                    )
                                    else -> FocusAndRoutinesTabContent(
                                        focusState = focusState,
                                        focusSessions = focusSessions,
                                        routines = routines,
                                        preferences = preferences,
                                        onConfigureFocus = prodVm::configureFocus,
                                        onStartFocus = prodVm::startOrResumeFocus,
                                        onPauseFocus = prodVm::pauseFocus,
                                        onResetFocus = prodVm::resetFocus,
                                        onSaveRoutine = prodVm::saveRoutine,
                                        onToggleRoutineStep = prodVm::toggleRoutineStepToday,
                                        onDeleteRoutine = prodVm::deleteRoutine,
                                    )
                                }
                            }
                        }
                    }

                    composable(RRRoutes.WEATHER) {
                        val weatherUi by weatherClockVm.weatherUiState.collectAsStateWithLifecycle()
                        WeatherScreen(
                            uiState = weatherUi,
                            preferences = preferences,
                            onSearchCity = weatherClockVm::searchWeatherCities,
                            onSelectCity = weatherClockVm::fetchWeatherForCity,
                            onClearCityResults = weatherClockVm::clearCityResults,
                            onUseLocation = weatherClockVm::fetchWeatherByCurrentLocation,
                            onRefresh = weatherClockVm::refreshSavedWeather,
                        )
                    }

                    composable(RRRoutes.SEARCH) {
                        val query by mainViewModel.searchQuery.collectAsStateWithLifecycle()
                        val results by mainViewModel.smartSearchResults.collectAsStateWithLifecycle()
                        BackHandler { navController.popBackStack() }
                        SmartSearchScreen(
                            query = query,
                            results = results,
                            onQueryChange = mainViewModel::setSearchQuery,
                            onBack = { navController.popBackStack() },
                            onNavigateReminders = { navController.navigateSingleTop(RRRoutes.REMINDERS) },
                            onNavigateAlarms = {
                                clockSubTab = 0
                                navController.navigateSingleTop(RRRoutes.CLOCK_HUB)
                            },
                            onNavigateTasks = {
                                workspaceSubTab = 1
                                navController.navigateSingleTop(RRRoutes.WORKSPACE_HUB)
                            },
                            onNavigateNotes = {
                                workspaceSubTab = 2
                                navController.navigateSingleTop(RRRoutes.WORKSPACE_HUB)
                            },
                            onNavigateHabits = {
                                workspaceSubTab = 3
                                navController.navigateSingleTop(RRRoutes.WORKSPACE_HUB)
                            },
                            onNavigateFocus = {
                                workspaceSubTab = 4
                                navController.navigateSingleTop(RRRoutes.WORKSPACE_HUB)
                            },
                        )
                    }

                    composable(RRRoutes.NOTIFICATIONS) {
                        val logs by mainViewModel.notificationLogs.collectAsStateWithLifecycle()
                        BackHandler { navController.popBackStack() }
                        NotificationCenterScreen(
                            logs = logs,
                            preferences = preferences,
                            onMarkAllRead = mainViewModel::markAllNotificationsRead,
                            onClearAll = mainViewModel::clearNotificationLogs,
                            onBack = { navController.popBackStack() },
                        )
                    }

                    composable(RRRoutes.SETTINGS) {
                        val backupStatus by settingsVm.backupStatus.collectAsStateWithLifecycle()
                        BackHandler { navController.popBackStack() }
                        SettingsScreen(
                            preferences = preferences,
                            backupStatus = backupStatus,
                            onBack = { navController.popBackStack() },
                            onSetThemeMode = settingsVm::setThemeMode,
                            onSetAccentPreset = settingsVm::setAccentPreset,
                            onSetUse24Hour = settingsVm::setUse24Hour,
                            onSetDateFormat = settingsVm::setDateFormat,
                            onSetFirstDayOfWeek = settingsVm::setFirstDayOfWeek,
                            onSetDefaultAlarmSnooze = settingsVm::setDefaultAlarmSnooze,
                            onSetVibrationEnabled = settingsVm::setVibrationEnabled,
                            onSetLocationRemindersEnabled = settingsVm::setLocationRemindersEnabled,
                            onSetUseDeviceCalendar = settingsVm::setUseDeviceCalendar,
                            onExportBackup = settingsVm::exportBackupToUri,
                            onImportBackup = settingsVm::importBackupFromUri,
                            onClearBackupStatus = settingsVm::clearBackupStatus,
                        )
                    }
                }
            }
        }
    }
}

private fun NavHostController.navigateSingleTop(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
