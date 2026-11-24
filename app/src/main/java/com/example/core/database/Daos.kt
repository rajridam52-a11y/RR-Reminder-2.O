package com.example.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class TaskWithSubtasks(
    @Embedded val task: TaskEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "taskId",
    )
    val subtasks: List<SubtaskEntity>,
)

data class HabitWithLogs(
    @Embedded val habit: HabitEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "habitId",
    )
    val logs: List<HabitLogEntity>,
)

data class RoutineWithSteps(
    @Embedded val routine: RoutineEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "routineId",
    )
    val steps: List<RoutineStepEntity>,
)

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY isCompleted ASC, triggerAtMillis ASC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE isCompleted = 0 ORDER BY triggerAtMillis ASC")
    suspend fun getAllActiveSync(): List<ReminderEntity>

    @Query("SELECT * FROM reminders")
    suspend fun getAllSync(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Delete
    suspend fun delete(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour ASC, minute ASC")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms")
    suspend fun getAllSync(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AlarmEntity?

    @Query("SELECT * FROM alarms WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): AlarmEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alarm: AlarmEntity): Long

    @Update
    suspend fun update(alarm: AlarmEntity)

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM alarm_history ORDER BY triggeredAtMillis DESC LIMIT 100")
    fun observeHistory(): Flow<List<AlarmHistoryEntity>>

    @Insert
    suspend fun insertHistory(entry: AlarmHistoryEntity)

    @Query("DELETE FROM alarm_history")
    suspend fun clearHistory()
}

@Dao
interface TaskDao {
    @Transaction
    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, dueDateMillis ASC, createdAtMillis DESC")
    fun observeAllWithSubtasks(): Flow<List<TaskWithSubtasks>>

    @Query("SELECT * FROM tasks")
    suspend fun getAllSync(): List<TaskEntity>

    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY sortOrder ASC")
    suspend fun getSubtasksSync(taskId: Long): List<SubtaskEntity>

    @Query("SELECT * FROM tasks WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubtask(subtask: SubtaskEntity): Long

    @Update
    suspend fun updateSubtask(subtask: SubtaskEntity)

    @Query("DELETE FROM subtasks WHERE id = :id")
    suspend fun deleteSubtaskById(id: Long)

    @Query("DELETE FROM subtasks WHERE taskId = :taskId")
    suspend fun deleteSubtasksForTask(taskId: Long)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY isPinned DESC, updatedAtMillis DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes")
    suspend fun getAllSync(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface HabitDao {
    @Transaction
    @Query("SELECT * FROM habits ORDER BY isArchived ASC, createdAtMillis ASC")
    fun observeAllWithLogs(): Flow<List<HabitWithLogs>>

    @Query("SELECT * FROM habits")
    suspend fun getAllSync(): List<HabitEntity>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId")
    suspend fun getLogsForHabitSync(habitId: Long): List<HabitLogEntity>

    @Query("SELECT * FROM habits WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): HabitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: HabitEntity): Long

    @Update
    suspend fun updateHabit(habit: HabitEntity)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteHabitById(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLog(log: HabitLogEntity)

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId AND dateEpochDay = :epochDay")
    suspend fun deleteLog(habitId: Long, epochDay: Long)
}

@Dao
interface RoutineDao {
    @Transaction
    @Query("SELECT * FROM routines ORDER BY startHour ASC, startMinute ASC")
    fun observeAllWithSteps(): Flow<List<RoutineWithSteps>>

    @Query("SELECT * FROM routines")
    suspend fun getAllSync(): List<RoutineEntity>

    @Query("SELECT * FROM routines WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): RoutineEntity?

    @Query("SELECT * FROM routines WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): RoutineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: RoutineEntity): Long

    @Update
    suspend fun updateRoutine(routine: RoutineEntity)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteRoutineById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStep(step: RoutineStepEntity): Long

    @Update
    suspend fun updateStep(step: RoutineStepEntity)

    @Query("DELETE FROM routine_steps WHERE routineId = :routineId")
    suspend fun deleteStepsForRoutine(routineId: Long)

    @Query("DELETE FROM routine_steps WHERE id = :stepId")
    suspend fun deleteStepById(stepId: Long)

    @Query("SELECT * FROM routine_steps WHERE routineId = :routineId ORDER BY sortOrder ASC")
    suspend fun getStepsSync(routineId: Long): List<RoutineStepEntity>
}

@Dao
interface FocusDao {
    @Query("SELECT * FROM focus_sessions ORDER BY completedAtMillis DESC LIMIT 100")
    fun observeSessions(): Flow<List<FocusSessionEntity>>

    @Insert
    suspend fun insertSession(session: FocusSessionEntity)
}

@Dao
interface TimerDao {
    @Query("SELECT * FROM timers ORDER BY createdAtMillis DESC")
    fun observeTimers(): Flow<List<TimerEntity>>

    @Query("SELECT * FROM timers")
    suspend fun getAllSync(): List<TimerEntity>

    @Query("SELECT * FROM timers WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TimerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimer(timer: TimerEntity): Long

    @Update
    suspend fun updateTimer(timer: TimerEntity)

    @Query("DELETE FROM timers WHERE id = :id")
    suspend fun deleteTimerById(id: Long)

    @Query("SELECT * FROM timer_presets ORDER BY sortOrder ASC")
    fun observePresets(): Flow<List<TimerPresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: TimerPresetEntity): Long

    @Query("DELETE FROM timer_presets WHERE id = :id")
    suspend fun deletePresetById(id: Long)

    @Query("SELECT * FROM timer_history ORDER BY completedAtMillis DESC LIMIT 50")
    fun observeHistory(): Flow<List<TimerHistoryEntity>>

    @Insert
    suspend fun insertHistory(entry: TimerHistoryEntity)

    @Query("DELETE FROM timer_history")
    suspend fun clearHistory()
}

@Dao
interface StopwatchDao {
    @Query("SELECT * FROM stopwatch_sessions ORDER BY recordedAtMillis DESC LIMIT 50")
    fun observeSessions(): Flow<List<StopwatchSessionEntity>>

    @Insert
    suspend fun insertSession(session: StopwatchSessionEntity)

    @Query("DELETE FROM stopwatch_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("DELETE FROM stopwatch_sessions")
    suspend fun clearAll()
}

@Dao
interface WorldClockDao {
    @Query("SELECT * FROM world_clock_cities ORDER BY sortOrder ASC, id ASC")
    fun observeCities(): Flow<List<WorldClockCityEntity>>

    @Query("SELECT * FROM world_clock_cities")
    suspend fun getAllSync(): List<WorldClockCityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCity(city: WorldClockCityEntity): Long

    @Query("DELETE FROM world_clock_cities WHERE id = :id")
    suspend fun deleteCityById(id: Long)
}

@Dao
interface WeatherCacheDao {
    @Query("SELECT * FROM weather_cache WHERE id = 1 LIMIT 1")
    fun observeCache(): Flow<WeatherCacheEntity?>

    @Query("SELECT * FROM weather_cache WHERE id = 1 LIMIT 1")
    suspend fun getCacheSync(): WeatherCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCache(cache: WeatherCacheEntity)
}

@Dao
interface CalendarEventDao {
    @Query("SELECT * FROM calendar_events ORDER BY startMillis ASC")
    fun observeAll(): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events ORDER BY startMillis ASC")
    suspend fun getAllSync(): List<CalendarEventEntity>

    @Query("SELECT * FROM calendar_events WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): CalendarEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: CalendarEventEntity): Long

    @Update
    suspend fun update(event: CalendarEventEntity)

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface NotificationLogDao {
    @Query("SELECT * FROM notification_logs ORDER BY postedAtMillis DESC LIMIT 150")
    fun observeLogs(): Flow<List<NotificationLogEntity>>

    @Query("SELECT COUNT(*) FROM notification_logs WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    @Insert
    suspend fun insertLog(log: NotificationLogEntity): Long

    @Query("UPDATE notification_logs SET isRead = 1")
    suspend fun markAllRead()

    @Query("DELETE FROM notification_logs")
    suspend fun clearAll()
}
