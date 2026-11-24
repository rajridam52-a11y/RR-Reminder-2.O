package com.example.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
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
        CalendarEventEntity::class,
        NotificationLogEntity::class,
    ],
    version = 2,
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
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun notificationLogDao(): NotificationLogDao

    companion object {
        @Volatile
        private var INSTANCE: RRDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE weather_cache ADD COLUMN apiTimezone TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE weather_cache ADD COLUMN windDirectionDeg INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE weather_cache ADD COLUMN pressureHpa REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE weather_cache ADD COLUMN uvIndex REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE weather_cache ADD COLUMN visibilityMeters REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE weather_cache ADD COLUMN precipitationMm REAL NOT NULL DEFAULT 0.0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS calendar_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        uuid TEXT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        startMillis INTEGER NOT NULL,
                        endMillis INTEGER NOT NULL,
                        location TEXT NOT NULL,
                        category TEXT NOT NULL,
                        isAllDay INTEGER NOT NULL,
                        createdAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

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
                .addMigrations(MIGRATION_1_2)
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
                WorldClockCityEntity(cityName = "New Delhi", countryName = "India", zoneId = "Asia/Kolkata", sortOrder = 0),
                WorldClockCityEntity(cityName = "London", countryName = "United Kingdom", zoneId = "Europe/London", sortOrder = 1),
                WorldClockCityEntity(cityName = "New York", countryName = "United States", zoneId = "America/New_York", sortOrder = 2),
                WorldClockCityEntity(cityName = "Tokyo", countryName = "Japan", zoneId = "Asia/Tokyo", sortOrder = 3),
            )
            defaultCities.forEach { clockDao.insertCity(it) }
        }
    }
}
