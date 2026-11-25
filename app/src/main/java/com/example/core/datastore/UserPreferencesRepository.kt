package com.example.core.datastore

import android.content.Context
import android.text.format.DateFormat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "rr_user_prefs")

enum class DashboardWidgetId(val key: String, val title: String) {
    BRIEFING("briefing", "Live Clock & Briefing"),
    QUICK_ACTIONS("quick_actions", "Quick Actions"),
    UPCOMING_REMINDERS("upcoming_reminders", "Upcoming Reminders"),
    NEXT_ALARM("next_alarm", "Next Scheduled Alarm"),
    ACTIVE_TIMERS("active_timers", "Active Timers"),
    WEATHER("weather", "Weather Summary"),
    WORLD_CLOCK("world_clock", "World Clock Previews"),
    TODAY_TASKS("today_tasks", "Today's Tasks"),
    SYSTEM_HEALTH("system_health", "RR System Health"),
    RECENT_ACTIVITY("recent_activity", "Recently Completed");

    companion object {
        val DEFAULT_ORDER = entries.map { it.key }
        fun fromKey(key: String): DashboardWidgetId? = entries.firstOrNull { it.key == key }
    }
}

enum class ClockFaceStyle(val key: String, val label: String, val subtitle: String) {
    DUAL_HYBRID("dual_hybrid", "Dual Hybrid", "Live analog dial + digital clock"),
    AURORA_DIGITAL("aurora_digital", "Aurora Digital", "Bold glassmorphic digital readout"),
    ANALOG_CHRONO("analog_chrono", "Analog Chrono", "Luminous precision circular dial"),
    MINIMAL_SWISS("minimal_swiss", "Minimal Swiss", "Crisp geometric railway indices"),
    NEON_ORBIT("neon_orbit", "Neon Orbit", "Orbital second ring & glow readout");

    companion object {
        fun fromKey(key: String?): ClockFaceStyle =
            entries.firstOrNull { it.key == key } ?: DUAL_HYBRID
    }
}

enum class DateFormatStyle(val key: String, val label: String, val pattern: String) {
    MEDIUM("medium", "Nov 23, 2026", "MMM d, yyyy"),
    DAY_MONTH("day_month", "23 Nov 2026", "d MMM yyyy"),
    ISO("iso", "2026-11-23", "yyyy-MM-dd"),
    SLASH_US("slash_us", "11/23/2026", "MM/dd/yyyy");

    companion object {
        fun fromKey(key: String?): DateFormatStyle =
            entries.firstOrNull { it.key == key } ?: MEDIUM
    }
}

enum class TimeFormatMode(val key: String, val label: String) {
    SYSTEM("system", "System Default"),
    H12("12h", "12-Hour"),
    H24("24h", "24-Hour");

