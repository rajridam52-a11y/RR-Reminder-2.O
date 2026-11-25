package com.example.core.di

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.R
import com.example.core.ai.AiAssistantRepository
import com.example.core.calendar.DeviceCalendarReader
import com.example.core.database.BackupManager
import com.example.core.database.RRDatabase
import com.example.core.database.TimerEntity
import com.example.core.datastore.UserPreferences
import com.example.core.datastore.UserPreferencesRepository
import com.example.core.firebase.GlobalAppConfig
import com.example.core.firebase.OwnerConfigRepository
import com.example.core.health.SystemHealthChecker
import com.example.core.location.LocationHelper
import com.example.core.network.NetworkModule
import com.example.core.network.NetworkTimeProvider
import com.example.core.network.OwnerTimeOverride
import com.example.core.notifications.AlarmForegroundService
import com.example.core.notifications.RRChannels
import com.example.core.notifications.RRNotificationManager
import com.example.core.notifications.ReminderAlarmScheduler
import com.example.data.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val containerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

    private val _sharedNowMillis = MutableStateFlow(networkTimeProvider.currentEpochMillis())
    val sharedNowMillis: StateFlow<Long> = _sharedNowMillis.asStateFlow()

    init {
        // Continuous, non-blocking top-of-second live clock engine across all screens
        containerScope.launch {
            while (isActive) {
                val now = networkTimeProvider.currentEpochMillis()
                _sharedNowMillis.value = now
                val remainder = ((now % 1000L) + 1000L) % 1000L
                val sleepMs = (1000L - remainder).let { if (it < 40L) it + 1000L else it }
                delay(sleepMs)
            }
        }

        // In-app running timer expiration watcher (zero idle cost when no timers are active)
        containerScope.launch {
            timerRepository.timers.collectLatest { list ->
                val running = list.filter { it.isRunning && it.endEpochMillis != null }
                if (running.isEmpty()) return@collectLatest
                while (isActive) {
                    val now = System.currentTimeMillis()
                    val expired = running.filter { (it.endEpochMillis ?: Long.MAX_VALUE) <= now }
                    if (expired.isNotEmpty()) {
                        expired.forEach { t ->
                            val finalized = timerRepository.finalizeExpiredTimer(
                                timerId = t.id,
                                nowMillis = now,
                                fromReceiver = false,
                            )
                            if (finalized != null) {
                                triggerTimerCompletionAlert(finalized)
                            }
                        }
                        break
                    }
                    delay(250L)
                }
            }
        }
    }

    private suspend fun triggerTimerCompletionAlert(timer: TimerEntity) {
        val vibrate = runCatching { preferencesRepository.getSnapshot().vibrationEnabled }.getOrDefault(true)
        val serviceIntent = Intent(appContext, AlarmForegroundService::class.java).apply {
            action = AlarmForegroundService.ACTION_START
            putExtra(AlarmForegroundService.EXTRA_ID, timer.id)
            putExtra(AlarmForegroundService.EXTRA_LABEL, timer.label)
            putExtra(AlarmForegroundService.EXTRA_IS_TIMER, true)
            putExtra(AlarmForegroundService.EXTRA_VIBRATE, vibrate)
            putExtra(AlarmForegroundService.EXTRA_GRADUAL_VOLUME, false)
        }
        runCatching {
            ContextCompat.startForegroundService(appContext, serviceIntent)
        }.onFailure {
            notificationManager.postSimpleNotification(
                id = AlarmForegroundService.FOREGROUND_NOTIFICATION_ID,
                channelId = RRChannels.TIMERS,
                title = "Timer Finished: ${timer.label}",
                body = "Countdown completed",
                sourceType = "TIMER",
                sourceId = timer.id,
            )
        }
    }

    fun emitClockTick() {
        _sharedNowMillis.value = networkTimeProvider.currentEpochMillis()
    }

    suspend fun syncClockAndEmit() {
        networkTimeProvider.syncNow()
        emitClockTick()
    }

    val systemHealthChecker: SystemHealthChecker by lazy {
        SystemHealthChecker(
            context = appContext,
            database = database,
            preferencesRepository = preferencesRepository,
            effectivePreferences = effectivePreferences,
            networkTimeProvider = networkTimeProvider,
            notificationManager = notificationManager,
        )
    }

    private val _remoteGlobalConfig = MutableStateFlow<GlobalAppConfig?>(null)
    val remoteGlobalConfig: StateFlow<GlobalAppConfig?> = _remoteGlobalConfig.asStateFlow()

    val effectivePreferences: Flow<UserPreferences> = preferencesRepository.preferences

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
            val fingerprint = config.syncFingerprint()
            containerScope.launch {
                val current = preferencesRepository.getSnapshot()
                if (current.lastAppliedOwnerFingerprint != fingerprint) {
                    preferencesRepository.applyOwnerConfigOverrides(
                        config = config,
                        fingerprint = fingerprint,
                    )
                }
            }
        }
        emitClockTick()
    }

    suspend fun reapplyOwnerConfigNow() {
        val cfg = _remoteGlobalConfig.value ?: return
        if (cfg.syncOverrideEnabled) {
            preferencesRepository.applyOwnerConfigOverrides(
                config = cfg,
                fingerprint = cfg.syncFingerprint(),
            )
        }
        emitClockTick()
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
