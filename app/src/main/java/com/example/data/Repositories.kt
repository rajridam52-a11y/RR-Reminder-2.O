package com.example.data

import android.content.Context
import com.example.core.database.*
import com.example.core.datastore.UserPreferencesRepository
import com.example.core.network.*
import com.example.core.notifications.ReminderAlarmScheduler
import com.example.core.notifications.ScheduleCalculator
import com.example.widgets.WidgetUpdateHelper
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.Flow

class ReminderRepository(
    private val context: Context,
    private val dao: ReminderDao,
    private val scheduler: ReminderAlarmScheduler,
) {
    val reminders: Flow<List<ReminderEntity>> = dao.observeAll()

    suspend fun saveReminder(reminder: ReminderEntity): Long {
        val id = if (reminder.id == 0L) {
            dao.insert(reminder.copy(updatedAtMillis = System.currentTimeMillis()))
        } else {
            dao.update(reminder.copy(updatedAtMillis = System.currentTimeMillis()))
            reminder.id
        }
        val saved = reminder.copy(id = id)
        if (saved.isCompleted) {
            scheduler.cancelReminder(id)
        } else {
            scheduler.scheduleReminder(saved)
        }
        WidgetUpdateHelper.updateAllWidgets(context)
        return id
    }

    suspend fun toggleComplete(reminder: ReminderEntity) {
        val now = System.currentTimeMillis()
        val updated = if (!reminder.isCompleted) {
            reminder.copy(isCompleted = true, completedAtMillis = now, snoozedUntilMillis = null, updatedAtMillis = now)
        } else {
            reminder.copy(isCompleted = false, completedAtMillis = null, updatedAtMillis = now)
        }
        dao.update(updated)
        if (updated.isCompleted) scheduler.cancelReminder(updated.id) else scheduler.scheduleReminder(updated)
        WidgetUpdateHelper.updateAllWidgets(context)
    }

    suspend fun snoozeReminder(reminder: ReminderEntity, minutes: Int) {
        val until = System.currentTimeMillis() + minutes * 60_000L
        val updated = reminder.copy(snoozedUntilMillis = until, isCompleted = false, updatedAtMillis = System.currentTimeMillis())
        dao.update(updated)
        scheduler.scheduleReminder(updated)
        WidgetUpdateHelper.updateAllWidgets(context)
    }

    suspend fun deleteReminder(id: Long) {
        scheduler.cancelReminder(id)
        dao.deleteById(id)
        WidgetUpdateHelper.updateAllWidgets(context)
    }
}

class AlarmRepository(
    private val context: Context,
    private val dao: AlarmDao,
    private val scheduler: ReminderAlarmScheduler,
) {
    val alarms: Flow<List<AlarmEntity>> = dao.observeAll()
    val history: Flow<List<AlarmHistoryEntity>> = dao.observeHistory()

    suspend fun saveAlarm(alarm: AlarmEntity): Long {
        val id = if (alarm.id == 0L) {
            dao.insert(alarm)
        } else {
            dao.update(alarm)
            alarm.id
        }
        val withId = alarm.copy(id = id)
        val next = if (withId.isEnabled) scheduler.scheduleAlarm(withId) else {
            scheduler.cancelAlarm(id)
            null
        }
        dao.update(withId.copy(nextTriggerAtMillis = next))
        WidgetUpdateHelper.updateAllWidgets(context)
        return id
    }

    suspend fun toggleAlarm(alarm: AlarmEntity, enabled: Boolean) {
        val updated = alarm.copy(isEnabled = enabled)
        val next = if (enabled) scheduler.scheduleAlarm(updated) else {
            scheduler.cancelAlarm(alarm.id)
            null
        }
        dao.update(updated.copy(nextTriggerAtMillis = next))
        WidgetUpdateHelper.updateAllWidgets(context)
    }

    suspend fun deleteAlarm(id: Long) {
        scheduler.cancelAlarm(id)
        dao.deleteById(id)
        WidgetUpdateHelper.updateAllWidgets(context)
    }

    suspend fun clearHistory() = dao.clearHistory()
}

