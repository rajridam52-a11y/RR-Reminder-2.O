package com.example.core.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat
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
            runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
            stopSelf()
            return START_NOT_STICKY
        }

        val id = runCatching { intent?.getLongExtra(EXTRA_ID, 0L) ?: 0L }.getOrDefault(0L)
        val label = runCatching { intent?.getStringExtra(EXTRA_LABEL) ?: "Alarm" }.getOrDefault("Alarm")
        val isTimer = runCatching { intent?.getBooleanExtra(EXTRA_IS_TIMER, false) ?: false }.getOrDefault(false)
        val vibrate = runCatching { intent?.getBooleanExtra(EXTRA_VIBRATE, true) ?: true }.getOrDefault(true)
        val gradualVolume = runCatching { intent?.getBooleanExtra(EXTRA_GRADUAL_VOLUME, true) ?: true }.getOrDefault(true)
        val ringtoneUriString = runCatching { intent?.getStringExtra(EXTRA_RINGTONE_URI) }.getOrNull()
        val snoozeMinutes = runCatching { intent?.getIntExtra(EXTRA_SNOOZE_MINUTES, 10) ?: 10 }.getOrDefault(10)
        val isSilent = ringtoneUriString == SILENT_RINGTONE_URI

        val notifSetup = runCatching {
            val db = RRDatabase.getInstance(applicationContext)
            val notifManager = RRNotificationManager(applicationContext, db.notificationLogDao())
            val notification = notifManager.buildAlarmRingingNotification(
                alarmId = id,
                label = label,
                isTimer = isTimer,
                snoozeMinutes = snoozeMinutes,
                silent = isSilent,
            )
            notifManager to notification
        }.getOrElse {
            stopSelf()
            return START_NOT_STICKY
        }
        val (notifManager, notification) = notifSetup

        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, FOREGROUND_NOTIFICATION_ID, notification, fgsType)
        }.onFailure {
            // If foreground promotion fails on restricted OEM states, post alert and stop safely.
            runCatching {
                notifManager.postSimpleNotification(
                    id = FOREGROUND_NOTIFICATION_ID,
                    channelId = if (isTimer) RRChannels.TIMERS else RRChannels.ALARMS,
                    title = if (isTimer) "Timer Finished: $label" else "Alarm: $label",
                    body = if (isTimer) "Countdown completed" else "Alarm triggered",
                    sourceType = if (isTimer) "TIMER" else "ALARM",
                    sourceId = id,
                )
            }
            stopSelf()
            return START_NOT_STICKY
        }
        runCatching {
            notifManager.logNotification(
                channelId = if (isTimer) RRChannels.TIMERS else RRChannels.ALARMS,
                title = if (isTimer) "Timer Finished: $label" else "Alarm: $label",
                body = if (isTimer) "Countdown completed" else "Alarm ringing",
                sourceType = if (isTimer) "TIMER" else "ALARM",
                sourceId = id,
            )
        }

        startAudio(ringtoneUriString, gradualVolume)
        if (vibrate) startVibration()

        // Safety auto-stop after 10 minutes to preserve battery
        autoStopJob?.cancel()
        autoStopJob = serviceScope.launch {
            delay(10 * 60_000L)
            stopAudioAndVibration()
            runCatching { ServiceCompat.stopForeground(this@AlarmForegroundService, ServiceCompat.STOP_FOREGROUND_REMOVE) }
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun startAudio(ringtoneUriString: String?, gradualVolume: Boolean) {
        stopAudioOnly()
        if (ringtoneUriString == SILENT_RINGTONE_URI) return
        val uri = ringtoneUriString?.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return

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
        runCatching {
            val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return
            vibrator = vib
            val pattern = longArrayOf(0, 600, 400, 600, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(pattern, 0)
            }
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
        const val SILENT_RINGTONE_URI = "silent"
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
            runCatching {
                context.stopService(Intent(context, AlarmForegroundService::class.java))
            }
        }
    }
}
