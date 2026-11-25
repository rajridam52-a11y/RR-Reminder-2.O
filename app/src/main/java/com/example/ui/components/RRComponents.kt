package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalRRExtraColors
import com.example.ui.theme.SpaceGroteskFontFamily

/**
 * Aurora UI 2.0 full-screen ambient atmosphere container. Renders subtle radial cyan/emerald
 * light fields over deep midnight navy in dark mode, or crisp luminous frost in light mode.
 */
@Composable
fun RRBackgroundContainer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val extra = LocalRRExtraColors.current
    val bg = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        extra.glowBrushStart,
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.14f, size.height * 0.08f),
                    radius = size.maxDimension * 0.62f,
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        extra.glowBrushEnd,
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.88f, size.height * 0.78f),
                    radius = size.maxDimension * 0.56f,
                ),
            )
        }
        content()
    }
}

/**
 * RR Reminder 2.0 glowing chronometer emblem with vibrant blue-cyan-purple-amber clock arc,
 * crisp ticks, clock hands, and bold geometric RR monogram.
 */
@Composable
fun RRBrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val extra = LocalRRExtraColors.current
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0D1B42),
                        Color(0xFF1A1047),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size * 0.86f)) {
            val strokeW = this.size.minDimension * 0.085f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = this.size.minDimension * 0.43f

            // Outer glowing blue-violet-cyan ring
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        Color(0xFF38BDF8),
                        Color(0xFF6366F1),
                        Color(0xFFA855F7),
                        Color(0xFFFBBF24),
                        Color(0xFF38BDF8),
                    ),
                ),
                startAngle = -70f,
                sweepAngle = 315f,
                useCenter = false,
                style = Stroke(width = strokeW, cap = StrokeCap.Round),
            )
            // Subtle inner dark dial
            drawCircle(
                color = Color(0xFF070F26).copy(alpha = 0.85f),
                radius = radius * 0.84f,
                center = center,
            )
            // Cardinal ticks
            for (angle in listOf(-90.0, 0.0, 90.0, 180.0)) {
                val rad = Math.toRadians(angle)
                val sx = center.x + (radius * 0.72f * kotlin.math.cos(rad)).toFloat()
                val sy = center.y + (radius * 0.72f * kotlin.math.sin(rad)).toFloat()
                val ex = center.x + (radius * 0.86f * kotlin.math.cos(rad)).toFloat()
                val ey = center.y + (radius * 0.86f * kotlin.math.sin(rad)).toFloat()
                drawLine(
                    color = Color(0xFF7DD3FC),
                    start = Offset(sx, sy),
                    end = Offset(ex, ey),
                    strokeWidth = strokeW * 0.55f,
                    cap = StrokeCap.Round,
                )
            }
            // Clock hands behind RR
            drawLine(
                color = Color(0xFF38BDF8).copy(alpha = 0.85f),
                start = center,
                end = Offset(center.x, center.y - radius * 0.52f),
                strokeWidth = strokeW * 0.65f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color(0xFFFBBF24).copy(alpha = 0.90f),
                start = center,
                end = Offset(center.x + radius * 0.44f, center.y - radius * 0.22f),
                strokeWidth = strokeW * 0.50f,
                cap = StrokeCap.Round,
            )
            // Top-right bright node
            drawCircle(
                color = extra.accentPrimary,
                radius = strokeW * 0.55f,
                center = Offset(this.size.width * 0.84f, this.size.height * 0.17f),
            )
        }
        Text(
            text = "RR",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = SpaceGroteskFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = (size.value * 0.34f).let { androidx.compose.ui.unit.TextUnit(it, androidx.compose.ui.unit.TextUnitType.Sp) },
                color = Color.White,
                letterSpacing = androidx.compose.ui.unit.TextUnit(-0.5f, androidx.compose.ui.unit.TextUnitType.Sp),
            ),
        )
    }
}

@Composable
fun RRGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    colors: List<Color> = listOf(Color(0xFF2563EB), Color(0xFF38BDF8)),
    enabled: Boolean = true,
) {
    val alpha = if (enabled) 1f else 0.5f
    Surface(
        shape = CircleShape,
        color = Color.Transparent,
        shadowElevation = if (enabled) 8.dp else 0.dp,
        modifier = modifier
            .height(52.dp)
            .clip(CircleShape)
            .background(Brush.horizontalGradient(colors.map { it.copy(alpha = alpha) }))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
            )
            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Glowing curved atmospheric Earth horizon used in Live Clock and World Clock screens.
 */
@Composable
fun EarthHorizonCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val radius = w * 1.45f
        val center = Offset(w / 2f, h + radius * 0.78f)
        // Outer atmospheric blue halo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x5538BDF8),
                    Color(0x222563EB),
                    Color.Transparent,
                ),
                center = Offset(w / 2f, h * 0.35f),
                radius = w * 0.9f,
            ),
            radius = radius * 1.04f,
            center = center,
        )
        // Planet body
        drawCircle(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0A326E),
                    Color(0xFF041432),
                    Color(0xFF020817),
                ),
            ),
            radius = radius,
            center = center,
        )
        // Atmospheric limb rim glow
        drawCircle(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0x0038BDF8),
                    Color(0xCC60A5FA),
                    Color(0xFF93C5FD),
                    Color(0xCC38BDF8),
                    Color(0x0038BDF8),
                ),
            ),
            radius = radius,
            center = center,
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}

