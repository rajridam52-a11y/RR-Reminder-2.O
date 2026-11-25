package com.example

import com.example.core.common.VoiceReminderParser
import com.example.core.database.BackupCalendarEvent
import com.example.core.database.BackupHabit
import com.example.core.database.BackupPayload
import com.example.core.database.BackupReminder
import com.example.core.database.ReminderEntity
import com.example.core.database.RepeatType
import com.example.core.datastore.ClockFaceStyle
import com.example.core.datastore.DashboardWidgetId
import com.example.core.datastore.DateFormatStyle
import com.example.core.datastore.PrimaryClockZone
import com.example.core.datastore.TimeFormatMode
import com.example.core.datastore.UserPreferences
import com.example.core.firebase.GlobalAppConfig
import com.example.core.firebase.OWNER_ADMIN_EMAIL
import com.example.core.firebase.isOwnerUser
import com.example.core.firebase.resolveEffectivePreferences
import com.example.core.health.HealthScoreCalculator
import com.example.core.health.HealthStatus
import com.example.core.health.SystemCheckId
import com.example.core.health.SystemHealthItem
import com.example.core.network.HourlyForecastItem
import com.example.core.network.NetworkModule
import com.example.core.network.NetworkTimeProvider
import com.example.core.network.OwnerTimeOverride
import com.example.core.network.WeatherCodeInterpreter
import com.example.core.network.WeatherUnits
import com.example.core.notifications.ScheduleCalculator
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.Purple80
import com.example.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class RRReminderUnitTests {

    @Test
    fun `recurring daily reminder preserves local wall time across DST spring-forward`() {
        val nyZone = ZoneId.of("America/New_York")
        // 2026-03-07 09:00 EST (day before US DST spring-forward on 2026-03-08)
        val beforeDst = ZonedDateTime.of(2026, 3, 7, 9, 0, 0, 0, nyZone)
        val reminder = ReminderEntity(
            title = "Morning Standup",
            triggerAtMillis = beforeDst.toInstant().toEpochMilli(),
            zoneId = nyZone.id,
            repeatType = RepeatType.DAILY.name,
            repeatIntervalValue = 1,
        )

        val nextMs = ScheduleCalculator.computeNextReminderTrigger(
            reminder = reminder,
            afterMillis = beforeDst.toInstant().toEpochMilli(),
            targetZone = nyZone,
        )
        assertNotNull(nextMs)
        val nextZdt = Instant.ofEpochMilli(nextMs!!).atZone(nyZone)
        assertEquals(8, nextZdt.dayOfMonth)
        assertEquals(9, nextZdt.hour)
        assertEquals(0, nextZdt.minute)
    }

    @Test
    fun `recurring reminder handles time-zone change accurately`() {
        val london = ZoneId.of("Europe/London")
        val tokyo = ZoneId.of("Asia/Tokyo")
        val triggerInLondon = ZonedDateTime.of(2026, 11, 23, 8, 0, 0, 0, london)
        val reminder = ReminderEntity(
            title = "Global Sync",
            triggerAtMillis = triggerInLondon.toInstant().toEpochMilli(),
            zoneId = london.id,
            repeatType = RepeatType.CUSTOM_HOURS.name,
            repeatIntervalValue = 4,
        )

        val nextMs = ScheduleCalculator.computeNextReminderTrigger(
            reminder = reminder,
            afterMillis = triggerInLondon.toInstant().toEpochMilli(),
            targetZone = tokyo,
        )
        assertNotNull(nextMs)
        // +4 hours from 08:00 UTC = 12:00 UTC = 21:00 JST
        val nextInTokyo = Instant.ofEpochMilli(nextMs!!).atZone(tokyo)
        assertEquals(21, nextInTokyo.hour)
    }

    @Test
    fun `weekday mask alarm advances to next selected weekday`() {
        // 2026-11-23 is Monday
        val monMorning = ZonedDateTime.of(2026, 11, 23, 8, 30, 0, 0, ZoneId.of("UTC"))
        // Mask: Wednesday only (bit 2 = 4)
        val wedMask = 1 shl 2
        val nextMs = ScheduleCalculator.computeNextAlarmTrigger(
            hour = 7,
            minute = 15,
            repeatDaysMask = wedMask,
            nowMillis = monMorning.toInstant().toEpochMilli(),
            zoneId = ZoneId.of("UTC"),
        )
        val nextZdt = Instant.ofEpochMilli(nextMs).atZone(ZoneId.of("UTC"))
        assertEquals(25, nextZdt.dayOfMonth)
        assertEquals(7, nextZdt.hour)
        assertEquals(15, nextZdt.minute)
    }

    @Test
    fun `one-time alarm past today rolls over to tomorrow and countdown delta formats accurately`() {
        val zone = ZoneId.of("UTC")
        val now = ZonedDateTime.of(2026, 11, 23, 18, 0, 0, 0, zone).toInstant().toEpochMilli()
        val nextMs = ScheduleCalculator.computeNextAlarmTrigger(
            hour = 7,
            minute = 30,
            repeatDaysMask = 0,
            nowMillis = now,
            zoneId = zone,
        )
        val nextZdt = Instant.ofEpochMilli(nextMs).atZone(zone)
        assertEquals(24, nextZdt.dayOfMonth)
        assertEquals(7, nextZdt.hour)
        assertEquals(30, nextZdt.minute)

        assertEquals("Due now", ScheduleCalculator.formatCountdownDelta(now - 5_000L, now))
        assertEquals("in < 1m", ScheduleCalculator.formatCountdownDelta(now + 30_000L, now))
        assertEquals("in 15m", ScheduleCalculator.formatCountdownDelta(now + 15 * 60_000L, now))
        assertEquals("in 2h 30m", ScheduleCalculator.formatCountdownDelta(now + 150 * 60_000L, now))
        assertEquals("in 1d 2h", ScheduleCalculator.formatCountdownDelta(now + 26 * 3_600_000L, now))
    }

    @Test
    fun `formatHourMinute formats 24h and 12h consistently`() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("14:05", ScheduleCalculator.formatHourMinute(14, 5, use24Hour = true))
            assertEquals("2:05 PM", ScheduleCalculator.formatHourMinute(14, 5, use24Hour = false))
            assertEquals("00:09", ScheduleCalculator.formatHourMinute(0, 9, use24Hour = true))
            assertEquals("12:09 AM", ScheduleCalculator.formatHourMinute(0, 9, use24Hour = false))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun `voice parser flags ambiguous time and extracts clean title`() {
        val now = ZonedDateTime.of(2026, 11, 23, 10, 0, 0, 0, ZoneId.of("UTC"))
        val ambiguous = VoiceReminderParser.parse("Remind me to call Alex at 5", now)
        assertEquals("call Alex", ambiguous.title)
        assertTrue(ambiguous.isAmbiguousTime)

        val explicit = VoiceReminderParser.parse("Submit report tomorrow at 3:30 pm", now)
        assertEquals("Submit report", explicit.title)
        assertFalse(explicit.isAmbiguousTime)
        val zdt = Instant.ofEpochMilli(explicit.suggestedTriggerMillis).atZone(ZoneId.of("UTC"))
        assertEquals(24, zdt.dayOfMonth)
        assertEquals(15, zdt.hour)
        assertEquals(30, zdt.minute)
    }

    @Test
    fun `backup payload v2 roundtrip serialization with Moshi includes calendar events and habit logs`() {
        val adapter = NetworkModule.moshi.adapter(BackupPayload::class.java)
        val payload = BackupPayload(
            schemaVersion = 2,
            reminders = listOf(
                BackupReminder(
                    uuid = "uuid-123",
                    title = "Pay Electricity Bill",
                    description = "Monthly auto-check",
                    triggerAtMillis = 1_800_000_000_000L,
                    zoneId = "UTC",
                    repeatType = "MONTHLY",
                    repeatIntervalValue = 1,
                    repeatWeekdaysMask = 0,
                    priority = "HIGH",
                    category = "Finance",
                    tagsCsv = "bills,monthly",
                    notes = "",
                    isCompleted = false,
                    completedAtMillis = null,
                    locationName = null,
                    locationLat = null,
                    locationLon = null,
                    locationTriggerType = "NONE",
                    createdAtMillis = 1_795_000_000_000L,
                ),
            ),
            habits = listOf(
                BackupHabit(
                    uuid = "habit-1",
                    name = "Hydrate",
                    description = "2L water",
                    frequencyType = "DAILY",
                    targetDaysMask = 0b1111111,
                    weeklyTargetCount = 7,
                    isPaused = false,
                    isArchived = false,
                    completedEpochDays = listOf(20780L, 20781L),
                ),
            ),
            calendarEvents = listOf(
                BackupCalendarEvent(
                    uuid = "event-1",
                    title = "Product Review",
                    description = "Quarterly sync",
                    startMillis = 1_800_010_000_000L,
                    endMillis = 1_800_013_600_000L,
                    location = "Studio A",
                    category = "Work",
                    isAllDay = false,
                    createdAtMillis = 1_795_000_000_000L,
                ),
            ),
        )
        val json = adapter.toJson(payload)
        val decoded = adapter.fromJson(json)
        assertNotNull(decoded)
        assertEquals("RR Reminder", decoded!!.app)
        assertEquals(2, decoded.schemaVersion)
        assertEquals(1, decoded.reminders.size)
        assertEquals("Pay Electricity Bill", decoded.reminders.first().title)
        assertEquals(1, decoded.habits.size)
        assertEquals(listOf(20780L, 20781L), decoded.habits.first().completedEpochDays)
        assertEquals(1, decoded.calendarEvents.size)
        assertEquals("Product Review", decoded.calendarEvents.first().title)
        assertEquals("Studio A", decoded.calendarEvents.first().location)
    }

    @Test
    fun `weather units conversions, compass, visibility, UV, and hourly slice work accurately`() {
        assertEquals(32.0, WeatherUnits.celsiusToFahrenheit(0.0), 0.01)
        assertEquals(212.0, WeatherUnits.celsiusToFahrenheit(100.0), 0.01)
        assertEquals(62.14, WeatherUnits.kmhToMph(100.0), 0.1)

        assertEquals("20°C", WeatherUnits.formatTemp(20.4, useFahrenheit = false))
        assertEquals("69°F", WeatherUnits.formatTemp(20.4, useFahrenheit = true))
        assertEquals("10 km/h N", WeatherUnits.formatWind(10.0, useMph = false, directionDeg = 0))
        assertEquals("6 mph E", WeatherUnits.formatWind(10.0, useMph = true, directionDeg = 90))
        assertEquals("SW", WeatherUnits.degreesToCompass(225))
        assertEquals("N", WeatherUnits.degreesToCompass(355))

        assertEquals("10.0 km", WeatherUnits.formatVisibility(10_000.0, useMph = false))
        assertEquals("6.2 mi", WeatherUnits.formatVisibility(10_000.0, useMph = true))
        assertEquals("--", WeatherUnits.formatVisibility(0.0, useMph = false))

        assertEquals("Low", WeatherUnits.describeUv(1.5))
        assertEquals("Moderate", WeatherUnits.describeUv(4.0))
        assertEquals("High", WeatherUnits.describeUv(6.5))
        assertEquals("Very High", WeatherUnits.describeUv(9.0))
        assertEquals("Extreme", WeatherUnits.describeUv(11.5))

        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("06:45", WeatherUnits.formatIsoTime("2026-11-23T06:45", use24Hour = true))
            assertEquals("6:45 AM", WeatherUnits.formatIsoTime("2026-11-23T06:45", use24Hour = false))
        } finally {
            Locale.setDefault(originalLocale)
        }

        val nowMs = ZonedDateTime.of(2026, 11, 23, 14, 25, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        val hourly = listOf(
            HourlyForecastItem("2026-11-23T12:00", 18.0, 10, 1),
            HourlyForecastItem("2026-11-23T14:00", 20.0, 15, 2),
            HourlyForecastItem("2026-11-23T15:00", 21.0, 20, 3),
        )
        val sliced = WeatherUnits.sliceHourlyFromNow(hourly, "UTC", nowMillis = nowMs, limit = 24)
        assertEquals(2, sliced.size)
        assertEquals("2026-11-23T14:00", sliced.first().timeIso)
    }

    @Test
    fun `stopwatch, timer, real-time clock seconds, and weather code interpretation`() {
        assertEquals("01:05.09", ScheduleCalculator.formatStopwatchMillis(65_090L))
        assertEquals("01:00:00", ScheduleCalculator.formatDurationMillis(3_600_000L))
        val sampleMillis = ZonedDateTime.of(2026, 11, 23, 14, 5, 9, 0, ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli()
        assertEquals("14:05:09", ScheduleCalculator.formatTime(sampleMillis, use24Hour = true, zoneId = ZoneId.of("UTC"), includeSeconds = true))
        assertEquals("14:05", ScheduleCalculator.formatTime(sampleMillis, use24Hour = true, zoneId = ZoneId.of("UTC"), includeSeconds = false))
        assertEquals("Thunderstorm", WeatherCodeInterpreter.describe(95))
        assertNotNull(WeatherCodeInterpreter.severeAlertFor(95, 20.0))
        assertNull(WeatherCodeInterpreter.severeAlertFor(0, 10.0))
    }

    @Test
    fun `india real time and date uses Asia Kolkata IST offset accurately across day boundary`() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("Asia/Kolkata", ScheduleCalculator.INDIA_ZONE.id)
            // 2026-11-23 19:09:22 PST (-08:00) == 2026-11-24 03:09:22 UTC == 2026-11-24 08:39:22 IST (+05:30)
            val pstMillis = ZonedDateTime.of(2026, 11, 23, 19, 9, 22, 0, ZoneId.of("America/Los_Angeles"))
                .toInstant()
                .toEpochMilli()
            assertEquals(
                "08:39:22",
                ScheduleCalculator.formatTime(pstMillis, use24Hour = true, zoneId = ScheduleCalculator.INDIA_ZONE, includeSeconds = true),
            )
            assertEquals(
                "8:39:22 AM",
                ScheduleCalculator.formatTime(pstMillis, use24Hour = false, zoneId = ScheduleCalculator.INDIA_ZONE, includeSeconds = true),
            )
            assertEquals(
                "Tuesday, Nov 24, 2026",
                ScheduleCalculator.formatDate(pstMillis, "EEEE, MMM d, yyyy", ScheduleCalculator.INDIA_ZONE),
            )
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun `theme colors and accent presets initialize cleanly without clinit cycles`() {
        assertEquals(AccentPreset.AURORA, AccentPreset.fromKey(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey(null))
        assertEquals(AccentPreset.AURORA.primaryDark, Purple80)
        assertTrue(AccentPreset.entries.isNotEmpty())
    }

    @Test
    fun `network time HTTP Date header parsing and PrimaryClockZone INDIA_IST mapping work accurately`() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            val parsedEpoch = NetworkTimeProvider.parseHttpDateHeader("Tue, 24 Nov 2026 06:23:00 GMT")
            assertNotNull(parsedEpoch)
            assertEquals(PrimaryClockZone.INDIA_IST, PrimaryClockZone.fromKey(null))
            val istZone = PrimaryClockZone.INDIA_IST.toZoneId()
            assertEquals("Asia/Kolkata", istZone.id)
            assertEquals("11:53:00 AM", ScheduleCalculator.formatTime(parsedEpoch!!, use24Hour = false, zoneId = istZone, includeSeconds = true))
            assertEquals("Tuesday, Nov 24, 2026", ScheduleCalculator.formatDate(parsedEpoch, "EEEE, MMM d, yyyy", istZone))
            assertNull(NetworkTimeProvider.parseHttpDateHeader("invalid-date"))
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun `owner verification strictly requires verified rajridam92 gmail address`() {
        assertEquals("rajridam92@gmail.com", OWNER_ADMIN_EMAIL)
        assertTrue(isOwnerUser("rajridam92@gmail.com", isEmailVerified = true))
        assertTrue(isOwnerUser("  RajRidam92@Gmail.com ", isEmailVerified = true))
        assertFalse(isOwnerUser("rajridam92@gmail.com", isEmailVerified = false))
        assertFalse(isOwnerUser("otheruser@gmail.com", isEmailVerified = true))
        assertFalse(isOwnerUser(null, isEmailVerified = true))
    }

    @Test
    fun `owner time overrides shift or anchor time smoothly and preference merge updates all user settings`() {
        var fakeBaseMs = 1_795_000_000_000L
        val provider = NetworkTimeProvider(
            okHttpClient = NetworkModule.httpClient,
            elapsedMsProvider = { 1000L },
            systemNowMsProvider = { fakeBaseMs },
        )

        assertEquals(fakeBaseMs, provider.currentEpochMillis())

        provider.setOwnerTimeOverride(
            OwnerTimeOverride(
                enabled = true,
                mode = "OFFSET",
                offsetMinutes = 90L,
            ),
        )
        assertEquals(fakeBaseMs + (90L * 60_000L), provider.currentEpochMillis())

        val targetFixed = 1_800_000_000_000L
        val anchorAtPublish = fakeBaseMs
        provider.setOwnerTimeOverride(
            OwnerTimeOverride(
                enabled = true,
                mode = "FIXED",
                fixedTargetEpochMillis = targetFixed,
                fixedAnchorEpochMillis = anchorAtPublish,
            ),
        )
        fakeBaseMs += 15_000L
        assertEquals(targetFixed + 15_000L, provider.currentEpochMillis())

        provider.clearOwnerTimeOverride()
        assertEquals(fakeBaseMs, provider.currentEpochMillis())

        val local = UserPreferences(
            themeMode = ThemeMode.DARK,
            accentPreset = AccentPreset.AURORA,
            primaryClockZone = PrimaryClockZone.INDIA_IST,
            timeFormatMode = TimeFormatMode.H12,
            use24Hour = false,
            dateFormatStyle = DateFormatStyle.MEDIUM,
            useFahrenheit = false,
            useMph = false,
        )
        val disabledRemote = GlobalAppConfig(
            syncOverrideEnabled = false,
            themeMode = "light",
            accentPreset = "rose",
        )
        assertEquals(local, resolveEffectivePreferences(local, disabledRemote))

        val activeRemote = GlobalAppConfig(
            syncOverrideEnabled = true,
            primaryClockZone = "device",
            timeFormatMode = "24h",
            dateFormatStyle = "iso",
            themeMode = "light",
            accentPreset = "emerald",
            weatherTempUnit = "fahrenheit",
            weatherWindUnit = "mph",
        )
        val merged = resolveEffectivePreferences(local, activeRemote)
        assertEquals(ThemeMode.LIGHT, merged.themeMode)
        assertEquals(AccentPreset.EMERALD, merged.accentPreset)
        assertEquals(PrimaryClockZone.DEVICE_LOCAL, merged.primaryClockZone)
        assertEquals(TimeFormatMode.H24, merged.timeFormatMode)
        assertTrue(merged.use24Hour)
        assertEquals(DateFormatStyle.ISO, merged.dateFormatStyle)
        assertTrue(merged.useFahrenheit)
        assertTrue(merged.useMph)
    }

    @Test
    fun `system health score never inflates untested checks and calculates honest weighted percentage`() {
        assertEquals(17, SystemCheckId.entries.size)

        val untested = SystemCheckId.entries.map { SystemHealthItem(id = it, status = HealthStatus.NOT_TESTED) }
        assertEquals(0, HealthScoreCalculator.computeHealthPercentage(untested))

        val allWorking = SystemCheckId.entries.map { SystemHealthItem(id = it, status = HealthStatus.WORKING) }
        assertEquals(100, HealthScoreCalculator.computeHealthPercentage(allWorking))

        val mixed = listOf(
            SystemHealthItem(SystemCheckId.LIVE_CLOCK, status = HealthStatus.WORKING), // 100
            SystemHealthItem(SystemCheckId.WEATHER_API, status = HealthStatus.NOT_CONNECTED), // 30
            SystemHealthItem(SystemCheckId.LOCATION_PERMISSION, status = HealthStatus.NEEDS_PERMISSION), // 35
            SystemHealthItem(SystemCheckId.BACKGROUND_EXECUTION, status = HealthStatus.WARNING), // 65
            SystemHealthItem(SystemCheckId.LOCAL_DATABASE, status = HealthStatus.FAILED), // 0
        )
        // (100 + 30 + 35 + 65 + 0) / 500 = 230 / 500 = 46%
        assertEquals(46, HealthScoreCalculator.computeHealthPercentage(mixed))

        val report = HealthScoreCalculator.summarize(
            items = mixed,
            isScanning = false,
            activeCheckId = null,
            lastFullScanMillis = 1_795_000_000_000L,
        )
        assertEquals(5, report.testedCount)
        assertEquals(1, report.workingCount)
        assertEquals(4, report.issueCount)

        val text = HealthScoreCalculator.buildDiagnosticReportText(
            report = report,
            zoneId = ZoneId.of("Asia/Kolkata"),
            appVersion = "2.0 (2)",
        )
        assertTrue(text.contains("RR SYSTEM HEALTH DIAGNOSTIC REPORT"))
        assertTrue(text.contains("46%"))
        assertTrue(text.contains("Weather API"))
    }

    @Test
    fun `clock face styles and upgraded dashboard widget order resolve cleanly`() {
        assertEquals(ClockFaceStyle.DUAL_HYBRID, ClockFaceStyle.fromKey(null))
        assertEquals(ClockFaceStyle.ANALOG_CHRONO, ClockFaceStyle.fromKey("analog_chrono"))
        assertEquals(ClockFaceStyle.NEON_ORBIT, ClockFaceStyle.fromKey("neon_orbit"))
        assertTrue(DashboardWidgetId.DEFAULT_ORDER.contains(DashboardWidgetId.ACTIVE_TIMERS.key))
        assertTrue(DashboardWidgetId.DEFAULT_ORDER.contains(DashboardWidgetId.WORLD_CLOCK.key))
        assertTrue(DashboardWidgetId.DEFAULT_ORDER.contains(DashboardWidgetId.SYSTEM_HEALTH.key))
    }
}
