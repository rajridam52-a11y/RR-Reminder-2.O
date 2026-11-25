package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.database.WorldClockCityEntity
import com.example.core.datastore.ClockFaceStyle
import com.example.core.network.NetworkTimeSyncStatus
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
                        style = TabularClockLarge.copy(
                            fontSize = MaterialTheme.typography.displaySmall.fontSize,
                            lineHeight = MaterialTheme.typography.displaySmall.lineHeight,
                        ),
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB8C7EA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                        dialSize = 86.dp,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = formattedTime,
                            style = TabularClockLarge.copy(
                                fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                                lineHeight = MaterialTheme.typography.headlineSmall.lineHeight,
                            ),
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFB8C7EA),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = style.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = extra.accentPrimary,
                            maxLines = 1,
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
                        dialSize = 74.dp,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = formattedTime,
                            style = TabularClockLarge.copy(
                                fontSize = MaterialTheme.typography.headlineMedium.fontSize,
                                lineHeight = MaterialTheme.typography.headlineMedium.lineHeight,
                            ),
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFB8C7EA),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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

/**
 * Full-screen Screen 3 Live Clock experience matching RR Reminder 2.0 cosmic mockup:
 * luminous glowing circular analog dial with 1..12 numerals, bold digital readout,
 * interactive 12H/24H mode toggle, glowing Earth horizon arc, and 3-city world preview strip.
 */