/**
 * Layered twilight alpine sky & mountain backdrop for Weather screen.
 */
@Composable
fun WeatherMountainCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Sky gradient
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF092A5E),
                    Color(0xFF124687),
                    Color(0xFF1D62A6),
                    Color(0xFF0B274C),
                ),
            ),
        )
        // Far ridge
        val farRidge = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, h * 0.62f)
            lineTo(w * 0.22f, h * 0.44f)
            lineTo(w * 0.46f, h * 0.56f)
            lineTo(w * 0.74f, h * 0.40f)
            lineTo(w, h * 0.54f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = farRidge,
            color = Color(0xFF0E376C).copy(alpha = 0.72f),
        )
        // Near alpine ridge
        val nearRidge = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, h * 0.70f)
            lineTo(w * 0.18f, h * 0.55f)
            lineTo(w * 0.40f, h * 0.67f)
            lineTo(w * 0.62f, h * 0.52f)
            lineTo(w * 0.85f, h * 0.64f)
            lineTo(w, h * 0.58f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = nearRidge,
            color = Color(0xFF071E3E).copy(alpha = 0.88f),
        )
        // Calm reflection band
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0x3338BDF8),
                    Color(0xAA06142C),
                ),
                startY = h * 0.72f,
                endY = h,
            ),
            topLeft = Offset(0f, h * 0.72f),
            size = androidx.compose.ui.geometry.Size(w, h * 0.28f),
        )
    }
}

/**
 * Diagnostic heartbeat waveform drawn behind the RR System Health gauge.
 */
@Composable
fun PulseWaveCanvas(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val mid = size.height / 2f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, mid)
            lineTo(w * 0.14f, mid)
            lineTo(w * 0.18f, mid - 18.dp.toPx())
            lineTo(w * 0.22f, mid + 22.dp.toPx())
            lineTo(w * 0.26f, mid - 30.dp.toPx())
            lineTo(w * 0.30f, mid + 14.dp.toPx())
            lineTo(w * 0.34f, mid)
            lineTo(w * 0.66f, mid)
            lineTo(w * 0.70f, mid - 18.dp.toPx())
            lineTo(w * 0.74f, mid + 24.dp.toPx())
            lineTo(w * 0.78f, mid - 26.dp.toPx())
            lineTo(w * 0.82f, mid + 12.dp.toPx())
            lineTo(w * 0.86f, mid)
            lineTo(w, mid)
        }
        drawPath(
            path = path,
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, color.copy(alpha = 0.55f), Color.Transparent),
            ),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

@Composable
fun RRCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color? = null,
    accentGlow: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val extra = LocalRRExtraColors.current
    val resolvedContainer = containerColor ?: extra.glassFill
    val borderStroke = if (accentGlow) {
        BorderStroke(
            width = 1.25.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    extra.accentPrimary.copy(alpha = 0.65f),
                    extra.accentSecondary.copy(alpha = 0.52f),
                ),
            ),
        )
    } else {
        BorderStroke(1.dp, extra.cardBorder)
    }
    val cardModifier = if (onClick != null) {
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
    } else {
        modifier.fillMaxWidth()
    }
    Card(
        modifier = cardModifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = resolvedContainer),
        border = borderStroke,
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (extra.isDark) 0.dp else 2.dp,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content,
        )
    }
}

@Composable
fun RRCircularProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 84.dp,
    strokeWidth: Dp = 8.dp,
    primaryColor: Color = LocalRRExtraColors.current.accentPrimary,
    secondaryColor: Color = LocalRRExtraColors.current.accentSecondary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    centerContent: @Composable BoxScope.() -> Unit,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 450),
        label = "ringProgress",
    )
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
            if (animatedProgress > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(primaryColor, secondaryColor, primaryColor),
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
        }
        centerContent()
    }
}

@Composable
fun RRSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (actionLabel != null && onActionClick != null) {
            TextButton(onClick = onActionClick) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun RRStatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(0.75.dp, color.copy(alpha = 0.38f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(13.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun RREmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    RRCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)),
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null && onActionClick != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(onClick = onActionClick) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun RRSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    testTag: String = "search_input",
) {
    val extra = LocalRRExtraColors.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        placeholder = { Text(placeholder) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "Search",
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Clear search",
                    )
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedContainerColor = extra.glassFill,
            unfocusedContainerColor = extra.glassFill,
            unfocusedBorderColor = extra.cardBorder,
        ),
    )
}

@Composable
fun RRConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (isDestructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun RRMetricBadge(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 10.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = color,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
