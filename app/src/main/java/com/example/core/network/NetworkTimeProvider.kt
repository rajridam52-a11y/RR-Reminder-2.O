package com.example.core.network

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class NetworkTimeSyncStatus(
    val isSynced: Boolean = false,
    val isSyncing: Boolean = false,
    val sourceLabel: String = "Device Clock",
    val lastSyncedAtMillis: Long? = null,
    val errorMessage: String? = null,
    val ownerOverrideActive: Boolean = false,
    val ownerOverrideLabel: String? = null,
)

data class OwnerTimeOverride(
    val enabled: Boolean = false,
    val mode: String = "GOOGLE_AUTO",
    val offsetMinutes: Long = 0L,
    val fixedTargetEpochMillis: Long = 0L,
    val fixedAnchorEpochMillis: Long = 0L,
)

class NetworkTimeProvider(
    private val okHttpClient: OkHttpClient,
    private val elapsedMsProvider: () -> Long = {
        runCatching { SystemClock.elapsedRealtime() }.getOrElse { System.nanoTime() / 1_000_000L }
    },
    private val systemNowMsProvider: () -> Long = { System.currentTimeMillis() },
) {

    @Volatile
    private var syncedServerEpochMs: Long? = null

    @Volatile
    private var syncedAnchorElapsedMs: Long = 0L

    @Volatile
    private var ownerOverride: OwnerTimeOverride = OwnerTimeOverride()

    @Volatile
    private var overrideAppliedElapsedMs: Long = elapsedMsProvider()

    private val _syncStatus = MutableStateFlow(NetworkTimeSyncStatus())
    val syncStatus: StateFlow<NetworkTimeSyncStatus> = _syncStatus.asStateFlow()

    fun baseNetworkEpochMillis(): Long {
        val anchorServer = syncedServerEpochMs ?: return systemNowMsProvider()
        val delta = (elapsedMsProvider() - syncedAnchorElapsedMs).coerceAtLeast(0L)
        return anchorServer + delta
    }

    fun currentEpochMillis(): Long {
        val base = baseNetworkEpochMillis()
        val ov = ownerOverride
        if (!ov.enabled) return base
        return when (ov.mode) {
            "OFFSET" -> base + (ov.offsetMinutes.coerceIn(-1440L, 1440L) * 60_000L)
            "FIXED" -> {
                if (ov.fixedTargetEpochMillis > 0L && ov.fixedAnchorEpochMillis > 0L) {
                    val baseElapsed = (base - ov.fixedAnchorEpochMillis).coerceAtLeast(0L)
                    val monoElapsed = (elapsedMsProvider() - overrideAppliedElapsedMs).coerceAtLeast(0L)
                    ov.fixedTargetEpochMillis + maxOf(baseElapsed, monoElapsed)
                } else {
                    base
                }
            }
            else -> base
        }
    }

    fun hasNetworkAnchor(): Boolean = syncedServerEpochMs != null

    fun driftFromDeviceMillis(): Long = baseNetworkEpochMillis() - systemNowMsProvider()

    fun setOwnerTimeOverride(override: OwnerTimeOverride) {
        overrideAppliedElapsedMs = elapsedMsProvider()
        ownerOverride = override
        val label = if (!override.enabled || override.mode == "GOOGLE_AUTO") {
            null
        } else if (override.mode == "OFFSET") {
            val sign = if (override.offsetMinutes >= 0) "+" else ""
            "Owner Shift ($sign${override.offsetMinutes}m)"
        } else {
            "Owner Custom Time"
        }
        _syncStatus.value = _syncStatus.value.copy(
            ownerOverrideActive = override.enabled && override.mode != "GOOGLE_AUTO",
            ownerOverrideLabel = label,
        )
    }

    fun clearOwnerTimeOverride() {
        setOwnerTimeOverride(OwnerTimeOverride())
    }

    suspend fun syncNow(): NetworkTimeSyncStatus {
        _syncStatus.value = _syncStatus.value.copy(isSyncing = true, errorMessage = null)
        return withContext(Dispatchers.IO) {
            val endpoints = listOf(
                "https://www.google.com" to "Google Time",
                "https://clients3.google.com/generate_204" to "Google Time",
                "https://api.open-meteo.com/v1/forecast?latitude=28.6139&longitude=77.2090&current=temperature_2m" to "Open-Meteo Time",
            )
            var lastError: String? = null
            for ((url, label) in endpoints) {
                val attempt = runCatching {
                    val startElapsed = elapsedMsProvider()
                    val req = Request.Builder()
                        .url(url)
                        .head()
                        .header("Cache-Control", "no-cache, no-store")
                        .build()
                    okHttpClient.newCall(req).execute().use { resp ->
                        val endElapsed = elapsedMsProvider()
                        val dateHeader = resp.header("Date")
                        val parsedEpoch = parseHttpDateHeader(dateHeader)
                            ?: throw IllegalStateException("Missing Date header from $label")
                        val halfRtt = (endElapsed - startElapsed).coerceAtLeast(0L) / 2L
                        val exactServerMs = parsedEpoch + halfRtt
                        Triple(exactServerMs, endElapsed, label)
                    }
                }
                if (attempt.isSuccess) {
                    val (serverMs, anchorElapsed, labelOut) = attempt.getOrThrow()
                    syncedServerEpochMs = serverMs
                    syncedAnchorElapsedMs = anchorElapsed
                    val current = _syncStatus.value
                    val status = current.copy(
                        isSynced = true,
                        isSyncing = false,
                        sourceLabel = labelOut,
                        lastSyncedAtMillis = serverMs,
                        errorMessage = null,
                    )
                    _syncStatus.value = status
                    return@withContext status
                } else {
                    lastError = attempt.exceptionOrNull()?.localizedMessage
                }
            }

            val fallbackStatus = _syncStatus.value.copy(
                isSyncing = false,
                errorMessage = lastError ?: "Offline — using device clock",
            )
            _syncStatus.value = fallbackStatus
            fallbackStatus
        }
    }

    companion object {
        fun parseHttpDateHeader(rawDate: String?): Long? {
            if (rawDate.isNullOrBlank()) return null
            return runCatching {
                ZonedDateTime.parse(rawDate.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant()
                    .toEpochMilli()
            }.getOrNull() ?: runCatching {
                val formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH)
                ZonedDateTime.parse(rawDate.trim(), formatter).toInstant().toEpochMilli()
            }.getOrNull()
        }
    }
}
