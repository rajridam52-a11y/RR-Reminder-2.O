package com.example.features.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.datastore.DateFormatStyle
import com.example.core.datastore.UserPreferences
import com.example.core.permissions.PermissionHelper
import com.example.ui.components.RRBrandMark
import com.example.ui.components.RRCard
import com.example.ui.components.RRStatusPill
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.LocalRRExtraColors
import com.example.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    backupStatus: String?,
    onBack: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetAccentPreset: (AccentPreset) -> Unit,
    onSetUse24Hour: (Boolean) -> Unit,
    onSetDateFormat: (DateFormatStyle) -> Unit,
    onSetFirstDayOfWeek: (Int) -> Unit,
    onSetDefaultAlarmSnooze: (Int) -> Unit,
    onSetVibrationEnabled: (Boolean) -> Unit,
    onSetLocationRemindersEnabled: (Boolean) -> Unit,
    onSetUseDeviceCalendar: (Boolean) -> Unit,
    onExportBackup: (android.content.Context, android.net.Uri) -> Unit,
    onImportBackup: (android.content.Context, android.net.Uri) -> Unit,
    onClearBackupStatus: () -> Unit,
) {
    val context = LocalContext.current
    val extra = LocalRRExtraColors.current
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) onExportBackup(context, uri)
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) onImportBackup(context, uri)
    }

    val locPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        onSetLocationRemindersEnabled(granted)
    }

    val calPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        onSetUseDeviceCalendar(granted)
    }

    val exactAlarmOk = PermissionHelper.canScheduleExactAlarms(context)
    val notifOk = PermissionHelper.hasNotificationPermission(context)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Personalization") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_btn")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 36.dp),
        ) {
            item {
                RRCard(accentGlow = true) {
                    Text("Appearance & Theme", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = preferences.themeMode == mode,
                                onClick = { onSetThemeMode(mode) },
                                label = { Text(mode.label.replace(" Mode", "").replace(" Default", "")) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("theme_mode_${mode.key}"),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Accent Color", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        AccentPreset.entries.forEach { preset ->
                            val selected = preset == preferences.accentPreset
                            Surface(
                                shape = CircleShape,
                                color = preset.primaryDark,
                                border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .clickable { onSetAccentPreset(preset) }
                                    .testTag("accent_${preset.key}"),
                            ) {
                                if (selected) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.Check, contentDescription = preset.label, tint = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                RRCard {
                    Text("Time, Date & Calendar", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("24-Hour Time Format")
                        Switch(
                            checked = preferences.use24Hour,
                            onCheckedChange = onSetUse24Hour,
                            modifier = Modifier.testTag("settings_24h_switch"),
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Date Format", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        DateFormatStyle.entries.forEach { style ->
                            FilterChip(
                                selected = preferences.dateFormatStyle == style,
                                onClick = { onSetDateFormat(style) },
                                label = { Text(style.label) },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("First Day of Week", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = preferences.firstDayOfWeek == 1,
                            onClick = { onSetFirstDayOfWeek(1) },
                            label = { Text("Monday") },
                        )
                        FilterChip(
                            selected = preferences.firstDayOfWeek == 7,
                            onClick = { onSetFirstDayOfWeek(7) },
                            label = { Text("Sunday") },
                        )
                    }
                }
            }

            item {
                RRCard {
                    Text("Alarms & Notifications", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Vibration Enabled")
                        Switch(
                            checked = preferences.vibrationEnabled,
                            onCheckedChange = onSetVibrationEnabled,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Default Alarm Snooze", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(5, 10, 15, 20).forEach { mins ->
                            FilterChip(
                                selected = preferences.defaultAlarmSnoozeMinutes == mins,
                                onClick = { onSetDefaultAlarmSnooze(mins) },
                                label = { Text("${mins}m") },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("System Notification Channels", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (notifOk) "Notifications permitted" else "Permission needed for alerts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(onClick = { PermissionHelper.openAppNotificationSettings(context) }) {
                            Text("Configure")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Exact Alarm Access", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (exactAlarmOk) "Exact scheduling active" else "Grant exact alarm access for timely rings",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        RRStatusPill(
                            text = if (exactAlarmOk) "Active" else "Check",
                            color = if (exactAlarmOk) extra.success else extra.warning,
                        )
                    }
                    if (!exactAlarmOk) {
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(onClick = { PermissionHelper.openExactAlarmSettings(context) }) {
                            Text("Open Exact Alarm Settings")
                        }
                    }
                }
            }

            item {
                RRCard {
                    Text("Backup & Restore (Local JSON)", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Export or restore reminders, alarms, tasks, notes, habits, routines, and world cities locally. Duplicate items are skipped automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (backupStatus != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(backupStatus, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                IconButton(onClick = onClearBackupStatus, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = { exportLauncher.launch("rr_reminder_backup.json") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("backup_export_btn"),
                        ) {
                            Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export")
                        }
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("backup_import_btn"),
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restore")
                        }
                    }
                }
            }

            item {
                RRCard {
                    Text("Privacy & Optional Integrations", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Location Reminders", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Periodic battery-conscious coarse location checks only when enabled.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = preferences.locationRemindersEnabled,
                            onCheckedChange = { enable ->
                                if (enable && !PermissionHelper.hasCoarseLocationPermission(context)) {
                                    locPermLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                } else {
                                    onSetLocationRemindersEnabled(enable)
                                }
                            },
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Android Calendar Sync", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Read-only calendar events displayed in the Agenda module.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = preferences.useDeviceCalendar,
                            onCheckedChange = { enable ->
                                if (enable && !PermissionHelper.hasCalendarReadPermission(context)) {
                                    calPermLauncher.launch(Manifest.permission.READ_CALENDAR)
                                } else {
                                    onSetUseDeviceCalendar(enable)
                                }
                            },
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(onClick = { showPrivacyDialog = true }) { Text("Privacy Policy") }
                        TextButton(onClick = { showHelpDialog = true }) { Text("Help & Support") }
                    }
                }
            }

            item {
                RRCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RRBrandMark(size = 46.dp)
                        Column {
                            Text("RR Reminder", style = MaterialTheme.typography.titleMedium)
                            Text("Your Time. Your Control.", style = MaterialTheme.typography.bodySmall, color = extra.accentPrimary)
                            Text(
                                "Founder: Ridam Raj",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "Version 1.0 • Local-First Architecture",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("RR Reminder Privacy Policy") },
            text = {
                Text(
                    "RR Reminder is strictly local-first. Your reminders, alarms, tasks, notes, habits, routines, and timers remain stored locally in your device's Room database and DataStore.\n\n" +
                        "• Weather requests connect over HTTPS exclusively to Open-Meteo using only your chosen city or coarse coordinates when you request weather.\n" +
                        "• Zero analytics trackers, zero third-party ad SDKs, and zero unconsented cloud uploads.\n" +
                        "• Backups are exported only to the file location you choose.",
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) { Text("Close") }
            },
        )
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("Help & Reliability Tips") },
            text = {
                Text(
                    "• Alarm Reliability: Ensure Notifications and Exact Alarms are enabled in system settings. Note that aggressive manufacturer battery savers can delay background alarms if restricted.\n\n" +
                        "• Home Screen Widgets: Long-press your Android home screen to add RR Next Reminder, RR World Clock, RR Weather, RR Quick Add, or RR Timer widgets.\n\n" +
                        "• Backup: Regularly export a JSON backup from Settings before switching devices.",
                )
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) { Text("Got It") }
            },
        )
    }
}