class WeatherRepository(
    private val context: Context,
    private val dao: WeatherCacheDao,
    private val prefsRepo: UserPreferencesRepository,
    private val weatherService: OpenMeteoWeatherService,
    private val geocodingService: OpenMeteoGeocodingService,
) {
    val weatherCache: Flow<WeatherCacheEntity?> = dao.observeCache()

    private val hourlyAdapter = NetworkModule.moshi.adapter<List<HourlyForecastItem>>(
        Types.newParameterizedType(List::class.java, HourlyForecastItem::class.java),
    )
    private val dailyAdapter = NetworkModule.moshi.adapter<List<DailyForecastItem>>(
        Types.newParameterizedType(List::class.java, DailyForecastItem::class.java),
    )

    suspend fun searchCities(query: String): Result<List<GeocodingResultDto>> {
        if (query.trim().length < 2) return Result.success(emptyList())
        return runCatching {
            geocodingService.searchCity(query.trim()).results ?: emptyList()
        }
    }

    suspend fun fetchAndCacheWeather(cityName: String, latitude: Double, longitude: Double): Result<WeatherCacheEntity> {
        return runCatching {
            val resp = weatherService.getForecast(latitude = latitude, longitude = longitude)
            val current = resp.current ?: throw IllegalStateException("Weather payload missing current conditions")

            val hourlyList = mutableListOf<HourlyForecastItem>()
            val hTimes = resp.hourly?.time.orEmpty()
            val hTemps = resp.hourly?.temperature2m.orEmpty()
            val hRain = resp.hourly?.precipitationProbability.orEmpty()
            val hCodes = resp.hourly?.weatherCode.orEmpty()
            val countH = minOf(24, hTimes.size, hTemps.size)
            for (i in 0 until countH) {
                hourlyList.add(
                    HourlyForecastItem(
                        timeIso = hTimes[i],
                        tempC = hTemps[i],
                        rainProbPercent = hRain.getOrElse(i) { 0 },
                        weatherCode = hCodes.getOrElse(i) { 0 },
                    ),
                )
            }

            val dailyList = mutableListOf<DailyForecastItem>()
            val dTimes = resp.daily?.time.orEmpty()
            val dMax = resp.daily?.temperatureMax.orEmpty()
            val dMin = resp.daily?.temperatureMin.orEmpty()
            val dRain = resp.daily?.precipitationProbabilityMax.orEmpty()
            val dCodes = resp.daily?.weatherCode.orEmpty()
            val countD = minOf(7, dTimes.size, dMax.size, dMin.size)
            for (i in 0 until countD) {
                dailyList.add(
                    DailyForecastItem(
                        dateIso = dTimes[i],
                        maxTempC = dMax[i],
                        minTempC = dMin[i],
                        rainProbPercent = dRain.getOrElse(i) { 0 },
                        weatherCode = dCodes.getOrElse(i) { 0 },
                    ),
                )
            }

            val entity = WeatherCacheEntity(
                id = 1,
                cityName = cityName,
                latitude = latitude,
                longitude = longitude,
                currentTempC = current.temperature2m,
                feelsLikeC = current.apparentTemperature,
                humidityPercent = current.relativeHumidity2m,
                windKmh = current.windSpeed10m,
                weatherCode = current.weatherCode,
                sunriseIso = resp.daily?.sunrise?.firstOrNull().orEmpty(),
                sunsetIso = resp.daily?.sunset?.firstOrNull().orEmpty(),
                hourlyJson = hourlyAdapter.toJson(hourlyList),
                dailyJson = dailyAdapter.toJson(dailyList),
                updatedAtMillis = System.currentTimeMillis(),
            )
            dao.upsertCache(entity)
            prefsRepo.setWeatherLocation(cityName, latitude, longitude)
            WidgetUpdateHelper.updateAllWidgets(context)
            entity
        }
    }

    fun parseHourly(json: String): List<HourlyForecastItem> =
        runCatching { hourlyAdapter.fromJson(json) ?: emptyList() }.getOrDefault(emptyList())

    fun parseDaily(json: String): List<DailyForecastItem> =
        runCatching { dailyAdapter.fromJson(json) ?: emptyList() }.getOrDefault(emptyList())
}

