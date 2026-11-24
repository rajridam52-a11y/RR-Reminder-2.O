package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Aurora UI 2.0 Dark Mode — Deep Midnight Navy & Obsidian Void
val NavyVoid = Color(0xFF040814)
val NavyDeep = Color(0xFF081024)
val NavySurface = Color(0xFF0D1730)
val NavySurfaceElevated = Color(0xFF142244)
val NavySurfaceHighest = Color(0xFF1B2E58)
val NavyOutline = Color(0xFF284173)
val DarkTextPrimary = Color(0xFFF3F8FF)
val DarkTextSecondary = Color(0xFF9EB3D8)

// Aurora UI 2.0 Light Mode — Clean Luminous Frost & Crisp Slate
val LightBackground = Color(0xFFF3F7FB)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE9F0F8)
val LightSurfaceHighest = Color(0xFFDDE7F3)
val LightOutline = Color(0xFFC5D5E8)
val LightTextPrimary = Color(0xFF0A1429)
val LightTextSecondary = Color(0xFF475A7D)

// Aurora Signature Accents
val AuroraEmerald = Color(0xFF10B981)
val AuroraEmeraldBright = Color(0xFF34D399)
val AuroraCyan = Color(0xFF06B6D4)
val AuroraCyanBright = Color(0xFF22D3EE)

// Semantic Status Colors
val SemanticSuccessDark = Color(0xFF34D399)
val SemanticSuccessLight = Color(0xFF059669)
val SemanticWarningDark = Color(0xFFFBBF24)
val SemanticWarningLight = Color(0xFFD97706)
val SemanticErrorDark = Color(0xFFFB7185)
val SemanticErrorLight = Color(0xFFE11D48)
val SemanticInfoDark = Color(0xFF22D3EE)
val SemanticInfoLight = Color(0xFF0284C7)

enum class AccentPreset(
    val key: String,
    val label: String,
    val primaryDark: Color,
    val secondaryDark: Color,
    val primaryLight: Color,
    val secondaryLight: Color,
) {
    AURORA(
        key = "aurora",
        label = "Aurora Cyan",
        primaryDark = Color(0xFF22D3EE),
        secondaryDark = Color(0xFF34D399),
        primaryLight = Color(0xFF0891B2),
        secondaryLight = Color(0xFF059669),
    ),
    EMERALD(
        key = "emerald",
        label = "Aurora Mint",
        primaryDark = Color(0xFF34D399),
        secondaryDark = Color(0xFF22D3EE),
        primaryLight = Color(0xFF059669),
        secondaryLight = Color(0xFF0284C7),
    ),
    COBALT(
        key = "cobalt",
        label = "Electric Cobalt",
        primaryDark = Color(0xFF4F8BFF),
        secondaryDark = Color(0xFF22D3EE),
        primaryLight = Color(0xFF2563EB),
        secondaryLight = Color(0xFF0891B2),
    ),
    VIOLET(
        key = "violet",
        label = "Amethyst Glow",
        primaryDark = Color(0xFFA78BFA),
        secondaryDark = Color(0xFF22D3EE),
        primaryLight = Color(0xFF7C3AED),
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
            entries.firstOrNull { it.key == key } ?: AURORA
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
val Purple80 = AuroraCyanBright
val PurpleGrey80 = DarkTextSecondary
val Pink80 = AuroraEmeraldBright
val Purple40 = Color(0xFF0891B2)
val PurpleGrey40 = LightTextSecondary
val Pink40 = Color(0xFF059669)
