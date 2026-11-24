package com.example.core.di

import android.content.Context
import android.text.format.DateFormat
import com.example.R
import com.example.core.ai.AiAssistantRepository
import com.example.core.calendar.DeviceCalendarReader
import com.example.core.database.BackupManager
import com.example.core.database.RRDatabase
import com.example.core.datastore.UserPreferences
import com.example.core.datastore.UserPreferencesRepository
import com.example.core.firebase.GlobalAppConfig
import com.example.core.firebase.OwnerConfigRepository
import com.example.core.firebase.resolveEffectivePreferences
import com.example.core.location.LocationHelper
import com.example.core.network.NetworkModule
import com.example.core.network.NetworkTimeProvider
import com.example.core.network.OwnerTimeOverride
import com.example.core.notifications.RRNotificationManager
import com.example.core.notifications.ReminderAlarmScheduler
import com.example.data.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: RRDatabase by lazy { RRDatabase.getInstance(appContext) }
    val preferencesRepository: UserPreferencesRepository by lazy { UserPreferencesRepository(appContext) }
    val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(appContext.getString(R.string.firestore_database_id))
    }
    val ownerConfigRepository: OwnerConfigRepository by lazy { OwnerConfigRepository(firestore) }
    val notificationManager: RRNotificationManager by lazy {
        RRNotificationManager(appContext, database.notificationLogDao())
    }
    val scheduler: ReminderAlarmScheduler by lazy { ReminderAlarmScheduler(appContext) }
    val locationHelper: LocationHelper by lazy { LocationHelper(appContext) }
    val calendarReader: DeviceCalendarReader by lazy { DeviceCalendarReader(appContext) }
    val backupManager: BackupManager by lazy { BackupManager(database) }
    val aiAssistantRepository: AiAssistantRepository by lazy { AiAssistantRepository() }
    val networkTimeProvider: NetworkTimeProvider by lazy { NetworkTimeProvider(NetworkModule.httpClient) }

    private val _remoteGlobalConfig = MutableStateFlow<GlobalAppConfig?>(null)
    val remoteGlobalConfig: StateFlow<GlobalAppConfig?> = _remoteGlobalConfig.asStateFlow()

    val effectivePreferences: Flow<UserPreferences> = combine(
        preferencesRepository.preferences,
        _remoteGlobalConfig,
    ) { local, remote ->
        resolveEffectivePreferences(
            local = local,
            remote = remote,
            isSystem24Hour = DateFormat.is24HourFormat(appContext),
        )
    }

    fun applyRemoteGlobalConfig(config: GlobalAppConfig?) {
        _remoteGlobalConfig.value = config
        if (config == null || !config.syncOverrideEnabled) {
            networkTimeProvider.clearOwnerTimeOverride()
        } else {
            networkTimeProvider.setOwnerTimeOverride(
                OwnerTimeOverride(
                    enabled = true,
                    mode = config.timeMode,
                    offsetMinutes = config.timeOffsetMinutes,
                    fixedTargetEpochMillis = config.fixedTargetEpochMillis,
                    fixedAnchorEpochMillis = config.fixedAnchorEpochMillis,
                ),
            )
        }
    }

    fun clearRemoteOverrides() {
        applyRemoteGlobalConfig(null)
    }

    val reminderRepository: ReminderRepository by lazy {
        ReminderRepository(appContext, database.reminderDao(), preferencesRepository, scheduler)
    }
    val alarmRepository: AlarmRepository by lazy {
        AlarmRepository(appContext, database.alarmDao(), scheduler)
    }
    val weatherRepository: WeatherRepository by lazy {
        WeatherRepository(
            context = appContext,
            dao = database.weatherCacheDao(),
            prefsRepo = preferencesRepository,
            weatherService = NetworkModule.weatherService,
            geocodingService = NetworkModule.geocodingService,
        )
    }
    val timerRepository: TimerRepository by lazy {
        TimerRepository(appContext, database.timerDao(), scheduler)
    }
    val taskRepository: TaskRepository by lazy { TaskRepository(database.taskDao()) }
    val noteRepository: NoteRepository by lazy { NoteRepository(database.noteDao()) }
    val habitRepository: HabitRepository by lazy { HabitRepository(database.habitDao()) }
    val routineFocusRepository: RoutineFocusRepository by lazy {
        RoutineFocusRepository(database.routineDao(), database.focusDao(), scheduler)
    }
    val calendarEventRepository: CalendarEventRepository by lazy {
        CalendarEventRepository(database.calendarEventDao())
    }
}