class TimerRepository(
    private val context: Context,
    private val dao: TimerDao,
    private val scheduler: ReminderAlarmScheduler,
) {
    val timers: Flow<List<TimerEntity>> = dao.observeTimers()
    val presets: Flow<List<TimerPresetEntity>> = dao.observePresets()
    val history: Flow<List<TimerHistoryEntity>> = dao.observeHistory()

    suspend fun createAndStartTimer(label: String, durationMillis: Long, repeat: Boolean): Long {
        val now = System.currentTimeMillis()
        val entity = TimerEntity(
            label = label.ifBlank { "Countdown" },
            durationMillis = durationMillis,
            remainingMillis = durationMillis,
            endEpochMillis = now + durationMillis,
            isRunning = true,
            isRepeat = repeat,
        )
        val id = dao.insertTimer(entity)
        scheduler.scheduleTimer(entity.copy(id = id))
        WidgetUpdateHelper.updateAllWidgets(context)
        return id
    }

    suspend fun pauseTimer(timer: TimerEntity) {
        if (!timer.isRunning) return
        val remaining = ((timer.endEpochMillis ?: System.currentTimeMillis()) - System.currentTimeMillis())
            .coerceAtLeast(0L)
        scheduler.cancelTimer(timer.id)
        dao.updateTimer(
            timer.copy(
                isRunning = false,
                remainingMillis = remaining,
                endEpochMillis = null,
            ),
        )
        WidgetUpdateHelper.updateAllWidgets(context)
    }

    suspend fun resumeTimer(timer: TimerEntity) {
        if (timer.isRunning) return
        val rem = if (timer.remainingMillis > 0L) timer.remainingMillis else timer.durationMillis
        val updated = timer.copy(
            isRunning = true,
            remainingMillis = rem,
            endEpochMillis = System.currentTimeMillis() + rem,
        )
        dao.updateTimer(updated)
        scheduler.scheduleTimer(updated)
        WidgetUpdateHelper.updateAllWidgets(context)
    }

    suspend fun resetTimer(timer: TimerEntity) {
        scheduler.cancelTimer(timer.id)
        dao.updateTimer(
            timer.copy(
                isRunning = false,
                remainingMillis = timer.durationMillis,
                endEpochMillis = null,
            ),
        )
        WidgetUpdateHelper.updateAllWidgets(context)
    }

    suspend fun deleteTimer(id: Long) {
        scheduler.cancelTimer(id)
        dao.deleteTimerById(id)
        WidgetUpdateHelper.updateAllWidgets(context)
    }

    suspend fun addPreset(label: String, durationMillis: Long) {
        dao.insertPreset(TimerPresetEntity(label = label, durationMillis = durationMillis))
    }

    suspend fun deletePreset(id: Long) = dao.deletePresetById(id)

    suspend fun clearHistory() = dao.clearHistory()
}

class TaskRepository(private val dao: TaskDao) {
    val tasksWithSubtasks: Flow<List<TaskWithSubtasks>> = dao.observeAllWithSubtasks()

    suspend fun saveTask(task: TaskEntity, subtaskTitles: List<String>) {
        val id = if (task.id == 0L) {
            dao.insertTask(task)
        } else {
            dao.updateTask(task)
            task.id
        }
        val existingSubs = dao.getSubtasksSync(id)
        if (subtaskTitles.isNotEmpty() || existingSubs.isEmpty()) {
            val existingByTitle = existingSubs.associateBy { it.title }
            dao.deleteSubtasksForTask(id)
            subtaskTitles.filter { it.isNotBlank() }.forEachIndexed { idx, title ->
                val wasDone = existingByTitle[title.trim()]?.isDone ?: false
                dao.insertSubtask(
                    SubtaskEntity(
                        taskId = id,
                        title = title.trim(),
                        isDone = wasDone,
                        sortOrder = idx,
                    ),
                )
            }
        }
    }

