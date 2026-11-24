package com.example.core.firebase

import androidx.annotation.Keep
import com.example.core.datastore.DateFormatStyle
import com.example.core.datastore.PrimaryClockZone
import com.example.core.datastore.TimeFormatMode
import com.example.core.datastore.UserPreferences
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.ThemeMode
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

const val OWNER_ADMIN_EMAIL = "rajridam92@gmail.com"
const val APP_CONFIG_COLLECTION = "app_config"
const val GLOBAL_CONFIG_DOC_ID = "global"
const val GLOBAL_CONFIG_PATH = "$APP_CONFIG_COLLECTION/$GLOBAL_CONFIG_DOC_ID"

enum class OwnerTimeMode(val key: String, val label: String) {
    GOOGLE_AUTO("GOOGLE_AUTO", "Google Live Time"),
    OFFSET("OFFSET", "Time Offset"),
    FIXED("FIXED", "Custom Date & Time");

    companion object {
        fun fromKey(raw: String?): OwnerTimeMode =
            entries.firstOrNull { it.key == raw } ?: GOOGLE_AUTO
    }
}

fun isOwnerUser(email: String?, isEmailVerified: Boolean): Boolean =
    isEmailVerified && email?.trim()?.equals(OWNER_ADMIN_EMAIL, ignoreCase = true) == true

@Keep
@IgnoreExtraProperties
data class GlobalAppConfig(
    @DocumentId val id: String = "",
    val timeMode: String = OwnerTimeMode.GOOGLE_AUTO.key,
    val timeOffsetMinutes: Long = 0L,
    val fixedTargetEpochMillis: Long = 0L,
    val fixedAnchorEpochMillis: Long = 0L,
    val syncOverrideEnabled: Boolean = false,
    val primaryClockZone: String = "",
    val timeFormatMode: String = "",
    val dateFormatStyle: String = "",
    val themeMode: String = "",
    val accentPreset: String = "",
    val weatherTempUnit: String = "",
    val weatherWindUnit: String = "",
    val bannerEnabled: Boolean = false,
    val bannerMessage: String = "",
    val updatedByEmail: String = OWNER_ADMIN_EMAIL,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun validatedForSave(): GlobalAppConfig {
        val safeMode = OwnerTimeMode.fromKey(timeMode).key
        val safeOffset = timeOffsetMinutes.coerceIn(-1440L, 1440L)
        val safeFixedTarget = fixedTargetEpochMillis.coerceAtLeast(0L)
        val safeFixedAnchor = fixedAnchorEpochMillis.coerceAtLeast(0L)
        val safeZone = primaryClockZone.takeIf { it in setOf("", "india_ist", "device") } ?: ""
        val safeTimeFmt = timeFormatMode.takeIf { it in setOf("", "system", "12h", "24h") } ?: ""
        val safeDateFmt = dateFormatStyle.takeIf { it in setOf("", "medium", "day_month", "iso", "slash_us") } ?: ""
        val safeTheme = themeMode.takeIf { it in setOf("", "system", "dark", "light") } ?: ""
        val safeAccent = accentPreset.takeIf { it in setOf("", "aurora", "emerald", "cobalt", "violet", "amber", "rose") } ?: ""
        val safeTempUnit = weatherTempUnit.takeIf { it in setOf("", "celsius", "fahrenheit") } ?: ""
        val safeWindUnit = weatherWindUnit.takeIf { it in setOf("", "kmh", "mph") } ?: ""
        val safeBanner = bannerMessage.trim().take(240)
        return copy(
            id = "",
            timeMode = safeMode,
            timeOffsetMinutes = safeOffset,
            fixedTargetEpochMillis = safeFixedTarget,
            fixedAnchorEpochMillis = safeFixedAnchor,
            primaryClockZone = safeZone,
            timeFormatMode = safeTimeFmt,
            dateFormatStyle = safeDateFmt,
            themeMode = safeTheme,
            accentPreset = safeAccent,
            weatherTempUnit = safeTempUnit,
            weatherWindUnit = safeWindUnit,
            bannerMessage = safeBanner,
            updatedByEmail = OWNER_ADMIN_EMAIL,
        )
    }

    fun toMutableFieldMap(updatedAtValue: Any): Map<String, Any> = mapOf(
        "timeMode" to timeMode,
        "timeOffsetMinutes" to timeOffsetMinutes,
        "fixedTargetEpochMillis" to fixedTargetEpochMillis,
        "fixedAnchorEpochMillis" to fixedAnchorEpochMillis,
        "syncOverrideEnabled" to syncOverrideEnabled,
        "primaryClockZone" to primaryClockZone,
        "timeFormatMode" to timeFormatMode,
        "dateFormatStyle" to dateFormatStyle,
        "themeMode" to themeMode,
        "accentPreset" to accentPreset,
        "weatherTempUnit" to weatherTempUnit,
        "weatherWindUnit" to weatherWindUnit,
        "bannerEnabled" to bannerEnabled,
        "bannerMessage" to bannerMessage,
        "updatedByEmail" to OWNER_ADMIN_EMAIL,
        "updatedAt" to updatedAtValue,
    )

    fun toCreateFieldMap(createdAtValue: Any, updatedAtValue: Any): Map<String, Any> =
        toMutableFieldMap(updatedAtValue) + mapOf("createdAt" to createdAtValue)
}

fun resolveEffectivePreferences(
    local: UserPreferences,
    remote: GlobalAppConfig?,
    isSystem24Hour: Boolean = false,
): UserPreferences {
    if (remote == null || !remote.syncOverrideEnabled) return local

    val resolvedTheme = if (remote.themeMode.isNotBlank()) ThemeMode.fromKey(remote.themeMode) else local.themeMode
    val resolvedAccent = if (remote.accentPreset.isNotBlank()) AccentPreset.fromKey(remote.accentPreset) else local.accentPreset
    val resolvedZone = if (remote.primaryClockZone.isNotBlank()) PrimaryClockZone.fromKey(remote.primaryClockZone) else local.primaryClockZone
    val resolvedTimeMode = if (remote.timeFormatMode.isNotBlank()) TimeFormatMode.fromKey(remote.timeFormatMode) else local.timeFormatMode
    val resolved24Hour = when (resolvedTimeMode) {
        TimeFormatMode.SYSTEM -> if (remote.timeFormatMode.isNotBlank()) isSystem24Hour else local.use24Hour
        TimeFormatMode.H12 -> false
        TimeFormatMode.H24 -> true
    }
    val resolvedDate = if (remote.dateFormatStyle.isNotBlank()) DateFormatStyle.fromKey(remote.dateFormatStyle) else local.dateFormatStyle
    val resolvedFahrenheit = when (remote.weatherTempUnit) {
        "fahrenheit" -> true
        "celsius" -> false
        else -> local.useFahrenheit
    }
    val resolvedMph = when (remote.weatherWindUnit) {
        "mph" -> true
        "kmh" -> false
        else -> local.useMph
    }

    return local.copy(
        themeMode = resolvedTheme,
        accentPreset = resolvedAccent,
        primaryClockZone = resolvedZone,
        timeFormatMode = resolvedTimeMode,
        use24Hour = resolved24Hour,
        dateFormatStyle = resolvedDate,
        useFahrenheit = resolvedFahrenheit,
        useMph = resolvedMph,
    )
}
