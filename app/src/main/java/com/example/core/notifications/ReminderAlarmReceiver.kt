package com.example.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.RRReminderApp
import com.example.core.database.AlarmHistoryEntity
import com.example.core.database.RRDatabase
import com.example.core.database.ReminderPriority
import com.example.core.datastore.UserPreferencesRepository
import com.example.data.AlarmRepository
import com.example.data.TimerRepository
import com.example.features.alarms.AlarmRingActivity
import com.example.widgets.WidgetUpdateHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZoneId

class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val container = (appContext as? RRReminderApp)?.container
                val db = container?.database ?: RRDatabase.getInstance(appContext)
                val prefsRepo = container?.preferencesRepository ?: UserPreferencesRepository(appContext)
                val notifManager = container?.notificationManager
                    ?: RRNotificationManager(appContext, db.notificationLogDao())
                val scheduler = container?.scheduler ?: ReminderAlarmScheduler(appContext)
                val prefs = runCatching { prefsRepo.getSnapshot() }.getOrNull()
                val activeZone = prefs?.primaryClockZone?.toZoneId() ?: ZoneId.systemDefault()
                val appNow = container?.networkTimeProvider?.currentEpochMillis() ?: System.currentTimeMillis()

                when (action) {
                    ACTION_FIRE_REMINDER -> if (id > 0) {
                        val reminder = db.reminderDao().getById(id)
                        if (reminder != null && !reminder.isCompleted) {
                            val priority = ReminderPriority.fromName(reminder.priority)
                            notifManager.postReminderNotification(
                                reminderId = reminder.id,
                                title = reminder.title,
                                body = reminder.description.ifBlank { reminder.notes },
                                priorityLabel = priority.label,
                                isLeadAlert = false,
                                soundUriString = prefs?.notificationSoundUri,
                                vibrate = prefs?.vibrationEnabled ?: true,
                            )
                            val nextTrigger = ScheduleCalculator.computeNextReminderTrigger(
                                reminder = reminder,
                                afterMillis = appNow,
                                targetZone = activeZone,
                            )
                            if (nextTrigger != null) {
                                val updated = reminder.copy(
                                    triggerAtMillis = nextTrigger,
                                    snoozedUntilMillis = null,
                                    updatedAtMillis = System.currentTimeMillis(),
                                )
                                db.reminderDao().update(updated)
                                scheduler.scheduleReminder(
                                    reminder = updated,
                                    leadMinutes = prefs?.defaultReminderLeadMinutes ?: 0,
                                    zoneId = activeZone,
                                )
                            } else if (reminder.snoozedUntilMillis != null) {
                                db.reminderDao().update(reminder.copy(snoozedUntilMillis = null))
                            }
                        }
                    }

                    ACTION_FIRE_REMINDER_LEAD -> if (id > 0) {
                        val leadMins = intent.getIntExtra(EXTRA_LEAD_MINUTES, 5)
                        val reminder = db.reminderDao().getById(id)
                        if (reminder != null && !reminder.isCompleted) {
                            val priority = ReminderPriority.fromName(reminder.priority)
                            notifManager.postReminderNotification(
                                reminderId = reminder.id,
                                title = reminder.title,
                                body = reminder.description.ifBlank { reminder.notes },
                                priorityLabel = priority.label,
                                isLeadAlert = true,
                                leadMinutes = leadMins,
                                soundUriString = prefs?.notificationSoundUri,
                                vibrate = prefs?.vibrationEnabled ?: true,
                            )
                        }
                    }

                    ACTION_COMPLETE_REMINDER -> if (id > 0) {
                        val reminder = db.reminderDao().getById(id)
                        if (reminder != null) {
                            db.reminderDao().update(
                                reminder.copy(
                                    isCompleted = true,
                                    completedAtMillis = System.currentTimeMillis(),
                                    snoozedUntilMillis = null,
                                ),
                            )
                            scheduler.cancelReminder(id)
                        }
                        notifManager.cancelNotification((1000 + id).toInt())
                        notifManager.cancelNotification((15_000 + id).toInt())
                    }

                    ACTION_SNOOZE_REMINDER -> if (id > 0) {
                        val reminder = db.reminderDao().getById(id)
                        if (reminder != null && !reminder.isCompleted) {
                            val snoozeUntil = appNow + 10 * 60_000L
                            val updated = reminder.copy(snoozedUntilMillis = snoozeUntil)
                            db.reminderDao().update(updated)
                            scheduler.scheduleReminder(
                                reminder = updated,
                                leadMinutes = prefs?.defaultReminderLeadMinutes ?: 0,
                                zoneId = activeZone,
                            )
                        }
                        notifManager.cancelNotification((1000 + id).toInt())
                        notifManager.cancelNotification((15_000 + id).toInt())
                    }

                    ACTION_FIRE_ALARM -> if (id > 0) {
                        val alarmRepo = container?.alarmRepository
                            ?: AlarmRepository(appContext, db.alarmDao(), prefsRepo, scheduler)
                        val alarm = alarmRepo.consumeDueAlarm(
                            alarmId = id,
                            nowMillis = appNow,
                            fromReceiver = true,
                        )
                        if (alarm != null) {
                            container?.setActiveAlert(
                                id = alarm.id,
                                label = alarm.label,
                                isTimer = false,
                                snoozeMinutes = alarm.snoozeMinutes,
                            )
                            val serviceIntent = Intent(appContext, AlarmForegroundService::class.java).apply {
                                this.action = AlarmForegroundService.ACTION_START
                                putExtra(AlarmForegroundService.EXTRA_ID, alarm.id)
                                putExtra(AlarmForegroundService.EXTRA_LABEL, alarm.label)
                                putExtra(AlarmForegroundService.EXTRA_IS_TIMER, false)
                                putExtra(AlarmForegroundService.EXTRA_VIBRATE, alarm.vibrate)
                                putExtra(AlarmForegroundService.EXTRA_GRADUAL_VOLUME, alarm.gradualVolume)
                                putExtra(AlarmForegroundService.EXTRA_RINGTONE_URI, alarm.ringtoneUri)
                                putExtra(AlarmForegroundService.EXTRA_SNOOZE_MINUTES, alarm.snoozeMinutes)
                            }
                            runCatching {
                                ContextCompat.startForegroundService(appContext, serviceIntent)
                            }.onFailure {
                                notifManager.postSimpleNotification(
                                    id = AlarmForegroundService.FOREGROUND_NOTIFICATION_ID,
                                    channelId = RRChannels.ALARMS,
                                    title = "Alarm: ${alarm.label}",
                                    body = "Tap to open RR Reminder",
                                    sourceType = "ALARM",
                                    sourceId = alarm.id,
                                )
                            }
                            runCatching {
                                appContext.startActivity(
                                    Intent(appContext, AlarmRingActivity::class.java).apply {
                                        addFlags(
                                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                                Intent.FLAG_ACTIVITY_SINGLE_TOP,
                                        )
                                        putExtra(AlarmRingActivity.EXTRA_ID, alarm.id)
                                        putExtra(AlarmRingActivity.EXTRA_LABEL, alarm.label)
                                        putExtra(AlarmRingActivity.EXTRA_IS_TIMER, false)
                                        putExtra(AlarmRingActivity.EXTRA_SNOOZE_MINUTES, alarm.snoozeMinutes)
                                    },
                                )
                            }
                        }
                    }

                    ACTION_DISMISS_ALARM -> {
                        AlarmForegroundService.stop(appContext)
                        notifManager.cancelNotification(AlarmForegroundService.FOREGROUND_NOTIFICATION_ID)
                        container?.clearActiveAlert()
                        if (id > 0) {
                            val alarm = db.alarmDao().getById(id)
                            if (alarm != null) {
                                db.alarmDao().insertHistory(
                                    AlarmHistoryEntity(
                                        alarmId = alarm.id,
                                        label = alarm.label,
                                        triggeredAtMillis = System.currentTimeMillis(),
                                        action = "DISMISSED",
                                    ),
                                )
                            }
                        }
                    }

                    ACTION_SNOOZE_ALARM -> {
                        AlarmForegroundService.stop(appContext)
                        notifManager.cancelNotification(AlarmForegroundService.FOREGROUND_NOTIFICATION_ID)
                        container?.clearActiveAlert()
                        if (id > 0) {
                            val snoozeMin = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 10)
                            val alarmRepo = container?.alarmRepository
                                ?: AlarmRepository(appContext, db.alarmDao(), prefsRepo, scheduler)
                            alarmRepo.snoozeAlarm(id, snoozeMin)
                        }
                    }

                    ACTION_FIRE_TIMER -> if (id > 0) {
                        val timerRepo = container?.timerRepository
                            ?: TimerRepository(appContext, db.timerDao(), scheduler)
                        val finalized = timerRepo.finalizeExpiredTimer(
                            timerId = id,
                            nowMillis = System.currentTimeMillis(),
                            fromReceiver = true,
                        )
                        if (finalized != null) {
                            container?.setActiveAlert(
                                id = finalized.id,
                                label = finalized.label,
                                isTimer = true,
                                snoozeMinutes = 10,
                            )
                            val serviceIntent = Intent(appContext, AlarmForegroundService::class.java).apply {
                                this.action = AlarmForegroundService.ACTION_START
                                putExtra(AlarmForegroundService.EXTRA_ID, finalized.id)
                                putExtra(AlarmForegroundService.EXTRA_LABEL, finalized.label)
                                putExtra(AlarmForegroundService.EXTRA_IS_TIMER, true)
                                putExtra(AlarmForegroundService.EXTRA_VIBRATE, prefs?.vibrationEnabled ?: true)
                                putExtra(AlarmForegroundService.EXTRA_GRADUAL_VOLUME, false)
                            }
                            runCatching {
                                ContextCompat.startForegroundService(appContext, serviceIntent)
                            }.onFailure {
                                notifManager.postSimpleNotification(
                                    id = AlarmForegroundService.FOREGROUND_NOTIFICATION_ID,
                                    channelId = RRChannels.TIMERS,
                                    title = "Timer Finished: ${finalized.label}",
                                    body = "Countdown completed",
                                    sourceType = "TIMER",
                                    sourceId = finalized.id,
                                )
                            }
                            runCatching {
                                appContext.startActivity(
                                    Intent(appContext, AlarmRingActivity::class.java).apply {
                                        addFlags(
                                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                                Intent.FLAG_ACTIVITY_SINGLE_TOP,
                                        )
                                        putExtra(AlarmRingActivity.EXTRA_ID, finalized.id)
                                        putExtra(AlarmRingActivity.EXTRA_LABEL, finalized.label)
                                        putExtra(AlarmRingActivity.EXTRA_IS_TIMER, true)
                                        putExtra(AlarmRingActivity.EXTRA_SNOOZE_MINUTES, 10)
                                    },
                                )
                            }
                        }
                    }

                    ACTION_DISMISS_TIMER -> {
                        AlarmForegroundService.stop(appContext)
                        notifManager.cancelNotification(AlarmForegroundService.FOREGROUND_NOTIFICATION_ID)
                        container?.clearActiveAlert()
                    }

                    ACTION_FIRE_ROUTINE -> if (id > 0) {
                        val routine = db.routineDao().getById(id)
                        if (routine != null && routine.reminderEnabled) {
                            notifManager.postSimpleNotification(
                                id = (70_000 + id).toInt(),
                                channelId = RRChannels.ROUTINES,
                                title = "Routine Ready: ${routine.name}",
                                body = "Tap to start your scheduled ${routine.routineType.lowercase()} routine.",
                                sourceType = "ROUTINE",
                                sourceId = routine.id,
                            )
                            scheduler.scheduleRoutine(routine, zoneId = activeZone)
                        }
                    }
                }

                WidgetUpdateHelper.updateAllWidgets(appContext)
            } catch (t: Throwable) {
                Log.e("ReminderAlarmReceiver", "Failed handling action=$action", t)
            } finally {
                runCatching { pendingResult.finish() }
            }
        }
    }

    companion object {
        const val EXTRA_ID = "extra_id"
        const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"
        const val EXTRA_LEAD_MINUTES = "extra_lead_minutes"
        const val ACTION_FIRE_REMINDER = "com.example.action.FIRE_REMINDER"
        const val ACTION_FIRE_REMINDER_LEAD = "com.example.action.FIRE_REMINDER_LEAD"
        const val ACTION_COMPLETE_REMINDER = "com.example.action.COMPLETE_REMINDER"
        const val ACTION_SNOOZE_REMINDER = "com.example.action.SNOOZE_REMINDER"
        const val ACTION_FIRE_ALARM = "com.example.action.FIRE_ALARM"
        const val ACTION_DISMISS_ALARM = "com.example.action.DISMISS_ALARM"
        const val ACTION_SNOOZE_ALARM = "com.example.action.SNOOZE_ALARM"
        const val ACTION_FIRE_TIMER = "com.example.action.FIRE_TIMER"
        const val ACTION_DISMISS_TIMER = "com.example.action.DISMISS_TIMER"
        const val ACTION_FIRE_ROUTINE = "com.example.action.FIRE_ROUTINE"
    }
}
