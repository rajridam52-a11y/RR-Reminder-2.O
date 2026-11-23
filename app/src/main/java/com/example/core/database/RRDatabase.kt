package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ReminderEntity::class,
        AlarmEntity::class,
        AlarmHistoryEntity::class,
        TaskEntity::class,
        SubtaskEntity::class,
        NoteEntity::class,
        HabitEntity::class,
        HabitLogEntity::class,
        RoutineEntity::class,
        RoutineStepEntity::class,
        FocusSessionEntity::class,
        TimerEntity::class,
        TimerPresetEntity::class,
        TimerHistoryEntity::class,
        StopwatchSessionEntity::class,
        WorldClockCityEntity::class,
        WeatherCacheEntity::class,
        NotificationLogEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class RRDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao
    abstract fun alarmDao(): AlarmDao
    abstract fun taskDao(): TaskDao
    abstract fun noteDao(): NoteDao
    abstract fun habitDao(): HabitDao
    abstract fun routineDao(): RoutineDao
    abstract fun focusDao(): FocusDao
    abstract fun timerDao(): TimerDao
    abstract fun stopwatchDao(): StopwatchDao
    abstract fun worldClockDao(): WorldClockDao
    abstract fun weatherCacheDao(): WeatherCacheDao
    abstract fun notificationLogDao(): NotificationLogDao

    companion object {
        @Volatile
        private var INSTANCE: RRDatabase? = null

        fun getInstance(context: Context): RRDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): RRDatabase {
            lateinit var db: RRDatabase
            db = Room.databaseBuilder(
                context,
                RRDatabase::class.java,
                "rr_reminder.db",
            )
                .addCallback(object : Callback() {
                    override fun onCreate(sqlDb: SupportSQLiteDatabase) {
                        super.onCreate(sqlDb)
                        CoroutineScope(Dispatchers.IO).launch {
                            seedDefaults(db)
                        }
                    }
                })
                .build()
            return db
        }

        private suspend fun seedDefaults(db: RRDatabase) {
            val timerDao = db.timerDao()
            val presets = listOf(
                TimerPresetEntity(label = "1 min Quick", durationMillis = 60_000L, sortOrder = 0),
                TimerPresetEntity(label = "5 min Tea", durationMillis = 300_000L, sortOrder = 1),
                TimerPresetEntity(label = "15 min Break", durationMillis = 900_000L, sortOrder = 2),
                TimerPresetEntity(label = "25 min Focus", durationMillis = 1_500_000L, sortOrder = 3),
                TimerPresetEntity(label = "45 min Deep", durationMillis = 2_700_000L, sortOrder = 4),
            )
            presets.forEach { timerDao.insertPreset(it) }

            val clockDao = db.worldClockDao()
            val defaultCities = listOf(
                WorldClockCityEntity(cityName = "London", countryName = "United Kingdom", zoneId = "Europe/London", sortOrder = 0),
                WorldClockCityEntity(cityName = "New York", countryName = "United States", zoneId = "America/New_York", sortOrder = 1),
                WorldClockCityEntity(cityName = "Tokyo", countryName = "Japan", zoneId = "Asia/Tokyo", sortOrder = 2),
            )
            defaultCities.forEach { clockDao.insertCity(it) }
        }
    }
}