    companion object {
        fun fromKey(key: String?): TimeFormatMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

enum class PrimaryClockZone(val key: String, val label: String) {
    INDIA_IST("india_ist", "India (IST)"),
    DEVICE_LOCAL("device", "Device Local");

    fun toZoneId(): java.time.ZoneId =
        if (this == INDIA_IST) java.time.ZoneId.of("Asia/Kolkata")
        else java.time.ZoneId.systemDefault()

    companion object {
        fun fromKey(key: String?): PrimaryClockZone =
            entries.firstOrNull { it.key == key } ?: INDIA_IST
    }
}

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentPreset: AccentPreset = AccentPreset.AURORA,
    val clockFaceStyle: ClockFaceStyle = ClockFaceStyle.DUAL_HYBRID,
    val primaryClockZone: PrimaryClockZone = PrimaryClockZone.INDIA_IST,
    val timeFormatMode: TimeFormatMode = TimeFormatMode.SYSTEM,
    val use24Hour: Boolean = false,
    val dateFormatStyle: DateFormatStyle = DateFormatStyle.MEDIUM,
    val firstDayOfWeek: Int = 1, // 1 = Monday, 7 = Sunday
    val useFahrenheit: Boolean = false,
    val useMph: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val dashboardWidgetOrder: List<String> = DashboardWidgetId.DEFAULT_ORDER,
    val hiddenDashboardWidgets: Set<String> = emptySet(),
    val defaultReminderLeadMinutes: Int = 0,
    val defaultAlarmSnoozeMinutes: Int = 10,
    val vibrationEnabled: Boolean = true,
    val notificationSoundUri: String? = null,
    val locationRemindersEnabled: Boolean = false,
    val useDeviceCalendar: Boolean = false,
    val savedWeatherCity: String = "",
    val savedWeatherLat: Double = 0.0,
    val savedWeatherLon: Double = 0.0,
    val hasSavedWeatherLocation: Boolean = false,
    // Monotonic Stopwatch state
    val stopwatchRunning: Boolean = false,
    val stopwatchStartElapsedNanos: Long = 0L,
    val stopwatchAccumulatedNanos: Long = 0L,
    val stopwatchLapsCsv: String = "",
    // Persistent Focus timer state
    val focusRunning: Boolean = false,
    val focusPhase: String = "FOCUS",
    val focusLabel: String = "Deep Focus",
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val focusRemainingMs: Long = 25 * 60_000L,
    val focusEndEpochMs: Long = 0L,
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_PRESET = stringPreferencesKey("accent_preset")
        val CLOCK_FACE_STYLE = stringPreferencesKey("clock_face_style")
        val PRIMARY_CLOCK_ZONE = stringPreferencesKey("primary_clock_zone")
        val TIME_FORMAT_MODE = stringPreferencesKey("time_format_mode")
        val USE_24_HOUR = booleanPreferencesKey("use_24_hour")
        val DATE_FORMAT = stringPreferencesKey("date_format")
        val FIRST_DAY_OF_WEEK = intPreferencesKey("first_day_of_week")
        val USE_FAHRENHEIT = booleanPreferencesKey("use_fahrenheit")
        val USE_MPH = booleanPreferencesKey("use_mph")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val WIDGET_ORDER = stringPreferencesKey("widget_order")
        val HIDDEN_WIDGETS = stringPreferencesKey("hidden_widgets")
        val DEFAULT_REMINDER_LEAD = intPreferencesKey("default_reminder_lead")
        val DEFAULT_ALARM_SNOOZE = intPreferencesKey("default_alarm_snooze")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val NOTIFICATION_SOUND_URI = stringPreferencesKey("notification_sound_uri")
        val LOCATION_REMINDERS_ENABLED = booleanPreferencesKey("location_reminders_enabled")
        val USE_DEVICE_CALENDAR = booleanPreferencesKey("use_device_calendar")
        val WEATHER_CITY = stringPreferencesKey("weather_city")
        val WEATHER_LAT = doublePreferencesKey("weather_lat")
        val WEATHER_LON = doublePreferencesKey("weather_lon")
        val HAS_WEATHER_LOC = booleanPreferencesKey("has_weather_loc")
        val SW_RUNNING = booleanPreferencesKey("sw_running")
        val SW_START_NANOS = longPreferencesKey("sw_start_nanos")
        val SW_ACCUM_NANOS = longPreferencesKey("sw_accum_nanos")
        val SW_LAPS_CSV = stringPreferencesKey("sw_laps_csv")
        val FOCUS_RUNNING = booleanPreferencesKey("focus_running")
        val FOCUS_PHASE = stringPreferencesKey("focus_phase")
        val FOCUS_LABEL = stringPreferencesKey("focus_label")
        val FOCUS_MINUTES = intPreferencesKey("focus_minutes")
        val BREAK_MINUTES = intPreferencesKey("break_minutes")
        val FOCUS_REMAINING_MS = longPreferencesKey("focus_remaining_ms")
        val FOCUS_END_EPOCH_MS = longPreferencesKey("focus_end_epoch_ms")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val orderRaw = prefs[Keys.WIDGET_ORDER]
        val orderList = if (orderRaw.isNullOrBlank()) {
            DashboardWidgetId.DEFAULT_ORDER
        } else {
            val parsed = orderRaw.split(",").filter { DashboardWidgetId.fromKey(it) != null }
            (parsed + DashboardWidgetId.DEFAULT_ORDER.filterNot { it in parsed })
        }
        val hiddenSet = prefs[Keys.HIDDEN_WIDGETS]
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()

        val timeFormatMode = prefs[Keys.TIME_FORMAT_MODE]?.let { TimeFormatMode.fromKey(it) }
            ?: prefs[Keys.USE_24_HOUR]?.let { if (it) TimeFormatMode.H24 else TimeFormatMode.H12 }
            ?: TimeFormatMode.SYSTEM

        val resolved24Hour = when (timeFormatMode) {
            TimeFormatMode.SYSTEM -> DateFormat.is24HourFormat(context)
            TimeFormatMode.H12 -> false
            TimeFormatMode.H24 -> true
        }

        UserPreferences(
            themeMode = ThemeMode.fromKey(prefs[Keys.THEME_MODE]),
            accentPreset = AccentPreset.fromKey(prefs[Keys.ACCENT_PRESET]),
            clockFaceStyle = ClockFaceStyle.fromKey(prefs[Keys.CLOCK_FACE_STYLE]),
            primaryClockZone = PrimaryClockZone.fromKey(prefs[Keys.PRIMARY_CLOCK_ZONE]),
            timeFormatMode = timeFormatMode,
            use24Hour = resolved24Hour,
            dateFormatStyle = DateFormatStyle.fromKey(prefs[Keys.DATE_FORMAT]),
            firstDayOfWeek = prefs[Keys.FIRST_DAY_OF_WEEK] ?: 1,
            useFahrenheit = prefs[Keys.USE_FAHRENHEIT] ?: false,
            useMph = prefs[Keys.USE_MPH] ?: false,
            onboardingCompleted = prefs[Keys.ONBOARDING_DONE] ?: false,
            dashboardWidgetOrder = orderList,
            hiddenDashboardWidgets = hiddenSet,
            defaultReminderLeadMinutes = prefs[Keys.DEFAULT_REMINDER_LEAD] ?: 0,
            defaultAlarmSnoozeMinutes = prefs[Keys.DEFAULT_ALARM_SNOOZE] ?: 10,
            vibrationEnabled = prefs[Keys.VIBRATION_ENABLED] ?: true,
            notificationSoundUri = prefs[Keys.NOTIFICATION_SOUND_URI],
            locationRemindersEnabled = prefs[Keys.LOCATION_REMINDERS_ENABLED] ?: false,
            useDeviceCalendar = prefs[Keys.USE_DEVICE_CALENDAR] ?: false,
            savedWeatherCity = prefs[Keys.WEATHER_CITY] ?: "",
            savedWeatherLat = prefs[Keys.WEATHER_LAT] ?: 0.0,
            savedWeatherLon = prefs[Keys.WEATHER_LON] ?: 0.0,
            hasSavedWeatherLocation = prefs[Keys.HAS_WEATHER_LOC] ?: false,
            stopwatchRunning = prefs[Keys.SW_RUNNING] ?: false,
            stopwatchStartElapsedNanos = prefs[Keys.SW_START_NANOS] ?: 0L,
            stopwatchAccumulatedNanos = prefs[Keys.SW_ACCUM_NANOS] ?: 0L,
            stopwatchLapsCsv = prefs[Keys.SW_LAPS_CSV] ?: "",
            focusRunning = prefs[Keys.FOCUS_RUNNING] ?: false,
            focusPhase = prefs[Keys.FOCUS_PHASE] ?: "FOCUS",
            focusLabel = prefs[Keys.FOCUS_LABEL] ?: "Deep Focus",
            focusMinutes = prefs[Keys.FOCUS_MINUTES] ?: 25,
            breakMinutes = prefs[Keys.BREAK_MINUTES] ?: 5,
            focusRemainingMs = prefs[Keys.FOCUS_REMAINING_MS] ?: (25 * 60_000L),
            focusEndEpochMs = prefs[Keys.FOCUS_END_EPOCH_MS] ?: 0L,
        )
    }

