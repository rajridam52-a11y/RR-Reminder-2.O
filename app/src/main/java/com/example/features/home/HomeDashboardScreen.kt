package com.example.features.home

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.common.VoiceReminderParser
import com.example.core.database.ReminderEntity
import com.example.core.database.ReminderPriority
import com.example.core.database.TaskEntity
import com.example.core.database.TimerEntity
import com.example.core.database.WorldClockCityEntity
import com.example.core.datastore.DashboardWidgetId
import com.example.core.datastore.PrimaryClockZone
import com.example.core.datastore.UserPreferences
import com.example.core.firebase.GlobalAppConfig
import com.example.core.health.HealthStatus
import com.example.core.health.SystemHealthReport
import com.example.core.network.NetworkTimeSyncStatus
import com.example.core.network.WeatherCodeInterpreter
import com.example.core.network.WeatherUnits
import com.example.core.notifications.ScheduleCalculator
import com.example.features.health.healthScoreColor
import com.example.ui.components.AuroraClockDisplay
import com.example.ui.components.RRBrandMark
import com.example.ui.components.RRCard
import com.example.ui.components.RRCircularProgressRing
import com.example.ui.components.RRMetricBadge
import com.example.ui.components.RRSectionHeader
import com.example.ui.components.RRStatusPill
import com.example.ui.theme.LocalRRExtraColors
import com.example.ui.theme.TabularClockMedium
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboardScreen(
    homeState: HomeUiState,
    preferences: UserPreferences,
    globalConfig: GlobalAppConfig? = null,
    timeSyncStatus: NetworkTimeSyncStatus = NetworkTimeSyncStatus(),
    onSyncNetworkTime: () -> Unit = {},
    onCycleClockFace: () -> Unit = {},
    onNavigateReminders: () -> Unit,
    onNavigateAlarms: () -> Unit,
    onNavigateWeather: () -> Unit,
    onNavigateWorldClock: () -> Unit,
    onNavigateTimer: () -> Unit,
    onNavigateStopwatch: () -> Unit,
    onNavigateCalendar: () -> Unit,
    onNavigateTasks: () -> Unit,
    onNavigateNotes: () -> Unit,
    onNavigateHabits: () -> Unit,
    onNavigateFocus: () -> Unit,
    onNavigateSystemHealth: () -> Unit = {},
    onNavigateSearch: () -> Unit,
    onNavigateNotifications: () -> Unit,
    onNavigateSettings: () -> Unit,
    onQuickCompleteReminder: (ReminderEntity) -> Unit,
    onQuickToggleTask: (TaskEntity) -> Unit,
    onQuickSaveReminder: (ReminderEntity) -> Unit,
    onQuickSaveTask: (TaskEntity) -> Unit,
    onMoveWidget: (Int, Int) -> Unit,
    onToggleWidgetVisibility: (String) -> Unit,
    onResetDashboard: () -> Unit,
) {
    val extra = LocalRRExtraColors.current
    var showCustomizeSheet by remember { mutableStateOf(false) }
    var showQuickAddDialog by remember { mutableStateOf(false) }
    var quickAddInitialTitle by remember { mutableStateOf("") }
    var quickAddInitialMillis by remember { mutableLongStateOf(homeState.nowMillis + 3_600_000L) }
    var voiceAmbiguityNote by remember { mutableStateOf<String?>(null) }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                val parsed = VoiceReminderParser.parse(spoken)
                quickAddInitialTitle = parsed.title
                quickAddInitialMillis = parsed.suggestedTriggerMillis
                voiceAmbiguityNote = if (parsed.isAmbiguousTime) parsed.detectedTimeDescription else null
                showQuickAddDialog = true
            }
        }
    }

    val visibleWidgets = remember(preferences.dashboardWidgetOrder, preferences.hiddenDashboardWidgets) {
        preferences.dashboardWidgetOrder.filter { it !in preferences.hiddenDashboardWidgets }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RRBrandMark(size = 34.dp)
                        Column {
                            Text(
                                text = "RR Reminder",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                            )
                            Text(
                                text = "Your Time. Your Control.",
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.accentPrimary,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateSearch,
                        modifier = Modifier.testTag("home_search_btn"),
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = "Smart Search & Assistant")
                    }
                    IconButton(
                        onClick = onNavigateNotifications,
                        modifier = Modifier.testTag("home_notifications_btn"),
                    ) {
                        BadgedBox(
                            badge = {
                                if (homeState.unreadNotificationsCount > 0) {
                                    Badge { Text("${homeState.unreadNotificationsCount.coerceAtMost(99)}") }
                                }
                            },
                        ) {
                            Icon(Icons.Rounded.Notifications, contentDescription = "Notification Center")
                        }
                    }
                    IconButton(
                        onClick = { showCustomizeSheet = true },
                        modifier = Modifier.testTag("home_customize_btn"),
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Customize Dashboard")
                    }
                    IconButton(
                        onClick = onNavigateSettings,
                        modifier = Modifier.testTag("home_settings_btn"),
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    quickAddInitialTitle = ""
                    quickAddInitialMillis = homeState.nowMillis + 3_600_000L
                    voiceAmbiguityNote = null
                    showQuickAddDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("home_quick_add_fab"),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Quick Add")
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (globalConfig != null &&
                globalConfig.syncOverrideEnabled &&
                globalConfig.bannerEnabled &&
                globalConfig.bannerMessage.isNotBlank()
            ) {
                item(key = "owner_broadcast_banner") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = extra.accentPrimary.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, extra.accentPrimary.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp)
                            .testTag("home_owner_banner"),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Campaign,
                                contentDescription = "Owner Broadcast",
                                tint = extra.accentPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "LIVE OWNER NOTICE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extra.accentPrimary,
                                )
                                Text(
                                    text = globalConfig.bannerMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
            items(visibleWidgets, key = { it }) { widgetKey ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp),
                ) {
                    when (widgetKey) {
                        DashboardWidgetId.BRIEFING.key -> DailyBriefingHeroCard(
                            homeState = homeState,
                            preferences = preferences,
                            timeSyncStatus = timeSyncStatus,
                            onSyncNetworkTime = onSyncNetworkTime,
                            onCycleClockFace = onCycleClockFace,
                            onOpenOverdue = onNavigateReminders,
                            onOpenWorldClock = onNavigateWorldClock,
                            onVoiceQuickAdd = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(
                                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                    )
                                    putExtra(
                                        RecognizerIntent.EXTRA_PROMPT,
                                        "Speak reminder title and time...",
                                    )
                                }
                                runCatching { voiceLauncher.launch(intent) }.onFailure {
                                    quickAddInitialTitle = ""
                                    voiceAmbiguityNote = "Voice recognition unavailable on this device — enter reminder manually."
                                    showQuickAddDialog = true
                                }
                            },
                        )

                        DashboardWidgetId.QUICK_ACTIONS.key -> QuickActionsStrip(
                            onReminders = onNavigateReminders,
                            onAlarms = onNavigateAlarms,
                            onTimer = onNavigateTimer,
                            onStopwatch = onNavigateStopwatch,
                            onWorldClock = onNavigateWorldClock,
                            onCalendar = onNavigateCalendar,
                            onTasks = onNavigateTasks,
                            onNotes = onNavigateNotes,
                            onHabits = onNavigateHabits,
                            onFocus = onNavigateFocus,
                            onSystemHealth = onNavigateSystemHealth,
                        )

                        DashboardWidgetId.UPCOMING_REMINDERS.key -> UpcomingRemindersCard(
                            reminders = homeState.upcomingReminders,
                            nowMillis = homeState.nowMillis,
                            preferences = preferences,
                            onViewAll = onNavigateReminders,
                            onComplete = onQuickCompleteReminder,
                        )

                        DashboardWidgetId.NEXT_ALARM.key -> NextAlarmDashboardCard(
                            nextAlarm = homeState.nextAlarm,
                            nowMillis = homeState.nowMillis,
                            preferences = preferences,
                            onClick = onNavigateAlarms,
                        )

                        DashboardWidgetId.ACTIVE_TIMERS.key -> ActiveTimersDashboardCard(
                            timers = homeState.activeTimers,
                            nowMillis = homeState.nowMillis,
                            onClick = onNavigateTimer,
                        )

                        DashboardWidgetId.WEATHER.key -> WeatherSummaryDashboardCard(
                            homeState = homeState,
                            preferences = preferences,
                            onClick = onNavigateWeather,
                        )

                        DashboardWidgetId.WORLD_CLOCK.key -> WorldClockPreviewCard(
                            cities = homeState.worldClockCities,
                            nowMillis = homeState.nowMillis,
                            preferences = preferences,
                            onClick = onNavigateWorldClock,
                        )

                        DashboardWidgetId.TODAY_TASKS.key -> TodayTasksDashboardCard(
                            homeState = homeState,
                            onViewAll = onNavigateTasks,
                            onToggleTask = onQuickToggleTask,
                        )

                        DashboardWidgetId.SYSTEM_HEALTH.key -> SystemHealthDashboardCard(
                            report = homeState.healthReport,
                            onClick = onNavigateSystemHealth,
                        )

                        DashboardWidgetId.RECENT_ACTIVITY.key -> RecentActivityCard(
                            items = homeState.recentCompleted,
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(84.dp)) }
        }
    }

    if (showCustomizeSheet) {
        ModalBottomSheet(onDismissRequest = { showCustomizeSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Customize Dashboard", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onResetDashboard) { Text("Reset") }
                }
                Text(
                    "Reorder or hide dashboard cards to match your workflow.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                preferences.dashboardWidgetOrder.forEachIndexed { index, key ->
                    val widget = DashboardWidgetId.fromKey(key) ?: return@forEachIndexed
                    val isVisible = key !in preferences.hiddenDashboardWidgets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Switch(
                            checked = isVisible,
                            onCheckedChange = { onToggleWidgetVisibility(key) },
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = widget.title,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { onMoveWidget(index, -1) },
                            enabled = index > 0,
                        ) {
                            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move up")
                        }
                        IconButton(
                            onClick = { onMoveWidget(index, 1) },
                            enabled = index < preferences.dashboardWidgetOrder.lastIndex,
                        ) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move down")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showQuickAddDialog) {
        val primaryZone = remember(preferences.primaryClockZone) { preferences.primaryClockZone.toZoneId() }
        QuickAddDialog(
            initialTitle = quickAddInitialTitle,
            initialMillis = quickAddInitialMillis,
            nowMillis = homeState.nowMillis,
            ambiguityNote = voiceAmbiguityNote,
            preferences = preferences,
            onDismiss = { showQuickAddDialog = false },
            onSaveReminder = { title, triggerMs, priority ->
                onQuickSaveReminder(
                    ReminderEntity(
                        title = title,
                        triggerAtMillis = triggerMs,
                        priority = priority.name,
                        zoneId = primaryZone.id,
                    ),
                )
                showQuickAddDialog = false
            },
            onSaveTask = { title, priority ->
                val tomorrowDue = Instant.ofEpochMilli(homeState.nowMillis)
                    .atZone(primaryZone)
                    .toLocalDate()
                    .plusDays(1)
                    .atTime(23, 59)
                    .atZone(primaryZone)
                    .toInstant()
                    .toEpochMilli()
                onQuickSaveTask(
                    TaskEntity(
                        title = title,
                        priority = priority.name,
                        dueDateMillis = tomorrowDue,
                    ),
                )
                showQuickAddDialog = false
            },
        )
    }
}

@Composable
private fun DailyBriefingHeroCard(
    homeState: HomeUiState,
    preferences: UserPreferences,
    timeSyncStatus: NetworkTimeSyncStatus,
    onSyncNetworkTime: () -> Unit,
    onCycleClockFace: () -> Unit,
    onOpenOverdue: () -> Unit,
    onOpenWorldClock: () -> Unit,
    onVoiceQuickAdd: () -> Unit,
) {
    val extra = LocalRRExtraColors.current
    val primaryZone = remember(preferences.primaryClockZone) {
        preferences.primaryClockZone.toZoneId()
    }
    val hour = remember(homeState.nowMillis, primaryZone) {
        Instant.ofEpochMilli(homeState.nowMillis).atZone(primaryZone).hour
    }
    val greeting = when (hour) {
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        in 17..21 -> "Good Evening"
        else -> "Good Night"
    }

    Card(
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
            1.25.dp,
            Brush.linearGradient(listOf(extra.accentPrimary.copy(alpha = 0.55f), extra.accentSecondary.copy(alpha = 0.45f))),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_rr_hero_banner),
                contentDescription = "Daily briefing backdrop",
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xF0050C1F),
                                Color(0xD809142E),
                                Color(0xB20D1B3B),
                            ),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val zoneLabel = if (preferences.primaryClockZone == PrimaryClockZone.INDIA_IST) {
                            "INDIA (IST)"
                        } else {
                            "LOCAL (${primaryZone.id})"
                        }
                        val syncText = when {
                            timeSyncStatus.ownerOverrideActive && !timeSyncStatus.ownerOverrideLabel.isNullOrBlank() ->
                                timeSyncStatus.ownerOverrideLabel
                            timeSyncStatus.isSynced -> timeSyncStatus.sourceLabel
                            timeSyncStatus.isSyncing -> "Syncing..."
                            else -> "Device Clock"
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "$greeting • $zoneLabel",
                                style = MaterialTheme.typography.labelMedium,
                                color = extra.accentPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Surface(
                                shape = CircleShape,
                                color = extra.accentPrimary.copy(alpha = 0.16f),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(onClick = onSyncNetworkTime)
                                    .testTag("home_sync_time_pill"),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.Sync,
                                        contentDescription = "Sync real time from Google",
                                        tint = extra.accentPrimary,
                                        modifier = Modifier.size(11.dp),
                                    )
                                    Text(
                                        text = syncText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                    )
                                }
                            }
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.12f),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(onClick = onCycleClockFace)
                                    .testTag("home_clock_face_pill"),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.WatchLater,
                                        contentDescription = "Switch clock face",
                                        tint = extra.accentSecondary,
                                        modifier = Modifier.size(11.dp),
                                    )
                                    Text(
                                        text = preferences.clockFaceStyle.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        AuroraClockDisplay(
                            nowMillis = homeState.nowMillis,
                            zoneId = primaryZone,
                            use24Hour = preferences.use24Hour,
                            datePattern = preferences.dateFormatStyle.pattern,
                            faceStyle = preferences.clockFaceStyle,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RRCircularProgressRing(
                            progress = homeState.todayProgressFraction,
                            size = 76.dp,
                            strokeWidth = 7.dp,
                            trackColor = Color.White.copy(alpha = 0.16f),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${(homeState.todayProgressFraction * 100).toInt()}%",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                )
                                Text(
                                    text = "${homeState.todayCompletedCount}/${homeState.todayTotalCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFB8C7EA),
                                )
                            }
                        }
                        FilledTonalIconButton(
                            onClick = onVoiceQuickAdd,
                            modifier = Modifier.testTag("home_voice_add_btn"),
                        ) {
                            Icon(Icons.Rounded.Mic, contentDescription = "Voice Quick Add")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                val systemZone = ZoneId.systemDefault()
                val showDeviceStrip = preferences.primaryClockZone == PrimaryClockZone.INDIA_IST &&
                    systemZone != ScheduleCalculator.INDIA_ZONE
                val stripZone = if (showDeviceStrip) systemZone else ScheduleCalculator.INDIA_ZONE
                val stripTitle = if (showDeviceStrip) {
                    "DEVICE LOCAL TIME (${systemZone.id})"
                } else {
                    "INDIA REAL TIME (IST • UTC+5:30)"
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, extra.accentPrimary.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenWorldClock)
                        .testTag("home_india_time_card"),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                Icons.Rounded.Public,
                                contentDescription = stripTitle,
                                tint = extra.accentPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                            Column {
                                Text(
                                    text = stripTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extra.accentPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = ScheduleCalculator.formatDate(
                                        millis = homeState.nowMillis,
                                        pattern = "EEEE, ${preferences.dateFormatStyle.pattern}",
                                        zoneId = stripZone,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFD6E3FF),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Text(
                            text = ScheduleCalculator.formatTime(
                                millis = homeState.nowMillis,
                                use24Hour = preferences.use24Hour,
                                zoneId = stripZone,
                                includeSeconds = true,
                            ),
                            style = TabularClockMedium.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
                            color = Color.White,
                        )
                    }
                }

                if (homeState.overdueRemindersCount > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onOpenOverdue),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = "${homeState.overdueRemindersCount} overdue reminder(s) • Tap to review",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RRMetricBadge(
                        label = "Reminders",
                        value = "${homeState.totalRemindersCount}",
                        color = extra.accentPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    RRMetricBadge(
                        label = "Pending",
                        value = "${homeState.pendingTasksCount}",
                        color = extra.warning,
                        modifier = Modifier.weight(1f),
                    )
                    RRMetricBadge(
                        label = "Done Tasks",
                        value = "${homeState.completedTasksCount}",
                        color = extra.accentSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    RRMetricBadge(
                        label = "Habits",
                        value = "${homeState.activeHabitsCount}",
                        color = extra.success,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private data class QuickModuleTile(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val tag: String,
)

@Composable
private fun QuickActionsStrip(
    onReminders: () -> Unit,
    onAlarms: () -> Unit,
    onTimer: () -> Unit,
    onStopwatch: () -> Unit,
    onWorldClock: () -> Unit,
    onCalendar: () -> Unit,
    onTasks: () -> Unit,
    onNotes: () -> Unit,
    onHabits: () -> Unit,
    onFocus: () -> Unit,
    onSystemHealth: () -> Unit,
) {
    val extra = LocalRRExtraColors.current
    val actions = listOf(
        QuickModuleTile("Reminders", Icons.Rounded.NotificationsActive, onReminders, "qa_reminders"),
        QuickModuleTile("Alarms", Icons.Rounded.Alarm, onAlarms, "qa_alarms"),
        QuickModuleTile("Timer", Icons.Rounded.Timer, onTimer, "qa_timer"),
        QuickModuleTile("Stopwatch", Icons.Rounded.AvTimer, onStopwatch, "qa_stopwatch"),
        QuickModuleTile("World Clock", Icons.Rounded.Public, onWorldClock, "qa_worldclock"),
        QuickModuleTile("System Health", Icons.Rounded.HealthAndSafety, onSystemHealth, "qa_system_health"),
        QuickModuleTile("Calendar", Icons.Rounded.CalendarMonth, onCalendar, "qa_calendar"),
        QuickModuleTile("Tasks", Icons.Rounded.CheckCircleOutline, onTasks, "qa_tasks"),
        QuickModuleTile("Notes", Icons.Rounded.EditNote, onNotes, "qa_notes"),
        QuickModuleTile("Habits", Icons.Rounded.TrackChanges, onHabits, "qa_habits"),
        QuickModuleTile("Focus", Icons.Rounded.SelfImprovement, onFocus, "qa_focus"),
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RRSectionHeader(title = "Modules & Quick Actions")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            actions.forEach { item ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = extra.glassFill,
                    border = BorderStroke(1.dp, extra.cardBorder),
                    modifier = Modifier
                        .width(88.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(onClick = item.onClick)
                        .testTag(item.tag),
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            extra.accentPrimary.copy(alpha = 0.22f),
                                            extra.accentSecondary.copy(alpha = 0.16f),
                                        ),
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = extra.accentPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpcomingRemindersCard(
    reminders: List<ReminderEntity>,
    nowMillis: Long,
    preferences: UserPreferences,
    onViewAll: () -> Unit,
    onComplete: (ReminderEntity) -> Unit,
) {
    val extra = LocalRRExtraColors.current
    RRCard {
        RRSectionHeader(
            title = "Upcoming Reminders",
            actionLabel = "See All",
            onActionClick = onViewAll,
        )
        Spacer(modifier = Modifier.height(6.dp))
        if (reminders.isEmpty()) {
            Text(
                text = "No upcoming reminders scheduled.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val primaryZone = remember(preferences.primaryClockZone) { preferences.primaryClockZone.toZoneId() }
            reminders.forEach { item ->
                val targetMs = item.snoozedUntilMillis ?: item.triggerAtMillis
                val isOverdue = !item.isCompleted && targetMs < nowMillis
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = item.isCompleted,
                        onCheckedChange = { onComplete(item) },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = "${ScheduleCalculator.formatDate(targetMs, preferences.dateFormatStyle.pattern, primaryZone)} • ${ScheduleCalculator.formatTime(targetMs, preferences.use24Hour, primaryZone)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isOverdue) {
                        RRStatusPill(text = "Overdue", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    RRStatusPill(
                        text = ReminderPriority.fromName(item.priority).label,
                        color = extra.accentPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun NextAlarmDashboardCard(
    nextAlarm: com.example.core.database.AlarmEntity?,
    nowMillis: Long,
    preferences: UserPreferences,
    onClick: () -> Unit,
) {
    val extra = LocalRRExtraColors.current
    RRCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Rounded.Alarm,
                    contentDescription = null,
                    tint = extra.accentPrimary,
                    modifier = Modifier.size(28.dp),
                )
                Column {
                    Text("Next Scheduled Alarm", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (nextAlarm != null && nextAlarm.nextTriggerAtMillis != null) {
                        Text(
                            text = "${ScheduleCalculator.formatHourMinute(nextAlarm.hour, nextAlarm.minute, preferences.use24Hour)} • ${nextAlarm.label}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = ScheduleCalculator.formatCountdownDelta(nextAlarm.nextTriggerAtMillis, nowMillis),
                            style = MaterialTheme.typography.bodySmall,
                            color = extra.accentPrimary,
                        )
                    } else {
                        Text("No active alarms", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Open Alarms")
        }
    }
}

@Composable
private fun WeatherSummaryDashboardCard(
    homeState: HomeUiState,
    preferences: UserPreferences,
    onClick: () -> Unit,
) {
    val extra = LocalRRExtraColors.current
    val weather = homeState.weather
    RRCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Rounded.WbSunny,
                    contentDescription = null,
                    tint = extra.warning,
                    modifier = Modifier.size(30.dp),
                )
                Column {
                    if (weather != null) {
                        val tempStr = WeatherUnits.formatTemp(weather.currentTempC, preferences.useFahrenheit)
                        val feelsStr = WeatherUnits.formatTemp(weather.feelsLikeC, preferences.useFahrenheit)
                        val windStr = WeatherUnits.formatWind(weather.windKmh, preferences.useMph, weather.windDirectionDeg)
                        Text("${weather.cityName} • $tempStr", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${WeatherCodeInterpreter.describe(weather.weatherCode)} • Feels $feelsStr • Wind $windStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text("Weather Center", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tap to load live GPS weather or search a city",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Open Weather")
        }
    }
}

@Composable
private fun TodayTasksDashboardCard(
    homeState: HomeUiState,
    onViewAll: () -> Unit,
    onToggleTask: (TaskEntity) -> Unit,
) {
    RRCard {
        RRSectionHeader(
            title = "Today's Tasks",
            actionLabel = "Manage",
            onActionClick = onViewAll,
        )
        Spacer(modifier = Modifier.height(6.dp))
        if (homeState.todayTasks.isEmpty()) {
            Text(
                text = "No open tasks. Tap + to create one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            homeState.todayTasks.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = item.task.isCompleted,
                        onCheckedChange = { onToggleTask(item.task) },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.task.title, style = MaterialTheme.typography.titleSmall)
                        if (item.subtasks.isNotEmpty()) {
                            val done = item.subtasks.count { it.isDone }
                            Text(
                                "$done/${item.subtasks.size} subtasks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentActivityCard(items: List<String>) {
    val extra = LocalRRExtraColors.current
    RRCard {
        Text("Recently Completed", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        if (items.isEmpty()) {
            Text(
                text = "Completed reminders and tasks will appear here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            items.forEach { entry ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 3.dp),
                ) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = extra.success,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(entry, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ActiveTimersDashboardCard(
    timers: List<TimerEntity>,
    nowMillis: Long,
    onClick: () -> Unit,
) {
    val extra = LocalRRExtraColors.current
    RRCard(onClick = onClick) {
        RRSectionHeader(
            title = "Active Timers",
            actionLabel = "Timers",
            onActionClick = onClick,
        )
        Spacer(modifier = Modifier.height(6.dp))
        if (timers.isEmpty()) {
            Text(
                text = "No running timers. Tap to start a countdown or preset.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            timers.forEach { timer ->
                val remaining = if (timer.isRunning && timer.endEpochMillis != null) {
                    (timer.endEpochMillis - nowMillis).coerceAtLeast(0L)
                } else {
                    timer.remainingMillis
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            Icons.Rounded.Timer,
                            contentDescription = null,
                            tint = if (timer.isRunning) extra.accentPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = timer.label,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    RRStatusPill(
                        text = ScheduleCalculator.formatDurationMillis(remaining),
                        color = if (timer.isRunning) extra.accentPrimary else extra.warning,
                    )
                }
            }
        }
    }
}

@Composable
private fun WorldClockPreviewCard(
    cities: List<WorldClockCityEntity>,
    nowMillis: Long,
    preferences: UserPreferences,
    onClick: () -> Unit,
) {
    val extra = LocalRRExtraColors.current
    RRCard(onClick = onClick) {
        RRSectionHeader(
            title = "World Clock Previews",
            actionLabel = "Manage",
            onActionClick = onClick,
        )
        Spacer(modifier = Modifier.height(6.dp))
        if (cities.isEmpty()) {
            Text(
                text = "Add global cities to track live timezones and day/night cycles.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                cities.forEach { city ->
                    val zone = remember(city.zoneId) {
                        runCatching { ZoneId.of(city.zoneId) }.getOrElse { ZoneId.systemDefault() }
                    }
                    val zdt = Instant.ofEpochMilli(nowMillis).atZone(zone)
                    val isDay = zdt.hour in 6..18
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = extra.glassElevatedFill,
                        border = BorderStroke(1.dp, extra.cardBorder),
                        modifier = Modifier.width(142.dp),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = if (isDay) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                    contentDescription = if (isDay) "Daytime" else "Nighttime",
                                    tint = if (isDay) extra.warning else extra.info,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = city.cityName,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = ScheduleCalculator.formatTime(
                                    millis = nowMillis,
                                    use24Hour = preferences.use24Hour,
                                    zoneId = zone,
                                ),
                                style = TabularClockMedium,
                            )
                            Text(
                                text = if (zone.rules.isDaylightSavings(Instant.ofEpochMilli(nowMillis))) "DST Active" else zone.id.substringAfter("/"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemHealthDashboardCard(
    report: SystemHealthReport,
    onClick: () -> Unit,
) {
    val extra = LocalRRExtraColors.current
    val scoreColor = healthScoreColor(report.overallHealthPercent)
    RRCard(
        onClick = onClick,
        modifier = Modifier.testTag("home_system_health_card"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                RRCircularProgressRing(
                    progress = report.overallHealthPercent / 100f,
                    size = 48.dp,
                    strokeWidth = 5.dp,
                    primaryColor = scoreColor,
                    secondaryColor = extra.accentSecondary,
                ) {
                    Text(
                        text = "${report.overallHealthPercent}%",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = scoreColor,
                    )
                }
                Column {
                    Text("RR System Health", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = when {
                            report.isScanning -> "Running diagnostics..."
                            report.issueCount > 0 -> "${report.issueCount} attention item(s) • ${report.workingCount}/${report.items.size} working"
                            report.testedCount > 0 -> "${report.workingCount}/${report.items.size} verified healthy"
                            else -> "Tap to inspect all 17 system modules"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (report.issueCount > 0) {
                    RRStatusPill(
                        text = "${report.issueCount} Issue",
                        color = if (report.items.any { it.status == HealthStatus.FAILED }) {
                            MaterialTheme.colorScheme.error
                        } else {
                            extra.warning
                        },
                    )
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = "Open RR System Health")
            }
        }
    }
}

@Composable
private fun QuickAddDialog(
    initialTitle: String,
    initialMillis: Long,
    nowMillis: Long,
    ambiguityNote: String?,
    preferences: UserPreferences,
    onDismiss: () -> Unit,
    onSaveReminder: (String, Long, ReminderPriority) -> Unit,
    onSaveTask: (String, ReminderPriority) -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var isReminder by remember { mutableStateOf(true) }
    var delayHours by remember { mutableIntStateOf(1) }
    var priority by remember { mutableStateOf(ReminderPriority.MEDIUM) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("RR Quick Add") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (ambiguityNote != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            text = ambiguityNote,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isReminder,
                        onClick = { isReminder = true },
                        label = { Text("Reminder") },
                    )
                    FilterChip(
                        selected = !isReminder,
                        onClick = { isReminder = false },
                        label = { Text("Task") },
                    )
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(if (isReminder) "Reminder title" else "Task title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_add_title_input"),
                )
                val primaryZone = remember(preferences.primaryClockZone) { preferences.primaryClockZone.toZoneId() }
                if (isReminder) {
                    Text(
                        text = if (initialTitle.isNotBlank() && ambiguityNote == null) {
                            "Scheduled: ${ScheduleCalculator.formatTime(initialMillis, preferences.use24Hour, primaryZone)}"
                        } else {
                            "Schedule in: $delayHours hour(s)"
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 3, 24).forEach { hrs ->
                            FilterChip(
                                selected = delayHours == hrs,
                                onClick = { delayHours = hrs },
                                label = { Text(if (hrs == 24) "Tomorrow" else "+${hrs}h") },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(ReminderPriority.LOW, ReminderPriority.MEDIUM, ReminderPriority.HIGH).forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p.label) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = title.trim()
                    if (trimmed.isNotBlank()) {
                        if (isReminder) {
                            val targetMs = if (initialTitle.isNotBlank() && ambiguityNote == null) {
                                initialMillis
                            } else {
                                nowMillis + delayHours * 3_600_000L
                            }
                            onSaveReminder(trimmed, targetMs, priority)
                        } else {
                            onSaveTask(trimmed, priority)
                        }
                    }
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("quick_add_save_btn"),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
