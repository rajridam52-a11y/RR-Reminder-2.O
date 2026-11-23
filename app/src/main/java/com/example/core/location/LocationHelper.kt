package com.example.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.core.database.RRDatabase
import com.example.core.datastore.UserPreferencesRepository
import com.example.core.notifications.RRChannels
import com.example.core.notifications.RRNotificationManager
import com.example.core.permissions.PermissionHelper
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class SimpleLocation(
    val latitude: Double,
    val longitude: Double,
    val displayName: String,
    val accuracyMeters: Float? = null,
    val provider: String? = null,
)

sealed class LocationFetchResult {
    data class Success(val location: SimpleLocation) : LocationFetchResult()
    data object PermissionDenied : LocationFetchResult()
    data object GpsDisabled : LocationFetchResult()
    data class Unavailable(val reason: String) : LocationFetchResult()
}

class LocationHelper(private val context: Context) {

    fun hasLocationPermission(): Boolean = PermissionHelper.hasAnyLocationPermission(context)

    fun isGpsEnabled(): Boolean = PermissionHelper.isLocationServicesEnabled(context)

    suspend fun getCurrentLocation(): SimpleLocation? {
        return when (val result = getCurrentLocationDetailed()) {
            is LocationFetchResult.Success -> result.location
            else -> null
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocationDetailed(): LocationFetchResult {
        if (!PermissionHelper.hasAnyLocationPermission(context)) {
            return LocationFetchResult.PermissionDenied
        }
        if (!PermissionHelper.isLocationServicesEnabled(context)) {
            return LocationFetchResult.GpsDisabled
        }

        val hasFine = PermissionHelper.hasFineLocationPermission(context)
        val priority = if (hasFine) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        // 1. Try Google Play Services FusedLocationProvider with bounded timeout
        val fusedLoc = withTimeoutOrNull(7_000L) {
            runCatching {
                val client = LocationServices.getFusedLocationProviderClient(context)
                suspendCancellableCoroutine<Location?> { cont ->
                    val cts = CancellationTokenSource()
                    client.getCurrentLocation(priority, cts.token)
                        .addOnSuccessListener { loc ->
                            if (cont.isActive) cont.resume(loc)
                        }
                        .addOnFailureListener {
                            if (cont.isActive) cont.resume(null)
                        }
                    cont.invokeOnCancellation { cts.cancel() }
                }
            }.getOrNull()
        }

        // 2. Fallback to native Android LocationManager (GPS_PROVIDER then NETWORK_PROVIDER)
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val nativeCurrentLoc = if (fusedLoc == null && lm != null) {
            val gpsLoc = if (hasFine && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                requestSingleNativeLocation(lm, LocationManager.GPS_PROVIDER, 7_000L)
            } else {
                null
            }
            gpsLoc ?: if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                requestSingleNativeLocation(lm, LocationManager.NETWORK_PROVIDER, 4_500L)
            } else {
                null
            }
        } else {
            null
        }

        // 3. Final fallback to freshest available last-known location across providers
        val bestLastKnown = if (fusedLoc == null && nativeCurrentLoc == null && lm != null) {
            listOfNotNull(
                if (hasFine) runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull() else null,
                runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    runCatching { lm.getLastKnownLocation(LocationManager.FUSED_PROVIDER) }.getOrNull()
                } else {
                    null
                },
                runCatching { lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER) }.getOrNull(),
            ).maxByOrNull { it.time }
        } else {
            null
        }

        val resolved = fusedLoc ?: nativeCurrentLoc ?: bestLastKnown
            ?: return LocationFetchResult.Unavailable(
                "Waiting for GPS signal. Move near an open window or search your city manually.",
            )

        val city = reverseGeocodeCity(resolved.latitude, resolved.longitude)
            ?: String.format(Locale.US, "GPS (%.2f, %.2f)", resolved.latitude, resolved.longitude)

        return LocationFetchResult.Success(
            SimpleLocation(
                latitude = resolved.latitude,
                longitude = resolved.longitude,
                displayName = city,
                accuracyMeters = if (resolved.hasAccuracy()) resolved.accuracy else null,
                provider = resolved.provider,
            ),
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSingleNativeLocation(
        lm: LocationManager,
        provider: String,
        timeoutMs: Long,
    ): Location? = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            val signal = android.os.CancellationSignal()
            val executor = ContextCompat.getMainExecutor(context)
            runCatching {
                LocationManagerCompat.getCurrentLocation(
                    lm,
                    provider,
                    signal,
                    executor,
                ) { loc ->
                    if (cont.isActive) cont.resume(loc)
                }
            }.onFailure {
                if (cont.isActive) cont.resume(null)
            }
            cont.invokeOnCancellation { signal.cancel() }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocodeCity(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        runCatching {
            if (!Geocoder.isPresent()) return@runCatching null
            val geocoder = Geocoder(context, Locale.getDefault())
            val addr: Address? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                withTimeoutOrNull(3_000L) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(
                            lat,
                            lon,
                            1,
                            object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<Address>) {
                                    if (cont.isActive) cont.resume(addresses.firstOrNull())
                                }

                                override fun onError(errorMessage: String?) {
                                    if (cont.isActive) cont.resume(null)
                                }
                            },
                        )
                    }
                }
            } else {
                geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()
            }
            addr?.locality ?: addr?.subAdminArea ?: addr?.adminArea ?: addr?.countryName
        }.getOrNull()
    }

    fun scheduleBatteryConsciousLocationCheck(enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled || !PermissionHelper.hasAnyLocationPermission(context)) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<LocationReminderWorker>(15, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        private const val WORK_NAME = "rr_location_reminder_check"
    }
}

class LocationReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = UserPreferencesRepository(applicationContext).getSnapshot()
        if (!prefs.locationRemindersEnabled || !PermissionHelper.hasAnyLocationPermission(applicationContext)) {
            return Result.success()
        }

        val db = RRDatabase.getInstance(applicationContext)
        val locationReminders = db.reminderDao().getAllActiveSync()
            .filter { !it.isCompleted && it.locationTriggerType != "NONE" && it.locationLat != null && it.locationLon != null }

        if (locationReminders.isEmpty()) return Result.success()

        val current = LocationHelper(applicationContext).getCurrentLocation() ?: return Result.retry()
        val notifManager = RRNotificationManager(applicationContext, db.notificationLogDao())

        locationReminders.forEach { reminder ->
            val results = FloatArray(1)
            Location.distanceBetween(
                current.latitude,
                current.longitude,
                reminder.locationLat ?: 0.0,
                reminder.locationLon ?: 0.0,
                results,
            )
            val distMeters = results[0]
            val inside = distMeters <= reminder.locationRadiusMeters
            val shouldNotify = when (reminder.locationTriggerType) {
                "ARRIVE" -> inside
                "DEPART" -> !inside
                else -> false
            }
            if (shouldNotify) {
                notifManager.postSimpleNotification(
                    id = (80_000 + reminder.id).toInt(),
                    channelId = RRChannels.SYSTEM,
                    title = "Location Reminder: ${reminder.title}",
                    body = "${reminder.locationTriggerType.lowercase().replaceFirstChar { it.uppercase() }} ${reminder.locationName ?: "saved location"}",
                    sourceType = "LOCATION",
                    sourceId = reminder.id,
                )
            }
        }
        return Result.success()
    }
}