    suspend fun getSnapshot(): UserPreferences = preferences.first()

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.key }
    }

    suspend fun setAccentPreset(preset: AccentPreset) {
        context.dataStore.edit { it[Keys.ACCENT_PRESET] = preset.key }
    }

    suspend fun setClockFaceStyle(style: ClockFaceStyle) {
        context.dataStore.edit { it[Keys.CLOCK_FACE_STYLE] = style.key }
    }

    suspend fun setPrimaryClockZone(zone: PrimaryClockZone) {
        context.dataStore.edit { it[Keys.PRIMARY_CLOCK_ZONE] = zone.key }
    }

    suspend fun setTimeFormatMode(mode: TimeFormatMode) {
        context.dataStore.edit {
            it[Keys.TIME_FORMAT_MODE] = mode.key
            when (mode) {
                TimeFormatMode.SYSTEM -> it.remove(Keys.USE_24_HOUR)
                TimeFormatMode.H12 -> it[Keys.USE_24_HOUR] = false
                TimeFormatMode.H24 -> it[Keys.USE_24_HOUR] = true
            }
        }
    }

    suspend fun setUse24Hour(use24: Boolean) {
        setTimeFormatMode(if (use24) TimeFormatMode.H24 else TimeFormatMode.H12)
    }

    suspend fun setDateFormat(style: DateFormatStyle) {
        context.dataStore.edit { it[Keys.DATE_FORMAT] = style.key }
    }

    suspend fun setFirstDayOfWeek(day: Int) {
        context.dataStore.edit { it[Keys.FIRST_DAY_OF_WEEK] = day }
    }

    suspend fun setUseFahrenheit(useF: Boolean) {
        context.dataStore.edit { it[Keys.USE_FAHRENHEIT] = useF }
    }

    suspend fun setUseMph(useMph: Boolean) {
        context.dataStore.edit { it[Keys.USE_MPH] = useMph }
    }

    suspend fun setOnboardingCompleted(done: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    suspend fun setDashboardLayout(order: List<String>, hidden: Set<String>) {
        context.dataStore.edit {
            it[Keys.WIDGET_ORDER] = order.joinToString(",")
            it[Keys.HIDDEN_WIDGETS] = hidden.joinToString(",")
        }
    }

    suspend fun setDefaultReminderLead(minutes: Int) {
        context.dataStore.edit { it[Keys.DEFAULT_REMINDER_LEAD] = minutes }
    }

    suspend fun setDefaultAlarmSnooze(minutes: Int) {
        context.dataStore.edit { it[Keys.DEFAULT_ALARM_SNOOZE] = minutes }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VIBRATION_ENABLED] = enabled }
    }

    suspend fun setNotificationSoundUri(uri: String?) {
        context.dataStore.edit {
            if (uri.isNullOrBlank()) it.remove(Keys.NOTIFICATION_SOUND_URI)
            else it[Keys.NOTIFICATION_SOUND_URI] = uri
        }
    }

    suspend fun setLocationRemindersEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LOCATION_REMINDERS_ENABLED] = enabled }
    }

    suspend fun setUseDeviceCalendar(enabled: Boolean) {
        context.dataStore.edit { it[Keys.USE_DEVICE_CALENDAR] = enabled }
    }

    suspend fun setWeatherLocation(city: String, lat: Double, lon: Double) {
        context.dataStore.edit {
            it[Keys.WEATHER_CITY] = city
            it[Keys.WEATHER_LAT] = lat
            it[Keys.WEATHER_LON] = lon
            it[Keys.HAS_WEATHER_LOC] = true
        }
    }

    suspend fun saveStopwatchState(
        running: Boolean,
        startElapsedNanos: Long,
        accumulatedNanos: Long,
        lapsCsv: String,
    ) {
        context.dataStore.edit {
            it[Keys.SW_RUNNING] = running
            it[Keys.SW_START_NANOS] = startElapsedNanos
            it[Keys.SW_ACCUM_NANOS] = accumulatedNanos
            it[Keys.SW_LAPS_CSV] = lapsCsv
        }
    }

    suspend fun saveFocusState(
        running: Boolean,
        phase: String,
        label: String,
        focusMinutes: Int,
        breakMinutes: Int,
        remainingMs: Long,
        endEpochMs: Long,
    ) {
        context.dataStore.edit {
            it[Keys.FOCUS_RUNNING] = running
            it[Keys.FOCUS_PHASE] = phase
            it[Keys.FOCUS_LABEL] = label
            it[Keys.FOCUS_MINUTES] = focusMinutes
            it[Keys.BREAK_MINUTES] = breakMinutes
            it[Keys.FOCUS_REMAINING_MS] = remainingMs
            it[Keys.FOCUS_END_EPOCH_MS] = endEpochMs
        }
    }
}
