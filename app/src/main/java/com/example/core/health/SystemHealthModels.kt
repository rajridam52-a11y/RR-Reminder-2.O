package com.example.core.health

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

enum class HealthStatus(val label: String, val severityOrder: Int) {
    WORKING("Working", 0),
    WARNING("Warning", 1),
    NEEDS_PERMISSION("Needs Permission", 2),
    NOT_CONNECTED("Not Connected", 3),
    NOT_TESTED("Not Tested", 4),
    FAILED("Failed", 5),
}

enum class HealthCategory(val title: String) {
    TIME_SYNC("Time & Clocks"),
    SCHEDULING("Scheduling & Background"),
    WEATHER_LOCATION("Weather & Location"),
    PRODUCTIVITY_TOOLS("Timers & Stopwatch"),
    SYSTEM_CORE("Storage, Network & UI"),
}

enum class HealthFixAction(val buttonLabel: String) {
    NONE(""),
    SYNC_CLOCK("Sync Clock"),
    REQUEST_NOTIFICATION_PERMISSION("Grant Notifications"),
    OPEN_EXACT_ALARM_SETTINGS("Alarm Settings"),
    OPEN_BATTERY_SETTINGS("Battery Settings"),
    REQUEST_LOCATION_PERMISSION("Grant Location"),
    OPEN_LOCATION_SETTINGS("Enable GPS"),
    TEST_WEATHER_API("Retry Weather"),
    OPEN_APP_SETTINGS("App Settings"),
    RUN_SELF_TEST("Run Check"),
}

enum class SystemCheckId(
    val title: String,
    val category: HealthCategory,
    val description: String,
) {
    LIVE_CLOCK(
        title = "Live Clock Engine",
        category = HealthCategory.TIME_SYNC,
        description = "Real-time ticker, timezone offset, and NTP/network anchor synchronization.",
    ),
    REMINDER_SCHEDULING(
        title = "Reminder Scheduling",
        category = HealthCategory.SCHEDULING,
        description = "Pending reminder queries, AlarmManager scheduling, and recurrence engine.",
    ),
    ALARM_SCHEDULING(
        title = "Alarm Scheduling",
        category = HealthCategory.SCHEDULING,
        description = "Exact alarm permission status and AlarmClock scheduling readiness.",
    ),
    NOTIFICATION_PERMISSIONS(
        title = "Notification Permissions",
        category = HealthCategory.SCHEDULING,
        description = "POST_NOTIFICATIONS runtime grant and notification channels availability.",
    ),
    BACKGROUND_EXECUTION(
        title = "Background Execution",
        category = HealthCategory.SCHEDULING,
        description = "Battery optimization exemptions, WorkManager, and BootReceiver readiness.",
    ),
    WEATHER_API(
        title = "Weather API (Open-Meteo)",
        category = HealthCategory.WEATHER_LOCATION,
        description = "Live network reachability and real meteorological payload validation.",
    ),
    GPS_AVAILABILITY(
        title = "GPS Availability",
        category = HealthCategory.WEATHER_LOCATION,
        description = "Hardware GPS/Network location provider enabled status on device.",
    ),
    LOCATION_PERMISSION(
        title = "Location Permission",
        category = HealthCategory.WEATHER_LOCATION,
        description = "ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION runtime permission.",
    ),
    WORLD_CLOCK(
        title = "World Clock",
        category = HealthCategory.TIME_SYNC,
        description = "IANA ZoneId resolution, DST rule evaluation, and saved city timezones.",
    ),
    STOPWATCH(
        title = "Stopwatch Engine",
        category = HealthCategory.PRODUCTIVITY_TOOLS,
        description = "Monotonic SystemClock.elapsedRealtime() precision and lap timing verification.",
    ),
    TIMER(
        title = "Timer Engine",
        category = HealthCategory.PRODUCTIVITY_TOOLS,
        description = "Wall-clock target calculation and DataStore timer state persistence.",
    ),
    LOCAL_DATABASE(
        title = "Local Database (Room)",
        category = HealthCategory.SYSTEM_CORE,
        description = "SQLite Room v2 integrity check and live DAO read/write query latency.",
    ),
    DATA_STORAGE(
        title = "Data Storage (DataStore)",
        category = HealthCategory.SYSTEM_CORE,
        description = "Preferences DataStore read verification and internal app storage capacity.",
    ),
    INTERNET_CONNECTIVITY(
        title = "Internet Connectivity",
        category = HealthCategory.SYSTEM_CORE,
        description = "Active ConnectivityManager transport and NET_CAPABILITY_VALIDATED state.",
    ),
    APP_PERFORMANCE(
        title = "Application Performance",
        category = HealthCategory.SYSTEM_CORE,
        description = "JVM heap utilization, active thread count, and DB query response latency.",
    ),
    THEME_FUNCTIONALITY(
        title = "Theme Functionality",
        category = HealthCategory.SYSTEM_CORE,
        description = "Light/Dark/Auto theme configuration, accent preset, and clock face state.",
    ),
    NAVIGATION_FUNCTIONALITY(
        title = "Navigation Functionality",
        category = HealthCategory.SYSTEM_CORE,
        description = "Verified route registry, active backstack telemetry, and screen routing.",
    ),
}

