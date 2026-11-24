package com.example.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.MainActivity
import com.example.core.database.AlarmEntity
import com.example.core.database.RRDatabase
import com.example.core.database.ReminderEntity
import com.example.core.database.RoutineEntity
import com.example.core.database.TimerEntity
import com.example.core.datastore.UserPreferencesRepository
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderAlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun scheduleReminder(reminder: ReminderEntity, leadMinutes: Int = 0) {
        cancelReminder(reminder.id)
        if (reminder.isCompleted) return

        val now = System.currentTimeMillis()
        val rawTrigger = reminder.snoozedUntilMillis ?: reminder.triggerAtMillis
        val effectiveTrigger = if (rawTrigger <= now - 60_000L) {
            ScheduleCalculator.computeNextReminderTrigger(reminder, afterMillis = now) ?: return
        } else {
            rawTrigger
        }

        scheduleExactOrFallback(
            requestCode = REMINDER_CODE_BASE + reminder.id.toInt(),
            triggerAtMillis = effectiveTrigger,
            intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ReminderAlarmReceiver.ACTION_FIRE_REMINDER
                putExtra(ReminderAlarmReceiver.EXTRA_ID, reminder.id)
            },
        )

        if (leadMinutes > 0 && reminder.snoozedUntilMillis == null) {
            val leadAt = effectiveTrigger - leadMinutes * 60_000L
            if (leadAt > now + 5_000L) {
                scheduleExactOrFallback(
                    requestCode = REMINDER_LEAD_CODE_BASE + reminder.id.toInt(),
                    triggerAtMillis = leadAt,
                    intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                        action = ReminderAlarmReceiver.ACTION_FIRE_REMINDER_LEAD
                        putExtra(ReminderAlarmReceiver.EXTRA_ID, reminder.id)
                        putExtra(ReminderAlarmReceiver.EXTRA_LEAD_MINUTES, leadMinutes)
                    },
                )
            }
        }
    }

    fun cancelReminder(reminderId: Long) {
        val mainPi = PendingIntent.getBroadcast(
            context,
            REMINDER_CODE_BASE + reminderId.toInt(),
            Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ReminderAlarmReceiver.ACTION_FIRE_REMINDER
            },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (mainPi != null) {
            alarmManager.cancel(mainPi)
            mainPi.cancel()
        }

        val leadPi = PendingIntent.getBroadcast(
            context,
            REMINDER_LEAD_CODE_BASE + reminderId.toInt(),
            Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ReminderAlarmReceiver.ACTION_FIRE_REMINDER_LEAD
            },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (leadPi != null) {
            alarmManager.cancel(leadPi)
            leadPi.cancel()
        }
    }

    fun scheduleAlarm(alarm: AlarmEntity): Long? {
        cancelAlarm(alarm.id)
        if (!alarm.isEnabled) return null

        val nextTrigger = ScheduleCalculator.computeNextAlarmTrigger(alarm)
        val fireIntent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_FIRE_ALARM
            putExtra(ReminderAlarmReceiver.EXTRA_ID, alarm.id)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            ALARM_CODE_BASE + alarm.id.toInt(),
            fireIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val showIntent = PendingIntent.getActivity(
            context,
            ALARM_SHOW_CODE_BASE + alarm.id.toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        if (canScheduleExactAlarms()) {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(nextTrigger, showIntent),
                pi,
            )
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger, pi)
        }
        return nextTrigger
    }

    fun scheduleSnoozedAlarm(alarmId: Long, snoozeMinutes: Int) {
        val triggerAt = System.currentTimeMillis() + snoozeMinutes.coerceIn(1, 60) * 60_000L
        scheduleExactOrFallback(
            requestCode = ALARM_CODE_BASE + alarmId.toInt(),
            triggerAtMillis = triggerAt,
            intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ReminderAlarmReceiver.ACTION_FIRE_ALARM
                putExtra(ReminderAlarmReceiver.EXTRA_ID, alarmId)
            },
        )
    }

    fun cancelAlarm(alarmId: Long) {
        val pi = PendingIntent.getBroadcast(
            context,
            ALARM_CODE_BASE + alarmId.toInt(),
            Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ReminderAlarmReceiver.ACTION_FIRE_ALARM
            },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pi != null) {
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }

    fun scheduleTimer(timer: TimerEntity) {
        cancelTimer(timer.id)
        val endEpoch = timer.endEpochMillis ?: return
        if (!timer.isRunning) return

        scheduleExactOrFallback(
            requestCode = TIMER_CODE_BASE + timer.id.toInt(),
            triggerAtMillis = endEpoch,
            intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ReminderAlarmReceiver.ACTION_FIRE_TIMER
                putExtra(ReminderAlarmReceiver.EXTRA_ID, timer.id)
            },
        )
    }

    fun cancelTimer(timerId: Long) {
        val pi = PendingIntent.getBroadcast(
            context,
            TIMER_CODE_BASE + timerId.toInt(),
            Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ReminderAlarmReceiver.ACTION_FIRE_TIMER
            },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pi != null) {
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }

    fun scheduleRoutine(routine: RoutineEntity) {
        cancelRoutine(routine.id)
        if (!routine.reminderEnabled) return

        val now = ZonedDateTime.now(ZoneId.systemDefault())
        var next = now.withHour(routine.startHour.coerceIn(0, 23))
            .withMinute(routine.startMinute.coerceIn(0, 59))
            .withSecond(0)
            .withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)

        scheduleExactOrFallback(
            requestCode = ROUTINE_CODE_BASE + routine.id.toInt(),
            triggerAtMillis = next.toInstant().toEpochMilli(),
            intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ReminderAlarmReceiver.ACTION_FIRE_ROUTINE
                putExtra(ReminderAlarmReceiver.EXTRA_ID, routine.id)
            },
        )
    }

    fun cancelRoutine(routineId: Long) {
        val pi = PendingIntent.getBroadcast(
            context,
            ROUTINE_CODE_BASE + routineId.toInt(),
            Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ReminderAlarmReceiver.ACTION_FIRE_ROUTINE
            },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pi != null) {
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }

    private fun scheduleExactOrFallback(
        requestCode: Int,
        triggerAtMillis: Long,
        intent: Intent,
    ) {
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    suspend fun rescheduleAll(db: RRDatabase) {
        val leadMinutes = runCatching {
            UserPreferencesRepository(context).getSnapshot().defaultReminderLeadMinutes
        }.getOrDefault(0)

        db.reminderDao().getAllActiveSync().forEach { reminder ->
            scheduleReminder(reminder, leadMinutes = leadMinutes)
        }
        db.alarmDao().getAllSync().filter { it.isEnabled }.forEach { alarm ->
            val next = scheduleAlarm(alarm)
            if (next != null && next != alarm.nextTriggerAtMillis) {
                db.alarmDao().update(alarm.copy(nextTriggerAtMillis = next))
            }
        }
        val now = System.currentTimeMillis()
        db.timerDao().getAllSync().filter { it.isRunning && it.endEpochMillis != null }.forEach { timer ->
            if ((timer.endEpochMillis ?: 0L) > now) {
                scheduleTimer(timer)
            } else {
                db.timerDao().updateTimer(
                    timer.copy(isRunning = false, remainingMillis = 0L, endEpochMillis = null),
                )
            }
        }
        db.routineDao().getAllSync().filter { it.reminderEnabled }.forEach { routine ->
            scheduleRoutine(routine)
        }
    }

    companion object {
        private const val REMINDER_CODE_BASE = 100_000
        private const val REMINDER_LEAD_CODE_BASE = 150_000
        private const val ALARM_CODE_BASE = 200_000
        private const val ALARM_SHOW_CODE_BASE = 250_000
        private const val TIMER_CODE_BASE = 300_000
        private const val ROUTINE_CODE_BASE = 400_000
    }
}
