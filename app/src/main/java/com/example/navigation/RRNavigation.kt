package com.example.navigation

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
import com.example.BuildConfig
import com.example.core.database.ReminderEntity
import com.example.core.database.TaskEntity
import com.example.core.datastore.TimeFormatMode
import com.example.core.datastore.UserPreferences
import com.example.core.di.AppContainer
import com.example.core.firebase.isOwnerUser
import com.example.core.notifications.ReminderAlarmReceiver
import com.example.features.alarms.AlarmsTabContent
import com.example.features.alarms.AlarmsViewModel
import com.example.features.auth.AuthScreen
import com.example.features.calendar.CalendarTabContent
import com.example.features.focus.FocusAndRoutinesTabContent
import com.example.features.habits.HabitsTabContent
import com.example.features.health.SystemHealthScreen
import com.example.features.health.SystemHealthViewModel
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
import com.example.ui.components.LuminousLiveClockHero
import com.example.ui.components.RRBackgroundContainer
import com.example.ui.components.RRBrandMark
import com.example.ui.components.RRGradientButton
import com.example.ui.theme.LocalRRExtraColors
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay
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
    const val SYSTEM_HEALTH = "system_health"

    val ALL_ROUTES = listOf(
        HOME,
        REMINDERS,
        CLOCK_HUB,
        WORKSPACE_HUB,
        WEATHER,
        SEARCH,
        NOTIFICATIONS,
        SETTINGS,
        SYSTEM_HEALTH,
    )
}

enum class TopNavTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val testTag: String,
) {
    HOME(RRRoutes.HOME, "Home", Icons.Rounded.Home, "nav_home"),
    CLOCK(RRRoutes.CLOCK_HUB, "Clock", Icons.Rounded.AccessTimeFilled, "nav_clock"),
    REMINDERS(RRRoutes.REMINDERS, "Reminders", Icons.AutoMirrored.Rounded.EventNote, "nav_reminders"),
    HEALTH(RRRoutes.SYSTEM_HEALTH, "Health", Icons.Rounded.VerifiedUser, "nav_health"),
    SETTINGS(RRRoutes.SETTINGS, "Settings", Icons.Rounded.Settings, "nav_settings"),
}

