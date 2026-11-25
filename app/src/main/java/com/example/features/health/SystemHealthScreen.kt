package com.example.features.health

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.datastore.UserPreferences
import com.example.core.health.HealthFixAction
import com.example.core.health.HealthStatus
import com.example.core.health.SystemCheckId
import com.example.core.health.SystemHealthItem
import com.example.core.health.SystemHealthReport
import com.example.core.network.NetworkTimeSyncStatus
import com.example.core.notifications.ScheduleCalculator
import com.example.core.permissions.PermissionHelper
import com.example.ui.components.RRCard
import com.example.ui.components.RRCircularProgressRing
import com.example.ui.components.RRMetricBadge
import com.example.ui.components.RRSectionHeader
import com.example.ui.components.RRStatusPill
import com.example.ui.theme.LocalRRExtraColors
import kotlinx.coroutines.launch

private enum class HealthFilter(val label: String) {
    ALL("All"),
    ISSUES("Issues"),
    WORKING("Working"),
    PERMISSIONS("Permissions"),
    NOT_TESTED("Not Tested"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemHealthScreen(
    report: SystemHealthReport,
    preferences: UserPreferences,
    timeSyncStatus: NetworkTimeSyncStatus,
    onRunFullCheck: () -> Unit,
    onRunSingleCheck: (SystemCheckId) -> Unit,
    onSyncClock: () -> Unit,
    onBuildDiagnosticReport: () -> String,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val extra = LocalRRExtraColors.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedFilter by remember { mutableStateOf(HealthFilter.ALL) }
    var expandedId by remember { mutableStateOf<SystemCheckId?>(null) }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        onRunSingleCheck(SystemCheckId.NOTIFICATION_PERMISSIONS)
        onRunSingleCheck(SystemCheckId.REMINDER_SCHEDULING)
    }

    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        onRunSingleCheck(SystemCheckId.LOCATION_PERMISSION)
        onRunSingleCheck(SystemCheckId.GPS_AVAILABILITY)
    }

