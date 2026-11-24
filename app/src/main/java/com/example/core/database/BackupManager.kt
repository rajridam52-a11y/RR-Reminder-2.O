package com.example.core.database

import androidx.room.withTransaction
import com.example.core.network.NetworkModule
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BackupPayload(
    val app: String = "RR Reminder",
    val schemaVersion: Int = 2,
    val exportedAtMillis: Long = System.currentTimeMillis(),
    val reminders: List<BackupReminder> = emptyList(),
    val alarms: List<BackupAlarm> = emptyList(),
    val tasks: List<BackupTask> = emptyList(),
    val notes: List<BackupNote> = emptyList(),
    val habits: List<BackupHabit> = emptyList(),
    val routines: List<BackupRoutine> = emptyList(),
    val timerPresets: List<BackupTimerPreset> = emptyList(),
    val worldClockCities: List<BackupWorldCity> = emptyList(),
    val calendarEvents: List<BackupCalendarEvent> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class BackupReminder(
    val uuid: String,
    val title: String,
    val description: String,
    val triggerAtMillis: Long,
    val zoneId: String,
    val repeatType: String,
    val repeatIntervalValue: Int,
    val repeatWeekdaysMask: Int,
    val priority: String,
    val category: String,
    val tagsCsv: String,
    val notes: String,
    val isCompleted: Boolean,
    val completedAtMillis: Long?,
    val locationName: String?,
    val locationLat: Double?,
    val locationLon: Double?,
    val locationTriggerType: String,
    val createdAtMillis: Long,
)

@JsonClass(generateAdapter = true)
data class BackupAlarm(
    val uuid: String,
    val label: String,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean,
    val repeatDaysMask: Int,
    val ringtoneTitle: String,
    val vibrate: Boolean,
    val snoozeMinutes: Int,
    val gradualVolume: Boolean,
)

@JsonClass(generateAdapter = true)
data class BackupSubtask(val title: String, val isDone: Boolean, val sortOrder: Int)

@JsonClass(generateAdapter = true)
data class BackupTask(
    val uuid: String,
    val title: String,
    val description: String,
    val priority: String,
    val dueDateMillis: Long?,
    val category: String,
    val isCompleted: Boolean,
    val completedAtMillis: Long?,
    val subtasks: List<BackupSubtask> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class BackupNote(
    val uuid: String,
    val title: String,
    val body: String,
    val isChecklist: Boolean,
    val checklistJson: String,
    val category: String,
    val isPinned: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@JsonClass(generateAdapter = true)
data class BackupHabit(
    val uuid: String,
    val name: String,
    val description: String,
    val frequencyType: String,
    val targetDaysMask: Int,
    val weeklyTargetCount: Int,
    val isPaused: Boolean,
    val isArchived: Boolean,
    val completedEpochDays: List<Long> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class BackupRoutineStep(val title: String, val durationMinutes: Int, val sortOrder: Int)

@JsonClass(generateAdapter = true)
data class BackupRoutine(
    val uuid: String,
    val name: String,
    val routineType: String,
    val startHour: Int,
    val startMinute: Int,
    val reminderEnabled: Boolean,
    val steps: List<BackupRoutineStep> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class BackupTimerPreset(val label: String, val durationMillis: Long, val sortOrder: Int)

@JsonClass(generateAdapter = true)
data class BackupWorldCity(val cityName: String, val countryName: String, val zoneId: String, val sortOrder: Int)

@JsonClass(generateAdapter = true)
data class BackupCalendarEvent(
    val uuid: String,
    val title: String,
    val description: String,
    val startMillis: Long,
    val endMillis: Long,
    val location: String,
    val category: String,
    val isAllDay: Boolean,
    val createdAtMillis: Long,
)

data class BackupImportResult(
    val importedCount: Int,
    val skippedDuplicatesCount: Int,
    val summaryMessage: String,
)

class BackupManager(private val db: RRDatabase) {
    private val adapter = NetworkModule.moshi.adapter(BackupPayload::class.java).indent("  ")

    suspend fun exportToJson(): String {
        val reminders = db.reminderDao().getAllSync().map {
            BackupReminder(
                uuid = it.uuid,
                title = it.title,
                description = it.description,
                triggerAtMillis = it.triggerAtMillis,
                zoneId = it.zoneId,
                repeatType = it.repeatType,
                repeatIntervalValue = it.repeatIntervalValue,
                repeatWeekdaysMask = it.repeatWeekdaysMask,
                priority = it.priority,
                category = it.category,
                tagsCsv = it.tagsCsv,
                notes = it.notes,
                isCompleted = it.isCompleted,
                completedAtMillis = it.completedAtMillis,
                locationName = it.locationName,
                locationLat = it.locationLat,
                locationLon = it.locationLon,
                locationTriggerType = it.locationTriggerType,
                createdAtMillis = it.createdAtMillis,
            )
        }
        val alarms = db.alarmDao().getAllSync().map {
            BackupAlarm(
                uuid = it.uuid,
                label = it.label,
                hour = it.hour,
                minute = it.minute,
                isEnabled = it.isEnabled,
                repeatDaysMask = it.repeatDaysMask,
                ringtoneTitle = it.ringtoneTitle,
                vibrate = it.vibrate,
                snoozeMinutes = it.snoozeMinutes,
                gradualVolume = it.gradualVolume,
            )
        }
        val tasks = db.taskDao().getAllSync().map { task ->
            val subs = db.taskDao().getSubtasksSync(task.id).map {
                BackupSubtask(it.title, it.isDone, it.sortOrder)
            }
            BackupTask(
                uuid = task.uuid,
                title = task.title,
                description = task.description,
                priority = task.priority,
                dueDateMillis = task.dueDateMillis,
                category = task.category,
                isCompleted = task.isCompleted,
                completedAtMillis = task.completedAtMillis,
                subtasks = subs,
            )
        }
        val notes = db.noteDao().getAllSync().map {
            BackupNote(
                uuid = it.uuid,
                title = it.title,
                body = it.body,
                isChecklist = it.isChecklist,
                checklistJson = it.checklistJson,
                category = it.category,
                isPinned = it.isPinned,
                createdAtMillis = it.createdAtMillis,
                updatedAtMillis = it.updatedAtMillis,
            )
        }
        val habitsWithLogs = db.habitDao().getAllSync().map { habit ->
            val logs = db.habitDao().getLogsForHabitSync(habit.id).map { it.dateEpochDay }
            BackupHabit(
                uuid = habit.uuid,
                name = habit.name,
                description = habit.description,
                frequencyType = habit.frequencyType,
                targetDaysMask = habit.targetDaysMask,
                weeklyTargetCount = habit.weeklyTargetCount,
                isPaused = habit.isPaused,
                isArchived = habit.isArchived,
                completedEpochDays = logs,
            )
        }
        val routines = db.routineDao().getAllSync().map { routine ->
            val steps = db.routineDao().getStepsSync(routine.id).map {
                BackupRoutineStep(it.title, it.durationMinutes, it.sortOrder)
            }
            BackupRoutine(
                uuid = routine.uuid,
                name = routine.name,
                routineType = routine.routineType,
                startHour = routine.startHour,
                startMinute = routine.startMinute,
                reminderEnabled = routine.reminderEnabled,
                steps = steps,
            )
        }
        val cities = db.worldClockDao().getAllSync().map {
            BackupWorldCity(it.cityName, it.countryName, it.zoneId, it.sortOrder)
        }
        val calendarEvents = db.calendarEventDao().getAllSync().map {
            BackupCalendarEvent(
                uuid = it.uuid,
                title = it.title,
                description = it.description,
                startMillis = it.startMillis,
                endMillis = it.endMillis,
                location = it.location,
                category = it.category,
                isAllDay = it.isAllDay,
                createdAtMillis = it.createdAtMillis,
            )
        }

        val payload = BackupPayload(
            schemaVersion = 2,
            reminders = reminders,
            alarms = alarms,
            tasks = tasks,
            notes = notes,
            habits = habitsWithLogs,
            routines = routines,
            worldClockCities = cities,
            calendarEvents = calendarEvents,
        )
        return adapter.toJson(payload)
    }

    suspend fun importFromJson(rawJson: String): Result<BackupImportResult> {
        return runCatching {
            val payload = adapter.fromJson(rawJson)
                ?: throw IllegalArgumentException("Backup file is empty or unreadable.")
            if (payload.app != "RR Reminder" || payload.schemaVersion !in 1..2) {
                throw IllegalArgumentException("Invalid RR Reminder backup format or unsupported schema version.")
            }

            var imported = 0
            var skipped = 0

            db.withTransaction {
                val reminderDao = db.reminderDao()
                payload.reminders.forEach { r ->
                    if (r.title.isBlank()) return@forEach
                    if (reminderDao.getByUuid(r.uuid) != null) {
                        skipped++
                    } else {
                        reminderDao.insert(
                            ReminderEntity(
                                uuid = r.uuid,
                                title = r.title.trim(),
                                description = r.description,
                                triggerAtMillis = r.triggerAtMillis,
                                zoneId = r.zoneId,
                                repeatType = r.repeatType,
                                repeatIntervalValue = r.repeatIntervalValue.coerceAtLeast(1),
                                repeatWeekdaysMask = r.repeatWeekdaysMask,
                                priority = r.priority,
                                category = r.category,
                                tagsCsv = r.tagsCsv,
                                notes = r.notes,
                                isCompleted = r.isCompleted,
                                completedAtMillis = r.completedAtMillis,
                                locationName = r.locationName,
                                locationLat = r.locationLat,
                                locationLon = r.locationLon,
                                locationTriggerType = r.locationTriggerType,
                                createdAtMillis = r.createdAtMillis,
                            ),
                        )
                        imported++
                    }
                }

                val alarmDao = db.alarmDao()
                payload.alarms.forEach { a ->
                    if (a.hour !in 0..23 || a.minute !in 0..59) return@forEach
                    if (alarmDao.getByUuid(a.uuid) != null) {
                        skipped++
                    } else {
                        alarmDao.insert(
                            AlarmEntity(
                                uuid = a.uuid,
                                label = a.label.ifBlank { "Alarm" },
                                hour = a.hour,
                                minute = a.minute,
                                isEnabled = a.isEnabled,
                                repeatDaysMask = a.repeatDaysMask,
                                ringtoneTitle = a.ringtoneTitle,
                                vibrate = a.vibrate,
                                snoozeMinutes = a.snoozeMinutes.coerceIn(1, 60),
                                gradualVolume = a.gradualVolume,
                            ),
                        )
                        imported++
                    }
                }

                val taskDao = db.taskDao()
                payload.tasks.forEach { t ->
                    if (t.title.isBlank()) return@forEach
                    if (taskDao.getByUuid(t.uuid) != null) {
                        skipped++
                    } else {
                        val taskId = taskDao.insertTask(
                            TaskEntity(
                                uuid = t.uuid,
                                title = t.title.trim(),
                                description = t.description,
                                priority = t.priority,
                                dueDateMillis = t.dueDateMillis,
                                category = t.category,
                                isCompleted = t.isCompleted,
                                completedAtMillis = t.completedAtMillis,
                            ),
                        )
                        t.subtasks.forEach { s ->
                            taskDao.insertSubtask(
                                SubtaskEntity(
                                    taskId = taskId,
                                    title = s.title,
                                    isDone = s.isDone,
                                    sortOrder = s.sortOrder,
                                ),
                            )
                        }
                        imported++
                    }
                }

                val noteDao = db.noteDao()
                payload.notes.forEach { n ->
                    if (n.title.isBlank() && n.body.isBlank()) return@forEach
                    if (noteDao.getByUuid(n.uuid) != null) {
                        skipped++
                    } else {
                        noteDao.insert(
                            NoteEntity(
                                uuid = n.uuid,
                                title = n.title,
                                body = n.body,
                                isChecklist = n.isChecklist,
                                checklistJson = n.checklistJson,
                                category = n.category,
                                isPinned = n.isPinned,
                                createdAtMillis = n.createdAtMillis,
                                updatedAtMillis = n.updatedAtMillis,
                            ),
                        )
                        imported++
                    }
                }

                val habitDao = db.habitDao()
                payload.habits.forEach { h ->
                    if (h.name.isBlank()) return@forEach
                    if (habitDao.getByUuid(h.uuid) != null) {
                        skipped++
                    } else {
                        val habitId = habitDao.insertHabit(
                            HabitEntity(
                                uuid = h.uuid,
                                name = h.name.trim(),
                                description = h.description,
                                frequencyType = h.frequencyType,
                                targetDaysMask = h.targetDaysMask,
                                weeklyTargetCount = h.weeklyTargetCount,
                                isPaused = h.isPaused,
                                isArchived = h.isArchived,
                            ),
                        )
                        h.completedEpochDays.forEach { day ->
                            habitDao.insertLog(HabitLogEntity(habitId = habitId, dateEpochDay = day))
                        }
                        imported++
                    }
                }

                val routineDao = db.routineDao()
                payload.routines.forEach { rt ->
                    if (rt.name.isBlank()) return@forEach
                    if (routineDao.getByUuid(rt.uuid) != null) {
                        skipped++
                    } else {
                        val routineId = routineDao.insertRoutine(
                            RoutineEntity(
                                uuid = rt.uuid,
                                name = rt.name.trim(),
                                routineType = rt.routineType,
                                startHour = rt.startHour.coerceIn(0, 23),
                                startMinute = rt.startMinute.coerceIn(0, 59),
                                reminderEnabled = rt.reminderEnabled,
                            ),
                        )
                        rt.steps.forEach { st ->
                            routineDao.insertStep(
                                RoutineStepEntity(
                                    routineId = routineId,
                                    title = st.title,
                                    durationMinutes = st.durationMinutes,
                                    sortOrder = st.sortOrder,
                                ),
                            )
                        }
                        imported++
                    }
                }

                val existingZones = db.worldClockDao().getAllSync().map { it.zoneId }.toSet()
                payload.worldClockCities.forEach { c ->
                    if (c.zoneId !in existingZones) {
                        db.worldClockDao().insertCity(
                            WorldClockCityEntity(
                                cityName = c.cityName,
                                countryName = c.countryName,
                                zoneId = c.zoneId,
                                sortOrder = c.sortOrder,
                            ),
                        )
                        imported++
                    } else {
                        skipped++
                    }
                }

                val eventDao = db.calendarEventDao()
                payload.calendarEvents.forEach { ev ->
                    if (ev.title.isBlank()) return@forEach
                    if (eventDao.getByUuid(ev.uuid) != null) {
                        skipped++
                    } else {
                        eventDao.insert(
                            CalendarEventEntity(
                                uuid = ev.uuid,
                                title = ev.title.trim(),
                                description = ev.description,
                                startMillis = ev.startMillis,
                                endMillis = ev.endMillis,
                                location = ev.location,
                                category = ev.category,
                                isAllDay = ev.isAllDay,
                                createdAtMillis = ev.createdAtMillis,
                            ),
                        )
                        imported++
                    }
                }
            }

            BackupImportResult(
                importedCount = imported,
                skippedDuplicatesCount = skipped,
                summaryMessage = "Restored $imported items ($skipped duplicates skipped).",
            )
        }
    }
}
