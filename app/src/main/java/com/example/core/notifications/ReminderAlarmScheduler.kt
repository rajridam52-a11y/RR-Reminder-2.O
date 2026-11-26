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
import com.example.core.network.NetworkTimeProvider
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderAlarmScheduler(
    private val context: Context,
    private val networkTimeProvider: NetworkTimeProvider? = null,
) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExactAlarms(): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }
        }.getOrDefault(false)
    }

    private fun appNowMillis(): Long =
        networkTimeProvider?.currentEpochMillis() ?: System.currentTimeMillis()

    private fun toDeviceWallClockTrigger(appTriggerMillis: Long, appNow: Long): Long {
        val delayMs = (appTriggerMillis - appNow).coerceAtLeast(1_000L)
        return System.currentTimeMillis() + delayMs
    }

    fun scheduleReminder(
        reminder: ReminderEntity,
        leadMinutes: Int = 0,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) {
        cancelReminder(reminder.id)
        if (reminder.isCompleted) return

        val appNow = appNowMillis()
        val rawTrigger = reminder.snoozedUntilMillis ?: reminder.triggerAtMillis
        val effectiveTrigger = if (rawTrigger <= appNow - 60_000L) {
            ScheduleCalculator.computeNextReminderTrigger(
                reminder = reminder,
                afterMillis = appNow,
                targetZone = zoneId,
            ) ?: return
        } else {
            rawTrigger
        }

        scheduleExactOrFallback(
            requestCode = REMINDER_CODE_BASE + reminder.id.toInt(),
            triggerAtMillis = toDeviceWallClockTrigger(effectiveTrigger, appNow),
            intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ReminderAlarmReceiver.ACTION_FIRE_REMINDER
                putExtra(ReminderAlarmReceiver.EXTRA_ID, reminder.id)
            },
        )

        if (leadMinutes > 0 && reminder.snoozedUntilMillis == null) {
            val leadAt = effectiveTrigger - leadMinutes * 60_000L
            if (leadAt > appNow + 5_000L) {
                scheduleExactOrFallback(
                    requestCode = REMINDER_LEAD_CODE_BASE + reminder.id.toInt(),
                    triggerAtMillis = toDeviceWallClockTrigger(leadAt, appNow),
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

    fun scheduleAlarm(
        alarm: AlarmEntity,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        cancelAlarm(alarm.id)
        if (!alarm.isEnabled) return null

        val appNow = appNowMillis()
        val nextAppTrigger = ScheduleCalculator.computeNextAlarmTrigger(
            alarm = alarm,
            nowMillis = appNow,
            zoneId = zoneId,
        )
        val wallTrigger = toDeviceWallClockTrigger(nextAppTrigger, appNow)
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

        runCatching {
            if (canScheduleExactAlarms()) {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(wallTrigger, showIntent),
                    pi,
                )
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wallTrigger, pi)
            }
        }.onFailure {
            runCatching {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wallTrigger, pi)
            }
        }
        return nextAppTrigger
    }

    fun scheduleSnoozedAlarm(alarmId: Long, snoozeMinutes: Int): Long {
        val delayMs = snoozeMinutes.coerceIn(1, 60) * 60_000L
        val wallTrigger = System.currentTimeMillis() + delayMs
        val appTrigger = appNowMillis() + delayMs
        scheduleExactOrFallback(
            requestCode = ALARM_CODE_BASE + alarmId.toInt(),
            triggerAtMillis = wallTrigger,
            intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ReminderAlarmReceiver.ACTION_FIRE_ALARM
                putExtra(ReminderAlarmReceiver.EXTRA_ID, alarmId)
            },
        )
        return appTrigger
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

    fun scheduleRoutine(
        routine: RoutineEntity,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) {
        cancelRoutine(routine.id)
        if (!routine.reminderEnabled) return

        val appNow = appNowMillis()
        val now = Instant.ofEpochMilli(appNow).atZone(zoneId)
        var next = now.withHour(routine.startHour.coerceIn(0, 23))
            .withMinute(routine.startMinute.coerceIn(0, 59))
            .withSecond(0)
            .withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)

        scheduleExactOrFallback(
            requestCode = ROUTINE_CODE_BASE + routine.id.toInt(),
            triggerAtMillis = toDeviceWallClockTrigger(next.toInstant().toEpochMilli(), appNow),
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
        runCatching {
            if (canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        }.onFailure {
            runCatching {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        }
    }

    suspend fun rescheduleAll(db: RRDatabase) {
        val prefsSnap = runCatching {
            UserPreferencesRepository(context).getSnapshot()
        }.getOrNull()
        val leadMinutes = prefsSnap?.defaultReminderLeadMinutes ?: 0
        val activeZone = prefsSnap?.primaryClockZone?.toZoneId() ?: ZoneId.systemDefault()

        db.reminderDao().getAllActiveSync().forEach { reminder ->
            scheduleReminder(reminder, leadMinutes = leadMinutes, zoneId = activeZone)
        }
        db.alarmDao().getAllSync().filter { it.isEnabled }.forEach { alarm ->
            val next = scheduleAlarm(alarm, zoneId = activeZone)
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
            scheduleRoutine(routine, zoneId = activeZone)
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
