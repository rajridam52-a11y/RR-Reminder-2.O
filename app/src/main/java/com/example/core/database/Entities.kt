package com.example.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class ReminderPriority(val label: String, val level: Int) {
    LOW("Low", 0),
    MEDIUM("Medium", 1),
    HIGH("High", 2),
    URGENT("Urgent", 3);

    companion object {
        fun fromName(name: String?): ReminderPriority =
            entries.firstOrNull { it.name == name } ?: MEDIUM
    }
}

enum class RepeatType(val label: String) {
    NONE("One-time"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly"),
    WEEKDAYS("Selected Weekdays"),
    CUSTOM_HOURS("Every N Hours"),
    CUSTOM_DAYS("Every N Days");

    companion object {
        fun fromName(name: String?): RepeatType =
            entries.firstOrNull { it.name == name } ?: NONE
    }
}

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val triggerAtMillis: Long,
    val zoneId: String = java.time.ZoneId.systemDefault().id,
    val repeatType: String = RepeatType.NONE.name,
    val repeatIntervalValue: Int = 1,
    val repeatWeekdaysMask: Int = 0, // bit 0 = Mon .. bit 6 = Sun
    val priority: String = ReminderPriority.MEDIUM.name,
    val category: String = "General",
    val tagsCsv: String = "",
    val notes: String = "",
    val attachmentUri: String? = null,
    val isCompleted: Boolean = false,
    val completedAtMillis: Long? = null,
    val snoozedUntilMillis: Long? = null,
    val locationName: String? = null,
    val locationLat: Double? = null,
    val locationLon: Double? = null,
    val locationTriggerType: String = "NONE", // NONE, ARRIVE, DEPART
    val locationRadiusMeters: Int = 250,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val label: String = "Alarm",
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean = true,
    val repeatDaysMask: Int = 0, // bit 0 = Mon .. bit 6 = Sun, 0 = one-time
    val ringtoneUri: String? = null,
    val ringtoneTitle: String = "Default Alarm",
    val vibrate: Boolean = true,
    val snoozeMinutes: Int = 10,
    val gradualVolume: Boolean = true,
    val nextTriggerAtMillis: Long? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

@Entity(tableName = "alarm_history")
data class AlarmHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alarmId: Long,
    val label: String,
    val triggeredAtMillis: Long,
    val action: String, // FIRED, SNOOZED, DISMISSED
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val priority: String = ReminderPriority.MEDIUM.name,
    val dueDateMillis: Long? = null,
    val category: String = "Work",
    val isCompleted: Boolean = false,
    val completedAtMillis: Long? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "subtasks",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("taskId")],
)
data class SubtaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val title: String,
    val isDone: Boolean = false,
    val sortOrder: Int = 0,
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val title: String,
    val body: String = "",
    val isChecklist: Boolean = false,
    val checklistJson: String = "[]", // JSON array of {text, checked}
    val category: String = "Personal",
    val isPinned: Boolean = false,
    val linkedReminderId: Long? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val frequencyType: String = "DAILY", // DAILY, WEEKLY, CUSTOM
    val targetDaysMask: Int = 0b1111111, // Mon-Sun
    val weeklyTargetCount: Int = 7,
    val isPaused: Boolean = false,
    val isArchived: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "habit_logs",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["habitId", "dateEpochDay"], unique = true)],
)
data class HabitLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val dateEpochDay: Long,
    val completedAtMillis: Long = System.currentTimeMillis(),
)

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val name: String,
    val routineType: String = "MORNING", // MORNING, EVENING, CUSTOM
    val startHour: Int = 7,
    val startMinute: Int = 0,
    val reminderEnabled: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "routine_steps",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("routineId")],
)
data class RoutineStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val title: String,
    val durationMinutes: Int = 10,
    val sortOrder: Int = 0,
    val lastCompletedEpochDay: Long? = null,
)

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val focusMinutes: Int,
    val breakMinutes: Int,
    val actualDurationSeconds: Int,
    val phase: String, // FOCUS, BREAK
    val completedAtMillis: Long = System.currentTimeMillis(),
)

@Entity(tableName = "timers")
data class TimerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val label: String,
    val durationMillis: Long,
    val remainingMillis: Long,
    val endEpochMillis: Long? = null, // non-null while running
    val isRunning: Boolean = false,
    val isRepeat: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

@Entity(tableName = "timer_presets")
data class TimerPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val durationMillis: Long,
    val sortOrder: Int = 0,
)

@Entity(tableName = "timer_history")
data class TimerHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val durationMillis: Long,
    val completedAtMillis: Long = System.currentTimeMillis(),
)

@Entity(tableName = "stopwatch_sessions")
data class StopwatchSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val totalElapsedMillis: Long,
    val lapsCsvMillis: String, // comma-separated lap durations in ms
    val recordedAtMillis: Long = System.currentTimeMillis(),
)

@Entity(tableName = "world_clock_cities")
data class WorldClockCityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cityName: String,
    val countryName: String,
    val zoneId: String,
    val sortOrder: Int = 0,
)

@Entity(tableName = "weather_cache")
data class WeatherCacheEntity(
    @PrimaryKey val id: Int = 1,
    val cityName: String,
    val latitude: Double,
    val longitude: Double,
    val currentTempC: Double,
    val feelsLikeC: Double,
    val humidityPercent: Int,
    val windKmh: Double,
    val weatherCode: Int,
    val sunriseIso: String,
    val sunsetIso: String,
    val hourlyJson: String,
    val dailyJson: String,
    val updatedAtMillis: Long,
)

@Entity(tableName = "notification_logs")
data class NotificationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: String,
    val title: String,
    val body: String,
    val sourceType: String, // REMINDER, ALARM, TIMER, ROUTINE, LOCATION, SYSTEM
    val sourceId: Long = 0L,
    val postedAtMillis: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
)
