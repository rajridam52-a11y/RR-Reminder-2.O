package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Dark Mode Deep Navy & Rich Obsidian Surfaces
val NavyVoid = Color(0xFF060A17)
val NavyDeep = Color(0xFF0B122C)
val NavySurface = Color(0xFF101936)
val NavySurfaceElevated = Color(0xFF172245)
val NavySurfaceHighest = Color(0xFF1F2D58)
val NavyOutline = Color(0xFF2B3D70)
val DarkTextPrimary = Color(0xFFF3F6FF)
val DarkTextSecondary = Color(0xFFA9B6D9)

// Light Mode Clean Crisp & Soft Slate Surfaces
val LightBackground = Color(0xFFF6F8FC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFECEFF7)
val LightSurfaceHighest = Color(0xFFE2E7F4)
val LightOutline = Color(0xFFD0D8EC)
val LightTextPrimary = Color(0xFF0E162E)
val LightTextSecondary = Color(0xFF4C5B7D)

// Semantic Status Colors
val SemanticSuccessDark = Color(0xFF34D399)
val SemanticSuccessLight = Color(0xFF059669)
val SemanticWarningDark = Color(0xFFFBBF24)
val SemanticWarningLight = Color(0xFFD97706)
val SemanticErrorDark = Color(0xFFFB7185)
val SemanticErrorLight = Color(0xFFE11D48)
val SemanticInfoDark = Color(0xFF38BDF8)
val SemanticInfoLight = Color(0xFF0284C7)

enum class AccentPreset(
    val key: String,
    val label: String,
    val primaryDark: Color,
    val secondaryDark: Color,
    val primaryLight: Color,
    val secondaryLight: Color,
) {
    COBALT(
        key = "cobalt",
        label = "Electric Cobalt",
        primaryDark = Color(0xFF4F8BFF),
        secondaryDark = Color(0xFF9D7BFF),
        primaryLight = Color(0xFF2563EB),
        secondaryLight = Color(0xFF7C3AED),
    ),
    VIOLET(
        key = "violet",
        label = "Amethyst Glow",
        primaryDark = Color(0xFFA78BFA),
        secondaryDark = Color(0xFF38BDF8),
        primaryLight = Color(0xFF7C3AED),
        secondaryLight = Color(0xFF0284C7),
    ),
    EMERALD(
        key = "emerald",
        label = "Aurora Mint",
        primaryDark = Color(0xFF34D399),
        secondaryDark = Color(0xFF38BDF8),
        primaryLight = Color(0xFF059669),
        secondaryLight = Color(0xFF0284C7),
    ),
    AMBER(
        key = "amber",
        label = "Solar Amber",
        primaryDark = Color(0xFFFBBF24),
        secondaryDark = Color(0xFFFB7185),
        primaryLight = Color(0xFFD97706),
        secondaryLight = Color(0xFFE11D48),
    ),
    ROSE(
        key = "rose",
        label = "Crimson Pulse",
        primaryDark = Color(0xFFFB7185),
        secondaryDark = Color(0xFFA78BFA),
        primaryLight = Color(0xFFE11D48),
        secondaryLight = Color(0xFF7C3AED),
    );

    companion object {
        fun fromKey(key: String?): AccentPreset =
            entries.firstOrNull { it.key == key } ?: COBALT
    }
}

enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "System Default"),
    DARK("dark", "Dark Mode"),
    LIGHT("light", "Light Mode");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

// Legacy compatibility constants if referenced
val Purple80 = AccentPreset.COBALT.primaryDark
val PurpleGrey80 = DarkTextSecondary
val Pink80 = AccentPreset.COBALT.secondaryDark
val Purple40 = AccentPreset.COBALT.primaryLight
val PurpleGrey40 = LightTextSecondary
val Pink40 = AccentPreset.COBALT.secondaryLight
