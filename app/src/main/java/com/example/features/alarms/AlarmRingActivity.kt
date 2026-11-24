package com.example.features.alarms

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.RRReminderApp
import com.example.core.datastore.UserPreferences
import com.example.core.notifications.ReminderAlarmReceiver
import com.example.core.notifications.ScheduleCalculator
import com.example.ui.theme.LocalRRExtraColors
import com.example.ui.theme.RRReminderTheme
import com.example.ui.theme.TabularClockLarge
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class AlarmRingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
        }

        val itemId = intent.getLongExtra(EXTRA_ID, 0L)
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "Alarm"
        val isTimer = intent.getBooleanExtra(EXTRA_IS_TIMER, false)
        val snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 10)
        val prefsRepo = (application as RRReminderApp).container.preferencesRepository

        setContent {
            val prefs by prefsRepo.preferences.collectAsStateWithLifecycle(initialValue = UserPreferences())
            RRReminderTheme(
                themeMode = prefs.themeMode,
                accentPreset = prefs.accentPreset,
            ) {
                AlarmRingScreen(
                    label = label,
                    isTimer = isTimer,
                    snoozeMinutes = snoozeMinutes,
                    use24Hour = prefs.use24Hour,
                    onDismiss = {
                        sendBroadcast(
                            Intent(this, ReminderAlarmReceiver::class.java).apply {
                                action = if (isTimer) {
                                    ReminderAlarmReceiver.ACTION_DISMISS_TIMER
                                } else {
                                    ReminderAlarmReceiver.ACTION_DISMISS_ALARM
                                }
                                putExtra(ReminderAlarmReceiver.EXTRA_ID, itemId)
                            },
                        )
                        finish()
                    },
                    onSnooze = {
                        sendBroadcast(
                            Intent(this, ReminderAlarmReceiver::class.java).apply {
                                action = ReminderAlarmReceiver.ACTION_SNOOZE_ALARM
                                putExtra(ReminderAlarmReceiver.EXTRA_ID, itemId)
                                putExtra(ReminderAlarmReceiver.EXTRA_SNOOZE_MINUTES, snoozeMinutes)
                            },
                        )
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        const val EXTRA_ID = "extra_id"
        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_IS_TIMER = "extra_is_timer"
        const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"
    }
}

@Composable
private fun AlarmRingScreen(
    label: String,
    isTimer: Boolean,
    snoozeMinutes: Int,
    use24Hour: Boolean,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
) {
    val extra = LocalRRExtraColors.current
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            val now = System.currentTimeMillis()
            nowMs = now
            val waitMs = (1000L - (now % 1000L)).coerceAtLeast(50L) + 10L
            delay(waitMs)
        }
    }

    val pulse by rememberInfiniteTransition(label = "ringPulse").animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        extra.accentPrimary.copy(alpha = 0.22f),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            )
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = CircleShape,
                    color = extra.accentPrimary.copy(alpha = 0.18f),
                    modifier = Modifier
                        .size(116.dp)
                        .scale(pulse),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isTimer) Icons.Rounded.Timer else Icons.Rounded.Alarm,
                            contentDescription = "Ringing alert",
                            tint = extra.accentPrimary,
                            modifier = Modifier.size(56.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = ScheduleCalculator.formatTime(nowMs, use24Hour = use24Hour, includeSeconds = true),
                    style = TabularClockLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (isTimer) "Timer Complete" else "RR Alarm",
                    style = MaterialTheme.typography.labelLarge,
                    color = extra.accentPrimary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (!isTimer) {
                    OutlinedButton(
                        onClick = onSnooze,
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .testTag("alarm_ring_snooze"),
                    ) {
                        Icon(Icons.Rounded.Snooze, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Snooze ${snoozeMinutes}m")
                    }
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp)
                        .testTag("alarm_ring_dismiss"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Dismiss")
                }
            }
        }
    }
}