data class SystemHealthItem(
    val id: SystemCheckId,
    val status: HealthStatus = HealthStatus.NOT_TESTED,
    val summary: String = "Awaiting diagnostic verification.",
    val technicalDetail: String = "Run a system check to inspect real telemetry.",
    val suggestedSolution: String? = null,
    val fixAction: HealthFixAction = HealthFixAction.RUN_SELF_TEST,
    val latencyMs: Long? = null,
    val checkedAtMillis: Long? = null,
)

data class SystemHealthReport(
    val items: List<SystemHealthItem> = SystemCheckId.entries.map { SystemHealthItem(id = it) },
    val isScanning: Boolean = false,
    val activeCheckId: SystemCheckId? = null,
    val lastFullScanMillis: Long? = null,
    val overallHealthPercent: Int = 0,
    val testedCount: Int = 0,
    val workingCount: Int = 0,
    val issueCount: Int = 0,
)

object HealthScoreCalculator {
    /**
     * Computes an honest 0..100 health score across all 17 checks.
     * Untested (`NOT_TESTED`) checks contribute 0 points so the percentage is never inflated
     * before real verification executes.
     */
    fun computeHealthPercentage(items: List<SystemHealthItem>): Int {
        if (items.isEmpty()) return 0
        val totalMax = items.size * 100.0
        val earned = items.sumOf { item ->
            when (item.status) {
                HealthStatus.WORKING -> 100.0
                HealthStatus.WARNING -> 65.0
                HealthStatus.NEEDS_PERMISSION -> 35.0
                HealthStatus.NOT_CONNECTED -> 30.0
                HealthStatus.NOT_TESTED -> 0.0
                HealthStatus.FAILED -> 0.0
            }
        }
        return ((earned / totalMax) * 100.0).roundToInt().coerceIn(0, 100)
    }

    fun summarize(
        items: List<SystemHealthItem>,
        isScanning: Boolean,
        activeCheckId: SystemCheckId?,
        lastFullScanMillis: Long?,
    ): SystemHealthReport {
        val testedCount = items.count { it.status != HealthStatus.NOT_TESTED }
        val workingCount = items.count { it.status == HealthStatus.WORKING }
        val issueCount = items.count {
            it.status == HealthStatus.WARNING ||
                it.status == HealthStatus.NEEDS_PERMISSION ||
                it.status == HealthStatus.NOT_CONNECTED ||
                it.status == HealthStatus.FAILED
        }
        return SystemHealthReport(
            items = items,
            isScanning = isScanning,
            activeCheckId = activeCheckId,
            lastFullScanMillis = lastFullScanMillis,
            overallHealthPercent = computeHealthPercentage(items),
            testedCount = testedCount,
            workingCount = workingCount,
            issueCount = issueCount,
        )
    }

    fun buildDiagnosticReportText(
        report: SystemHealthReport,
        zoneId: ZoneId,
        appVersion: String,
    ): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(zoneId)
        val scanTime = report.lastFullScanMillis?.let { formatter.format(Instant.ofEpochMilli(it)) }
            ?: "Not scanned yet"
        return buildString {
            appendLine("=== RR SYSTEM HEALTH DIAGNOSTIC REPORT ===")
            appendLine("App Version: RR Reminder $appVersion")
            appendLine("Last Full Scan: $scanTime")
            appendLine(
                "Overall Health: ${report.overallHealthPercent}% " +
                    "(${report.workingCount}/${report.items.size} Working, " +
                    "${report.testedCount}/${report.items.size} Tested, " +
                    "${report.issueCount} Issues)",
            )
            appendLine()
            report.items.forEachIndexed { index, item ->
                val latency = item.latencyMs?.let { " [${it}ms]" } ?: ""
                appendLine("${index + 1}. ${item.id.title}: ${item.status.label}$latency")
                appendLine("   Summary: ${item.summary}")
                appendLine("   Details: ${item.technicalDetail}")
                item.suggestedSolution?.let { solution ->
                    appendLine("   Suggested Fix: $solution")
                }
            }
        }
    }
}
