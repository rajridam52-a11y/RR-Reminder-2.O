package com.example.features.alarms

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.database.AlarmEntity
import com.example.core.database.AlarmHistoryEntity
import com.example.core.datastore.UserPreferences
import com.example.core.notifications.ScheduleCalculator
import com.example.core.permissions.PermissionHelper
import com.example.ui.components.RRCard
import com.example.ui.components.RRConfirmDialog
import com.example.ui.components.RREmptyState
import com.example.ui.components.RRStatusPill
import com.example.ui.theme.LocalRRExtraColors
import com.example.ui.theme.TabularClockMedium

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmsTabContent(
    alarms: List<AlarmEntity>,
    history: List<AlarmHistoryEntity>,
    preferences: UserPreferences,
    onSaveAlarm: (AlarmEntity) -> Unit,
    onToggleAlarm: (AlarmEntity, Boolean) -> Unit,
    onDeleteAlarm: (Long) -> Unit,
    onClearHistory: () -> Unit,
) {
    val context = LocalContext.current
    val extra = LocalRRExtraColors.current
    var showHistory by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var editingAlarm by remember { mutableStateOf<AlarmEntity?>(null) }
    var deleteCandidate by remember { mutableStateOf<AlarmEntity?>(null) }

    val exactAlarmAllowed = PermissionHelper.canScheduleExactAlarms(context)
    val nextAlarm = alarms.filter { it.isEnabled && it.nextTriggerAtMillis != null }
        .minByOrNull { it.nextTriggerAtMillis ?: Long.MAX_VALUE }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
        ) {
            if (!exactAlarmAllowed) {
                item {
                    RRCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Exact Alarm Permission Disabled", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Alarms may be delayed by Android power saving until exact alarm access is granted.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            TextButton(onClick = { PermissionHelper.openExactAlarmSettings(context) }) {
                                Text("Enable")
                            }
                        }
                    }
                }
            }

            item {
                RRCard(accentGlow = nextAlarm != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("NEXT ALARM", style = MaterialTheme.typography.labelMedium, color = extra.accentPrimary)
                            Spacer(modifier = Modifier.height(4.dp))
                            if (nextAlarm != null && nextAlarm.nextTriggerAtMillis != null) {
                                Text(
                                    text = ScheduleCalculator.formatHourMinute(nextAlarm.hour, nextAlarm.minute, preferences.use24Hour),
                                    style = TabularClockMedium,
                                )
                                Text(
                                    text = "${nextAlarm.label} • ${ScheduleCalculator.formatCountdownDelta(nextAlarm.nextTriggerAtMillis)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text("No upcoming alarms", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        FilterChip(
                            selected = showHistory,
                            onClick = { showHistory = !showHistory },
                            label = { Text(if (showHistory) "Alarms" else "History (${history.size})") },
                            leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        )
                    }
                }
            }

            if (!showHistory) {
                if (alarms.isEmpty()) {
                    item {
                        RREmptyState(
                            icon = Icons.Rounded.AlarmOff,
                            title = "No alarms set",
                            subtitle = "Create a one-time or recurring weekday alarm with gradual volume and custom ringtones.",
                            actionLabel = "Add Alarm",
                            onActionClick = {
                                editingAlarm = null
                                showEditor = true
                            },
                        )
                    }
                } else {
                    items(alarms, key = { it.id }) { alarm ->
                        RRCard(
                            onClick = {
                                editingAlarm = alarm
                                showEditor = true
                            },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = ScheduleCalculator.formatHourMinute(alarm.hour, alarm.minute, preferences.use24Hour),
                                        style = TabularClockMedium,
                                        color = if (alarm.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = "${alarm.label} • ${ScheduleCalculator.formatWeekdaysMask(alarm.repeatDaysMask)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        RRStatusPill(text = alarm.ringtoneTitle, color = extra.accentPrimary)
                                        if (alarm.gradualVolume) {
                                            RRStatusPill(text = "Fade-in", color = extra.info)
                                        }
                                        RRStatusPill(text = "Snooze ${alarm.snoozeMinutes}m", color = extra.accentSecondary)
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Switch(
                                        checked = alarm.isEnabled,
                                        onCheckedChange = { onToggleAlarm(alarm, it) },
                                        modifier = Modifier.testTag("alarm_toggle_${alarm.id}"),
                                    )
                                    IconButton(onClick = { deleteCandidate = alarm }) {
                                        Icon(
                                            Icons.Rounded.DeleteOutline,
                                            contentDescription = "Delete alarm",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (history.isNotEmpty()) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = onClearHistory) { Text("Clear History") }
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
                        RRCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(h.label, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${ScheduleCalculator.formatDate(h.triggeredAtMillis, preferences.dateFormatStyle.pattern)} • ${ScheduleCalculator.formatTime(h.triggeredAtMillis, preferences.use24Hour)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                RRStatusPill(text = h.action, color = extra.accentPrimary)
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                editingAlarm = null
                showEditor = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("alarms_fab"),
        ) {
            Icon(Icons.Rounded.AddAlarm, contentDescription = "Add Alarm")
        }
    }

    if (showEditor) {
        AlarmEditorDialog(
            initial = editingAlarm,
            preferences = preferences,
            onDismiss = { showEditor = false },
            onSave = {
                onSaveAlarm(it)
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
    preferences: UserPreferences,
    onDismiss: () -> Unit,
    onSave: (AlarmEntity) -> Unit,
) {
    val context = LocalContext.current
    val timeState = rememberTimePickerState(
        initialHour = initial?.hour ?: 7,
        initialMinute = initial?.minute ?: 0,
        is24Hour = preferences.use24Hour,
    )
    var label by remember { mutableStateOf(initial?.label ?: "Morning Alarm") }
    var repeatMask by remember { mutableIntStateOf(initial?.repeatDaysMask ?: 0) }
    var vibrate by remember { mutableStateOf(initial?.vibrate ?: preferences.vibrationEnabled) }
    var gradualVolume by remember { mutableStateOf(initial?.gradualVolume ?: true) }
    var snoozeMinutes by remember { mutableIntStateOf(initial?.snoozeMinutes ?: preferences.defaultAlarmSnoozeMinutes) }
    var ringtoneUri by remember { mutableStateOf(initial?.ringtoneUri) }
    var ringtoneTitle by remember { mutableStateOf(initial?.ringtoneTitle ?: "Default Alarm") }

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
            ringtoneUri = uri?.toString()
            ringtoneTitle = if (uri != null) {
                runCatching { RingtoneManager.getRingtone(context, uri).getTitle(context) }.getOrDefault("Custom Tone")
            } else {
                "Silent"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Alarm" else "Edit Alarm") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TimePicker(state = timeState)
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Alarm Label") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("alarm_label_input"),
                )
                Text("Repeat Weekdays", style = MaterialTheme.typography.labelMedium)
                val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    dayLabels.forEachIndexed { idx, txt ->
                        val active = (repeatMask and (1 shl idx)) != 0
                        FilterChip(
                            selected = active,
                            onClick = { repeatMask = repeatMask xor (1 shl idx) },
                            label = { Text(txt) },
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Sound")
                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                ringtoneUri?.let { Uri.parse(it) },
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

                Text("Snooze Duration", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
        },
        confirmButton = {
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
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
