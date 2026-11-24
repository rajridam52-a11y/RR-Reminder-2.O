package com.example.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
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
import com.example.core.datastore.UserPreferences
import com.example.core.di.AppContainer
import com.example.core.firebase.isOwnerUser
import com.example.features.alarms.AlarmsTabContent
import com.example.features.alarms.AlarmsViewModel
import com.example.features.auth.AuthScreen
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
import com.example.ui.components.RRBackgroundContainer
import com.example.ui.theme.LocalRRExtraColors
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    var splashDone by rememberSaveable { mutableStateOf(false) }
    var welcomeDismissed by rememberSaveable { mutableStateOf(false) }
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
            if (auth.currentUser == null) {
                container.clearRemoteOverrides()
            }
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(Unit) {
        if (!splashDone) {
            delay(1150L)
            splashDone = true
        }
    }

    if (!splashDone) {
        SplashScreen()
        return
    }

    if (!welcomeDismissed && initialDestination == null) {
        val preAuthPrefs by container.effectivePreferences
            .collectAsStateWithLifecycle(initialValue = UserPreferences())
        val syncStatus by container.networkTimeProvider.syncStatus.collectAsStateWithLifecycle()
        var preAuthNowMillis by remember { mutableLongStateOf(container.networkTimeProvider.currentEpochMillis()) }

        LaunchedEffect(Unit) {
            launch {
                container.networkTimeProvider.syncNow()
                preAuthNowMillis = container.networkTimeProvider.currentEpochMillis()
            }
            while (isActive) {
                val now = container.networkTimeProvider.currentEpochMillis()
                preAuthNowMillis = now
                val waitMs = (1000L - (now % 1000L)).coerceAtLeast(50L) + 10L
                delay(waitMs)
            }
        }

        WelcomePreviewScreen(
            nowMillis = preAuthNowMillis,
            preferences = preAuthPrefs,
            isFirstLaunch = !preAuthPrefs.onboardingCompleted,
            timeSyncStatus = syncStatus,
            onContinue = { welcomeDismissed = true },
        )
        return
    }

    val signedInUser = currentUser
    if (signedInUser == null) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
            },
        )
        return
    }

    val uid = signedInUser.uid
    key(uid) {
        val mainViewModel: MainViewModel = viewModel(key = uid, factory = MainViewModel.Factory(container))
        val preferences by mainViewModel.preferences.collectAsStateWithLifecycle()

        if (!preferences.onboardingCompleted) {
            OnboardingScreen(
                themeMode = preferences.themeMode,
                accentPreset = preferences.accentPreset,
                onSelectThemeMode = mainViewModel::setThemeMode,
                onSelectAccent = mainViewModel::setAccentPreset,
                onFinish = mainViewModel::completeOnboarding,
            )
        } else {
            RRMainShell(
                uid = uid,
                currentUser = signedInUser,
                container = container,
                mainViewModel = mainViewModel,
                initialDestination = initialDestination,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RRMainShell(
    uid: String,
    currentUser: FirebaseUser,
    container: AppContainer,
    mainViewModel: MainViewModel,
    initialDestination: String?,
) {
    val context = LocalContext.current
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val extra = LocalRRExtraColors.current

    val remindersVm: RemindersViewModel = viewModel(key = uid, factory = RemindersViewModel.Factory(container))
    val alarmsVm: AlarmsViewModel = viewModel(key = uid, factory = AlarmsViewModel.Factory(container))
    val weatherClockVm: WeatherWorldClockViewModel = viewModel(key = uid, factory = WeatherWorldClockViewModel.Factory(container))
    val timerSwVm: TimerStopwatchViewModel = viewModel(key = uid, factory = TimerStopwatchViewModel.Factory(container))
    val prodVm: ProductivityViewModel = viewModel(key = uid, factory = ProductivityViewModel.Factory(container))
    val settingsVm: SettingsViewModel = viewModel(key = uid, factory = SettingsViewModel.Factory(container))

    val preferences by mainViewModel.preferences.collectAsStateWithLifecycle()
    val remoteConfig by mainViewModel.remoteConfig.collectAsStateWithLifecycle()
    val homeState by mainViewModel.homeState.collectAsStateWithLifecycle()
    val timeSyncStatus by mainViewModel.timeSyncStatus.collectAsStateWithLifecycle()

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

    RRBackgroundContainer {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Row(modifier = Modifier.fillMaxSize()) {
                if (useRail && showPrimaryNav) {
                    NavigationRail(
                        containerColor = extra.navPillFill,
                        modifier = Modifier.safeDrawingPadding(),
                    ) {
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
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        if (!useRail && showPrimaryNav) {
                            AuroraFloatingNavBar(
                                currentRoute = currentRoute,
                                onSelectTab = { route -> navController.navigateSingleTop(route) },
                            )
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
                                globalConfig = remoteConfig,
                                timeSyncStatus = timeSyncStatus,
                                onSyncNetworkTime = mainViewModel::syncNetworkTime,
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
                            val locMessage by remindersVm.locationMessage.collectAsStateWithLifecycle()

                            RemindersScreen(
                                reminders = reminders,
                                nowMillis = homeState.nowMillis,
                                searchQuery = query,
                                selectedFilter = filter,
                                selectedCategory = cat,
                                preferences = preferences,
                                capturedLocation = capturedLoc,
                                locationMessage = locMessage,
                                onSearchQueryChange = remindersVm::setSearchQuery,
                                onSelectFilter = remindersVm::setFilter,
                                onSelectCategory = remindersVm::setCategory,
                                onSaveReminder = remindersVm::saveReminder,
                                onToggleComplete = remindersVm::toggleComplete,
                                onSnooze = remindersVm::snoozeReminder,
                                onDeleteReminder = remindersVm::deleteReminder,
                                onCaptureLocation = remindersVm::captureCurrentLocation,
                                onClearCapturedLocation = remindersVm::clearCapturedLocation,
                                onNavigateNotifications = { navController.navigate(RRRoutes.NOTIFICATIONS) },
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
                                containerColor = Color.Transparent,
                                topBar = {
                                    Column {
                                        TopAppBar(
                                            title = { Text("Clock & Precision Time") },
                                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                                        )
                                        PrimaryTabRow(
                                            selectedTabIndex = clockSubTab,
                                            containerColor = Color.Transparent,
                                        ) {
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
                                            timeSyncStatus = timeSyncStatus,
                                            onSyncNetworkTime = weatherClockVm::syncNetworkTime,
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
                            val calendarEvents by prodVm.calendarEvents.collectAsStateWithLifecycle()
                            val tasks by prodVm.tasksWithSubtasks.collectAsStateWithLifecycle()
                            val notes by prodVm.notes.collectAsStateWithLifecycle()
                            val habits by prodVm.habitsWithLogs.collectAsStateWithLifecycle()
                            val routines by prodVm.routines.collectAsStateWithLifecycle()
                            val focusState by prodVm.focusState.collectAsStateWithLifecycle()
                            val focusSessions by prodVm.focusSessions.collectAsStateWithLifecycle()
                            val deviceEvents by prodVm.deviceCalendarEvents.collectAsStateWithLifecycle()

                            val tabTitles = listOf("Calendar", "Tasks", "Notes", "Habits", "Focus")
                            Scaffold(
                                containerColor = Color.Transparent,
                                topBar = {
                                    Column {
                                        TopAppBar(
                                            title = { Text("Productivity Workspace") },
                                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                                        )
                                        ScrollableTabRow(
                                            selectedTabIndex = workspaceSubTab,
                                            edgePadding = 12.dp,
                                            containerColor = Color.Transparent,
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
                                            rrEvents = calendarEvents,
                                            deviceEvents = deviceEvents,
                                            nowMillis = homeState.nowMillis,
                                            preferences = preferences,
                                            onLoadDeviceCalendarMonth = prodVm::loadDeviceCalendarForMonth,
                                            onSaveEvent = prodVm::saveCalendarEvent,
                                            onDeleteEvent = prodVm::deleteCalendarEvent,
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
                                            onSaveNote = { note, onSavedId -> prodVm.saveNote(note, onSavedId) },
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
                                nowMillis = homeState.nowMillis,
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
                            val assistantState by mainViewModel.assistantState.collectAsStateWithLifecycle()
                            BackHandler { navController.popBackStack() }
                            SmartSearchScreen(
                                query = query,
                                results = results,
                                assistantState = assistantState,
                                preferences = preferences,
                                onQueryChange = mainViewModel::setSearchQuery,
                                onAskAssistant = mainViewModel::askProductivityAssistant,
                                onClearAssistant = mainViewModel::clearAssistantResponse,
                                onBack = { navController.popBackStack() },
                                onNavigateReminders = { navController.navigateSingleTop(RRRoutes.REMINDERS) },
                                onNavigateAlarms = {
                                    clockSubTab = 0
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
                                onNavigateWeather = { navController.navigateSingleTop(RRRoutes.WEATHER) },
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
                            val settingsPrefs by settingsVm.preferences.collectAsStateWithLifecycle()
                            val settingsRemote by settingsVm.remoteConfig.collectAsStateWithLifecycle()
                            val settingsNow by settingsVm.nowMillis.collectAsStateWithLifecycle()
                            val isSavingOwner by settingsVm.isSavingOwnerConfig.collectAsStateWithLifecycle()
                            val ownerStatus by settingsVm.ownerSaveStatus.collectAsStateWithLifecycle()
                            val ownerError by settingsVm.ownerSaveError.collectAsStateWithLifecycle()
                            val isOwner = isOwnerUser(currentUser.email, currentUser.isEmailVerified)
                            BackHandler { navController.popBackStack() }
                            SettingsScreen(
                                preferences = settingsPrefs,
                                backupStatus = backupStatus,
                                currentUserEmail = currentUser.email,
                                currentUserName = currentUser.displayName,
                                isEmailVerified = currentUser.isEmailVerified,
                                isOwnerAccount = isOwner,
                                remoteConfig = settingsRemote,
                                nowMillis = settingsNow,
                                baseNetworkNowMillis = settingsVm.currentBaseNetworkMillis(),
                                isSavingOwnerConfig = isSavingOwner,
                                ownerSaveStatus = ownerStatus,
                                ownerSaveError = ownerError,
                                onPublishOwnerConfig = settingsVm::publishOwnerConfig,
                                onResetOwnerConfig = settingsVm::resetOwnerConfig,
                                onClearOwnerMessages = settingsVm::clearOwnerMessages,
                                onSignOut = { settingsVm.signOut(credentialManager) },
                                onBack = { navController.popBackStack() },
                                onSetThemeMode = settingsVm::setThemeMode,
                                onSetAccentPreset = settingsVm::setAccentPreset,
                                onSetPrimaryClockZone = settingsVm::setPrimaryClockZone,
                                onSetTimeFormatMode = settingsVm::setTimeFormatMode,
                                onSetDateFormat = settingsVm::setDateFormat,
                                onSetFirstDayOfWeek = settingsVm::setFirstDayOfWeek,
                                onSetUseFahrenheit = settingsVm::setUseFahrenheit,
                                onSetUseMph = settingsVm::setUseMph,
                                onSetDefaultReminderLead = settingsVm::setDefaultReminderLead,
                                onSetDefaultAlarmSnooze = settingsVm::setDefaultAlarmSnooze,
                                onSetVibrationEnabled = settingsVm::setVibrationEnabled,
                                onSetNotificationSoundUri = settingsVm::setNotificationSoundUri,
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
}

@Composable
private fun AuroraFloatingNavBar(
    currentRoute: String,
    onSelectTab: (String) -> Unit,
) {
    val extra = LocalRRExtraColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = extra.navPillFill,
            border = BorderStroke(1.dp, extra.cardBorder),
            shadowElevation = if (extra.isDark) 8.dp else 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 540.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopNavTab.entries.forEach { tab ->
                    val selected = currentRoute == tab.route
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .background(
                                if (selected) extra.accentPrimary.copy(alpha = 0.16f) else Color.Transparent,
                            )
                            .clickable { onSelectTab(tab.route) }
                            .padding(vertical = 6.dp, horizontal = 4.dp)
                            .testTag(tab.testTag),
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (selected) extra.accentPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(21.dp),
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) extra.accentPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
