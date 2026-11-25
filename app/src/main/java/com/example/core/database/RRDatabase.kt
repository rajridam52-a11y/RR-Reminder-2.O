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
    version = 3,
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

        private fun addColumnIfMissing(
            db: SupportSQLiteDatabase,
            tableName: String,
            columnName: String,
            columnDefinition: String,
        ) {
            val existingColumns = mutableSetOf<String>()
            db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0) {
                        existingColumns.add(cursor.getString(nameIndex))
                    }
                }
            }
            if (columnName !in existingColumns) {
                db.execSQL("ALTER TABLE `$tableName` ADD COLUMN `$columnName` $columnDefinition")
            }
        }

        private fun ensureV2AndV3SchemaElements(db: SupportSQLiteDatabase) {
            addColumnIfMissing(db, "weather_cache", "apiTimezone", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "weather_cache", "windDirectionDeg", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "weather_cache", "pressureHpa", "REAL NOT NULL DEFAULT 0.0")
            addColumnIfMissing(db, "weather_cache", "uvIndex", "REAL NOT NULL DEFAULT 0.0")
            addColumnIfMissing(db, "weather_cache", "visibilityMeters", "REAL NOT NULL DEFAULT 0.0")
            addColumnIfMissing(db, "weather_cache", "precipitationMm", "REAL NOT NULL DEFAULT 0.0")
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

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureV2AndV3SchemaElements(db)
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureV2AndV3SchemaElements(db)
            }
        }

        fun getInstance(context: Context): RRDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): RRDatabase {
            lateinit var roomDb: RRDatabase
            roomDb = Room.databaseBuilder(
                context,
                RRDatabase::class.java,
                "rr_reminder.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            runCatching { seedDefaults(roomDb) }
                        }
                    }
                })
                .build()
            return roomDb
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
            if (clockDao.getAllSync().isEmpty()) {
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
}