    fun handleFixAction(item: SystemHealthItem) {
        when (item.fixAction) {
            HealthFixAction.SYNC_CLOCK -> onSyncClock()
            HealthFixAction.REQUEST_NOTIFICATION_PERMISSION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    PermissionHelper.openAppNotificationSettings(context)
                }
            }
            HealthFixAction.OPEN_EXACT_ALARM_SETTINGS -> PermissionHelper.openExactAlarmSettings(context)
            HealthFixAction.OPEN_BATTERY_SETTINGS -> openBatteryOptimizationSettings(context)
            HealthFixAction.REQUEST_LOCATION_PERMISSION -> {
                locationPermLauncher.launch(PermissionHelper.LOCATION_PERMISSIONS)
            }
            HealthFixAction.OPEN_LOCATION_SETTINGS -> PermissionHelper.openLocationSettings(context)
            HealthFixAction.TEST_WEATHER_API -> onRunSingleCheck(SystemCheckId.WEATHER_API)
            HealthFixAction.OPEN_APP_SETTINGS -> openAppDetailsSettings(context)
            HealthFixAction.RUN_SELF_TEST, HealthFixAction.NONE -> onRunSingleCheck(item.id)
        }
    }

    val primaryZone = remember(preferences.primaryClockZone) { preferences.primaryClockZone.toZoneId() }
    val filteredItems = remember(report.items, selectedFilter) {
        when (selectedFilter) {
            HealthFilter.ALL -> report.items
            HealthFilter.ISSUES -> report.items.filter {
                it.status != HealthStatus.WORKING && it.status != HealthStatus.NOT_TESTED
            }
            HealthFilter.WORKING -> report.items.filter { it.status == HealthStatus.WORKING }
            HealthFilter.PERMISSIONS -> report.items.filter {
                it.id == SystemCheckId.NOTIFICATION_PERMISSIONS ||
                    it.id == SystemCheckId.LOCATION_PERMISSION ||
                    it.id == SystemCheckId.ALARM_SCHEDULING ||
                    it.id == SystemCheckId.BACKGROUND_EXECUTION
            }
            HealthFilter.NOT_TESTED -> report.items.filter { it.status == HealthStatus.NOT_TESTED }
        }
    }
    val issueItems = remember(report.items) {
        report.items.filter {
            it.status != HealthStatus.WORKING &&
                it.status != HealthStatus.NOT_TESTED &&
                it.suggestedSolution != null
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("RR System Health", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = "Real-time 17-point diagnostics",
                            style = MaterialTheme.typography.labelSmall,
                            color = extra.accentPrimary,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("health_back_btn"),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val text = onBuildDiagnosticReport()
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cb?.setPrimaryClip(ClipData.newPlainText("RR System Health Report", text))
                            scope.launch { snackbarHostState.showSnackbar("Detailed diagnostic report copied.") }
                        },
                        modifier = Modifier.testTag("health_copy_report_btn"),
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy diagnostic report")
                    }
                    IconButton(
                        onClick = onRunFullCheck,
                        enabled = !report.isScanning,
                        modifier = Modifier.testTag("health_refresh_btn"),
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh system status")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "health_hero") {
                val scoreColor = healthScoreColor(report.overallHealthPercent)
                val lastScanText = report.lastFullScanMillis?.let { ms ->
                    "${ScheduleCalculator.formatDate(ms, preferences.dateFormatStyle.pattern, primaryZone)} • " +
                        ScheduleCalculator.formatTime(ms, preferences.use24Hour, primaryZone, includeSeconds = true)
                } ?: "Initial scan pending"

                RRCard(
                    accentGlow = true,
                    modifier = Modifier.widthIn(max = 760.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        RRCircularProgressRing(
                            progress = report.overallHealthPercent / 100f,
                            size = 96.dp,
                            strokeWidth = 9.dp,
                            primaryColor = scoreColor,
                            secondaryColor = extra.accentSecondary,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${report.overallHealthPercent}%",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = scoreColor,
                                )
                                Text(
                                    text = "HEALTH",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.HealthAndSafety,
                                    contentDescription = null,
                                    tint = scoreColor,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = if (report.isScanning) {
                                        "Scanning: ${report.activeCheckId?.title ?: "..."}"
                                    } else if (report.issueCount == 0 && report.testedCount == report.items.size) {
                                        "All Systems Verified"
                                    } else {
                                        "${report.issueCount} Issue(s) Detected"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = "Last checked: $lastScanText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "Clock anchor: ${if (timeSyncStatus.isSynced) timeSyncStatus.sourceLabel else "Device Clock"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = extra.accentPrimary,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RRMetricBadge(
                            label = "Working",
                            value = "${report.workingCount}/${report.items.size}",
                            color = extra.success,
                            modifier = Modifier.weight(1f),
                        )
                        RRMetricBadge(
                            label = "Tested",
                            value = "${report.testedCount}/${report.items.size}",
                            color = extra.info,
                            modifier = Modifier.weight(1f),
                        )
                        RRMetricBadge(
                            label = "Issues",
                            value = "${report.issueCount}",
                            color = if (report.issueCount > 0) extra.warning else extra.accentSecondary,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = onRunFullCheck,
                            enabled = !report.isScanning,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("health_run_full_btn"),
                        ) {
                            if (report.isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scanning...")
                            } else {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Run Full System Check")
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                val text = onBuildDiagnosticReport()
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cb?.setPrimaryClip(ClipData.newPlainText("RR System Health Report", text))
                                scope.launch { snackbarHostState.showSnackbar("Diagnostic report copied to clipboard.") }
                            },
                            modifier = Modifier.testTag("health_export_btn"),
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Report")
                        }
                    }
                }
            }

            item(key = "health_subsystem_strip") {
                val map = remember(report.items) { report.items.associateBy { it.id } }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SubsystemMiniCard(
                        icon = Icons.Rounded.Schedule,
                        title = "Clock Sync",
                        item = map[SystemCheckId.LIVE_CLOCK],
                    )
                    SubsystemMiniCard(
                        icon = Icons.Rounded.CloudOff,
                        title = "Weather API",
                        item = map[SystemCheckId.WEATHER_API],
                    )
                    SubsystemMiniCard(
                        icon = Icons.Rounded.Lock,
                        title = "Permissions",
                        item = map[SystemCheckId.NOTIFICATION_PERMISSIONS],
                    )
                    SubsystemMiniCard(
                        icon = Icons.Rounded.Storage,
                        title = "Database",
                        item = map[SystemCheckId.LOCAL_DATABASE],
                    )
                    SubsystemMiniCard(
                        icon = Icons.Rounded.Memory,
                        title = "Performance",
                        item = map[SystemCheckId.APP_PERFORMANCE],
                    )
                    SubsystemMiniCard(
                        icon = Icons.Rounded.BatteryChargingFull,
                        title = "Background",
                        item = map[SystemCheckId.BACKGROUND_EXECUTION],
                    )
                }
            }

            if (issueItems.isNotEmpty()) {
                item(key = "detected_issues_card") {
                    RRCard(modifier = Modifier.widthIn(max = 760.dp)) {
                        RRSectionHeader(title = "Detected Issues & Suggested Solutions")
                        Spacer(modifier = Modifier.height(6.dp))
                        issueItems.forEach { issue ->
                            val statusColor = healthStatusColor(issue.status)
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = statusColor.copy(alpha = 0.09f),
                                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.28f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        imageVector = healthStatusIcon(issue.status),
                                        contentDescription = issue.status.label,
                                        tint = statusColor,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${issue.id.title} • ${issue.status.label}",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = statusColor,
                                        )
                                        Text(
                                            text = issue.suggestedSolution.orEmpty(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (issue.fixAction != HealthFixAction.NONE) {
                                        TextButton(onClick = { handleFixAction(issue) }) {
                                            Text(issue.fixAction.buttonLabel)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "filter_chips") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HealthFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.label) },
                            modifier = Modifier.testTag("health_filter_${filter.name.lowercase()}"),
                        )
                    }
                }
            }

            items(filteredItems, key = { it.id.name }) { item ->
                val isExpanded = expandedId == item.id
                val isRunningThis = report.isScanning && report.activeCheckId == item.id
                SystemCheckCard(
                    item = item,
                    isExpanded = isExpanded,
                    isRunning = isRunningThis,
                    onToggleExpand = {
                        expandedId = if (isExpanded) null else item.id
                    },
                    onTestSingle = { onRunSingleCheck(item.id) },
                    onFix = { handleFixAction(item) },
                )
            }

            item { Spacer(modifier = Modifier.height(36.dp)) }
        }
    }
}

