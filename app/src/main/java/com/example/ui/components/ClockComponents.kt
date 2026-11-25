package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.datastore.ClockFaceStyle
import com.example.core.notifications.ScheduleCalculator
import com.example.ui.theme.LocalRRExtraColors
import com.example.ui.theme.TabularClockLarge
import java.time.Instant
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dynamic Live Clock experience supporting 5 selectable clock faces:
 * Dual Hybrid, Aurora Digital, Analog Chrono, Minimal Swiss, and Neon Orbit.
 */
@Composable
fun AuroraClockDisplay(
    nowMillis: Long,
    zoneId: ZoneId,
    use24Hour: Boolean,
    datePattern: String,
    faceStyle: ClockFaceStyle,
    modifier: Modifier = Modifier,
) {
    val extra = LocalRRExtraColors.current
    val zdt = remember(nowMillis, zoneId) {
        Instant.ofEpochMilli(nowMillis).atZone(zoneId)
    }
    val formattedTime = ScheduleCalculator.formatTime(
        millis = nowMillis,
        use24Hour = use24Hour,
        zoneId = zoneId,
        includeSeconds = true,
    )
    val formattedDate = ScheduleCalculator.formatDate(
        millis = nowMillis,
        pattern = "EEEE, $datePattern",
        zoneId = zoneId,
    )

    AnimatedContent(
        targetState = faceStyle,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "clockFaceTransition",
        modifier = modifier,
    ) { style ->
        when (style) {
            ClockFaceStyle.AURORA_DIGITAL -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = formattedTime,
                        style = TabularClockLarge,
                        color = Color.White,
                    )
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB8C7EA),
                    )
                }
            }

            ClockFaceStyle.ANALOG_CHRONO,
            ClockFaceStyle.MINIMAL_SWISS,
            ClockFaceStyle.NEON_ORBIT,
            -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AuroraAnalogClockCanvas(
                        hours = zdt.hour,
                        minutes = zdt.minute,
                        seconds = zdt.second,
                        style = style,
                        dialSize = 104.dp,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = formattedTime,
                            style = TabularClockLarge.copy(fontSize = MaterialTheme.typography.headlineMedium.fontSize),
                            color = Color.White,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFB8C7EA),
                        )
                        Text(
                            text = style.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = extra.accentPrimary,
                        )
                    }
                }
            }

            ClockFaceStyle.DUAL_HYBRID -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AuroraAnalogClockCanvas(
                        hours = zdt.hour,
                        minutes = zdt.minute,
                        seconds = zdt.second,
                        style = ClockFaceStyle.DUAL_HYBRID,
                        dialSize = 78.dp,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = formattedTime,
                            style = TabularClockLarge.copy(fontSize = MaterialTheme.typography.headlineLarge.fontSize),
                            color = Color.White,
                        )
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFB8C7EA),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AuroraAnalogClockCanvas(
    hours: Int,
    minutes: Int,
    seconds: Int,
    style: ClockFaceStyle,
    dialSize: Dp = 96.dp,
    modifier: Modifier = Modifier,
) {
    val extra = LocalRRExtraColors.current
    val accentPrimary = extra.accentPrimary
    val accentSecondary = extra.accentSecondary

    Canvas(modifier = modifier.size(dialSize)) {
        val radius = size.minDimension / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val center = Offset(cx, cy)

        // Outer dial surface
        drawCircle(
            color = when (style) {
                ClockFaceStyle.MINIMAL_SWISS -> Color(0xFF10192E).copy(alpha = 0.85f)
                ClockFaceStyle.NEON_ORBIT -> Color(0xFF060E24).copy(alpha = 0.92f)
                else -> Color.White.copy(alpha = 0.08f)
            },
            radius = radius * 0.94f,
            center = center,
        )

        // Outer bezel or Neon Orbit second arc
        if (style == ClockFaceStyle.NEON_ORBIT) {
            drawCircle(
                color = Color.White.copy(alpha = 0.14f),
                radius = radius * 0.90f,
                center = center,
                style = Stroke(width = size.minDimension * 0.04f),
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(accentPrimary, accentSecondary, accentPrimary)),
                startAngle = -90f,
                sweepAngle = (seconds / 60f) * 360f,
                useCenter = false,
                style = Stroke(width = size.minDimension * 0.045f, cap = StrokeCap.Round),
            )
        } else {
            drawCircle(
                brush = Brush.linearGradient(listOf(accentPrimary, accentSecondary)),
                radius = radius * 0.94f,
                center = center,
                style = Stroke(width = size.minDimension * 0.03f),
            )
        }

        // Hour markers (12 or 4 cardinal for Minimal Swiss)
        val markerCount = if (style == ClockFaceStyle.MINIMAL_SWISS) 12 else 12
        for (i in 0 until markerCount) {
            val angleRad = ((i * 30.0) - 90.0) * PI / 180.0
            val isMajor = i % 3 == 0
            val innerFactor = if (isMajor) 0.72f else 0.80f
            val outerFactor = 0.88f
            val sx = cx + (radius * innerFactor * cos(angleRad)).toFloat()
            val sy = cy + (radius * innerFactor * sin(angleRad)).toFloat()
            val ex = cx + (radius * outerFactor * cos(angleRad)).toFloat()
            val ey = cy + (radius * outerFactor * sin(angleRad)).toFloat()
            drawLine(
                color = if (isMajor) Color.White else Color.White.copy(alpha = 0.45f),
                start = Offset(sx, sy),
                end = Offset(ex, ey),
                strokeWidth = if (isMajor) size.minDimension * 0.032f else size.minDimension * 0.016f,
                cap = StrokeCap.Round,
            )
        }

        // Angles in radians (-90 deg at 12 o'clock)
        val secondAngle = ((seconds * 6.0) - 90.0) * PI / 180.0
        val minuteAngle = (((minutes + seconds / 60.0) * 6.0) - 90.0) * PI / 180.0
        val hourAngle = ((((hours % 12) + minutes / 60.0) * 30.0) - 90.0) * PI / 180.0

        // Hour hand
        drawLine(
            color = Color.White,
            start = center,
            end = Offset(
                cx + (radius * 0.48f * cos(hourAngle)).toFloat(),
                cy + (radius * 0.48f * sin(hourAngle)).toFloat(),
            ),
            strokeWidth = size.minDimension * 0.052f,
            cap = StrokeCap.Round,
        )

        // Minute hand
        drawLine(
            color = if (style == ClockFaceStyle.MINIMAL_SWISS) accentSecondary else Color(0xFFE1ECFF),
            start = center,
            end = Offset(
                cx + (radius * 0.70f * cos(minuteAngle)).toFloat(),
                cy + (radius * 0.70f * sin(minuteAngle)).toFloat(),
            ),
            strokeWidth = size.minDimension * 0.036f,
            cap = StrokeCap.Round,
        )

        // Live second hand
        val secondColor = if (style == ClockFaceStyle.MINIMAL_SWISS) Color(0xFFFF5A5F) else accentPrimary
        drawLine(
            color = secondColor,
            start = Offset(
                cx - (radius * 0.14f * cos(secondAngle)).toFloat(),
                cy - (radius * 0.14f * sin(secondAngle)).toFloat(),
            ),
            end = Offset(
                cx + (radius * 0.78f * cos(secondAngle)).toFloat(),
                cy + (radius * 0.78f * sin(secondAngle)).toFloat(),
            ),
            strokeWidth = size.minDimension * 0.020f,
            cap = StrokeCap.Round,
        )

        // Center pinion
        drawCircle(
            color = secondColor,
            radius = size.minDimension * 0.045f,
            center = center,
        )
    }
}
