package com.example.core.health

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import androidx.core.location.LocationManagerCompat
import androidx.work.WorkManager
import com.example.core.database.RRDatabase
import com.example.core.database.ReminderEntity
import com.example.core.database.RepeatType
import com.example.core.datastore.UserPreferences
import com.example.core.datastore.UserPreferencesRepository
import com.example.core.network.NetworkModule
import com.example.core.network.NetworkTimeProvider
import com.example.core.notifications.BootAndTimeReceiver
import com.example.core.notifications.RRChannels
import com.example.core.notifications.RRNotificationManager
import com.example.core.notifications.ScheduleCalculator
import com.example.core.permissions.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

class SystemHealthChecker(
    private val context: Context,
    private val database: RRDatabase,
    private val preferencesRepository: UserPreferencesRepository,
    private val effectivePreferences: Flow<UserPreferences>,
    private val networkTimeProvider: NetworkTimeProvider,
    private val notificationManager: RRNotificationManager,
) {
    private val scanMutex = Mutex()

    @Volatile
    private var registeredRoutes: List<String> = emptyList()

    @Volatile
    private var currentRouteName: String? = null

    private val visitedRoutes = LinkedHashSet<String>()

    private val _report = MutableStateFlow(SystemHealthReport())
    val report: StateFlow<SystemHealthReport> = _report.asStateFlow()

    fun updateNavigationTelemetry(currentRoute: String?, knownRoutes: List<String>) {
        if (knownRoutes.isNotEmpty()) {
            registeredRoutes = knownRoutes
        }
        if (!currentRoute.isNullOrBlank()) {
            currentRouteName = currentRoute
            synchronized(visitedRoutes) {
                visitedRoutes.add(currentRoute)
            }
        }
    }

    suspend fun runQuickStartupChecksIfNeeded() {
        if (_report.value.lastFullScanMillis != null) return
        // Run safe, instant local checks automatically on first launch without hitting external APIs unexpectedly,
        // or run a complete real scan including network status checks.
        runFullSystemCheck(includeNetworkApiProbe = false)
    }

    suspend fun runFullSystemCheck(includeNetworkApiProbe: Boolean = true): SystemHealthReport =
        scanMutex.withLock {
            withContext(Dispatchers.IO) {
                val startMap = _report.value.items.associateBy { it.id }.toMutableMap()
                _report.value = HealthScoreCalculator.summarize(
                    items = SystemCheckId.entries.map { startMap[it] ?: SystemHealthItem(it) },
                    isScanning = true,
                    activeCheckId = null,
                    lastFullScanMillis = _report.value.lastFullScanMillis,
                )

                for (checkId in SystemCheckId.entries) {
                    _report.value = _report.value.copy(activeCheckId = checkId)
                    val result = executeCheck(checkId, allowLiveNetworkCall = includeNetworkApiProbe)
                    startMap[checkId] = result
                    _report.value = HealthScoreCalculator.summarize(
                        items = SystemCheckId.entries.map { startMap[it] ?: SystemHealthItem(it) },
                        isScanning = true,
                        activeCheckId = checkId,
                        lastFullScanMillis = _report.value.lastFullScanMillis,
                    )
                }

                val now = networkTimeProvider.currentEpochMillis()
                val finalReport = HealthScoreCalculator.summarize(
                    items = SystemCheckId.entries.map { startMap[it] ?: SystemHealthItem(it) },
                    isScanning = false,
                    activeCheckId = null,
                    lastFullScanMillis = now,
                )
                _report.value = finalReport
                finalReport
            }
        }

    suspend fun runSingleCheck(checkId: SystemCheckId): SystemHealthItem =
        scanMutex.withLock {
            withContext(Dispatchers.IO) {
                _report.value = _report.value.copy(isScanning = true, activeCheckId = checkId)
                val updatedItem = executeCheck(checkId, allowLiveNetworkCall = true)
                val updatedMap = _report.value.items.associateBy { it.id }.toMutableMap()
                updatedMap[checkId] = updatedItem
                _report.value = HealthScoreCalculator.summarize(
                    items = SystemCheckId.entries.map { updatedMap[it] ?: SystemHealthItem(it) },
                    isScanning = false,
                    activeCheckId = null,
                    lastFullScanMillis = _report.value.lastFullScanMillis,
                )
                updatedItem
            }
        }

    private suspend fun executeCheck(
        id: SystemCheckId,
        allowLiveNetworkCall: Boolean,
    ): SystemHealthItem {
        val startedNs = SystemClock.elapsedRealtimeNanos()
        val now = networkTimeProvider.currentEpochMillis()
        val rawItem = runCatching {
            when (id) {
                SystemCheckId.LIVE_CLOCK -> checkLiveClock(now)
                SystemCheckId.REMINDER_SCHEDULING -> checkReminderScheduling(now)
                SystemCheckId.ALARM_SCHEDULING -> checkAlarmScheduling(now)
                SystemCheckId.NOTIFICATION_PERMISSIONS -> checkNotificationPermissions(now)
                SystemCheckId.BACKGROUND_EXECUTION -> checkBackgroundExecution(now)
                SystemCheckId.WEATHER_API -> checkWeatherApi(now, allowLiveNetworkCall)
                SystemCheckId.GPS_AVAILABILITY -> checkGpsAvailability(now)
                SystemCheckId.LOCATION_PERMISSION -> checkLocationPermission(now)
                SystemCheckId.WORLD_CLOCK -> checkWorldClock(now)
                SystemCheckId.STOPWATCH -> checkStopwatch(now)
                SystemCheckId.TIMER -> checkTimer(now)
                SystemCheckId.LOCAL_DATABASE -> checkLocalDatabase(now)
                SystemCheckId.DATA_STORAGE -> checkDataStorage(now)
                SystemCheckId.INTERNET_CONNECTIVITY -> checkInternetConnectivity(now)
                SystemCheckId.APP_PERFORMANCE -> checkAppPerformance(now)
                SystemCheckId.THEME_FUNCTIONALITY -> checkThemeFunctionality(now)
                SystemCheckId.NAVIGATION_FUNCTIONALITY -> checkNavigationFunctionality(now)
            }
        }.getOrElse { err ->
            SystemHealthItem(
                id = id,
                status = HealthStatus.FAILED,
                summary = "Check threw exception: ${err::class.java.simpleName}",
                technicalDetail = err.localizedMessage ?: "Unexpected diagnostic error.",
                suggestedSolution = "Retry this diagnostic check or inspect app logs.",
                fixAction = HealthFixAction.RUN_SELF_TEST,
                checkedAtMillis = now,
            )
        }
        val elapsedMs = ((SystemClock.elapsedRealtimeNanos() - startedNs) / 1_000_000L).coerceAtLeast(1L)
        return rawItem.copy(latencyMs = elapsedMs, checkedAtMillis = now)
    }

    private suspend fun checkLiveClock(now: Long): SystemHealthItem {
        val t1 = networkTimeProvider.currentEpochMillis()
        val e1 = SystemClock.elapsedRealtimeNanos()
        val t2 = networkTimeProvider.currentEpochMillis()
        val e2 = SystemClock.elapsedRealtimeNanos()
        val prefs = runCatching { effectivePreferences.first() }.getOrDefault(UserPreferences())
        val syncState = networkTimeProvider.syncStatus.value
        val zone = prefs.primaryClockZone.toZoneId()
        val driftMs = networkTimeProvider.driftFromDeviceMillis()

        if (e2 < e1 || t2 < t1 - 5_000L) {
            return SystemHealthItem(
                id = SystemCheckId.LIVE_CLOCK,
                status = HealthStatus.FAILED,
                summary = "Clock monotonicity anomaly detected",
                technicalDetail = "elapsedRealtime delta=${e2 - e1}ns, epoch delta=${t2 - t1}ms",
                suggestedSolution = "Run clock re-sync to re-anchor network reference.",
                fixAction = HealthFixAction.SYNC_CLOCK,
            )
        }

        val hasAnchor = networkTimeProvider.hasNetworkAnchor()
        return when {
            !hasAnchor && syncState.errorMessage != null -> SystemHealthItem(
                id = SystemCheckId.LIVE_CLOCK,
                status = HealthStatus.WARNING,
                summary = "Running on device clock (network sync pending)",
                technicalDetail = "Zone=${zone.id}, drift=0ms (unverified). Last error: ${syncState.errorMessage}",
                suggestedSolution = "Connect to the internet and tap Sync Clock to verify drift against Google Time.",
                fixAction = HealthFixAction.SYNC_CLOCK,
            )
            !hasAnchor -> SystemHealthItem(
                id = SystemCheckId.LIVE_CLOCK,
                status = HealthStatus.WARNING,
                summary = "Device clock active; network anchor not yet synced",
                technicalDetail = "Zone=${zone.id}, Format=${if (prefs.use24Hour) "24h" else "12h"}, Override=${syncState.ownerOverrideActive}",
                suggestedSolution = "Tap Sync Clock to anchor against live network time.",
                fixAction = HealthFixAction.SYNC_CLOCK,
            )
            abs(driftMs) > 5_000L && !syncState.ownerOverrideActive -> SystemHealthItem(
                id = SystemCheckId.LIVE_CLOCK,
                status = HealthStatus.WARNING,
                summary = "Device clock differs by ${driftMs / 1000L}s from ${syncState.sourceLabel}",
                technicalDetail = "Anchor=${syncState.sourceLabel}, Drift=${driftMs}ms, Zone=${zone.id}",
                suggestedSolution = "Enable automatic network time in Android system Date & Time settings.",
                fixAction = HealthFixAction.SYNC_CLOCK,
            )
            else -> SystemHealthItem(
                id = SystemCheckId.LIVE_CLOCK,
                status = HealthStatus.WORKING,
                summary = "Synchronized with ${syncState.sourceLabel} (${zone.id})",
                technicalDetail = "Drift=${driftMs}ms, Format=${if (prefs.use24Hour) "24h" else "12h"}, OwnerOverride=${syncState.ownerOverrideLabel ?: "None"}",
                suggestedSolution = null,
                fixAction = HealthFixAction.NONE,
            )
        }
    }

    private suspend fun checkReminderScheduling(now: Long): SystemHealthItem {
        val active = database.reminderDao().getAllActiveSync()
        val exactGranted = PermissionHelper.canScheduleExactAlarms(context)
        val notifGranted = PermissionHelper.hasNotificationPermission(context)

        // Real recurrence calculation verification
        val probe = ReminderEntity(
            id = 0,
            title = "Health Probe",
            triggerAtMillis = now - 3_600_000L,
            repeatType = RepeatType.DAILY.name,
            repeatIntervalValue = 1,
        )
        val nextComputed = ScheduleCalculator.computeNextReminderTrigger(probe, afterMillis = now)
        if (nextComputed == null || nextComputed <= now) {
            return SystemHealthItem(
                id = SystemCheckId.REMINDER_SCHEDULING,
                status = HealthStatus.FAILED,
                summary = "Recurrence schedule engine failed self-test",
                technicalDetail = "computeNextReminderTrigger returned $nextComputed for afterMillis=$now",
                suggestedSolution = "Check device timezone rules and rerun reminder self-check.",
                fixAction = HealthFixAction.RUN_SELF_TEST,
            )
        }

        if (!notifGranted) {
            return SystemHealthItem(
                id = SystemCheckId.REMINDER_SCHEDULING,
                status = HealthStatus.NEEDS_PERMISSION,
                summary = "${active.size} active reminders; notification permission missing",
                technicalDetail = "Recurrence engine OK. Reminders cannot post alerts without POST_NOTIFICATIONS.",
                suggestedSolution = "Grant notification permission so due reminders appear.",
                fixAction = HealthFixAction.REQUEST_NOTIFICATION_PERMISSION,
            )
        }

        if (!exactGranted) {
            return SystemHealthItem(
                id = SystemCheckId.REMINDER_SCHEDULING,
                status = HealthStatus.WARNING,
                summary = "${active.size} active reminders (inexact timing fallback)",
                technicalDetail = "SCHEDULE_EXACT_ALARM denied; AlarmManager uses inexact setAndAllowWhileIdle.",
                suggestedSolution = "Allow Alarms & reminders in system settings for minute-accurate triggers.",
                fixAction = HealthFixAction.OPEN_EXACT_ALARM_SETTINGS,
            )
        }

        return SystemHealthItem(
            id = SystemCheckId.REMINDER_SCHEDULING,
            status = HealthStatus.WORKING,
            summary = "Ready (${active.size} active scheduled)",
            technicalDetail = "Exact alarms granted, notifications enabled, recurrence math verified.",
            suggestedSolution = null,
            fixAction = HealthFixAction.NONE,
        )
    }

    private suspend fun checkAlarmScheduling(now: Long): SystemHealthItem {
        val alarms = database.alarmDao().getAllSync()
        val enabledAlarms = alarms.filter { it.isEnabled }
        val exactGranted = PermissionHelper.canScheduleExactAlarms(context)
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val systemNextAlarm = runCatching { am?.nextAlarmClock?.triggerTime }.getOrNull()

        val probeTrigger = ScheduleCalculator.computeNextAlarmTrigger(7, 30, 0, nowMillis = now)
        if (probeTrigger <= now) {
            return SystemHealthItem(
                id = SystemCheckId.ALARM_SCHEDULING,
                status = HealthStatus.FAILED,
                summary = "Alarm time calculation invalid",
                technicalDetail = "Next alarm probe returned $probeTrigger <= $now",
                suggestedSolution = "Verify device timezone configuration.",
                fixAction = HealthFixAction.RUN_SELF_TEST,
            )
        }

        if (!exactGranted) {
            return SystemHealthItem(
                id = SystemCheckId.ALARM_SCHEDULING,
                status = HealthStatus.NEEDS_PERMISSION,
                summary = "Exact alarm permission required (${enabledAlarms.size} enabled)",
                technicalDetail = "AlarmManager.canScheduleExactAlarms() == false on Android ${Build.VERSION.SDK_INT}.",
                suggestedSolution = "Tap Alarm Settings and enable 'Allow setting alarms and reminders'.",
                fixAction = HealthFixAction.OPEN_EXACT_ALARM_SETTINGS,
            )
        }

        return SystemHealthItem(
            id = SystemCheckId.ALARM_SCHEDULING,
            status = HealthStatus.WORKING,
            summary = "${enabledAlarms.size} active / ${alarms.size} saved alarms ready",
            technicalDetail = "Exact AlarmClock API granted. OS nextAlarmClock=${systemNextAlarm ?: "None"}",
            suggestedSolution = null,
            fixAction = HealthFixAction.NONE,
        )
    }

    private fun checkNotificationPermissions(now: Long): SystemHealthItem {
        notificationManager.ensureChannels()
        val runtimeGranted = PermissionHelper.hasNotificationPermission(context)
        val appNotifsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val requiredChannels = listOf(
            RRChannels.REMINDERS,
            RRChannels.ALARMS,
            RRChannels.TIMERS,
            RRChannels.ROUTINES,
            RRChannels.SYSTEM,
        )
        val missingOrBlocked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
            requiredChannels.filter { chId ->
                val ch = nm.getNotificationChannel(chId)
                ch == null || ch.importance == NotificationManager.IMPORTANCE_NONE
            }
        } else {
            emptyList()
        }

        return when {
            !runtimeGranted -> SystemHealthItem(
                id = SystemCheckId.NOTIFICATION_PERMISSIONS,
                status = HealthStatus.NEEDS_PERMISSION,
                summary = "Notification permission denied",
                technicalDetail = "POST_NOTIFICATIONS is not granted on Android ${Build.VERSION.SDK_INT}.",
                suggestedSolution = "Grant notification permission so reminders, alarms, and timers can alert you.",
                fixAction = HealthFixAction.REQUEST_NOTIFICATION_PERMISSION,
            )
            !appNotifsEnabled -> SystemHealthItem(
                id = SystemCheckId.NOTIFICATION_PERMISSIONS,
                status = HealthStatus.NEEDS_PERMISSION,
                summary = "System app notifications disabled",
                technicalDetail = "areNotificationsEnabled() returned false in Android Settings.",
                suggestedSolution = "Open App Settings and turn ON notifications for RR Reminder.",
                fixAction = HealthFixAction.OPEN_APP_SETTINGS,
            )
            missingOrBlocked.isNotEmpty() -> SystemHealthItem(
                id = SystemCheckId.NOTIFICATION_PERMISSIONS,
                status = HealthStatus.WARNING,
                summary = "${missingOrBlocked.size} notification channel(s) muted or missing",
                technicalDetail = "Affected channels: ${missingOrBlocked.joinToString(", ")}",
                suggestedSolution = "Re-enable muted notification channels in Android App Settings.",
                fixAction = HealthFixAction.OPEN_APP_SETTINGS,
            )
            else -> SystemHealthItem(
                id = SystemCheckId.NOTIFICATION_PERMISSIONS,
                status = HealthStatus.WORKING,
                summary = "Granted — all 5 alert channels active",
                technicalDetail = "Channels verified: ${requiredChannels.joinToString(", ")}",
                suggestedSolution = null,
                fixAction = HealthFixAction.NONE,
            )
        }
    }

    private fun checkBackgroundExecution(now: Long): SystemHealthItem {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val ignoringBattery = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        val receiverState = runCatching {
            context.packageManager.getComponentEnabledSetting(
                ComponentName(context, BootAndTimeReceiver::class.java),
            )
        }.getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        val receiverDisabled = receiverState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
            receiverState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
        val workManagerOk = runCatching { WorkManager.getInstance(context); true }.getOrDefault(false)

        return when {
            !workManagerOk || receiverDisabled -> SystemHealthItem(
                id = SystemCheckId.BACKGROUND_EXECUTION,
                status = HealthStatus.FAILED,
                summary = "Background scheduling component disabled",
                technicalDetail = "WorkManager=$workManagerOk, BootReceiverDisabled=$receiverDisabled",
                suggestedSolution = "Re-enable app background execution and WorkManager initialization.",
                fixAction = HealthFixAction.OPEN_APP_SETTINGS,
            )
            !ignoringBattery -> SystemHealthItem(
                id = SystemCheckId.BACKGROUND_EXECUTION,
                status = HealthStatus.WARNING,
                summary = "Battery optimization active (may delay Doze triggers)",
                technicalDetail = "isIgnoringBatteryOptimizations=false; WorkManager=OK; BootReceiver=Enabled",
                suggestedSolution = "Set battery usage to Unrestricted in Battery Settings for 100% reliable alarms.",
                fixAction = HealthFixAction.OPEN_BATTERY_SETTINGS,
            )
            else -> SystemHealthItem(
                id = SystemCheckId.BACKGROUND_EXECUTION,
                status = HealthStatus.WORKING,
                summary = "Unrestricted background execution & BootReceiver active",
                technicalDetail = "Battery optimization exempt, WorkManager initialized, BootAndTimeReceiver ready.",
                suggestedSolution = null,
                fixAction = HealthFixAction.NONE,
            )
        }
    }

    private suspend fun checkWeatherApi(now: Long, allowLiveNetworkCall: Boolean): SystemHealthItem {
        if (!isNetworkConnected()) {
            return SystemHealthItem(
                id = SystemCheckId.WEATHER_API,
                status = HealthStatus.NOT_CONNECTED,
                summary = "Offline — cannot reach Open-Meteo API",
                technicalDetail = "No active validated internet connection for https://api.open-meteo.com.",
                suggestedSolution = "Connect to Wi-Fi or mobile data and tap Retry Weather.",
                fixAction = HealthFixAction.TEST_WEATHER_API,
            )
        }

        val prefs = runCatching { effectivePreferences.first() }.getOrDefault(UserPreferences())
        val cached = database.weatherCacheDao().getCacheSync()

        if (!allowLiveNetworkCall) {
            return if (cached != null) {
                val ageMins = ((now - cached.updatedAtMillis) / 60_000L).coerceAtLeast(0L)
                SystemHealthItem(
                    id = SystemCheckId.WEATHER_API,
                    status = HealthStatus.NOT_TESTED,
                    summary = "Live API probe not yet run (cache: ${cached.cityName}, ${ageMins}m old)",
                    technicalDetail = "Tap 'Test' or 'Run Full Check' to verify live Open-Meteo HTTPS response.",
                    suggestedSolution = "Tap Retry Weather to verify live API connectivity.",
                    fixAction = HealthFixAction.TEST_WEATHER_API,
                )
            } else {
                SystemHealthItem(
                    id = SystemCheckId.WEATHER_API,
                    status = HealthStatus.NOT_TESTED,
                    summary = "Open-Meteo live check not yet run",
                    technicalDetail = "Run a live check to verify HTTPS response from api.open-meteo.com.",
                    suggestedSolution = "Tap Retry Weather to test live weather API reachability.",
                    fixAction = HealthFixAction.TEST_WEATHER_API,
                )
            }
        }

        val lat = if (prefs.hasSavedWeatherLocation) prefs.savedWeatherLat else 28.6139
        val lon = if (prefs.hasSavedWeatherLocation) prefs.savedWeatherLon else 77.2090
        val city = if (prefs.hasSavedWeatherLocation && prefs.savedWeatherCity.isNotBlank()) prefs.savedWeatherCity else "New Delhi"

        val result = runCatching {
            NetworkModule.weatherService.getForecast(latitude = lat, longitude = lon, forecastDays = 1)
        }
        return result.fold(
            onSuccess = { resp ->
                val temp = resp.current?.temperature2m
                if (temp == null) {
                    SystemHealthItem(
                        id = SystemCheckId.WEATHER_API,
                        status = HealthStatus.FAILED,
                        summary = "Open-Meteo returned empty weather payload",
                        technicalDetail = "HTTP 200 but current.temperature2m was null for ($lat, $lon).",
                        suggestedSolution = "Retry weather probe in a few seconds.",
                        fixAction = HealthFixAction.TEST_WEATHER_API,
                    )
                } else {
                    SystemHealthItem(
                        id = SystemCheckId.WEATHER_API,
                        status = HealthStatus.WORKING,
                        summary = "Open-Meteo live ($city: ${temp}°C)",
                        technicalDetail = "HTTPS forecast verified at ($lat, $lon), timezone=${resp.timezone.ifBlank { "auto" }}.",
                        suggestedSolution = null,
                        fixAction = HealthFixAction.NONE,
                    )
                }
            },
            onFailure = { err ->
                SystemHealthItem(
                    id = SystemCheckId.WEATHER_API,
                    status = HealthStatus.FAILED,
                    summary = "Weather API request failed",
                    technicalDetail = err.localizedMessage ?: err::class.java.simpleName,
                    suggestedSolution = "Verify internet access or firewall restrictions and retry.",
                    fixAction = HealthFixAction.TEST_WEATHER_API,
                )
            },
        )
    }

    private fun checkGpsAvailability(now: Long): SystemHealthItem {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return SystemHealthItem(
                id = SystemCheckId.GPS_AVAILABILITY,
                status = HealthStatus.FAILED,
                summary = "LocationManager system service unavailable",
                technicalDetail = "getSystemService(LOCATION_SERVICE) returned null.",
                suggestedSolution = "Device hardware does not expose Android location service.",
                fixAction = HealthFixAction.NONE,
            )

        val servicesEnabled = LocationManagerCompat.isLocationEnabled(lm)
        val hasGpsHardware = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)
        val gpsProviderOn = runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
        val networkProviderOn = runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)

        return when {
            !servicesEnabled -> SystemHealthItem(
                id = SystemCheckId.GPS_AVAILABILITY,
                status = HealthStatus.WARNING,
                summary = "Device Location / GPS is turned OFF",
                technicalDetail = "LocationManagerCompat.isLocationEnabled=false, hasGpsHardware=$hasGpsHardware",
                suggestedSolution = "Turn on Device Location in Quick Settings or tap Enable GPS.",
                fixAction = HealthFixAction.OPEN_LOCATION_SETTINGS,
            )
            !gpsProviderOn && !networkProviderOn -> SystemHealthItem(
                id = SystemCheckId.GPS_AVAILABILITY,
                status = HealthStatus.WARNING,
                summary = "No active GPS or Network location provider",
                technicalDetail = "gpsProvider=false, networkProvider=false, hasGpsHardware=$hasGpsHardware",
                suggestedSolution = "Enable High Accuracy location mode in Location Settings.",
                fixAction = HealthFixAction.OPEN_LOCATION_SETTINGS,
            )
            else -> SystemHealthItem(
                id = SystemCheckId.GPS_AVAILABILITY,
                status = HealthStatus.WORKING,
                summary = buildString {
                    append("Location hardware active (")
                    append(if (gpsProviderOn) "GPS" else "Network")
                    if (gpsProviderOn && networkProviderOn) append(" + Network")
                    append(")")
                },
                technicalDetail = "isLocationEnabled=true, GPS=$gpsProviderOn, Network=$networkProviderOn, HW=$hasGpsHardware",
                suggestedSolution = null,
                fixAction = HealthFixAction.NONE,
            )
        }
    }

    private fun checkLocationPermission(now: Long): SystemHealthItem {
        val fine = PermissionHelper.hasFineLocationPermission(context)
        val coarse = PermissionHelper.hasCoarseLocationPermission(context)
        return when {
            fine -> SystemHealthItem(
                id = SystemCheckId.LOCATION_PERMISSION,
                status = HealthStatus.WORKING,
                summary = "Precise GPS location permission granted",
                technicalDetail = "ACCESS_FINE_LOCATION=GRANTED, ACCESS_COARSE_LOCATION=GRANTED",
                suggestedSolution = null,
                fixAction = HealthFixAction.NONE,
            )
            coarse -> SystemHealthItem(
                id = SystemCheckId.LOCATION_PERMISSION,
                status = HealthStatus.WARNING,
                summary = "Approximate location only (Coarse granted)",
                technicalDetail = "ACCESS_FINE_LOCATION=DENIED, ACCESS_COARSE_LOCATION=GRANTED",
                suggestedSolution = "Grant Precise location if you want street-level GPS weather & geofencing.",
                fixAction = HealthFixAction.REQUEST_LOCATION_PERMISSION,
            )
            else -> SystemHealthItem(
                id = SystemCheckId.LOCATION_PERMISSION,
                status = HealthStatus.NEEDS_PERMISSION,
                summary = "Location permission not granted",
                technicalDetail = "Both ACCESS_FINE_LOCATION and ACCESS_COARSE_LOCATION are denied. Weather uses manual city until granted.",
                suggestedSolution = "Tap Grant Location to enable automatic GPS city detection.",
                fixAction = HealthFixAction.REQUEST_LOCATION_PERMISSION,
            )
        }
    }

    private suspend fun checkWorldClock(now: Long): SystemHealthItem {
        val cities = database.worldClockDao().getAllSync()
        val instant = Instant.ofEpochMilli(now)
        val invalidZones = cities.filter { runCatching { ZoneId.of(it.zoneId) }.isFailure }
        val dstActiveCount = cities.count { city ->
            runCatching { ZoneId.of(city.zoneId).rules.isDaylightSavings(instant) }.getOrDefault(false)
        }
        val availableZoneCount = ZoneId.getAvailableZoneIds().size

        return when {
            availableZoneCount == 0 -> SystemHealthItem(
                id = SystemCheckId.WORLD_CLOCK,
                status = HealthStatus.FAILED,
                summary = "IANA tzdb zone registry unavailable",
                technicalDetail = "ZoneId.getAvailableZoneIds() returned 0 entries.",
                suggestedSolution = "Restart application or check device tzdata.",
                fixAction = HealthFixAction.RUN_SELF_TEST,
            )
            invalidZones.isNotEmpty() -> SystemHealthItem(
                id = SystemCheckId.WORLD_CLOCK,
                status = HealthStatus.WARNING,
                summary = "${invalidZones.size} saved city timezone(s) invalid",
                technicalDetail = "Invalid zones: ${invalidZones.joinToString { it.cityName }}",
                suggestedSolution = "Remove and re-add affected cities in World Clock.",
                fixAction = HealthFixAction.RUN_SELF_TEST,
            )
            else -> SystemHealthItem(
                id = SystemCheckId.WORLD_CLOCK,
                status = HealthStatus.WORKING,
                summary = "${cities.size} saved cities ($dstActiveCount currently in DST)",
                technicalDetail = "IANA tzdb loaded ($availableZoneCount zones); automatic DST rules verified.",
                suggestedSolution = null,
                fixAction = HealthFixAction.NONE,
            )
        }
    }

    private suspend fun checkStopwatch(now: Long): SystemHealthItem {
        val m1 = SystemClock.elapsedRealtimeNanos()
        val m2 = SystemClock.elapsedRealtimeNanos()
        val prefs = preferencesRepository.getSnapshot()
        if (m2 < m1) {
            return SystemHealthItem(
                id = SystemCheckId.STOPWATCH,
                status = HealthStatus.FAILED,
                summary = "Monotonic clock regression detected",
                technicalDetail = "elapsedRealtimeNanos moved backwards ($m1 -> $m2)",
                suggestedSolution = "Restart device if system monotonic clock is unstable.",
                fixAction = HealthFixAction.RUN_SELF_TEST,
            )
        }
        val stateLabel = if (prefs.stopwatchRunning) "Running" else if (prefs.stopwatchAccumulatedNanos > 0L) "Paused" else "Idle"
        val lapCount = prefs.stopwatchLapsCsv.split(",").count { it.isNotBlank() }
        return SystemHealthItem(
            id = SystemCheckId.STOPWATCH,
            status = HealthStatus.WORKING,
            summary = "Monotonic nanosecond clock verified ($stateLabel, $lapCount laps)",
            technicalDetail = "SystemClock.elapsedRealtimeNanos() progression valid; DataStore persistence active.",
            suggestedSolution = null,
            fixAction = HealthFixAction.NONE,
        )
    }

    private suspend fun checkTimer(now: Long): SystemHealthItem {
        val timers = database.timerDao().getAllSync()
        val running = timers.filter { it.isRunning }
        val staleRunning = running.filter { (it.endEpochMillis ?: 0L) <= now - 60_000L }
        val prefs = preferencesRepository.getSnapshot()

        return if (staleRunning.isNotEmpty()) {
            SystemHealthItem(
                id = SystemCheckId.TIMER,
                status = HealthStatus.WARNING,
                summary = "${staleRunning.size} expired timer(s) awaiting acknowledgment",
                technicalDetail = "Running timers=${running.size}, FocusRunning=${prefs.focusRunning}",
                suggestedSolution = "Open Timers screen to review or reset completed countdowns.",
                fixAction = HealthFixAction.RUN_SELF_TEST,
            )
        } else {
            SystemHealthItem(
                id = SystemCheckId.TIMER,
                status = HealthStatus.WORKING,
                summary = "${timers.size} countdown timers (${running.size} active)",
                technicalDetail = "Timestamp-based countdown math active; Focus timer=${if (prefs.focusRunning) "Running" else "Ready"}.",
                suggestedSolution = null,
                fixAction = HealthFixAction.NONE,
            )
        }
    }

    private suspend fun checkLocalDatabase(now: Long): SystemHealthItem {
        val startNs = SystemClock.elapsedRealtimeNanos()
        val sqlite = database.openHelper.readableDatabase
        val version = sqlite.version
        val integrity = sqlite.isDatabaseIntegrityOk
        val reminders = database.reminderDao().getAllSync().size
        val alarms = database.alarmDao().getAllSync().size
        val tasks = database.taskDao().getAllSync().size
        val notes = database.noteDao().getAllSync().size
        val queryMs = ((SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000L).coerceAtLeast(1L)

        return if (!integrity) {
            SystemHealthItem(
                id = SystemCheckId.LOCAL_DATABASE,
                status = HealthStatus.FAILED,
                summary = "SQLite integrity check reported corruption",
                technicalDetail = "Room v$version integrityOk=false",
                suggestedSolution = "Export a backup in Settings immediately.",
                fixAction = HealthFixAction.RUN_SELF_TEST,
            )
        } else {
            SystemHealthItem(
                id = SystemCheckId.LOCAL_DATABASE,
                status = HealthStatus.WORKING,
                summary = "Room v$version healthy (${reminders + alarms + tasks + notes} core records)",
                technicalDetail = "PRAGMA integrity_check=OK, 4-table query latency=${queryMs}ms (R:$reminders, A:$alarms, T:$tasks, N:$notes)",
                suggestedSolution = null,
                fixAction = HealthFixAction.NONE,
            )
        }
    }

    private suspend fun checkDataStorage(now: Long): SystemHealthItem {
        val startNs = SystemClock.elapsedRealtimeNanos()
        val snapshot = preferencesRepository.getSnapshot()
        val readMs = ((SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000L).coerceAtLeast(1L)
        val freeMb = context.filesDir.usableSpace / (1024L * 1024L)

        return if (freeMb < 50L) {
            SystemHealthItem(
                id = SystemCheckId.DATA_STORAGE,
                status = HealthStatus.WARNING,
                summary = "Low internal storage (${freeMb} MB free)",
                technicalDetail = "DataStore read=${readMs}ms, onboardingCompleted=${snapshot.onboardingCompleted}, usableSpace=${freeMb}MB",
                suggestedSolution = "Free up device storage so backups and database writes never fail.",
                fixAction = HealthFixAction.NONE,
            )
        } else {
            SystemHealthItem(
                id = SystemCheckId.DATA_STORAGE,
                status = HealthStatus.WORKING,
                summary = "Preferences DataStore OK (${freeMb} MB free)",
                technicalDetail = "DataStore snapshot read=${readMs}ms, Zone=${snapshot.primaryClockZone.key}, ClockFace=${snapshot.clockFaceStyle.key}",
                suggestedSolution = null,
                fixAction = HealthFixAction.NONE,
            )
        }
    }

    private fun checkInternetConnectivity(now: Long): SystemHealthItem {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return SystemHealthItem(
                id = SystemCheckId.INTERNET_CONNECTIVITY,
                status = HealthStatus.FAILED,
                summary = "ConnectivityManager unavailable",
                technicalDetail = "System connectivity service returned null.",
                suggestedSolution = "Restart app or check network configuration.",
                fixAction = HealthFixAction.NONE,
            )
        val active = cm.activeNetwork
        val caps = if (active != null) cm.getNetworkCapabilities(active) else null
        val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val transport = when {
            caps == null -> "None"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Other"
        }

        return when {
            !hasInternet -> SystemHealthItem(
                id = SystemCheckId.INTERNET_CONNECTIVITY,
                status = HealthStatus.NOT_CONNECTED,
                summary = "No active internet connection",
                technicalDetail = "ConnectivityManager activeNetwork=${active != null}, transport=$transport",
                suggestedSolution = "Enable Wi-Fi or mobile data for live weather, network time, and cloud sync.",
                fixAction = HealthFixAction.NONE,
            )
            !validated -> SystemHealthItem(
                id = SystemCheckId.INTERNET_CONNECTIVITY,
                status = HealthStatus.WARNING,
                summary = "$transport connected (captive portal or unvalidated)",
                technicalDetail = "NET_CAPABILITY_INTERNET=true, NET_CAPABILITY_VALIDATED=false",
                suggestedSolution = "Check Wi-Fi sign-in portal or mobile data signal.",
                fixAction = HealthFixAction.NONE,
            )
            else -> SystemHealthItem(
                id = SystemCheckId.INTERNET_CONNECTIVITY,
                status = HealthStatus.WORKING,
                summary = "Online via $transport (validated)",
                technicalDetail = "NET_CAPABILITY_INTERNET=true, NET_CAPABILITY_VALIDATED=true",
                suggestedSolution = null,
                fixAction = HealthFixAction.NONE,
            )
        }
    }

    private suspend fun checkAppPerformance(now: Long): SystemHealthItem {
        val runtime = Runtime.getRuntime()
        val maxMb = (runtime.maxMemory() / (1024L * 1024L)).coerceAtLeast(1L)
        val usedMb = ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)).coerceAtLeast(0L)
        val heapPct = ((usedMb * 100.0) / maxMb).toInt().coerceIn(0, 100)
        val threadCount = Thread.activeCount()

        val dbStartNs = SystemClock.elapsedRealtimeNanos()
        database.reminderDao().getAllActiveSync()
        val dbMs = ((SystemClock.elapsedRealtimeNanos() - dbStartNs) / 1_000_000L).coerceAtLeast(1L)

        return when {
            heapPct >= 88 -> SystemHealthItem(
                id = SystemCheckId.APP_PERFORMANCE,
                status = HealthStatus.WARNING,
                summary = "High JVM heap usage (${usedMb}MB / ${maxMb}MB • $heapPct%)",
                technicalDetail = "Threads=$threadCount, Active query=${dbMs}ms",
                suggestedSolution = "Close heavy background tabs or restart app to reclaim memory.",
                fixAction = HealthFixAction.RUN_SELF_TEST,
            )
            dbMs > 250L -> SystemHealthItem(
                id = SystemCheckId.APP_PERFORMANCE,
                status = HealthStatus.WARNING,
                summary = "Slow local query latency (${dbMs}ms)",
                technicalDetail = "Heap=${usedMb}MB/${maxMb}MB ($heapPct%), Threads=$threadCount",
                suggestedSolution = "Archive old completed items or run Backup & Clean in Settings.",
                fixAction = HealthFixAction.RUN_SELF_TEST,
            )
            else -> SystemHealthItem(
                id = SystemCheckId.APP_PERFORMANCE,
                status = HealthStatus.WORKING,
                summary = "Smooth (${usedMb}MB/${maxMb}MB heap • ${dbMs}ms query)",
                technicalDetail = "Heap utilization=$heapPct%, Active JVM threads=$threadCount, DB query=${dbMs}ms",
                suggestedSolution = null,
                fixAction = HealthFixAction.NONE,
            )
        }
    }

    private suspend fun checkThemeFunctionality(now: Long): SystemHealthItem {
        val prefs = effectivePreferences.first()
        val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val systemDark = nightMask == Configuration.UI_MODE_NIGHT_YES
        val activeThemeLabel = when (prefs.themeMode.key) {
            "dark" -> "Dark (Locked)"
            "light" -> "Light (Locked)"
            else -> if (systemDark) "Auto -> Dark" else "Auto -> Light"
        }
        return SystemHealthItem(
            id = SystemCheckId.THEME_FUNCTIONALITY,
            status = HealthStatus.WORKING,
            summary = "$activeThemeLabel • ${prefs.accentPreset.label} • ${prefs.clockFaceStyle.label}",
            technicalDetail = "ThemeMode=${prefs.themeMode.key}, SystemNight=$systemDark, Accent=${prefs.accentPreset.key}, Face=${prefs.clockFaceStyle.key}",
            suggestedSolution = null,
            fixAction = HealthFixAction.NONE,
        )
    }

    private fun checkNavigationFunctionality(now: Long): SystemHealthItem {
        val routes = registeredRoutes
        val active = currentRouteName
        val visitedCount = synchronized(visitedRoutes) { visitedRoutes.size }
        return when {
            routes.isEmpty() -> SystemHealthItem(
                id = SystemCheckId.NAVIGATION_FUNCTIONALITY,
                status = HealthStatus.NOT_TESTED,
                summary = "Navigation host telemetry not yet attached",
                technicalDetail = "No NavHost route registry snapshot recorded yet.",
                suggestedSolution = "Navigate through the app screens and re-run check.",
                fixAction = HealthFixAction.RUN_SELF_TEST,
            )
            active.isNullOrBlank() -> SystemHealthItem(
                id = SystemCheckId.NAVIGATION_FUNCTIONALITY,
                status = HealthStatus.WARNING,
                summary = "${routes.size} routes registered; active destination unknown",
                technicalDetail = "Registered: ${routes.joinToString(", ")}",
                suggestedSolution = "Switch tabs or re-open Navigation Host.",
                fixAction = HealthFixAction.RUN_SELF_TEST,
            )
            else -> SystemHealthItem(
                id = SystemCheckId.NAVIGATION_FUNCTIONALITY,
                status = HealthStatus.WORKING,
                summary = "${routes.size} routes active • Current: $active ($visitedCount visited)",
                technicalDetail = "Verified routes: ${routes.joinToString(", ")}",
                suggestedSolution = null,
                fixAction = HealthFixAction.NONE,
            )
        }
    }

    private fun isNetworkConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
