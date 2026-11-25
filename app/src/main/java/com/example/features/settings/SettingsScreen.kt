package com.example.features.settings

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.core.datastore.ClockFaceStyle
import com.example.core.datastore.DateFormatStyle
import com.example.core.datastore.PrimaryClockZone
import com.example.core.datastore.TimeFormatMode
import com.example.core.datastore.UserPreferences
import com.example.core.firebase.GlobalAppConfig
import com.example.core.firebase.OWNER_ADMIN_EMAIL
import com.example.core.firebase.OwnerTimeMode
import com.example.core.health.SystemHealthReport
import com.example.core.notifications.ScheduleCalculator
import com.example.core.permissions.PermissionHelper
import com.example.features.health.healthScoreColor
import com.example.ui.components.RRBackgroundContainer
import com.example.ui.components.RRBrandMark
import com.example.ui.components.RRCard
import com.example.ui.components.RRCircularProgressRing
import com.example.ui.components.RRStatusPill
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.LocalRRExtraColors
import com.example.ui.theme.TabularClockMedium
import com.example.ui.theme.ThemeMode
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    backupStatus: String?,
    healthReport: SystemHealthReport = SystemHealthReport(),
    currentUserEmail: String? = null,
    currentUserName: String? = null,
    isEmailVerified: Boolean = false,
    isOwnerAccount: Boolean = false,
    remoteConfig: GlobalAppConfig? = null,
    nowMillis: Long = System.currentTimeMillis(),
    baseNetworkNowMillis: Long = System.currentTimeMillis(),
    isSavingOwnerConfig: Boolean = false,
    ownerSaveStatus: String? = null,
    ownerSaveError: String? = null,
    onPublishOwnerConfig: (GlobalAppConfig) -> Unit = {},
    onResetOwnerConfig: () -> Unit = {},
    onClearOwnerMessages: () -> Unit = {},
    onReapplyOwnerPreset: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onBack: () -> Unit,
    onOpenSystemHealth: () -> Unit = {},
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetAccentPreset: (AccentPreset) -> Unit,
    onSetClockFaceStyle: (ClockFaceStyle) -> Unit = {},
    onSetPrimaryClockZone: (PrimaryClockZone) -> Unit,
    onSetTimeFormatMode: (TimeFormatMode) -> Unit,
    onSetDateFormat: (DateFormatStyle) -> Unit,
    onSetFirstDayOfWeek: (Int) -> Unit,
    onSetUseFahrenheit: (Boolean) -> Unit,
    onSetUseMph: (Boolean) -> Unit,
    onSetDefaultReminderLead: (Int) -> Unit,
    onSetDefaultAlarmSnooze: (Int) -> Unit,
    onSetVibrationEnabled: (Boolean) -> Unit,
    onSetNotificationSoundUri: (String?) -> Unit,
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
    var showAboutDialog by remember { mutableStateOf(false) }

    var expandAppearance by remember { mutableStateOf(true) }
    var expandTimeRegion by remember { mutableStateOf(true) }
    var expandNotifications by remember { mutableStateOf(true) }
    var expandSound by remember { mutableStateOf(false) }
    var expandLocation by remember { mutableStateOf(false) }
    var expandBackup by remember { mutableStateOf(true) }

    val soundTitle = remember(preferences.notificationSoundUri) {
        val raw = preferences.notificationSoundUri
        if (raw.isNullOrBlank()) {
            "System Default"
        } else {
            runCatching { RingtoneManager.getRingtone(context, Uri.parse(raw)).getTitle(context) }
                .getOrDefault("Custom Sound")
        }
    }

    val notifSoundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            onSetNotificationSoundUri(uri?.toString())
        }
    }

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
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        onSetLocationRemindersEnabled(
            grants.values.any { it } || PermissionHelper.hasAnyLocationPermission(context),
        )
    }

    val calPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        onSetUseDeviceCalendar(granted)
    }

    val exactAlarmOk = PermissionHelper.canScheduleExactAlarms(context)
    val notifOk = PermissionHelper.hasNotificationPermission(context)

    RRBackgroundContainer {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_btn")) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { exportLauncher.launch("rr_reminder_backup.json") },
                            modifier = Modifier.testTag("settings_quick_export_btn"),
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Quick JSON backup")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 104.dp),
            ) {
                // Screen 11 Profile Hero Card
                item {
                    RRCard(
                        accentGlow = true,
                        modifier = Modifier.testTag("settings_account_card"),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF2563EB), Color(0xFF7C3AED)),
                                        ),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = "User profile",
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentUserName?.takeIf { it.isNotBlank() } ?: "RR User",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                )
                                Text(
                                    text = if (isOwnerAccount) "Owner Mode • Admin" else "Premium Member",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color(0xFFFBBF24),
                                )
                                Text(
                                    text = currentUserEmail ?: "Signed in with Google",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WorkspacePremium,
                                    contentDescription = if (isOwnerAccount) "Owner badge" else "Member badge",
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(28.dp),
                                )
                                OutlinedButton(
                                    onClick = onSignOut,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .height(32.dp)
                                        .testTag("settings_sign_out_btn"),
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.Logout,
                                        contentDescription = "Sign out",
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sign Out", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        if (!isOwnerAccount && remoteConfig?.syncOverrideEnabled == true) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = extra.accentPrimary.copy(alpha = 0.12f),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.Sync,
                                        contentDescription = null,
                                        tint = extra.accentPrimary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = "Live Owner Sync is active: published Owner updates apply automatically, and you can adjust your own settings below anytime.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(
                                        onClick = onReapplyOwnerPreset,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    ) {
                                        Text("Re-sync", maxLines = 1, softWrap = false)
                                    }
                                }
                            }
                        }
                    }
                }

                // Owner Mode is strictly rendered ONLY when signed in as verified rajridam92@gmail.com
                if (isOwnerAccount) {
                    item {
                        OwnerModeControlCard(
                            remoteConfig = remoteConfig,
                            preferences = preferences,
                            nowMillis = nowMillis,
                            baseNetworkNowMillis = baseNetworkNowMillis,
                            isSaving = isSavingOwnerConfig,
                            saveStatus = ownerSaveStatus,
                            saveError = ownerSaveError,
                            onPublish = onPublishOwnerConfig,
                            onReset = onResetOwnerConfig,
                            onClearMessages = onClearOwnerMessages,
                        )
                    }
                }

                // Screen 11 Row 1: Appearance (Dark / Light / Accent / Clock Face)
                item {
                    SettingsSectionCard(
                        icon = Icons.Rounded.Palette,
                        iconColor = Color(0xFF38BDF8),
                        title = "Appearance",
                        subtitle = "${preferences.themeMode.label.replace(" Mode", "")} • ${preferences.accentPreset.label}",
                        expanded = expandAppearance,
                        onToggleExpand = { expandAppearance = !expandAppearance },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = preferences.themeMode == mode,
                                    onClick = { onSetThemeMode(mode) },
                                    label = {
                                        Text(
                                            text = mode.label.replace(" Mode", "").replace(" Default", ""),
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("theme_mode_${mode.key}"),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Accent Color", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            AccentPreset.entries.forEach { preset ->
                                val selected = preset == preferences.accentPreset
                                Surface(
                                    shape = CircleShape,
                                    color = preset.primaryDark,
                                    border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
                                    modifier = Modifier
                                        .size(44.dp)
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
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Live Clock Face", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ClockFaceStyle.entries.forEach { face ->
                                FilterChip(
                                    selected = preferences.clockFaceStyle == face,
                                    onClick = { onSetClockFaceStyle(face) },
                                    label = { Text(face.label, maxLines = 1, softWrap = false) },
                                    modifier = Modifier.testTag("settings_clock_face_${face.key}"),
                                )
                            }
                        }
                    }
                }

                // Time, Date & Weather Units
                item {
                    SettingsSectionCard(
                        icon = Icons.Rounded.Schedule,
                        iconColor = Color(0xFF60A5FA),
                        title = "Time & Regional Units",
                        subtitle = "${preferences.primaryClockZone.label} • ${if (preferences.use24Hour) "24-Hour" else "12-Hour"}",
                        expanded = expandTimeRegion,
                        onToggleExpand = { expandTimeRegion = !expandTimeRegion },
                    ) {
                        Text("Primary Clock Timezone", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PrimaryClockZone.entries.forEach { zoneOpt ->
                                FilterChip(
                                    selected = preferences.primaryClockZone == zoneOpt,
                                    onClick = { onSetPrimaryClockZone(zoneOpt) },
                                    label = { Text(zoneOpt.label, maxLines = 1, softWrap = false) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("settings_zone_${zoneOpt.key}"),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Time Format", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            TimeFormatMode.entries.forEach { mode ->
                                val shortLabel = when (mode) {
                                    TimeFormatMode.SYSTEM -> "System"
                                    TimeFormatMode.H12 -> "12-Hour"
                                    TimeFormatMode.H24 -> "24-Hour"
                                }
                                FilterChip(
                                    selected = preferences.timeFormatMode == mode,
                                    onClick = { onSetTimeFormatMode(mode) },
                                    label = { Text(shortLabel, maxLines = 1, softWrap = false) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("settings_time_fmt_${mode.key}"),
                                )
                            }
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
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Temperature in Fahrenheit (°F)")
                            Switch(
                                checked = preferences.useFahrenheit,
                                onCheckedChange = onSetUseFahrenheit,
                                modifier = Modifier.testTag("settings_fahrenheit_switch"),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Wind Speed in mph & Miles")
                            Switch(
                                checked = preferences.useMph,
                                onCheckedChange = onSetUseMph,
                                modifier = Modifier.testTag("settings_mph_switch"),
                            )
                        }
                    }
                }

                // Screen 11 Row 2: Notifications
                item {
                    SettingsSectionCard(
                        icon = Icons.Rounded.Notifications,
                        iconColor = Color(0xFFF43F5E),
                        title = "Notifications",
                        subtitle = if (notifOk && exactAlarmOk) "Active • Early Alert ${preferences.defaultReminderLeadMinutes}m" else "Check notification or exact alarm permission",
                        expanded = expandNotifications,
                        onToggleExpand = { expandNotifications = !expandNotifications },
                    ) {
                        Text("Default Reminder Early Alert", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0 to "Off", 5 to "5m", 15 to "15m", 30 to "30m").forEach { (mins, lbl) ->
                                FilterChip(
                                    selected = preferences.defaultReminderLeadMinutes == mins,
                                    onClick = { onSetDefaultReminderLead(mins) },
                                    label = { Text(lbl) },
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
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
                        Spacer(modifier = Modifier.height(6.dp))
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
                            TextButton(onClick = { PermissionHelper.openExactAlarmSettings(context) }) {
                                Text("Open Exact Alarm Settings")
                            }
                        }
                    }
                }

                // Screen 11 Row 3: Sound & Vibration
                item {
                    SettingsSectionCard(
                        icon = Icons.Rounded.VolumeUp,
                        iconColor = Color(0xFFF59E0B),
                        title = "Sound & Vibration",
                        subtitle = "$soundTitle • Snooze ${preferences.defaultAlarmSnoozeMinutes}m",
                        expanded = expandSound,
                        onToggleExpand = { expandSound = !expandSound },
                    ) {
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
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Reminder Notification Sound")
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    putExtra(
                                        RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                        preferences.notificationSoundUri?.let { Uri.parse(it) },
                                    )
                                }
                                runCatching { notifSoundLauncher.launch(intent) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reminder Sound: $soundTitle")
                        }
                    }
                }

                // Screen 11 Row 4: Location & GPS
                item {
                    SettingsSectionCard(
                        icon = Icons.Rounded.LocationOn,
                        iconColor = Color(0xFF10B981),
                        title = "Location & GPS",
                        subtitle = if (preferences.locationRemindersEnabled) "Location Reminders On" else "Location & Calendar Integrations",
                        expanded = expandLocation,
                        onToggleExpand = { expandLocation = !expandLocation },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Location Reminders", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Periodic battery-conscious GPS location checks only when enabled.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = preferences.locationRemindersEnabled,
                                onCheckedChange = { enable ->
                                    if (enable && !PermissionHelper.hasAnyLocationPermission(context)) {
                                        locPermLauncher.launch(PermissionHelper.LOCATION_PERMISSIONS)
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
                    }
                }

                // Screen 11 Row 5: Privacy & Security + Backup & Restore
                item {
                    SettingsSectionCard(
                        icon = Icons.Rounded.Security,
                        iconColor = Color(0xFF06B6D4),
                        title = "Privacy & Security",
                        subtitle = "Local JSON Backup, Restore & Policy",
                        expanded = expandBackup,
                        onToggleExpand = { expandBackup = !expandBackup },
                    ) {
                        Text(
                            "Export or restore reminders, alarms, calendar events, tasks, notes, habits, routines, and world cities locally.",
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
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextButton(onClick = { showPrivacyDialog = true }) { Text("Privacy Policy") }
                            TextButton(onClick = { showHelpDialog = true }) { Text("Help & Support") }
                        }
                    }
                }

                // RR System Health Card
                item {
                    val scoreColor = healthScoreColor(healthReport.overallHealthPercent)
                    RRCard(
                        onClick = onOpenSystemHealth,
                        modifier = Modifier.testTag("settings_system_health_card"),
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
                                    progress = healthReport.overallHealthPercent / 100f,
                                    size = 46.dp,
                                    strokeWidth = 5.dp,
                                    primaryColor = scoreColor,
                                    secondaryColor = extra.accentSecondary,
                                ) {
                                    Text(
                                        text = "${healthReport.overallHealthPercent}%",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = scoreColor,
                                    )
                                }
                                Column {
                                    Text("RR System Health", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = if (healthReport.testedCount > 0) {
                                            "${healthReport.workingCount}/${healthReport.items.size} working • ${healthReport.issueCount} issue(s)"
                                        } else {
                                            "Inspect clock, APIs, permissions, database & storage"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Icon(Icons.Rounded.ChevronRight, contentDescription = "Open RR System Health")
                        }
                    }
                }

                // Screen 11 Bottom Row: About RR Reminder
                item {
                    RRCard(onClick = { showAboutDialog = true }) {
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
                                RRBrandMark(size = 46.dp)
                                Column {
                                    Text(
                                        "About RR Reminder",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        "Version ${BuildConfig.VERSION_NAME} • Founder: Ridam Raj",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = extra.accentPrimary,
                                    )
                                    Text(
                                        "Smart Time • Smart Life",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Icon(Icons.Rounded.ChevronRight, contentDescription = "About RR Reminder")
                        }
                    }
                }
            }
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About RR Reminder ${BuildConfig.VERSION_NAME}") },
            text = {
                Text(
                    "RR Reminder 2.0 — Next-Generation Smart Time-Management Suite\n\n" +
                        "• Founder: Ridam Raj\n" +
                        "• Engine: Network-Anchored Real-Time Clock + 5 Aurora Clock Faces\n" +
                        "• Storage: Offline-First Room SQLite v2 + DataStore + JSON Backup\n" +
                        "• Live Services: Open-Meteo Real Weather, Firebase Auth & Owner Sync, 17-Point RR System Health Diagnostics",
                )
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("Close") }
            },
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("RR Reminder Privacy Policy") },
            text = {
                Text(
                    "Your reminders, alarms, calendar events, tasks, notes, habits, routines, and timers remain stored locally in your device's Room database and DataStore.\n\n" +
                        "• Google Sign-In authenticates your session and syncs live global app configuration updates published by the authorized owner.\n" +
                        "• Weather requests connect over HTTPS exclusively to Open-Meteo using only your chosen city or coordinates when you request weather.\n" +
                        "• Optional AI Assistant queries are only sent when you explicitly tap Send in Smart Search.\n" +
                        "• Zero third-party ad SDKs, and backups export only to your chosen file.",
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

@Composable
private fun OwnerModeControlCard(
    remoteConfig: GlobalAppConfig?,
    preferences: UserPreferences,
    nowMillis: Long,
    baseNetworkNowMillis: Long,
    isSaving: Boolean,
    saveStatus: String?,
    saveError: String?,
    onPublish: (GlobalAppConfig) -> Unit,
    onReset: () -> Unit,
    onClearMessages: () -> Unit,
) {
    val context = LocalContext.current
    val extra = LocalRRExtraColors.current

    var syncOverrideEnabled by remember(remoteConfig) {
        mutableStateOf(remoteConfig?.syncOverrideEnabled ?: true)
    }
    var timeMode by remember(remoteConfig) {
        mutableStateOf(OwnerTimeMode.fromKey(remoteConfig?.timeMode))
    }
    var offsetMinutesText by remember(remoteConfig) {
        mutableStateOf((remoteConfig?.timeOffsetMinutes ?: 0L).toString())
    }
    var fixedTargetMs by remember(remoteConfig) {
        mutableLongStateOf(
            remoteConfig?.fixedTargetEpochMillis?.takeIf { it > 0L } ?: nowMillis,
        )
    }
    var primaryClockZone by remember(remoteConfig) {
        mutableStateOf(remoteConfig?.primaryClockZone ?: "")
    }
    var timeFormatMode by remember(remoteConfig) {
        mutableStateOf(remoteConfig?.timeFormatMode ?: "")
    }
    var dateFormatStyle by remember(remoteConfig) {
        mutableStateOf(remoteConfig?.dateFormatStyle ?: "")
    }
    var themeMode by remember(remoteConfig) {
        mutableStateOf(remoteConfig?.themeMode ?: "")
    }
    var accentPreset by remember(remoteConfig) {
        mutableStateOf(remoteConfig?.accentPreset ?: "")
    }
    var weatherTempUnit by remember(remoteConfig) {
        mutableStateOf(remoteConfig?.weatherTempUnit ?: "")
    }
    var weatherWindUnit by remember(remoteConfig) {
        mutableStateOf(remoteConfig?.weatherWindUnit ?: "")
    }
    var bannerEnabled by remember(remoteConfig) {
        mutableStateOf(remoteConfig?.bannerEnabled ?: false)
    }
    var bannerMessage by remember(remoteConfig) {
        mutableStateOf(remoteConfig?.bannerMessage ?: "")
    }

    val previewZone: ZoneId = remember(primaryClockZone, preferences.primaryClockZone) {
        if (primaryClockZone.isNotBlank()) PrimaryClockZone.fromKey(primaryClockZone).toZoneId()
        else preferences.primaryClockZone.toZoneId()
    }
    val preview24Hour = remember(timeFormatMode, preferences.use24Hour) {
        when (timeFormatMode) {
            "24h" -> true
            "12h" -> false
            else -> preferences.use24Hour
        }
    }
    val offsetMinutesParsed = offsetMinutesText.toLongOrNull()?.coerceIn(-1440L, 1440L) ?: 0L
    val previewMillis = when (timeMode) {
        OwnerTimeMode.GOOGLE_AUTO -> baseNetworkNowMillis
        OwnerTimeMode.OFFSET -> baseNetworkNowMillis + (offsetMinutesParsed * 60_000L)
        OwnerTimeMode.FIXED -> fixedTargetMs
    }

    RRCard(
        accentGlow = true,
        modifier = Modifier.testTag("settings_owner_mode_card"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Owner Mode Control Center",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = "Authorized Admin: $OWNER_ADMIN_EMAIL",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.accentPrimary,
                )
            }
            RRStatusPill(text = "LIVE SYNC", color = extra.accentPrimary)
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Push Owner Overrides to All Users", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Changes saved here automatically update every signed-in user's app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = syncOverrideEnabled,
                onCheckedChange = { syncOverrideEnabled = it },
                modifier = Modifier.testTag("owner_master_switch"),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = extra.cardBorder)
        Spacer(modifier = Modifier.height(12.dp))

        Text("1. Global Time Control", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OwnerTimeMode.entries.forEach { mode ->
                FilterChip(
                    selected = timeMode == mode,
                    onClick = { timeMode = mode },
                    label = { Text(mode.label) },
                )
            }
        }

        if (timeMode == OwnerTimeMode.OFFSET) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(-60L, -30L, 0L, 30L, 60L, 330L).forEach { mins ->
                    val label = if (mins > 0) "+${mins}m" else "${mins}m"
                    FilterChip(
                        selected = offsetMinutesParsed == mins,
                        onClick = { offsetMinutesText = mins.toString() },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { offsetMinutesText = (offsetMinutesParsed - 15L).coerceIn(-1440L, 1440L).toString() },
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) { Text("-15m") }
                OutlinedTextField(
                    value = offsetMinutesText,
                    onValueChange = { raw ->
                        if (raw.isEmpty() || raw == "-" || raw.toLongOrNull() != null) {
                            offsetMinutesText = raw.take(6)
                        }
                    },
                    label = { Text("Offset (min)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = { offsetMinutesText = (offsetMinutesParsed + 15L).coerceIn(-1440L, 1440L).toString() },
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) { Text("+15m") }
            }
        } else if (timeMode == OwnerTimeMode.FIXED) {
            Spacer(modifier = Modifier.height(8.dp))
            val zdt = Instant.ofEpochMilli(fixedTargetMs).atZone(previewZone)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                val updated = LocalDateTime.of(y, m + 1, d, zdt.hour, zdt.minute, 0)
                                    .atZone(previewZone)
                                    .toInstant()
                                    .toEpochMilli()
                                fixedTargetMs = updated.coerceAtLeast(0L)
                            },
                            zdt.year,
                            zdt.monthValue - 1,
                            zdt.dayOfMonth,
                        ).show()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pick Date")
                }
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, h, min ->
                                val updated = LocalDateTime.of(zdt.year, zdt.monthValue, zdt.dayOfMonth, h, min, 0)
                                    .atZone(previewZone)
                                    .toInstant()
                                    .toEpochMilli()
                                fixedTargetMs = updated.coerceAtLeast(0L)
                            },
                            zdt.hour,
                            zdt.minute,
                            preview24Hour,
                        ).show()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pick Time")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = extra.accentPrimary.copy(alpha = 0.10f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = "Preview Time for All Users (${previewZone.id})",
                    style = MaterialTheme.typography.labelSmall,
                    color = extra.accentPrimary,
                )
                Text(
                    text = ScheduleCalculator.formatTime(
                        millis = previewMillis,
                        use24Hour = preview24Hour,
                        zoneId = previewZone,
                        includeSeconds = true,
                    ) + " • " + ScheduleCalculator.formatDate(
                        millis = previewMillis,
                        pattern = "EEEE, ${DateFormatStyle.fromKey(dateFormatStyle.ifBlank { preferences.dateFormatStyle.key }).pattern}",
                        zoneId = previewZone,
                    ),
                    style = TabularClockMedium.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("2. Global Zone, Format & Weather Units", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("" to "User Choice", "india_ist" to "India (IST)", "device" to "Device Local").forEach { (k, lbl) ->
                FilterChip(
                    selected = primaryClockZone == k,
                    onClick = { primaryClockZone = k },
                    label = { Text(lbl) },
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("" to "User Format", "system" to "System", "12h" to "12-Hour", "24h" to "24-Hour").forEach { (k, lbl) ->
                FilterChip(
                    selected = timeFormatMode == k,
                    onClick = { timeFormatMode = k },
                    label = { Text(lbl) },
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(
                selected = dateFormatStyle == "",
                onClick = { dateFormatStyle = "" },
                label = { Text("User Date") },
            )
            DateFormatStyle.entries.forEach { style ->
                FilterChip(
                    selected = dateFormatStyle == style.key,
                    onClick = { dateFormatStyle = style.key },
                    label = { Text(style.label) },
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("" to "Temp: User", "celsius" to "°C", "fahrenheit" to "°F").forEach { (k, lbl) ->
                FilterChip(
                    selected = weatherTempUnit == k,
                    onClick = { weatherTempUnit = k },
                    label = { Text(lbl) },
                )
            }
            listOf("" to "Wind: User", "kmh" to "km/h", "mph" to "mph").forEach { (k, lbl) ->
                FilterChip(
                    selected = weatherWindUnit == k,
                    onClick = { weatherWindUnit = k },
                    label = { Text(lbl) },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("3. Global Theme & Accent Color", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("" to "User Theme", "system" to "System", "dark" to "Dark", "light" to "Light").forEach { (k, lbl) ->
                FilterChip(
                    selected = themeMode == k,
                    onClick = { themeMode = k },
                    label = { Text(lbl) },
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = accentPreset == "",
                onClick = { accentPreset = "" },
                label = { Text("User Accent") },
            )
            AccentPreset.entries.forEach { preset ->
                val selected = accentPreset == preset.key
                Surface(
                    shape = CircleShape,
                    color = preset.primaryDark,
                    border = if (selected) BorderStroke(2.5.dp, MaterialTheme.colorScheme.onSurface) else null,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { accentPreset = preset.key },
                ) {
                    if (selected) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Check, contentDescription = preset.label, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("4. Broadcast Announcement Banner", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Show Notice on All Dashboards", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = bannerEnabled,
                onCheckedChange = { bannerEnabled = it },
            )
        }
        OutlinedTextField(
            value = bannerMessage,
            onValueChange = { bannerMessage = it.take(240) },
            label = { Text("Announcement message (max 240 chars)") },
            supportingText = { Text("${bannerMessage.length}/240") },
            maxLines = 3,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("owner_banner_input"),
        )

        if (!saveStatus.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = extra.success.copy(alpha = 0.16f),
                shape = RoundedCornerShape(10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(saveStatus, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClearMessages, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Dismiss", modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
        if (!saveError.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        saveError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onClearMessages, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Dismiss", modifier = Modifier.size(15.dp))
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
                onClick = {
                    val config = GlobalAppConfig(
                        timeMode = timeMode.key,
                        timeOffsetMinutes = offsetMinutesParsed,
                        fixedTargetEpochMillis = if (timeMode == OwnerTimeMode.FIXED) fixedTargetMs else 0L,
                        fixedAnchorEpochMillis = if (timeMode == OwnerTimeMode.FIXED) baseNetworkNowMillis else 0L,
                        syncOverrideEnabled = syncOverrideEnabled,
                        primaryClockZone = primaryClockZone,
                        timeFormatMode = timeFormatMode,
                        dateFormatStyle = dateFormatStyle,
                        themeMode = themeMode,
                        accentPreset = accentPreset,
                        weatherTempUnit = weatherTempUnit,
                        weatherWindUnit = weatherWindUnit,
                        bannerEnabled = bannerEnabled,
                        bannerMessage = bannerMessage,
                        updatedByEmail = OWNER_ADMIN_EMAIL,
                    )
                    onPublish(config)
                },
                enabled = !isSaving,
                modifier = Modifier
                    .weight(1f)
                    .testTag("owner_publish_btn"),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Publishing...")
                } else {
                    Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Publish Live")
                }
            }
            OutlinedButton(
                onClick = onReset,
                enabled = !isSaving,
                modifier = Modifier.testTag("owner_reset_btn"),
            ) {
                Text("Reset")
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val extra = LocalRRExtraColors.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = extra.glassFill,
        border = BorderStroke(1.dp, extra.cardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = iconColor.copy(alpha = 0.22f),
                    border = BorderStroke(1.dp, iconColor.copy(alpha = 0.48f)),
                    modifier = Modifier.size(38.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = iconColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ChevronRight,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    content = content,
                )
            }
        }
    }
}

