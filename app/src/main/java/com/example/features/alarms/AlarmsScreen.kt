package com.example.features.alarms

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.core.database.AlarmEntity
import com.example.core.database.AlarmHistoryEntity
import com.example.core.datastore.UserPreferences
import com.example.core.network.NetworkTimeSyncStatus
import com.example.core.notifications.AlarmForegroundService
import com.example.core.notifications.ScheduleCalculator
import com.example.core.permissions.PermissionHelper
import com.example.ui.components.RRCard
import com.example.ui.components.RRConfirmDialog
import com.example.ui.components.RREmptyState
import com.example.ui.components.RRGradientButton
import com.example.ui.components.RRStatusPill
import com.example.ui.theme.LocalRRExtraColors
import com.example.ui.theme.TabularClockLarge
import com.example.ui.theme.TabularClockMedium
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmsTabContent(
    alarms: List<AlarmEntity>,
    history: List<AlarmHistoryEntity>,
    preferences: UserPreferences,
    nowMillis: Long = System.currentTimeMillis(),
    timeSyncStatus: NetworkTimeSyncStatus = NetworkTimeSyncStatus(),
    onSyncNetworkTime: () -> Unit = {},
    onCycleClockFace: () -> Unit = {},
    onSaveAlarm: (AlarmEntity) -> Unit,
    onToggleAlarm: (AlarmEntity, Boolean) -> Unit,
    onDeleteAlarm: (Long) -> Unit,
    onClearHistory: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val extra = LocalRRExtraColors.current
    var showHistory by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var editingAlarm by remember { mutableStateOf<AlarmEntity?>(null) }
    var deleteCandidate by remember { mutableStateOf<AlarmEntity?>(null) }

    var exactAlarmAllowed by remember { mutableStateOf(PermissionHelper.canScheduleExactAlarms(context)) }
    var notifAllowed by remember { mutableStateOf(PermissionHelper.hasNotificationPermission(context)) }
    var fullScreenAllowed by remember { mutableStateOf(PermissionHelper.canUseFullScreenIntent(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exactAlarmAllowed = PermissionHelper.canScheduleExactAlarms(context)
                notifAllowed = PermissionHelper.hasNotificationPermission(context)
                fullScreenAllowed = PermissionHelper.canUseFullScreenIntent(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notifAllowed = granted || PermissionHelper.hasNotificationPermission(context)
    }

    val nextAlarm = alarms.filter { it.isEnabled && it.nextTriggerAtMillis != null }
        .minByOrNull { it.nextTriggerAtMillis ?: Long.MAX_VALUE }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF040917),
                        Color(0xFF081430),
                        Color(0xFF050B1A),
                    ),
                ),
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 16.dp),
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = !showHistory,
                                onClick = { showHistory = false },
                                label = { Text("Alarms (${alarms.size})") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF1D4ED8),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF0E1B36),
                                    labelColor = Color(0xFFA2B8DE),
                                ),
                            )
                            FilterChip(
                                selected = showHistory,
                                onClick = { showHistory = true },
                                label = { Text("History (${history.size})") },
                                leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF1D4ED8),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF0E1B36),
                                    labelColor = Color(0xFFA2B8DE),
                                ),
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF1D4ED8),
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable {
                                    editingAlarm = null
                                    showEditor = true
                                }
                                .testTag("alarms_fab"),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Add, contentDescription = "Add Alarm", tint = Color.White)
                            }
                        }
                    }
                }

                if (!notifAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0x33D97706),
                            border = BorderStroke(1.dp, Color(0x77FBBF24)),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Icons.Rounded.NotificationsOff, contentDescription = null, tint = Color(0xFFFBBF24))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Notification Permission Needed", style = MaterialTheme.typography.titleSmall, color = Color.White)
                                    Text(
                                        "Allow notifications so ringing alarm controls appear.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFD6E3FF),
                                    )
                                }
                                TextButton(onClick = { notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                                    Text("Allow", color = Color(0xFF38BDF8))
                                }
                            }
                        }
                    }
                }

                if (!exactAlarmAllowed) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0x33E11D48),
                            border = BorderStroke(1.dp, Color(0x77FB7185)),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Icons.Rounded.Warning, contentDescription = null, tint = Color(0xFFFB7185))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Exact Alarm Permission Disabled", style = MaterialTheme.typography.titleSmall, color = Color.White)
                                    Text(
                                        "Grant exact alarm permission so alarms fire on time.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFD6E3FF),
                                    )
                                }
                                TextButton(onClick = { PermissionHelper.openExactAlarmSettings(context) }) {
                                    Text("Enable", color = Color(0xFF38BDF8))
                                }
                            }
                        }
                    }
                }

                if (!fullScreenAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0x332563EB),
                            border = BorderStroke(1.dp, Color(0x7760A5FA)),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Icons.Rounded.Fullscreen, contentDescription = null, tint = Color(0xFF60A5FA))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Full-Screen Alarm Display", style = MaterialTheme.typography.titleSmall, color = Color.White)
                                    Text(
                                        "Allow full-screen alerts to wake over the lock screen.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFD6E3FF),
                                    )
                                }
                                TextButton(onClick = { PermissionHelper.openFullScreenIntentSettings(context) }) {
                                    Text("Allow", color = Color(0xFF38BDF8))
                                }
                            }
                        }
                    }
                }

                if (!showHistory) {
                    if (alarms.isEmpty()) {
                        item {
                            RREmptyState(
                                icon = Icons.Rounded.AlarmOff,
                                title = "No alarms set",
                                subtitle = "Create a one-time or recurring alarm with custom tones and fade-in volume.",
                                actionLabel = "Add Alarm",
                                onActionClick = {
                                    editingAlarm = null
                                    showEditor = true
                                },
                            )
                        }
                    } else {
                        items(alarms, key = { it.id }) { alarm ->
                            val repeatText = if (alarm.repeatDaysMask == 0) {
                                "One time"
                            } else {
                                ScheduleCalculator.formatWeekdaysMask(alarm.repeatDaysMask)
                            }
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xCC0D1A38),
                                border = BorderStroke(
                                    1.dp,
                                    if (alarm.isEnabled) Color(0x5538BDF8) else Color(0x2860A5FA),
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable {
                                        editingAlarm = alarm
                                        showEditor = true
                                    },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = ScheduleCalculator.formatHourMinute(alarm.hour, alarm.minute, preferences.use24Hour),
                                            style = TabularClockLarge.copy(
                                                fontSize = MaterialTheme.typography.headlineMedium.fontSize,
                                                fontWeight = FontWeight.Bold,
                                            ),
                                            color = if (alarm.isEnabled) Color.White else Color(0xFF8AA4D6),
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = repeatText,
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                            color = Color.White.copy(alpha = if (alarm.isEnabled) 0.9f else 0.6f),
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        ) {
                                            Icon(
                                                Icons.Rounded.MusicNote,
                                                contentDescription = null,
                                                tint = Color(0xFFA855F7),
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Text(
                                                text = "${alarm.label} • ${alarm.ringtoneTitle}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF9EB5DF),
                                                maxLines = 1,
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Switch(
                                            checked = alarm.isEnabled,
                                            onCheckedChange = { enable ->
                                                if (enable && !notifAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                }
                                                onToggleAlarm(alarm, enable)
                                            },
                                            modifier = Modifier.testTag("alarm_toggle_${alarm.id}"),
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { deleteCandidate = alarm },
                                                modifier = Modifier.size(30.dp),
                                            ) {
                                                Icon(
                                                    Icons.Rounded.DeleteOutline,
                                                    contentDescription = "Delete alarm",
                                                    tint = Color(0xFFFB7185),
                                                    modifier = Modifier.size(17.dp),
                                                )
                                            }
                                            Icon(
                                                Icons.Rounded.ChevronRight,
                                                contentDescription = "Edit alarm",
                                                tint = Color(0xFF8AA4D6),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Next Alarm summary card (Screen 5)
                    item {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xDD0B1733),
                            border = BorderStroke(1.dp, Color(0x4438BDF8)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x33FBBF24)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Rounded.Schedule,
                                        contentDescription = null,
                                        tint = Color(0xFFFBBF24),
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Next Alarm",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                    )
                                    Text(
                                        text = if (nextAlarm != null && nextAlarm.nextTriggerAtMillis != null) {
                                            "${ScheduleCalculator.formatCountdownDelta(nextAlarm.nextTriggerAtMillis, nowMillis)} • ${ScheduleCalculator.formatHourMinute(nextAlarm.hour, nextAlarm.minute, preferences.use24Hour)}"
                                        } else {
                                            "No upcoming alarms enabled"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF9EB5DF),
                                    )
                                }
                            }
                        }
                    }
                } else {
                    if (history.isNotEmpty()) {
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = onClearHistory) { Text("Clear History", color = Color(0xFF38BDF8)) }
                            }
                        }
                    }
                    if (history.isEmpty()) {
                        item {
                            RREmptyState(
                                icon = Icons.Rounded.History,
                                title = "No alarm history yet",
                                subtitle = "Triggered, snoozed, and dismissed alarm events are logged here.",
                            )
                        }
                    } else {
                        items(history, key = { it.id }) { h ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xCC0D1A38),
                                border = BorderStroke(1.dp, Color(0x3360A5FA)),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column {
                                        Text(h.label, style = MaterialTheme.typography.titleSmall, color = Color.White)
                                        Text(
                                            "${ScheduleCalculator.formatDate(h.triggeredAtMillis, preferences.dateFormatStyle.pattern)} • ${ScheduleCalculator.formatTime(h.triggeredAtMillis, preferences.use24Hour)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF9EB5DF),
                                        )
                                    }
                                    RRStatusPill(text = h.action, color = extra.accentPrimary)
                                }
                            }
                        }
                    }
                }
            }

            // Bottom "+ Add Alarm" CTA pill matching Screen 5
            RRGradientButton(
                text = "Add Alarm",
                leadingIcon = Icons.Rounded.Add,
                colors = listOf(Color(0xFF2563EB), Color(0xFF38BDF8)),
                onClick = {
                    editingAlarm = null
                    showEditor = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .height(48.dp)
                    .testTag("alarms_add_cta"),
            )
        }
    }

    if (showEditor) {
        AlarmEditorDialog(
            initial = editingAlarm,
            nowMillis = nowMillis,
            preferences = preferences,
            onDismiss = { showEditor = false },
            onSave = { saved ->
                if (!notifAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                onSaveAlarm(saved)
                showEditor = false
            },
        )
    }

    deleteCandidate?.let { candidate ->
        RRConfirmDialog(
            title = "Delete Alarm",
            message = "Delete \"${candidate.label}\"?",
            confirmText = "Delete",
            isDestructive = true,
            onConfirm = {
                onDeleteAlarm(candidate.id)
                deleteCandidate = null
            },
            onDismiss = { deleteCandidate = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmEditorDialog(
    initial: AlarmEntity?,
    nowMillis: Long,
    preferences: UserPreferences,
    onDismiss: () -> Unit,
    onSave: (AlarmEntity) -> Unit,
) {
    val context = LocalContext.current
    val initialZdt = remember(initial?.id, preferences.primaryClockZone) {
        Instant.ofEpochMilli(nowMillis)
            .atZone(preferences.primaryClockZone.toZoneId())
            .plusMinutes(5)
    }
    val timeState = rememberTimePickerState(
        initialHour = initial?.hour ?: initialZdt.hour,
        initialMinute = initial?.minute ?: initialZdt.minute,
        is24Hour = preferences.use24Hour,
    )
    var useKeyboardInput by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf(initial?.label ?: "Alarm") }
    var repeatMask by remember { mutableIntStateOf(initial?.repeatDaysMask ?: 0) }
    var vibrate by remember { mutableStateOf(initial?.vibrate ?: preferences.vibrationEnabled) }
    var gradualVolume by remember { mutableStateOf(initial?.gradualVolume ?: true) }
    var snoozeMinutes by remember { mutableIntStateOf(initial?.snoozeMinutes ?: preferences.defaultAlarmSnoozeMinutes) }
    var ringtoneUri by remember { mutableStateOf(initial?.ringtoneUri) }
    var ringtoneTitle by remember {
        mutableStateOf(
            when {
                initial?.ringtoneUri == AlarmForegroundService.SILENT_RINGTONE_URI -> "Silent"
                else -> initial?.ringtoneTitle ?: "Default Alarm"
            },
        )
    }

    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            ringtoneUri = uri?.toString() ?: AlarmForegroundService.SILENT_RINGTONE_URI
            ringtoneTitle = if (uri != null) {
                runCatching { RingtoneManager.getRingtone(context, uri).getTitle(context) }.getOrDefault("Custom Tone")
            } else {
                "Silent"
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .widthIn(max = 520.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (initial == null) "New Alarm" else "Edit Alarm",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    IconButton(onClick = { useKeyboardInput = !useKeyboardInput }) {
                        Icon(
                            imageVector = if (useKeyboardInput) Icons.Rounded.AccessTime else Icons.Rounded.Keyboard,
                            contentDescription = if (useKeyboardInput) "Switch to clock dial" else "Switch to keyboard input",
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (useKeyboardInput) {
                        TimeInput(state = timeState)
                    } else {
                        TimePicker(state = timeState)
                    }
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Alarm Label") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("alarm_label_input"),
                    )
                    Text(
                        text = "Repeat Weekdays",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        dayLabels.forEachIndexed { idx, txt ->
                            val active = (repeatMask and (1 shl idx)) != 0
                            Surface(
                                shape = CircleShape,
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                border = BorderStroke(
                                    1.dp,
                                    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                ),
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .clickable { repeatMask = repeatMask xor (1 shl idx) },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = txt,
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Sound")
                                putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                    ringtoneUri?.takeIf { it.isNotBlank() && it != AlarmForegroundService.SILENT_RINGTONE_URI }?.let { Uri.parse(it) },
                                )
                            }
                            runCatching { ringtoneLauncher.launch(intent) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sound: $ringtoneTitle")
                    }

                    Text(
                        text = "Snooze Duration",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(5, 10, 15, 20).forEach { mins ->
                            FilterChip(
                                selected = snoozeMinutes == mins,
                                onClick = { snoozeMinutes = mins },
                                label = { Text("${mins}m") },
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Vibrate")
                        Switch(checked = vibrate, onCheckedChange = { vibrate = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Gradual Volume Fade-In")
                        Switch(checked = gradualVolume, onCheckedChange = { gradualVolume = it })
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val saved = (initial ?: AlarmEntity(hour = timeState.hour, minute = timeState.minute)).copy(
                                label = label.trim().ifBlank { "Alarm" },
                                hour = timeState.hour,
                                minute = timeState.minute,
                                isEnabled = true,
                                repeatDaysMask = repeatMask,
                                ringtoneUri = ringtoneUri,
                                ringtoneTitle = ringtoneTitle,
                                vibrate = vibrate,
                                snoozeMinutes = snoozeMinutes,
                                gradualVolume = gradualVolume,
                            )
                            onSave(saved)
                        },
                        modifier = Modifier.testTag("alarm_save_btn"),
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
