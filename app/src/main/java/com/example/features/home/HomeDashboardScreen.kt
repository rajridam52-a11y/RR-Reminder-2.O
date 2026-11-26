package com.example.features.home

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.common.VoiceReminderParser
import com.example.core.database.AlarmEntity
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
import com.example.ui.components.RRCard
import com.example.ui.components.RRCircularProgressRing
import com.example.ui.components.RRSectionHeader
import com.example.ui.components.RRStatusPill
import com.example.ui.theme.LocalRRExtraColors
import com.example.ui.theme.TabularClockLarge
import com.example.ui.theme.TabularClockMedium
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboardScreen(
    homeState: HomeUiState,
    liveNowMillis: Long = homeState.nowMillis,
    timerTickNowMillis: Long = liveNowMillis,
    preferences: UserPreferences,
    globalConfig: GlobalAppConfig? = null,
    timeSyncStatus: NetworkTimeSyncStatus = NetworkTimeSyncStatus(),
    isOwnerAccount: Boolean = false,
    onOpenDrawer: () -> Unit = {},
    onOpenLiveClock: () -> Unit = {},
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
    onToggleAlarm: (AlarmEntity, Boolean) -> Unit = { _, _ -> },
    onPauseTimer: (TimerEntity) -> Unit = {},
    onResumeTimer: (TimerEntity) -> Unit = {},
    onResetTimer: (TimerEntity) -> Unit = {},
    onQuickSaveReminder: (ReminderEntity) -> Unit,
    onQuickSaveTask: (TaskEntity) -> Unit,
    onMoveWidget: (Int, Int) -> Unit,
    onToggleWidgetVisibility: (String) -> Unit,
    onResetDashboard: () -> Unit,
) {
    val extra = LocalRRExtraColors.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showCustomizeSheet by remember { mutableStateOf(false) }
    var showMoreToolsSheet by remember { mutableStateOf(false) }
    var showQuickAddDialog by remember { mutableStateOf(false) }
    var quickAddInitialTitle by remember { mutableStateOf("") }
    var quickAddInitialMillis by remember { mutableLongStateOf(liveNowMillis + 3_600_000L) }
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

    val primaryZone = remember(preferences.primaryClockZone) {
        preferences.primaryClockZone.toZoneId()
    }
    val zdt = remember(liveNowMillis, primaryZone) {
        Instant.ofEpochMilli(liveNowMillis).atZone(primaryZone)
    }
    val greeting = when (zdt.hour) {
        in 5..11 -> "Good Morning!"
        in 12..16 -> "Good Afternoon!"
        in 17..21 -> "Good Evening!"
        else -> "Good Night!"
    }
    val isDaytime = zdt.hour in 6..18

    val digitsStr = remember(liveNowMillis, primaryZone, preferences.use24Hour) {
        val pat = if (preferences.use24Hour) "HH:mm:ss" else "hh:mm:ss"
        zdt.format(DateTimeFormatter.ofPattern(pat, Locale.getDefault()))
    }
    val amPmStr = remember(liveNowMillis, primaryZone, preferences.use24Hour) {
        if (preferences.use24Hour) "" else zdt.format(DateTimeFormatter.ofPattern("a", Locale.ENGLISH)).uppercase()
    }
    val dateStr = ScheduleCalculator.formatDate(
        millis = liveNowMillis,
        pattern = "EEE, ${preferences.dateFormatStyle.pattern}",
        zoneId = primaryZone,
    )

    val visibleWidgets = remember(preferences.dashboardWidgetOrder, preferences.hiddenDashboardWidgets) {
        preferences.dashboardWidgetOrder.filter { it !in preferences.hiddenDashboardWidgets }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier.testTag("home_drawer_btn"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Menu,
                        contentDescription = "Open Side Menu",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(26.dp),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    IconButton(
                        onClick = onNavigateSearch,
                        modifier = Modifier.testTag("home_search_btn"),
                    ) {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = "Smart Search & Assistant",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                            Icon(
                                Icons.Rounded.NotificationsNone,
                                contentDescription = "Notification Center",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(
                        onClick = onNavigateSettings,
                        modifier = Modifier.testTag("home_settings_btn"),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFF59E0B).copy(alpha = if (isOwnerAccount) 0.22f else 0.14f),
                            modifier = Modifier.size(34.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.WorkspacePremium,
                                    contentDescription = if (isOwnerAccount) "Owner Account & Settings" else "Settings",
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    quickAddInitialTitle = ""
                    quickAddInitialMillis = liveNowMillis + 3_600_000L
                    voiceAmbiguityNote = null
                    showQuickAddDialog = true
                },
                shape = CircleShape,
                containerColor = Color(0xFF2563EB),
                contentColor = Color.White,
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                            Text(
                                text = globalConfig.bannerMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // Greeting + Giant Digital Live Clock Hero (Screen 2 top section)
            item(key = "hero_greeting_clock") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = greeting,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Icon(
                            imageVector = if (isDaytime) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay,
                            contentDescription = null,
                            tint = if (isDaytime) Color(0xFFF59E0B) else Color(0xFF60A5FA),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Text(
                        text = "Have a great day ahead!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Giant centered Live Clock cardless hero (tap opens Live Clock)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(onClick = onOpenLiveClock)
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = digitsStr,
                                style = TabularClockLarge.copy(
                                    fontSize = 44.sp,
                                    lineHeight = 48.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                softWrap = false,
                            )
                            if (amPmStr.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = amPmStr,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                                    modifier = Modifier.padding(bottom = 6.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = dateStr,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center,
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Subtle live sync + voice quick add row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val syncLabel = when {
                            timeSyncStatus.ownerOverrideActive && !timeSyncStatus.ownerOverrideLabel.isNullOrBlank() ->
                                timeSyncStatus.ownerOverrideLabel
                            timeSyncStatus.isSynced -> timeSyncStatus.sourceLabel
                            timeSyncStatus.isSyncing -> "Syncing..."
                            else -> "Device Clock"
                        }
                        val zoneShort = if (preferences.primaryClockZone == PrimaryClockZone.INDIA_IST) "IST" else primaryZone.id.substringAfterLast("/")
                        Surface(
                            shape = CircleShape,
                            color = extra.glassFill,
                            border = BorderStroke(1.dp, extra.cardBorder),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    onSyncNetworkTime()
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Syncing live clock time...")
                                    }
                                }
                                .testTag("home_sync_time_pill"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Sync,
                                    contentDescription = "Sync Google time",
                                    tint = extra.accentPrimary,
                                    modifier = Modifier.size(13.dp),
                                )
                                Text(
                                    text = "$zoneShort • $syncLabel",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = extra.glassFill,
                            border = BorderStroke(1.dp, extra.cardBorder),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
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
                                        voiceAmbiguityNote = "Voice input unavailable — type reminder manually."
                                        showQuickAddDialog = true
                                    }
                                }
                                .testTag("home_voice_add_btn"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Mic,
                                    contentDescription = "Voice Quick Add",
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(13.dp),
                                )
                                Text(
                                    text = "Voice",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    if (homeState.overdueRemindersCount > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .clip(CircleShape)
                                .clickable(onClick = onNavigateReminders),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.WarningAmber,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = "${homeState.overdueRemindersCount} overdue reminder(s)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }

            // Card 1: Weather Card (Screen 2)
            item(key = "hero_weather_card") {
                val weather = homeState.weather
                RRCard(
                    onClick = onNavigateWeather,
                    modifier = Modifier.widthIn(max = 720.dp),
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
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF59E0B).copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if ((weather?.weatherCode ?: 0) >= 51) Icons.Rounded.WaterDrop else if ((weather?.weatherCode ?: 0) in 2..3) Icons.Rounded.WbCloudy else Icons.Rounded.WbSunny,
                                    contentDescription = "Weather",
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                            Column {
                                Text(
                                    text = if (weather != null) {
                                        WeatherUnits.formatTemp(weather.currentTempC, preferences.useFahrenheit)
                                    } else {
                                        "--°"
                                    },
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = if (weather != null) WeatherCodeInterpreter.describe(weather.weatherCode) else "Tap to load GPS",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.LocationOn,
                                    contentDescription = null,
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = weather?.cityName ?: "Weather Center",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (weather != null) {
                                Text(
                                    text = "Humidity ${weather.humidityPercent}% • ${WeatherUnits.formatWind(weather.windKmh, preferences.useMph)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // Card 2: Next Reminder Card (Screen 2)
            item(key = "hero_next_reminder_card") {
                val nextReminder = homeState.upcomingReminders.firstOrNull()
                RRCard(
                    onClick = onNavigateReminders,
                    modifier = Modifier.widthIn(max = 720.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981).copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Rounded.NotificationsActive,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                                Text(
                                    text = "Next Reminder",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (homeState.activeRemindersCount > 0) {
                                RRStatusPill(
                                    text = "${homeState.activeRemindersCount} active",
                                    color = Color(0xFF2563EB),
                                )
                            }
                        }

                        if (nextReminder != null) {
                            val targetMs = nextReminder.snoozedUntilMillis ?: nextReminder.triggerAtMillis
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF6366F1).copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.EventNote,
                                        contentDescription = null,
                                        tint = Color(0xFF6366F1),
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = nextReminder.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${ScheduleCalculator.formatDate(targetMs, preferences.dateFormatStyle.pattern, primaryZone)} • ${ScheduleCalculator.formatTime(targetMs, preferences.use24Hour, primaryZone)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        onQuickCompleteReminder(nextReminder)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Completed \"${nextReminder.title}\"")
                                        }
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("home_next_reminder_complete_btn"),
                                ) {
                                    Icon(
                                        Icons.Rounded.CheckCircleOutline,
                                        contentDescription = "Mark reminder complete",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                IconButton(
                                    onClick = onNavigateReminders,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("home_next_reminder_open_btn"),
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.ArrowForward,
                                        contentDescription = "View Reminders",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "No upcoming reminders • Tap to add one",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = "Open Reminders",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Card 3: Next Alarm Card with Live Interactive Switch (Screen 2)
            item(key = "hero_next_alarm_card") {
                val alarm = homeState.featuredAlarm
                RRCard(
                    onClick = onNavigateAlarms,
                    modifier = Modifier.widthIn(max = 720.dp),
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
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF59E0B).copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.Alarm,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Column {
                                Text(
                                    text = "Next Alarm",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (alarm != null) {
                                    Text(
                                        text = ScheduleCalculator.formatHourMinute(alarm.hour, alarm.minute, preferences.use24Hour),
                                        style = TabularClockMedium.copy(
                                            fontSize = MaterialTheme.typography.titleLarge.fontSize,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    val sub = if (alarm.isEnabled && alarm.nextTriggerAtMillis != null) {
                                        "${alarm.label} • ${ScheduleCalculator.formatCountdownDelta(alarm.nextTriggerAtMillis, liveNowMillis)}"
                                    } else {
                                        "${alarm.label} • Off"
                                    }
                                    Text(
                                        text = sub,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                } else {
                                    Text(
                                        text = "No alarm set",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }

                        Switch(
                            checked = alarm?.isEnabled == true,
                            onCheckedChange = { checked ->
                                if (alarm != null) {
                                    onToggleAlarm(alarm, checked)
                                } else {
                                    onNavigateAlarms()
                                }
                            },
                            modifier = Modifier.testTag("home_next_alarm_switch"),
                        )
                    }
                }
            }

            // Quick Access 8-tile grid exposing all core tools directly on Home
            item(key = "hero_quick_access") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RRSectionHeader(
                        title = "Quick Access",
                        actionLabel = "Customize",
                        onActionClick = { showCustomizeSheet = true },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        QuickAccessSquareTile(
                            label = "Reminders",
                            icon = Icons.Rounded.NotificationsActive,
                            iconBgColors = listOf(Color(0xFF2563EB), Color(0xFF38BDF8)),
                            onClick = onNavigateReminders,
                            testTag = "qa_reminders",
                            modifier = Modifier.weight(1f),
                        )
                        QuickAccessSquareTile(
                            label = "Alarms",
                            icon = Icons.Rounded.Alarm,
                            iconBgColors = listOf(Color(0xFFDB2777), Color(0xFFF472B6)),
                            onClick = onNavigateAlarms,
                            testTag = "qa_alarms",
                            modifier = Modifier.weight(1f),
                        )
                        QuickAccessSquareTile(
                            label = "Timer",
                            icon = Icons.Rounded.HourglassBottom,
                            iconBgColors = listOf(Color(0xFF7C3AED), Color(0xFF6366F1)),
                            onClick = onNavigateTimer,
                            testTag = "qa_timer",
                            modifier = Modifier.weight(1f),
                        )
                        QuickAccessSquareTile(
                            label = "Stopwatch",
                            icon = Icons.Rounded.Timer,
                            iconBgColors = listOf(Color(0xFF0891B2), Color(0xFF22D3EE)),
                            onClick = onNavigateStopwatch,
                            testTag = "qa_stopwatch",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        QuickAccessSquareTile(
                            label = "Live Clock",
                            icon = Icons.Rounded.AccessTimeFilled,
                            iconBgColors = listOf(Color(0xFF1D4ED8), Color(0xFF60A5FA)),
                            onClick = onOpenLiveClock,
                            testTag = "qa_live_clock",
                            modifier = Modifier.weight(1f),
                        )
                        QuickAccessSquareTile(
                            label = "World Clock",
                            icon = Icons.Rounded.Public,
                            iconBgColors = listOf(Color(0xFF0D9488), Color(0xFF34D399)),
                            onClick = onNavigateWorldClock,
                            testTag = "qa_world_clock",
                            modifier = Modifier.weight(1f),
                        )
                        QuickAccessSquareTile(
                            label = "Weather",
                            icon = Icons.Rounded.WbSunny,
                            iconBgColors = listOf(Color(0xFF0284C7), Color(0xFF38BDF8)),
                            onClick = onNavigateWeather,
                            testTag = "qa_weather",
                            modifier = Modifier.weight(1f),
                        )
                        QuickAccessSquareTile(
                            label = "More",
                            icon = Icons.Rounded.MoreHoriz,
                            iconBgColors = listOf(Color(0xFF1E3A8A), Color(0xFF0F172A)),
                            onClick = { showMoreToolsSheet = true },
                            testTag = "qa_more_tools",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // Optional expandable/customizable telemetry cards below the clean hero dashboard
            visibleWidgets.forEach { widgetKey ->
                when (widgetKey) {
                    DashboardWidgetId.ACTIVE_TIMERS.key -> {
                        if (homeState.activeTimers.isNotEmpty()) {
                            item(key = "widget_active_timers") {
                                ActiveTimersDashboardCard(
                                    timers = homeState.activeTimers,
                                    nowMillis = timerTickNowMillis,
                                    onClick = onNavigateTimer,
                                    onPauseTimer = onPauseTimer,
                                    onResumeTimer = onResumeTimer,
                                    onResetTimer = onResetTimer,
                                )
                            }
                        }
                    }

                    DashboardWidgetId.WORLD_CLOCK.key -> {
                        if (homeState.worldClockCities.isNotEmpty()) {
                            item(key = "widget_world_clock") {
                                WorldClockPreviewCard(
                                    cities = homeState.worldClockCities,
                                    nowMillis = liveNowMillis,
                                    preferences = preferences,
                                    onClick = onNavigateWorldClock,
                                )
                            }
                        }
                    }

                    DashboardWidgetId.TODAY_TASKS.key -> {
                        item(key = "widget_today_tasks") {
                            TodayTasksDashboardCard(
                                homeState = homeState,
                                onViewAll = onNavigateTasks,
                                onToggleTask = onQuickToggleTask,
                            )
                        }
                    }

                    DashboardWidgetId.SYSTEM_HEALTH.key -> {
                        item(key = "widget_system_health") {
                            SystemHealthDashboardCard(
                                report = homeState.healthReport,
                                onClick = onNavigateSystemHealth,
                            )
                        }
                    }

                    DashboardWidgetId.RECENT_ACTIVITY.key -> {
                        if (homeState.recentCompleted.isNotEmpty()) {
                            item(key = "widget_recent_activity") {
                                RecentActivityCard(items = homeState.recentCompleted)
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(84.dp)) }
        }
    }

    if (showMoreToolsSheet) {
        ModalBottomSheet(onDismissRequest = { showMoreToolsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("All RR Reminder Tools", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                val tools = listOf(
                    Triple("Live Clock", Icons.Rounded.AccessTimeFilled, onOpenLiveClock),
                    Triple("World Clock", Icons.Rounded.Public, onNavigateWorldClock),
                    Triple("Timer", Icons.Rounded.Timer, onNavigateTimer),
                    Triple("Stopwatch", Icons.Rounded.AvTimer, onNavigateStopwatch),
                    Triple("System Health", Icons.Rounded.HealthAndSafety, onNavigateSystemHealth),
                    Triple("Calendar", Icons.Rounded.CalendarMonth, onNavigateCalendar),
                    Triple("Tasks", Icons.Rounded.CheckCircleOutline, onNavigateTasks),
                    Triple("Notes", Icons.Rounded.EditNote, onNavigateNotes),
                    Triple("Habits", Icons.Rounded.TrackChanges, onNavigateHabits),
                    Triple("Focus", Icons.Rounded.SelfImprovement, onNavigateFocus),
                    Triple("Smart Search", Icons.Rounded.Search, onNavigateSearch),
                    Triple("Settings", Icons.Rounded.Settings, onNavigateSettings),
                )
                tools.chunked(4).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowItems.forEach { (label, icon, action) ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = extra.glassElevatedFill,
                                border = BorderStroke(1.dp, extra.cardBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        showMoreToolsSheet = false
                                        action()
                                    },
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        tint = extra.accentPrimary,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
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
            nowMillis = liveNowMillis,
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
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Reminder \"$title\" saved")
                }
            },
            onSaveTask = { title, priority ->
                val tomorrowDue = Instant.ofEpochMilli(liveNowMillis)
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
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Task \"$title\" saved")
                }
            },
        )
    }
}

@Composable
private fun QuickAccessSquareTile(
    label: String,
    icon: ImageVector,
    iconBgColors: List<Color>,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val extra = LocalRRExtraColors.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = extra.glassFill,
        border = BorderStroke(1.dp, extra.cardBorder),
        shadowElevation = if (extra.isDark) 0.dp else 2.dp,
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(iconBgColors)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TodayTasksDashboardCard(
    homeState: HomeUiState,
    onViewAll: () -> Unit,
    onToggleTask: (TaskEntity) -> Unit,
) {
    RRCard(modifier = Modifier.widthIn(max = 720.dp)) {
        RRSectionHeader(
            title = "Today's Tasks (${homeState.todayCompletedCount}/${homeState.todayTotalCount})",
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
    RRCard(modifier = Modifier.widthIn(max = 720.dp)) {
        Text("Recently Completed", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
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

@Composable
private fun ActiveTimersDashboardCard(
    timers: List<TimerEntity>,
    nowMillis: Long,
    onClick: () -> Unit,
    onPauseTimer: (TimerEntity) -> Unit = {},
    onResumeTimer: (TimerEntity) -> Unit = {},
    onResetTimer: (TimerEntity) -> Unit = {},
) {
    val extra = LocalRRExtraColors.current
    RRCard(
        onClick = onClick,
        modifier = Modifier.widthIn(max = 720.dp),
    ) {
        RRSectionHeader(
            title = "Active Timers",
            actionLabel = "Timers",
            onActionClick = onClick,
        )
        Spacer(modifier = Modifier.height(6.dp))
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    RRStatusPill(
                        text = ScheduleCalculator.formatDurationMillis(remaining),
                        color = if (timer.isRunning) extra.accentPrimary else extra.warning,
                    )
                    IconButton(
                        onClick = {
                            if (timer.isRunning) onPauseTimer(timer) else onResumeTimer(timer)
                        },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = if (timer.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (timer.isRunning) "Pause timer" else "Resume timer",
                            tint = extra.accentPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(
                        onClick = { onResetTimer(timer) },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Replay,
                            contentDescription = "Reset timer",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp),
                        )
                    }
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
    RRCard(
        onClick = onClick,
        modifier = Modifier.widthIn(max = 720.dp),
    ) {
        RRSectionHeader(
            title = "World Clock Previews",
            actionLabel = "Manage",
            onActionClick = onClick,
        )
        Spacer(modifier = Modifier.height(6.dp))
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

@Composable
private fun SystemHealthDashboardCard(
    report: SystemHealthReport,
    onClick: () -> Unit,
) {
    val extra = LocalRRExtraColors.current
    val scoreColor = healthScoreColor(report.overallHealthPercent)
    RRCard(
        onClick = onClick,
        modifier = Modifier
            .widthIn(max = 720.dp)
            .testTag("home_system_health_card"),
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