@Composable
fun LuminousLiveClockHero(
    nowMillis: Long,
    zoneId: ZoneId,
    use24Hour: Boolean,
    datePattern: String,
    faceStyle: ClockFaceStyle,
    timeSyncStatus: NetworkTimeSyncStatus,
    worldCities: List<WorldClockCityEntity>,
    onSyncNetworkTime: () -> Unit,
    onCycleClockFace: () -> Unit,
    onSetTimeFormat24: (Boolean) -> Unit,
    onOpenWorldClock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zdt = remember(nowMillis, zoneId) { Instant.ofEpochMilli(nowMillis).atZone(zoneId) }
    val formattedDigits = remember(nowMillis, zoneId, use24Hour) {
        val pattern = if (use24Hour) "HH:mm:ss" else "hh:mm:ss"
        zdt.format(java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale.getDefault()))
    }
    val amPm = remember(nowMillis, zoneId, use24Hour) {
        if (use24Hour) "" else zdt.format(java.time.format.DateTimeFormatter.ofPattern("a", java.util.Locale.ENGLISH)).uppercase()
    }
    val formattedDate = ScheduleCalculator.formatDate(
        millis = nowMillis,
        pattern = "EEE, $datePattern",
        zoneId = zoneId,
    )

    val previewCities = remember(worldCities) {
        if (worldCities.isNotEmpty()) {
            worldCities.take(3).map { Triple(it.cityName, it.zoneId, it.countryName) }
        } else {
            listOf(
                Triple("New York", "America/New_York", "US"),
                Triple("London", "Europe/London", "UK"),
                Triple("Tokyo", "Asia/Tokyo", "JP"),
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF030816),
                        Color(0xFF071330),
                        Color(0xFF081A42),
                        Color(0xFF030814),
                    ),
                ),
            ),
    ) {
        EarthHorizonCanvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(215.dp)
                .align(Alignment.BottomCenter),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top subtle status pills (Sync + Face style)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0x660F244D),
                    border = BorderStroke(1.dp, Color(0x5538BDF8)),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onSyncNetworkTime)
                        .testTag("live_clock_sync_pill"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = "Sync Google Time",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = if (timeSyncStatus.isSynced) timeSyncStatus.sourceLabel else "Device Clock",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = Color(0x5512264F),
                    border = BorderStroke(1.dp, Color(0x4460A5FA)),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onCycleClockFace)
                        .testTag("live_clock_face_pill"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WatchLater,
                            contentDescription = "Switch clock face",
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = faceStyle.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                }
            }

            // Luminous analog chronometer dial
            LuminousAnalogChronometerDial(
                hours = zdt.hour,
                minutes = zdt.minute,
                seconds = zdt.second,
                faceStyle = faceStyle,
                dialSize = 212.dp,
                modifier = Modifier.clickable(onClick = onCycleClockFace),
            )

            // Digital time readout + Date + 12H / 24H toggle
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = formattedDigits,
                        style = TabularClockLarge.copy(
                            fontSize = MaterialTheme.typography.displayMedium.fontSize,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                    )
                    if (amPm.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = amPm,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFF93C5FD),
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFBFD7FF),
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TimeFormatTogglePill(
                        label = "12H",
                        selected = !use24Hour,
                        onClick = { onSetTimeFormat24(false) },
                        testTag = "live_clock_12h_btn",
                    )
                    TimeFormatTogglePill(
                        label = "24H",
                        selected = use24Hour,
                        onClick = { onSetTimeFormat24(true) },
                        testTag = "live_clock_24h_btn",
                    )
                }
            }

            // 3-City World Clock footer over Earth Horizon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                previewCities.forEach { (cityName, tzId, _) ->
                    val cityZone = remember(tzId) { runCatching { ZoneId.of(tzId) }.getOrDefault(zoneId) }
                    val cityZdt = remember(nowMillis, cityZone) { Instant.ofEpochMilli(nowMillis).atZone(cityZone) }
                    val isCityDay = cityZdt.hour in 6..18
                    val cityTime = ScheduleCalculator.formatTime(
                        millis = nowMillis,
                        use24Hour = use24Hour,
                        zoneId = cityZone,
                        includeSeconds = false,
                    )
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xAA07142D),
                        border = BorderStroke(1.dp, Color(0x4460A5FA)),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = onOpenWorldClock),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = if (isCityDay) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                contentDescription = if (isCityDay) "Daytime" else "Nighttime",
                                tint = if (isCityDay) Color(0xFFFBBF24) else Color(0xFF93C5FD),
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = cityName,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = cityTime,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFBFD7FF),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeFormatTogglePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Color(0xFF1D4ED8) else Color(0x880B1936),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) Color(0xFF60A5FA) else Color(0x3393C5FD),
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun LuminousAnalogChronometerDial(
    hours: Int,
    minutes: Int,
    seconds: Int,
    faceStyle: ClockFaceStyle,
    dialSize: Dp = 212.dp,
    modifier: Modifier = Modifier,
) {
    val textPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
        }
    }

    Canvas(modifier = modifier.size(dialSize)) {
        val radius = size.minDimension / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val center = Offset(cx, cy)

        // Outer ambient halo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x5538BDF8),
                    Color(0x222563EB),
                    Color.Transparent,
                ),
            ),
            radius = radius * 1.05f,
            center = center,
        )

        // Deep cosmic inner face
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF11295C),
                    Color(0xFF060E24),
                ),
            ),
            radius = radius * 0.90f,
            center = center,
        )

        // Luminous electric blue ring bezel
        val ringColors = when (faceStyle) {
            ClockFaceStyle.NEON_ORBIT -> listOf(Color(0xFF22D3EE), Color(0xFFA855F7), Color(0xFF22D3EE))
            else -> listOf(Color(0xFF60A5FA), Color(0xFF38BDF8), Color(0xFF2563EB), Color(0xFF60A5FA))
        }
        drawCircle(
            brush = Brush.sweepGradient(ringColors),
            radius = radius * 0.90f,
            center = center,
            style = Stroke(width = 3.5.dp.toPx()),
        )

        // 60 subtle minute ticks around perimeter
        for (tick in 0 until 60) {
            val angle = ((tick * 6.0) - 90.0) * PI / 180.0
            val major = tick % 5 == 0
            val rIn = if (major) radius * 0.80f else radius * 0.84f
            val rOut = radius * 0.875f
            drawLine(
                color = if (major) Color(0xFF93C5FD) else Color.White.copy(alpha = 0.25f),
                start = Offset(cx + (rIn * cos(angle)).toFloat(), cy + (rIn * sin(angle)).toFloat()),
                end = Offset(cx + (rOut * cos(angle)).toFloat(), cy + (rOut * sin(angle)).toFloat()),
                strokeWidth = if (major) 1.8.dp.toPx() else 0.9.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        // Numerals 1..12
        textPaint.textSize = size.minDimension * 0.088f
        val fontMetrics = textPaint.fontMetrics
        val textCenterOffset = (fontMetrics.ascent + fontMetrics.descent) / 2f
        val numRadius = radius * 0.67f
        for (n in 1..12) {
            val angle = ((n * 30.0) - 90.0) * PI / 180.0
            val nx = cx + (numRadius * cos(angle)).toFloat()
            val ny = cy + (numRadius * sin(angle)).toFloat() - textCenterOffset
            drawContext.canvas.nativeCanvas.drawText(n.toString(), nx, ny, textPaint)
        }

        // Hands
        val secAngle = ((seconds * 6.0) - 90.0) * PI / 180.0
        val minAngle = (((minutes + seconds / 60.0) * 6.0) - 90.0) * PI / 180.0
        val hrAngle = ((((hours % 12) + minutes / 60.0) * 30.0) - 90.0) * PI / 180.0

        drawLine(
            color = Color.White,
            start = center,
            end = Offset(cx + (radius * 0.44f * cos(hrAngle)).toFloat(), cy + (radius * 0.44f * sin(hrAngle)).toFloat()),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color(0xFFE0ECFF),
            start = center,
            end = Offset(cx + (radius * 0.66f * cos(minAngle)).toFloat(), cy + (radius * 0.66f * sin(minAngle)).toFloat()),
            strokeWidth = 2.6.dp.toPx(),
            cap = StrokeCap.Round,
        )
        // Gold sweeping second hand
        drawLine(
            color = Color(0xFFFBBF24),
            start = Offset(cx - (radius * 0.14f * cos(secAngle)).toFloat(), cy - (radius * 0.14f * sin(secAngle)).toFloat()),
            end = Offset(cx + (radius * 0.74f * cos(secAngle)).toFloat(), cy + (radius * 0.74f * sin(secAngle)).toFloat()),
            strokeWidth = 1.8.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = Color(0xFFFBBF24),
            radius = 4.5.dp.toPx(),
            center = center,
        )
        drawCircle(
            color = Color(0xFF0A1633),
            radius = 2.dp.toPx(),
            center = center,
        )
    }
}
