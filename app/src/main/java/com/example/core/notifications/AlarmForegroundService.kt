package com.example.core.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.core.database.RRDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AlarmForegroundService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var fadeJob: Job? = null
    private var autoStopJob: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            autoStopJob?.cancel()
            stopAudioAndVibration()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val id = intent?.getLongExtra(EXTRA_ID, 0L) ?: 0L
        val label = intent?.getStringExtra(EXTRA_LABEL) ?: "Alarm"
        val isTimer = intent?.getBooleanExtra(EXTRA_IS_TIMER, false) ?: false
        val vibrate = intent?.getBooleanExtra(EXTRA_VIBRATE, true) ?: true
        val gradualVolume = intent?.getBooleanExtra(EXTRA_GRADUAL_VOLUME, true) ?: true
        val ringtoneUriString = intent?.getStringExtra(EXTRA_RINGTONE_URI)
        val snoozeMinutes = intent?.getIntExtra(EXTRA_SNOOZE_MINUTES, 10) ?: 10

        val db = RRDatabase.getInstance(applicationContext)
        val notifManager = RRNotificationManager(applicationContext, db.notificationLogDao())
        val notification = notifManager.buildAlarmRingingNotification(
            alarmId = id,
            label = label,
            isTimer = isTimer,
            snoozeMinutes = snoozeMinutes,
        )

        startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        notifManager.logNotification(
            channelId = if (isTimer) RRChannels.TIMERS else RRChannels.ALARMS,
            title = if (isTimer) "Timer Finished: $label" else "Alarm: $label",
            body = if (isTimer) "Countdown completed" else "Alarm ringing",
            sourceType = if (isTimer) "TIMER" else "ALARM",
            sourceId = id,
        )

        startAudio(ringtoneUriString, gradualVolume)
        if (vibrate) startVibration()

        // Safety auto-stop after 10 minutes to preserve battery
        autoStopJob?.cancel()
        autoStopJob = serviceScope.launch {
            delay(10 * 60_000L)
            stopAudioAndVibration()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun startAudio(ringtoneUriString: String?, gradualVolume: Boolean) {
        stopAudioOnly()
        val uri = ringtoneUriString?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        runCatching {
            val player = MediaPlayer().apply {
                setDataSource(applicationContext, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                isLooping = true
                prepare()
                if (gradualVolume) {
                    setVolume(0.15f, 0.15f)
                } else {
                    setVolume(1.0f, 1.0f)
                }
                start()
            }
            mediaPlayer = player

            if (gradualVolume) {
                fadeJob?.cancel()
                fadeJob = serviceScope.launch {
                    var step = 1
                    while (isActive && step <= 10) {
                        delay(2000L)
                        val vol = (0.15f + step * 0.085f).coerceAtMost(1.0f)
                        runCatching { mediaPlayer?.setVolume(vol, vol) }
                        step++
                    }
                }
            }
        }
    }

    private fun startVibration() {
        val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator = vib
        val pattern = longArrayOf(0, 600, 400, 600, 400)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(pattern, 0)
        }
    }

    private fun stopAudioOnly() {
        fadeJob?.cancel()
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
    }

    private fun stopAudioAndVibration() {
        stopAudioOnly()
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    override fun onDestroy() {
        autoStopJob?.cancel()
        stopAudioAndVibration()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val FOREGROUND_NOTIFICATION_ID = 9001
        const val ACTION_START = "com.example.action.ALARM_SERVICE_START"
        const val ACTION_STOP = "com.example.action.ALARM_SERVICE_STOP"
        const val EXTRA_ID = "extra_id"
        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_IS_TIMER = "extra_is_timer"
        const val EXTRA_VIBRATE = "extra_vibrate"
        const val EXTRA_GRADUAL_VOLUME = "extra_gradual"
        const val EXTRA_RINGTONE_URI = "extra_ringtone_uri"
        const val EXTRA_SNOOZE_MINUTES = "extra_snooze_min"

        fun stop(context: Context) {
            context.startService(
                Intent(context, AlarmForegroundService::class.java).apply {
                    action = ACTION_STOP
                },
            )
        }
    }
}
