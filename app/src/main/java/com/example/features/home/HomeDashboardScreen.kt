package com.example.features.home

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.core.datastore.DashboardWidgetId
import com.example.core.datastore.UserPreferences
import com.example.core.network.WeatherCodeInterpreter
import com.example.core.notifications.ScheduleCalculator
import com.example.ui.components.RRBrandMark
import com.example.ui.components.RRCard
import com.example.ui.components.RRMetricBadge
import com.example.ui.components.RRSectionHeader
import com.example.ui.components.RRStatusPill
import com.example.ui.theme.LocalRRExtraColors
import com.example.ui.theme.TabularClockLarge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboardScreen(
    homeState: HomeUiState,
    preferences: UserPreferences,
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
    var showCustomizeSheet by remember { mutableStateOf(false) }
    var showQuickAddDialog by remember { mutableStateOf(false) }
    var quickAddInitialTitle by remember { mutableStateOf("") }
    var quickAddInitialMillis by remember { mutableLongStateOf(System.currentTimeMillis() + 3_600_000L) }
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateSearch,
                        modifier = Modifier.testTag("home_search_btn"),
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = "Smart Search")
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
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    quickAddInitialTitle = ""
                    quickAddInitialMillis = System.currentTimeMillis() + 3_600_000L
                    voiceAmbiguityNote = null
                    showQuickAddDialog = true
                },
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
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
                        )

                        DashboardWidgetId.UPCOMING_REMINDERS.key -> UpcomingRemindersCard(
                            reminders = homeState.upcomingReminders,
                            preferences = preferences,
                            onViewAll = onNavigateReminders,
                            onComplete = onQuickCompleteReminder,
                        )

                        DashboardWidgetId.NEXT_ALARM.key -> NextAlarmDashboardCard(
                            nextAlarm = homeState.nextAlarm,
                            preferences = preferences,
                            onClick = onNavigateAlarms,
                        )

                        DashboardWidgetId.WEATHER.key -> WeatherSummaryDashboardCard(
                            homeState = homeState,
                            onClick = onNavigateWeather,
                        )

                        DashboardWidgetId.TODAY_TASKS.key -> TodayTasksDashboardCard(
                            homeState = homeState,
                            onViewAll = onNavigateTasks,
                            onToggleTask = onQuickToggleTask,
                        )

                        DashboardWidgetId.RECENT_ACTIVITY.key -> RecentActivityCard(
                            items = homeState.recentCompleted,
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(76.dp)) }
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
        QuickAddDialog(
            initialTitle = quickAddInitialTitle,
            initialMillis = quickAddInitialMillis,
            ambiguityNote = voiceAmbiguityNote,
            onDismiss = { showQuickAddDialog = false },
            onSaveReminder = { title, triggerMs, priority ->
                onQuickSaveReminder(
                    ReminderEntity(
                        title = title,
                        triggerAtMillis = triggerMs,
                        priority = priority.name,
                    ),
                )
                showQuickAddDialog = false
            },
            onSaveTask = { title, priority ->
                onQuickSaveTask(
                    TaskEntity(
                        title = title,
                        priority = priority.name,
                        dueDateMillis = System.currentTimeMillis() + 86_400_000L,
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
    onVoiceQuickAdd: () -> Unit,
) {
    val extra = LocalRRExtraColors.current
    Card(
        shape = MaterialTheme.shapes.large,
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
                                Color(0xEE080F24),
                                Color(0xCC0B142E),
                                Color(0x990D1836),
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
                    verticalAlignment = Alignment.Top,
                ) {
                    Column {
                        Text(
                            text = "RR DAILY BRIEFING",
                            style = MaterialTheme.typography.labelMedium,
                            color = extra.accentPrimary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = ScheduleCalculator.formatTime(
                                millis = homeState.nowMillis,
                                use24Hour = preferences.use24Hour,
                                includeSeconds = true,
                            ),
                            style = TabularClockLarge,
                            color = Color.White,
                        )
                        Text(
                            text = ScheduleCalculator.formatDate(
                                homeState.nowMillis,
                                "EEEE, ${preferences.dateFormatStyle.pattern}",
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFB8C7EA),
                        )
                    }
                    FilledTonalIconButton(
                        onClick = onVoiceQuickAdd,
                        modifier = Modifier.testTag("home_voice_add_btn"),
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = "Voice Quick Add")
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RRMetricBadge(
                        label = "Reminders",
                        value = "${homeState.upcomingReminders.size}",
                        color = extra.accentPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    RRMetricBadge(
                        label = "Tasks",
                        value = "${homeState.todayTasks.size}",
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
) {
    val actions = listOf(
        QuickModuleTile("Reminders", Icons.Rounded.NotificationsActive, onReminders, "qa_reminders"),
        QuickModuleTile("Alarms", Icons.Rounded.Alarm, onAlarms, "qa_alarms"),
        QuickModuleTile("Timer", Icons.Rounded.Timer, onTimer, "qa_timer"),
        QuickModuleTile("Stopwatch", Icons.Rounded.AvTimer, onStopwatch, "qa_stopwatch"),
        QuickModuleTile("World Clock", Icons.Rounded.Public, onWorldClock, "qa_worldclock"),
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
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .width(86.dp)
                        .clip(RoundedCornerShape(16.dp))
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
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = MaterialTheme.colorScheme.primary,
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
    preferences: UserPreferences,
    onViewAll: () -> Unit,
    onComplete: (ReminderEntity) -> Unit,
) {
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
            reminders.forEach { item ->
                val targetMs = item.snoozedUntilMillis ?: item.triggerAtMillis
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
                            text = "${ScheduleCalculator.formatDate(targetMs, preferences.dateFormatStyle.pattern)} • ${ScheduleCalculator.formatTime(targetMs, preferences.use24Hour)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    RRStatusPill(
                        text = ReminderPriority.fromName(item.priority).label,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun NextAlarmDashboardCard(
    nextAlarm: com.example.core.database.AlarmEntity?,
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
                            text = ScheduleCalculator.formatCountdownDelta(nextAlarm.nextTriggerAtMillis),
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
                        Text("${weather.cityName} • ${weather.currentTempC.toInt()}°C", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${WeatherCodeInterpreter.describe(weather.weatherCode)} • Feels ${weather.feelsLikeC.toInt()}°C • Wind ${weather.windKmh.toInt()} km/h",
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
private fun QuickAddDialog(
    initialTitle: String,
    initialMillis: Long,
    ambiguityNote: String?,
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
                if (isReminder) {
                    Text(
                        text = if (initialTitle.isNotBlank() && ambiguityNote == null) {
                            "Scheduled: ${ScheduleCalculator.formatTime(initialMillis, false)}"
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
                                System.currentTimeMillis() + delayHours * 3_600_000L
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
