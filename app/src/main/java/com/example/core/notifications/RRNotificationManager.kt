package com.example.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.core.database.NotificationLogDao
import com.example.core.database.NotificationLogEntity
import com.example.features.alarms.AlarmRingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object RRChannels {
    const val REMINDERS = "rr_reminders"
    const val ALARMS = "rr_alarms"
    const val TIMERS = "rr_timers"
    const val ROUTINES = "rr_routines"
    const val SYSTEM = "rr_system"
}

class RRNotificationManager(
    private val context: Context,
    private val notificationLogDao: NotificationLogDao,
) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val alarmAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channels = listOf(
            NotificationChannel(
                RRChannels.REMINDERS,
                context.getString(R.string.channel_reminders_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_reminders_desc)
                enableVibration(true)
            },
            NotificationChannel(
                RRChannels.ALARMS,
                context.getString(R.string.channel_alarms_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_alarms_desc)
                setSound(alarmSound, alarmAttrs)
                enableVibration(true)
                setBypassDnd(true)
            },
            NotificationChannel(
                RRChannels.TIMERS,
                context.getString(R.string.channel_timers_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_timers_desc)
                enableVibration(true)
            },
            NotificationChannel(
                RRChannels.ROUTINES,
                context.getString(R.string.channel_routines_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.channel_routines_desc)
            },
            NotificationChannel(
                RRChannels.SYSTEM,
                context.getString(R.string.channel_system_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.channel_system_desc)
            },
        )
        channels.forEach { manager.createNotificationChannel(it) }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun postReminderNotification(
        reminderId: Long,
        title: String,
        body: String,
        priorityLabel: String,
        isLeadAlert: Boolean = false,
        leadMinutes: Int = 0,
        soundUriString: String? = null,
        vibrate: Boolean = true,
    ) {
        ensureChannels()
        val displayTitle = if (isLeadAlert) "Upcoming in ${leadMinutes}m: $title" else title
        val displayBody = body.ifBlank {
            if (isLeadAlert) "Scheduled in $leadMinutes minutes ($priorityLabel) • RR Reminder"
            else "Priority: $priorityLabel • RR Reminder"
        }
        logNotification(RRChannels.REMINDERS, displayTitle, displayBody, "REMINDER", reminderId)
        if (!hasNotificationPermission()) return

        val openIntent = PendingIntent.getActivity(
            context,
            (10_000 + reminderId).toInt(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "reminders")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val completeIntent = PendingIntent.getBroadcast(
            context,
            (20_000 + reminderId).toInt(),
            Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ReminderAlarmReceiver.ACTION_COMPLETE_REMINDER
                putExtra(ReminderAlarmReceiver.EXTRA_ID, reminderId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val snoozeIntent = PendingIntent.getBroadcast(
            context,
            (30_000 + reminderId).toInt(),
            Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ReminderAlarmReceiver.ACTION_SNOOZE_REMINDER
                putExtra(ReminderAlarmReceiver.EXTRA_ID, reminderId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, RRChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(displayTitle)
            .setContentText(displayBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .addAction(0, "Complete", completeIntent)
            .addAction(0, "Snooze 10m", snoozeIntent)

        if (!vibrate) {
            builder.setVibrate(longArrayOf(0L))
        }
        soundUriString?.let { raw ->
            runCatching { Uri.parse(raw) }.getOrNull()?.let { builder.setSound(it) }
        }

        val notifId = if (isLeadAlert) (15_000 + reminderId).toInt() else (1000 + reminderId).toInt()
        NotificationManagerCompat.from(context).notify(notifId, builder.build())
    }

    fun buildAlarmRingingNotification(
        alarmId: Long,
        label: String,
        isTimer: Boolean,
        snoozeMinutes: Int,
    ): android.app.Notification {
        ensureChannels()

        val fullScreenIntent = Intent(context, AlarmRingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmRingActivity.EXTRA_ID, alarmId)
            putExtra(AlarmRingActivity.EXTRA_LABEL, label)
            putExtra(AlarmRingActivity.EXTRA_IS_TIMER, isTimer)
            putExtra(AlarmRingActivity.EXTRA_SNOOZE_MINUTES, snoozeMinutes)
        }
        val fullScreenPi = PendingIntent.getActivity(
            context,
            (40_000 + alarmId).toInt(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val dismissPi = PendingIntent.getBroadcast(
            context,
            (50_000 + alarmId).toInt(),
            Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = if (isTimer) ReminderAlarmReceiver.ACTION_DISMISS_TIMER else ReminderAlarmReceiver.ACTION_DISMISS_ALARM
                putExtra(ReminderAlarmReceiver.EXTRA_ID, alarmId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val channel = if (isTimer) RRChannels.TIMERS else RRChannels.ALARMS
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (isTimer) "Timer Finished" else "Alarm: $label")
            .setContentText(if (isTimer) "$label countdown completed" else "Tap to manage alarm")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPi, true)
            .setContentIntent(fullScreenPi)
            .addAction(0, "Dismiss", dismissPi)

        if (!isTimer) {
            val snoozePi = PendingIntent.getBroadcast(
                context,
                (60_000 + alarmId).toInt(),
                Intent(context, ReminderAlarmReceiver::class.java).apply {
                    action = ReminderAlarmReceiver.ACTION_SNOOZE_ALARM
                    putExtra(ReminderAlarmReceiver.EXTRA_ID, alarmId)
                    putExtra(ReminderAlarmReceiver.EXTRA_SNOOZE_MINUTES, snoozeMinutes)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, "Snooze ${snoozeMinutes}m", snoozePi)
        }

        return builder.build()
    }

    fun postSimpleNotification(
        id: Int,
        channelId: String,
        title: String,
        body: String,
        sourceType: String,
        sourceId: Long = 0L,
    ) {
        ensureChannels()
        logNotification(channelId, title, body, sourceType, sourceId)
        if (!hasNotificationPermission()) return

        val openIntent = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()

        NotificationManagerCompat.from(context).notify(id, notification)
    }

    fun cancelNotification(id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    fun logNotification(
        channelId: String,
        title: String,
        body: String,
        sourceType: String,
        sourceId: Long,
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            notificationLogDao.insertLog(
                NotificationLogEntity(
                    channelId = channelId,
                    title = title,
                    body = body,
                    sourceType = sourceType,
                    sourceId = sourceId,
                ),
            )
        }
    }
}