    suspend fun toggleTaskComplete(task: TaskEntity) {
        val now = System.currentTimeMillis()
        dao.updateTask(
            task.copy(
                isCompleted = !task.isCompleted,
                completedAtMillis = if (!task.isCompleted) now else null,
            ),
        )
    }

    suspend fun toggleSubtask(subtask: SubtaskEntity) {
        dao.updateSubtask(subtask.copy(isDone = !subtask.isDone))
    }

    suspend fun deleteTask(id: Long) = dao.deleteTaskById(id)
}

class NoteRepository(private val dao: NoteDao) {
    val notes: Flow<List<NoteEntity>> = dao.observeAll()

    suspend fun saveNote(note: NoteEntity): Long {
        val updated = note.copy(updatedAtMillis = System.currentTimeMillis())
        return if (updated.id == 0L) dao.insert(updated) else {
            dao.update(updated)
            updated.id
        }
    }

    suspend fun togglePin(note: NoteEntity) {
        dao.update(note.copy(isPinned = !note.isPinned, updatedAtMillis = System.currentTimeMillis()))
    }

    suspend fun deleteNote(id: Long) = dao.deleteById(id)
}

class HabitRepository(private val dao: HabitDao) {
    val habits: Flow<List<HabitWithLogs>> = dao.observeAllWithLogs()

    suspend fun saveHabit(habit: HabitEntity): Long =
        if (habit.id == 0L) dao.insertHabit(habit) else {
            dao.updateHabit(habit)
            habit.id
        }

    suspend fun toggleCheckIn(habitId: Long, epochDay: Long, currentlyDone: Boolean) {
        if (currentlyDone) {
            dao.deleteLog(habitId, epochDay)
        } else {
            dao.insertLog(HabitLogEntity(habitId = habitId, dateEpochDay = epochDay))
        }
    }

    suspend fun setPaused(habit: HabitEntity, paused: Boolean) =
        dao.updateHabit(habit.copy(isPaused = paused))

    suspend fun setArchived(habit: HabitEntity, archived: Boolean) =
        dao.updateHabit(habit.copy(isArchived = archived))

    suspend fun deleteHabit(id: Long) = dao.deleteHabitById(id)
}

class RoutineFocusRepository(
    private val routineDao: RoutineDao,
    private val focusDao: FocusDao,
    private val scheduler: ReminderAlarmScheduler,
) {
    val routines: Flow<List<RoutineWithSteps>> = routineDao.observeAllWithSteps()
    val focusSessions: Flow<List<FocusSessionEntity>> = focusDao.observeSessions()

    suspend fun saveRoutine(routine: RoutineEntity, stepTitlesAndDurations: List<Pair<String, Int>>): Long {
        val id = if (routine.id == 0L) {
            routineDao.insertRoutine(routine)
        } else {
            routineDao.updateRoutine(routine)
            routine.id
        }
        routineDao.deleteStepsForRoutine(id)
        stepTitlesAndDurations.filter { it.first.isNotBlank() }.forEachIndexed { idx, (title, mins) ->
            routineDao.insertStep(
                RoutineStepEntity(
                    routineId = id,
                    title = title.trim(),
                    durationMinutes = mins.coerceAtLeast(1),
                    sortOrder = idx,
                ),
            )
        }
        val saved = routine.copy(id = id)
        if (saved.reminderEnabled) scheduler.scheduleRoutine(saved) else scheduler.cancelRoutine(id)
        return id
    }

    suspend fun toggleStepDoneToday(step: RoutineStepEntity, todayEpochDay: Long) {
        val isDone = step.lastCompletedEpochDay == todayEpochDay
        routineDao.updateStep(
            step.copy(lastCompletedEpochDay = if (isDone) null else todayEpochDay),
        )
    }

    suspend fun deleteRoutine(id: Long) {
        scheduler.cancelRoutine(id)
        routineDao.deleteRoutineById(id)
    }

    suspend fun recordFocusSession(session: FocusSessionEntity) = focusDao.insertSession(session)
}
