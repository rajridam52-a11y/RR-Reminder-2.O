package com.example.core.di

import android.content.Context
import com.example.core.calendar.DeviceCalendarReader
import com.example.core.database.BackupManager
import com.example.core.database.RRDatabase
import com.example.core.datastore.UserPreferencesRepository
import com.example.core.location.LocationHelper
import com.example.core.network.NetworkModule
import com.example.core.notifications.RRNotificationManager
import com.example.core.notifications.ReminderAlarmScheduler
import com.example.data.*

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: RRDatabase by lazy { RRDatabase.getInstance(appContext) }
    val preferencesRepository: UserPreferencesRepository by lazy { UserPreferencesRepository(appContext) }
    val notificationManager: RRNotificationManager by lazy {
        RRNotificationManager(appContext, database.notificationLogDao())
    }
    val scheduler: ReminderAlarmScheduler by lazy { ReminderAlarmScheduler(appContext) }
    val locationHelper: LocationHelper by lazy { LocationHelper(appContext) }
    val calendarReader: DeviceCalendarReader by lazy { DeviceCalendarReader(appContext) }
    val backupManager: BackupManager by lazy { BackupManager(database) }

    val reminderRepository: ReminderRepository by lazy {
        ReminderRepository(appContext, database.reminderDao(), scheduler)
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
}
