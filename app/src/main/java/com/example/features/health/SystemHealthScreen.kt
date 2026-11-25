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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.datastore.UserPreferences
import com.example.core.health.HealthFixAction
import com.example.core.health.HealthStatus
import com.example.core.health.SystemCheckId
import com.example.core.health.SystemHealthItem
import com.example.core.health.SystemHealthReport
import com.example.core.network.NetworkTimeSyncStatus
import com.example.core.notifications.ScheduleCalculator
import com.example.core.permissions.PermissionHelper
import com.example.ui.components.PulseWaveCanvas
import com.example.ui.components.RRCircularProgressRing
import com.example.ui.components.RRGradientButton
import com.example.ui.components.RRStatusPill
import com.example.ui.theme.LocalRRExtraColors
import com.example.ui.theme.SpaceGroteskFontFamily
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
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedFilter by remember { mutableStateOf(HealthFilter.ALL) }
    var inspectedItem by remember { mutableStateOf<SystemHealthItem?>(null) }

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

    val scoreColor = healthScoreColor(report.overallHealthPercent)
    val lastScanText = report.lastFullScanMillis?.let { ms ->
        "${ScheduleCalculator.formatDate(ms, preferences.dateFormatStyle.pattern, primaryZone)}, " +
            ScheduleCalculator.formatTime(ms, preferences.use24Hour, primaryZone, includeSeconds = false)
    } ?: "Pending scan"

    val ratingLabel = when {
        report.issueCount == 0 && report.overallHealthPercent >= 90 -> "Excellent"
        report.overallHealthPercent >= 75 -> "Good"
        report.overallHealthPercent >= 50 -> "Fair"
        else -> "Needs Attention"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF030B14),
                        Color(0xFF061E24),
                        Color(0xFF04141F),
                        Color(0xFF030812),
                    ),
                ),
            ),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Rounded.VerifiedUser,
                                contentDescription = null,
                                tint = Color(0xFF22E59B),
                                modifier = Modifier.size(22.dp),
                            )
                            Text(
                                text = "RR System Health",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
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
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy diagnostic report", tint = Color(0xFF93C5FD))
                        }
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("health_back_btn"),
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RRGradientButton(
                        text = if (report.isScanning) {
                            "Scanning: ${report.activeCheckId?.title ?: "..."}"
                        } else {
                            "Run Full Check"
                        },
                        onClick = onRunFullCheck,
                        enabled = !report.isScanning,
                        trailingIcon = Icons.Rounded.Refresh,
                        colors = listOf(Color(0xFF0EA5E9), Color(0xFF2563EB)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 520.dp)
                            .testTag("health_run_full_btn"),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Last Checked: $lastScanText",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                    )
                }
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp),
            ) {
                // Hero Central Circular Gauge with Emerald Heartbeat Pulse Wave
                item(key = "health_hero") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(168.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            PulseWaveCanvas(
                                color = scoreColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(92.dp),
                            )
                            RRCircularProgressRing(
                                progress = report.overallHealthPercent / 100f,
                                size = 148.dp,
                                strokeWidth = 11.dp,
                                primaryColor = scoreColor,
                                secondaryColor = Color(0xFF10B981),
                                trackColor = Color(0x331E293B),
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${report.overallHealthPercent}%",
                                        style = MaterialTheme.typography.displaySmall.copy(
                                            fontFamily = SpaceGroteskFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 36.sp,
                                        ),
                                        color = Color.White,
                                    )
                                    Text(
                                        text = ratingLabel,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = scoreColor,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = if (report.isScanning) {
                                    "Scanning ${report.activeCheckId?.title ?: "..."}..."
                                } else if (report.issueCount == 0 && report.testedCount == report.items.size) {
                                    "All Systems Working Perfectly"
                                } else {
                                    "${report.issueCount} Issue(s) • ${report.workingCount}/${report.items.size} Working"
                                },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                                textAlign = TextAlign.Center,
                            )
                            Icon(
                                imageVector = if (report.issueCount == 0 && !report.isScanning) {
                                    Icons.Rounded.CheckCircle
                                } else {
                                    Icons.Rounded.Info
                                },
                                contentDescription = null,
                                tint = scoreColor,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Clock anchor: ${if (timeSyncStatus.isSynced) timeSyncStatus.sourceLabel else "Device Clock"} • ${report.testedCount}/${report.items.size} tested",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF93C5FD),
                        )
                    }
                }

                // Filter bar
                item(key = "filter_chips") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        HealthFilter.entries.forEach { filter ->
                            val selected = selectedFilter == filter
                            Surface(
                                shape = CircleShape,
                                color = if (selected) Color(0xFF0EA5E9) else Color(0x880B192E),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) Color(0xFF7DD3FC) else Color(0x3360A5FA),
                                ),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { selectedFilter = filter }
                                    .testTag("health_filter_${filter.name.lowercase()}"),
                            ) {
                                Text(
                                    text = filter.label,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                                )
                            }
                        }
                    }
                }

                // Suggested fixes banner if any real issues exist
                if (issueItems.isNotEmpty()) {
                    item(key = "detected_issues_card") {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0x993B1907),
                            border = BorderStroke(1.dp, Color(0xFFFBBF24).copy(alpha = 0.45f)),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    "Suggested Actions (${issueItems.size})",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFFDE68A),
                                )
                                issueItems.take(3).forEach { issue ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = "${issue.id.title}: ${issue.suggestedSolution}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (issue.fixAction != HealthFixAction.NONE) {
                                            TextButton(onClick = { handleFixAction(issue) }) {
                                                Text(issue.fixAction.buttonLabel, color = Color(0xFF38BDF8))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Screen 10: Two-Column Compact Diagnostic Checklist Grid
                val chunked = filteredItems.chunked(2)
                items(chunked, key = { row -> row.joinToString("_") { it.id.name } }) { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowItems.forEach { item ->
                            val isRunningThis = report.isScanning && report.activeCheckId == item.id
                            CompactHealthGridCell(
                                item = item,
                                isRunning = isRunningThis,
                                onClick = { inspectedItem = item },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    inspectedItem?.let { current ->
        val liveItem = report.items.firstOrNull { it.id == current.id } ?: current
        val statusColor = healthStatusColor(liveItem.status)
        AlertDialog(
            onDismissRequest = { inspectedItem = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(healthStatusIcon(liveItem.status), contentDescription = null, tint = statusColor)
                    Text(liveItem.id.title)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RRStatusPill(text = liveItem.status.label, color = statusColor)
                    Text(liveItem.id.description, style = MaterialTheme.typography.bodySmall)
                    Text(liveItem.summary, style = MaterialTheme.typography.bodyMedium)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = buildString {
                                append(liveItem.technicalDetail)
                                liveItem.latencyMs?.let { append(" • ${it}ms") }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                    liveItem.suggestedSolution?.let { solution ->
                        Text(
                            text = "Solution: $solution",
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor,
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (liveItem.fixAction != HealthFixAction.NONE && liveItem.fixAction != HealthFixAction.RUN_SELF_TEST) {
                        OutlinedButton(onClick = { handleFixAction(liveItem) }) {
                            Text(liveItem.fixAction.buttonLabel)
                        }
                    }
                    Button(onClick = { onRunSingleCheck(liveItem.id) }) {
                        Text("Test Now")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { inspectedItem = null }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun CompactHealthGridCell(
    item: SystemHealthItem,
    isRunning: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusColor = healthStatusColor(item.status)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0x88071726),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.28f)),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("health_item_${item.id.name.lowercase()}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = statusColor.copy(alpha = 0.20f),
                modifier = Modifier.size(22.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            strokeWidth = 2.dp,
                            color = statusColor,
                        )
                    } else {
                        Icon(
                            imageVector = healthStatusIcon(item.status),
                            contentDescription = item.status.label,
                            tint = statusColor,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.id.title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.status.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    maxLines = 1,
                )
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
    HealthStatus.WORKING -> Icons.Rounded.Check
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