@Composable
fun RRAppRoot(
    container: AppContainer,
    initialDestination: String? = null,
) {
    var splashDone by rememberSaveable { mutableStateOf(false) }
    var welcomeDismissed by rememberSaveable { mutableStateOf(false) }
    val firebaseAuth = remember { runCatching { Firebase.auth }.getOrNull() }
    var currentUser by remember { mutableStateOf(firebaseAuth?.currentUser) }

    DisposableEffect(firebaseAuth) {
        if (firebaseAuth != null) {
            val listener = FirebaseAuth.AuthStateListener { auth ->
                currentUser = auth.currentUser
                if (auth.currentUser == null) {
                    container.clearRemoteOverrides()
                }
            }
            firebaseAuth.addAuthStateListener(listener)
            onDispose { runCatching { firebaseAuth.removeAuthStateListener(listener) } }
        } else {
            onDispose { }
        }
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
        val preAuthNowMillis by container.sharedNowMillis.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            runCatching { container.syncClockAndEmit() }
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
                currentUser = runCatching { Firebase.auth.currentUser }.getOrNull()
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
    val credentialManager = remember(context) { runCatching { CredentialManager.create(context) }.getOrNull() }
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val extra = LocalRRExtraColors.current

    val remindersVm: RemindersViewModel = viewModel(key = uid, factory = RemindersViewModel.Factory(container))
    val alarmsVm: AlarmsViewModel = viewModel(key = uid, factory = AlarmsViewModel.Factory(container))
    val weatherClockVm: WeatherWorldClockViewModel = viewModel(key = uid, factory = WeatherWorldClockViewModel.Factory(container))
    val timerSwVm: TimerStopwatchViewModel = viewModel(key = uid, factory = TimerStopwatchViewModel.Factory(container))
    val prodVm: ProductivityViewModel = viewModel(key = uid, factory = ProductivityViewModel.Factory(container))
    val settingsVm: SettingsViewModel = viewModel(key = uid, factory = SettingsViewModel.Factory(container))
    val healthVm: SystemHealthViewModel = viewModel(key = uid, factory = SystemHealthViewModel.Factory(container))

    val preferences by mainViewModel.preferences.collectAsStateWithLifecycle()
    val remoteConfig by mainViewModel.remoteConfig.collectAsStateWithLifecycle()
    val homeState by mainViewModel.homeState.collectAsStateWithLifecycle()
    val liveNowMillis by mainViewModel.nowMillis.collectAsStateWithLifecycle()
    val timeSyncStatus by mainViewModel.timeSyncStatus.collectAsStateWithLifecycle()
    val activeRingAlert by container.activeRingAlert.collectAsStateWithLifecycle()
    val isOwnerAccount = isOwnerUser(currentUser.email, currentUser.isEmailVerified)

    // 0=Live Clock, 1=Alarms, 2=World Clock, 3=Timer, 4=Stopwatch
    var clockSubTab by rememberSaveable { mutableIntStateOf(0) }
    // 0=Calendar, 1=Tasks, 2=Notes, 3=Habits, 4=Focus & Routines
    var workspaceSubTab by rememberSaveable { mutableIntStateOf(0) }
    var showDrawerAboutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(initialDestination) {
        when (initialDestination) {
            "reminders" -> navController.navigateSingleTop(RRRoutes.REMINDERS)
            "weather" -> navController.navigateSingleTop(RRRoutes.WEATHER)
            "worldclock" -> {
                clockSubTab = 2
                navController.navigateSingleTop(RRRoutes.CLOCK_HUB)
            }
            "timer" -> {
                clockSubTab = 3
                navController.navigateSingleTop(RRRoutes.CLOCK_HUB)
            }
            "quick_add" -> navController.navigateSingleTop(RRRoutes.REMINDERS)
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: RRRoutes.HOME
    val showPrimaryNav = currentRoute in listOf(
        RRRoutes.HOME,
        RRRoutes.CLOCK_HUB,
        RRRoutes.REMINDERS,
        RRRoutes.SETTINGS,
    )

    LaunchedEffect(currentRoute) {
        container.systemHealthChecker.updateNavigationTelemetry(
            currentRoute = currentRoute,
            knownRoutes = RRRoutes.ALL_ROUTES,
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            RRSideMenuDrawerContent(
                currentRoute = currentRoute,
                clockSubTab = clockSubTab,
                isOwnerAccount = isOwnerAccount,
                onClose = { scope.launch { drawerState.close() } },
                onNavigateHome = {
                    scope.launch { drawerState.close() }
                    navController.navigateSingleTop(RRRoutes.HOME)
                },
                onNavigateReminders = {
                    scope.launch { drawerState.close() }
                    navController.navigateSingleTop(RRRoutes.REMINDERS)
                },
                onNavigateClockSub = { subIdx ->
                    clockSubTab = subIdx
                    scope.launch { drawerState.close() }
                    navController.navigateSingleTop(RRRoutes.CLOCK_HUB)
                },
                onNavigateWeather = {
                    scope.launch { drawerState.close() }
                    navController.navigateSingleTop(RRRoutes.WEATHER)
                },
                onNavigatePlanner = {
                    scope.launch { drawerState.close() }
                    navController.navigateSingleTop(RRRoutes.WORKSPACE_HUB)
                },
                onNavigateSystemHealth = {
                    scope.launch { drawerState.close() }
                    navController.navigateSingleTop(RRRoutes.SYSTEM_HEALTH)
                },
                onNavigateSettings = {
                    scope.launch { drawerState.close() }
                    navController.navigateSingleTop(RRRoutes.SETTINGS)
                },
                onOpenAbout = {
                    scope.launch { drawerState.close() }
                    showDrawerAboutDialog = true
                },
                onLogout = {
                    scope.launch {
                        drawerState.close()
                        settingsVm.signOut(credentialManager)
                    }
                },
            )
        },
    ) {
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
                                    liveNowMillis = liveNowMillis,
                                    preferences = preferences,
                                    globalConfig = remoteConfig,
                                    timeSyncStatus = timeSyncStatus,
                                    isOwnerAccount = isOwnerAccount,
                                    onOpenDrawer = { scope.launch { drawerState.open() } },
                                    onOpenLiveClock = {
                                        clockSubTab = 0
                                        navController.navigateSingleTop(RRRoutes.CLOCK_HUB)
                                    },
                                    onSyncNetworkTime = mainViewModel::syncNetworkTime,
                                    onCycleClockFace = mainViewModel::cycleClockFaceStyle,
                                    onNavigateReminders = { navController.navigateSingleTop(RRRoutes.REMINDERS) },
                                    onNavigateAlarms = {
                                        clockSubTab = 1
                                        navController.navigateSingleTop(RRRoutes.CLOCK_HUB)
                                    },
                                    onNavigateWeather = { navController.navigateSingleTop(RRRoutes.WEATHER) },
                                    onNavigateWorldClock = {
                                        clockSubTab = 2
                                        navController.navigateSingleTop(RRRoutes.CLOCK_HUB)
                                    },
                                    onNavigateTimer = {
                                        clockSubTab = 3
                                        navController.navigateSingleTop(RRRoutes.CLOCK_HUB)
                                    },
                                    onNavigateStopwatch = {
                                        clockSubTab = 4
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
                                    onNavigateSystemHealth = { navController.navigateSingleTop(RRRoutes.SYSTEM_HEALTH) },
                                    onNavigateSearch = { navController.navigate(RRRoutes.SEARCH) },
                                    onNavigateNotifications = { navController.navigate(RRRoutes.NOTIFICATIONS) },
                                    onNavigateSettings = { navController.navigateSingleTop(RRRoutes.SETTINGS) },
                                    onQuickCompleteReminder = mainViewModel::quickCompleteReminder,
                                    onQuickToggleTask = mainViewModel::quickToggleTask,
                                    onToggleAlarm = mainViewModel::quickToggleAlarm,
                                    onPauseTimer = timerSwVm::pauseTimer,
                                    onResumeTimer = timerSwVm::resumeTimer,
                                    onResetTimer = timerSwVm::resetTimer,
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
                                    nowMillis = liveNowMillis,
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

                                val tabTitles = listOf("Live Clock", "Alarms", "World Clock", "Timer", "Stopwatch")
                                Scaffold(
                                    containerColor = Color.Transparent,
                                    topBar = {
                                        Column(
                                            modifier = Modifier.background(Color(0xFF040B1B)),
                                        ) {
                                            TopAppBar(
                                                title = {
                                                    Text(
                                                        text = tabTitles.getOrElse(clockSubTab) { "Live Clock" },
                                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                        color = Color.White,
                                                    )
                                                },
                                                navigationIcon = {
                                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                                        Icon(Icons.Rounded.Menu, contentDescription = "Open Menu", tint = Color.White)
                                                    }
                                                },
                                                actions = {
                                                    IconButton(onClick = { navController.navigateSingleTop(RRRoutes.HOME) }) {
                                                        Icon(Icons.Rounded.Close, contentDescription = "Close to Home", tint = Color.White)
                                                    }
                                                },
                                                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                                            )
                                            ScrollableTabRow(
                                                selectedTabIndex = clockSubTab.coerceIn(0, tabTitles.lastIndex),
                                                edgePadding = 12.dp,
                                                containerColor = Color.Transparent,
                                                contentColor = Color(0xFF38BDF8),
                                            ) {
                                                tabTitles.forEachIndexed { idx, title ->
                                                    Tab(
                                                        selected = clockSubTab == idx,
                                                        onClick = { clockSubTab = idx },
                                                        text = {
                                                            Text(
                                                                text = title,
                                                                maxLines = 1,
                                                                color = if (clockSubTab == idx) Color(0xFF38BDF8) else Color(0xFF93C5FD),
                                                            )
                                                        },
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
                                            0 -> LuminousLiveClockHero(
                                                nowMillis = clockNow,
                                                zoneId = preferences.primaryClockZone.toZoneId(),
                                                use24Hour = preferences.use24Hour,
                                                datePattern = preferences.dateFormatStyle.pattern,
                                                faceStyle = preferences.clockFaceStyle,
                                                timeSyncStatus = timeSyncStatus,
                                                worldCities = worldCities,
                                                onSyncNetworkTime = weatherClockVm::syncNetworkTime,
                                                onCycleClockFace = mainViewModel::cycleClockFaceStyle,
                                                onSetTimeFormat24 = { is24 ->
                                                    settingsVm.setTimeFormatMode(if (is24) TimeFormatMode.H24 else TimeFormatMode.H12)
                                                },
                                                onOpenWorldClock = { clockSubTab = 2 },
                                            )
                                            1 -> AlarmsTabContent(
                                                alarms = alarms,
                                                history = alarmHistory,
                                                preferences = preferences,
                                                nowMillis = clockNow,
                                                timeSyncStatus = timeSyncStatus,
                                                onSyncNetworkTime = weatherClockVm::syncNetworkTime,
                                                onCycleClockFace = mainViewModel::cycleClockFaceStyle,
                                                onSaveAlarm = alarmsVm::saveAlarm,
                                                onToggleAlarm = alarmsVm::toggleAlarm,
                                                onDeleteAlarm = alarmsVm::deleteAlarm,
                                                onClearHistory = alarmsVm::clearHistory,
                                            )
                                            2 -> WorldClockTabContent(
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
                                            3 -> TimerTabContent(
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

                                BackHandler { navController.popBackStack() }
                                val tabTitles = listOf("Calendar", "Tasks", "Notes", "Habits", "Focus")
                                Scaffold(
                                    containerColor = Color.Transparent,
                                    topBar = {
                                        Column {
                                            TopAppBar(
                                                title = { Text("Planner Workspace") },
                                                navigationIcon = {
                                                    IconButton(onClick = { navController.popBackStack() }) {
                                                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                                                    }
                                                },
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
                                                nowMillis = liveNowMillis,
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
                                BackHandler { navController.popBackStack() }
                                WeatherScreen(
                                    uiState = weatherUi,
                                    preferences = preferences,
                                    nowMillis = liveNowMillis,
                                    onSearchCity = weatherClockVm::searchWeatherCities,
                                    onSelectCity = weatherClockVm::fetchWeatherForCity,
                                    onClearCityResults = weatherClockVm::clearCityResults,
                                    onUseLocation = weatherClockVm::fetchWeatherByCurrentLocation,
                                    onRefresh = weatherClockVm::refreshSavedWeather,
                                    onBack = { navController.popBackStack() },
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
                                        clockSubTab = 1
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
                                val healthReport by settingsVm.healthReport.collectAsStateWithLifecycle()
                                val settingsPrefs by settingsVm.preferences.collectAsStateWithLifecycle()
                                val settingsRemote by settingsVm.remoteConfig.collectAsStateWithLifecycle()
                                val settingsNow by settingsVm.nowMillis.collectAsStateWithLifecycle()
                                val isSavingOwner by settingsVm.isSavingOwnerConfig.collectAsStateWithLifecycle()
                                val ownerStatus by settingsVm.ownerSaveStatus.collectAsStateWithLifecycle()
                                val ownerError by settingsVm.ownerSaveError.collectAsStateWithLifecycle()
                                BackHandler { navController.popBackStack() }
                                SettingsScreen(
                                    preferences = settingsPrefs,
                                    backupStatus = backupStatus,
                                    healthReport = healthReport,
                                    currentUserEmail = currentUser.email,
                                    currentUserName = currentUser.displayName,
                                    isEmailVerified = currentUser.isEmailVerified,
                                    isOwnerAccount = isOwnerAccount,
                                    remoteConfig = settingsRemote,
                                    nowMillis = settingsNow,
                                    baseNetworkNowMillis = settingsVm.currentBaseNetworkMillis(),
                                    isSavingOwnerConfig = isSavingOwner,
                                    ownerSaveStatus = ownerStatus,
                                    ownerSaveError = ownerError,
                                    onPublishOwnerConfig = settingsVm::publishOwnerConfig,
                                    onResetOwnerConfig = settingsVm::resetOwnerConfig,
                                    onClearOwnerMessages = settingsVm::clearOwnerMessages,
                                    onReapplyOwnerPreset = settingsVm::reapplyOwnerPreset,
                                    onSignOut = { settingsVm.signOut(credentialManager) },
                                    onBack = { navController.popBackStack() },
                                    onOpenSystemHealth = { navController.navigateSingleTop(RRRoutes.SYSTEM_HEALTH) },
                                    onSetThemeMode = settingsVm::setThemeMode,
                                    onSetAccentPreset = settingsVm::setAccentPreset,
                                    onSetClockFaceStyle = settingsVm::setClockFaceStyle,
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

                            composable(RRRoutes.SYSTEM_HEALTH) {
                                val healthReport by healthVm.report.collectAsStateWithLifecycle()
                                val healthPrefs by healthVm.preferences.collectAsStateWithLifecycle()
                                val healthSync by healthVm.timeSyncStatus.collectAsStateWithLifecycle()
                                SystemHealthScreen(
                                    report = healthReport,
                                    preferences = healthPrefs,
                                    timeSyncStatus = healthSync,
                                    onRunFullCheck = healthVm::runFullSystemCheck,
                                    onRunSingleCheck = healthVm::runSingleCheck,
                                    onSyncClock = healthVm::syncClockAndRecheck,
                                    onBuildDiagnosticReport = healthVm::buildDiagnosticReportText,
                                    onBack = { navController.popBackStack() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDrawerAboutDialog) {
        AlertDialog(
            onDismissRequest = { showDrawerAboutDialog = false },
            title = { Text("About RR Reminder ${BuildConfig.VERSION_NAME}") },
            text = {
                Text(
                    "RR Reminder 2.0 — Smart Time • Smart Life\n\n" +
                        "• Founder: Ridam Raj\n" +
                        "• Live Network-Synced Clock & Analog Dial\n" +
                        "• Real Open-Meteo GPS Weather & 7-Day Outlook\n" +
                        "• 17-Point Honest RR System Health Diagnostics",
                )
            },
            confirmButton = {
                TextButton(onClick = { showDrawerAboutDialog = false }) { Text("Close") }
            },
        )
    }

    activeRingAlert?.let { alert ->
        AlertDialog(
            onDismissRequest = {},
            icon = {
                Icon(
                    imageVector = if (alert.isTimer) Icons.Rounded.Timer else Icons.Rounded.Alarm,
                    contentDescription = "Ringing alert",
                    tint = extra.accentPrimary,
                    modifier = Modifier.size(36.dp),
                )
            },
            title = {
                Text(
                    text = if (alert.isTimer) "Timer Finished" else "Alarm Ringing",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                )
            },
            text = {
                Text(
                    text = alert.label,
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        container.clearActiveAlert()
                        context.sendBroadcast(
                            Intent(context, ReminderAlarmReceiver::class.java).apply {
                                action = if (alert.isTimer) {
                                    ReminderAlarmReceiver.ACTION_DISMISS_TIMER
                                } else {
                                    ReminderAlarmReceiver.ACTION_DISMISS_ALARM
                                }
                                putExtra(ReminderAlarmReceiver.EXTRA_ID, alert.id)
                            },
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("inapp_ring_dismiss_btn"),
                ) {
                    Text("Dismiss")
                }
            },
            dismissButton = if (!alert.isTimer) {
                {
                    OutlinedButton(
                        onClick = {
                            container.clearActiveAlert()
                            context.sendBroadcast(
                                Intent(context, ReminderAlarmReceiver::class.java).apply {
                                    action = ReminderAlarmReceiver.ACTION_SNOOZE_ALARM
                                    putExtra(ReminderAlarmReceiver.EXTRA_ID, alert.id)
                                    putExtra(ReminderAlarmReceiver.EXTRA_SNOOZE_MINUTES, alert.snoozeMinutes)
                                },
                            )
                        },
                        modifier = Modifier.testTag("inapp_ring_snooze_btn"),
                    ) {
                        Text("Snooze ${alert.snoozeMinutes}m")
                    }
                }
            } else {
                null
            },
        )
    }
}

/**
 * Screen 12: Cosmic Navy Side Navigation Drawer matching RR Reminder 2.0 mockup.
 */
@Composable
private fun RRSideMenuDrawerContent(
    currentRoute: String,
    clockSubTab: Int,
    isOwnerAccount: Boolean,
    onClose: () -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateReminders: () -> Unit,
    onNavigateClockSub: (Int) -> Unit,
    onNavigateWeather: () -> Unit,
    onNavigatePlanner: () -> Unit,
    onNavigateSystemHealth: () -> Unit,
    onNavigateSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onLogout: () -> Unit,
) {
    ModalDrawerSheet(
        drawerContainerColor = Color(0xFF071127),
        drawerContentColor = Color.White,
        modifier = Modifier.width(292.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RRBrandMark(size = 46.dp)
                    Column {
                        Text(
                            text = "RR Reminder",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                        )
                        Text(
                            text = if (isOwnerAccount) "Version ${BuildConfig.VERSION_NAME} • Owner" else "Version ${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF93C5FD),
                        )
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close menu", tint = Color(0xFF93C5FD))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0x2260A5FA))
            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                DrawerMenuItem("Home", Icons.Rounded.Home, currentRoute == RRRoutes.HOME, onNavigateHome)
                DrawerMenuItem("Reminders", Icons.Rounded.Notifications, currentRoute == RRRoutes.REMINDERS, onNavigateReminders)
                DrawerMenuItem("Live Clock", Icons.Rounded.AccessTimeFilled, currentRoute == RRRoutes.CLOCK_HUB && clockSubTab == 0) { onNavigateClockSub(0) }
                DrawerMenuItem("Alarms", Icons.Rounded.Alarm, currentRoute == RRRoutes.CLOCK_HUB && clockSubTab == 1) { onNavigateClockSub(1) }
                DrawerMenuItem("Weather", Icons.Rounded.WbSunny, currentRoute == RRRoutes.WEATHER, onNavigateWeather)
                DrawerMenuItem("World Clock", Icons.Rounded.Public, currentRoute == RRRoutes.CLOCK_HUB && clockSubTab == 2) { onNavigateClockSub(2) }
                DrawerMenuItem("Stopwatch", Icons.Rounded.Timer, currentRoute == RRRoutes.CLOCK_HUB && clockSubTab == 4) { onNavigateClockSub(4) }
                DrawerMenuItem("Timer", Icons.Rounded.HourglassBottom, currentRoute == RRRoutes.CLOCK_HUB && clockSubTab == 3) { onNavigateClockSub(3) }
                DrawerMenuItem("Planner", Icons.Rounded.CalendarMonth, currentRoute == RRRoutes.WORKSPACE_HUB, onNavigatePlanner)
                DrawerMenuItem("System Health", Icons.Rounded.VerifiedUser, currentRoute == RRRoutes.SYSTEM_HEALTH, onNavigateSystemHealth)
                DrawerMenuItem("Settings", Icons.Rounded.Settings, currentRoute == RRRoutes.SETTINGS, onNavigateSettings)
                DrawerMenuItem("About RR Reminder", Icons.Rounded.Info, false, onOpenAbout)
            }

            Spacer(modifier = Modifier.height(12.dp))
            RRGradientButton(
                text = "Logout",
                leadingIcon = Icons.AutoMirrored.Rounded.Logout,
                colors = listOf(Color(0xFF4F46E5), Color(0xFF2563EB)),
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("drawer_logout_btn"),
            )
        }
    }
}

@Composable
private fun DrawerMenuItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bgModifier = if (selected) {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF4F46E5), Color(0xFF2563EB))))
            .clickable(onClick = onClick)
    } else {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    }

    Row(
        modifier = bgModifier.padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) Color.White else Color(0xFF93C5FD),
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = if (selected) Color.White else Color(0xFFD6E4FF),
            maxLines = 1,
        )
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
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
