package com.example.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

@Immutable
data class RRExtraColors(
    val isDark: Boolean,
    val success: Color,
    val warning: Color,
    val info: Color,
    val accentPrimary: Color,
    val accentSecondary: Color,
    val glowBrushStart: Color,
    val glowBrushEnd: Color,
    val cardBorder: Color,
)

val LocalRRExtraColors = staticCompositionLocalOf {
    RRExtraColors(
        isDark = true,
        success = SemanticSuccessDark,
        warning = SemanticWarningDark,
        info = SemanticInfoDark,
        accentPrimary = AccentPreset.COBALT.primaryDark,
        accentSecondary = AccentPreset.COBALT.secondaryDark,
        glowBrushStart = AccentPreset.COBALT.primaryDark.copy(alpha = 0.22f),
        glowBrushEnd = AccentPreset.COBALT.secondaryDark.copy(alpha = 0.14f),
        cardBorder = NavyOutline,
    )
}

val RRShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun RRReminderTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentPreset: AccentPreset = AccentPreset.COBALT,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val targetPrimary = if (useDark) accentPreset.primaryDark else accentPreset.primaryLight
    val targetSecondary = if (useDark) accentPreset.secondaryDark else accentPreset.secondaryLight

    val animSpec = tween<Color>(durationMillis = 280)
    val primary = animateColorAsState(targetPrimary, animSpec, label = "primary").value
    val secondary = animateColorAsState(targetSecondary, animSpec, label = "secondary").value
    val background = animateColorAsState(if (useDark) NavyVoid else LightBackground, animSpec, label = "bg").value
    val surface = animateColorAsState(if (useDark) NavySurface else LightSurface, animSpec, label = "surface").value
    val surfaceVariant = animateColorAsState(if (useDark) NavySurfaceElevated else LightSurfaceVariant, animSpec, label = "sv").value
    val surfaceContainerHigh = animateColorAsState(if (useDark) NavySurfaceHighest else LightSurfaceHighest, animSpec, label = "sch").value

    val colorScheme: ColorScheme = if (useDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = Color(0xFF041029),
            primaryContainer = primary.copy(alpha = 0.20f),
            onPrimaryContainer = primary.copy(alpha = 0.95f),
            secondary = secondary,
            onSecondary = Color(0xFF0D0824),
            secondaryContainer = secondary.copy(alpha = 0.18f),
            onSecondaryContainer = secondary,
            tertiary = SemanticInfoDark,
            background = background,
            onBackground = DarkTextPrimary,
            surface = surface,
            onSurface = DarkTextPrimary,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = DarkTextSecondary,
            surfaceContainerLow = NavyDeep,
            surfaceContainer = surface,
            surfaceContainerHigh = surfaceContainerHigh,
            outline = NavyOutline,
            outlineVariant = NavyOutline.copy(alpha = 0.55f),
            error = SemanticErrorDark,
            onError = Color(0xFF2B040E),
            errorContainer = SemanticErrorDark.copy(alpha = 0.18f),
            onErrorContainer = SemanticErrorDark,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primary.copy(alpha = 0.13f),
            onPrimaryContainer = primary,
            secondary = secondary,
            onSecondary = Color.White,
            secondaryContainer = secondary.copy(alpha = 0.12f),
            onSecondaryContainer = secondary,
            tertiary = SemanticInfoLight,
            background = background,
            onBackground = LightTextPrimary,
            surface = surface,
            onSurface = LightTextPrimary,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = LightTextSecondary,
            surfaceContainerLow = LightBackground,
            surfaceContainer = surface,
            surfaceContainerHigh = surfaceContainerHigh,
            outline = LightOutline,
            outlineVariant = LightOutline.copy(alpha = 0.65f),
            error = SemanticErrorLight,
            onError = Color.White,
            errorContainer = SemanticErrorLight.copy(alpha = 0.12f),
            onErrorContainer = SemanticErrorLight,
        )
    }

    val extraColors = RRExtraColors(
        isDark = useDark,
        success = if (useDark) SemanticSuccessDark else SemanticSuccessLight,
        warning = if (useDark) SemanticWarningDark else SemanticWarningLight,
        info = if (useDark) SemanticInfoDark else SemanticInfoLight,
        accentPrimary = primary,
        accentSecondary = secondary,
        glowBrushStart = primary.copy(alpha = if (useDark) 0.24f else 0.12f),
        glowBrushEnd = secondary.copy(alpha = if (useDark) 0.16f else 0.08f),
        cardBorder = if (useDark) NavyOutline.copy(alpha = 0.65f) else LightOutline.copy(alpha = 0.75f),
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !useDark
                isAppearanceLightNavigationBars = !useDark
            }
        }
    }

    CompositionLocalProvider(LocalRRExtraColors provides extraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = RRShapes,
            content = content,
        )
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    RRReminderTheme(
        themeMode = if (darkTheme) ThemeMode.DARK else ThemeMode.LIGHT,
        accentPreset = AccentPreset.COBALT,
        content = content,
    )
}