@Composable
private fun SubsystemMiniCard(
    icon: ImageVector,
    title: String,
    item: SystemHealthItem?,
) {
    val status = item?.status ?: HealthStatus.NOT_TESTED
    val color = healthStatusColor(status)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.34f)),
        modifier = Modifier.width(142.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Text(title, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
            Text(
                text = status.label,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = color,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SystemCheckCard(
    item: SystemHealthItem,
    isExpanded: Boolean,
    isRunning: Boolean,
    onToggleExpand: () -> Unit,
    onTestSingle: () -> Unit,
    onFix: () -> Unit,
) {
    val statusColor = healthStatusColor(item.status)
    RRCard(
        onClick = onToggleExpand,
        modifier = Modifier
            .widthIn(max = 760.dp)
            .testTag("health_item_${item.id.name.lowercase()}"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = statusColor.copy(alpha = 0.15f),
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = statusColor,
                        )
                    } else {
                        Icon(
                            imageVector = healthStatusIcon(item.status),
                            contentDescription = item.status.label,
                            tint = statusColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.id.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RRStatusPill(
                text = item.status.label,
                color = statusColor,
            )
            Icon(
                imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (isExpanded) "Collapse details" else "Expand details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = item.id.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = buildString {
                            append(item.technicalDetail)
                            item.latencyMs?.let { append(" • ${it}ms") }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(10.dp),
                    )
                }
                item.suggestedSolution?.let { solution ->
                    Text(
                        text = "Solution: $solution",
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.fixAction != HealthFixAction.NONE && item.fixAction != HealthFixAction.RUN_SELF_TEST) {
                        OutlinedButton(onClick = onFix) {
                            Icon(Icons.Rounded.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(item.fixAction.buttonLabel)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    TextButton(onClick = onTestSingle) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test Now")
                    }
                }
            }
        }
    }
}

@Composable
fun healthStatusColor(status: HealthStatus): Color {
    val extra = LocalRRExtraColors.current
    return when (status) {
        HealthStatus.WORKING -> extra.success
        HealthStatus.WARNING -> extra.warning
        HealthStatus.NEEDS_PERMISSION -> extra.info
        HealthStatus.NOT_CONNECTED -> MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
        HealthStatus.NOT_TESTED -> MaterialTheme.colorScheme.onSurfaceVariant
        HealthStatus.FAILED -> MaterialTheme.colorScheme.error
    }
}

@Composable
fun healthScoreColor(percent: Int): Color {
    val extra = LocalRRExtraColors.current
    return when {
        percent >= 85 -> extra.success
        percent >= 60 -> extra.warning
        else -> MaterialTheme.colorScheme.error
    }
}

private fun healthStatusIcon(status: HealthStatus): ImageVector = when (status) {
    HealthStatus.WORKING -> Icons.Rounded.CheckCircle
    HealthStatus.WARNING -> Icons.Rounded.WarningAmber
    HealthStatus.NEEDS_PERMISSION -> Icons.Rounded.Lock
    HealthStatus.NOT_CONNECTED -> Icons.Rounded.CloudOff
    HealthStatus.NOT_TESTED -> Icons.AutoMirrored.Rounded.HelpOutline
    HealthStatus.FAILED -> Icons.Rounded.ErrorOutline
}

private fun openBatteryOptimizationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        openAppDetailsSettings(context)
    }
}

private fun openAppDetailsSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
