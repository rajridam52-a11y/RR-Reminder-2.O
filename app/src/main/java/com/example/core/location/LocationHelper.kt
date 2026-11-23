package com.example.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
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
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class SimpleLocation(
    val latitude: Double,
    val longitude: Double,
    val displayName: String,
)

class LocationHelper(private val context: Context) {

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): SimpleLocation? {
        if (!PermissionHelper.hasCoarseLocationPermission(context)) return null

        val fusedLoc = runCatching {
            val client = LocationServices.getFusedLocationProviderClient(context)
            suspendCancellableCoroutine<Location?> { cont ->
                val cts = CancellationTokenSource()
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                    .addOnSuccessListener { loc ->
                        if (cont.isActive) cont.resume(loc)
                    }
                    .addOnFailureListener {
                        if (cont.isActive) cont.resume(null)
                    }
                cont.invokeOnCancellation { cts.cancel() }
            }
        }.getOrNull()

        val loc = fusedLoc ?: runCatching {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        }.getOrNull()

        if (loc == null) return null

        val city = reverseGeocodeCity(loc.latitude, loc.longitude) ?: "Current Location"
        return SimpleLocation(loc.latitude, loc.longitude, city)
    }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocodeCity(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        runCatching {
            if (!Geocoder.isPresent()) return@runCatching null
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            val addr = addresses?.firstOrNull()
            addr?.locality ?: addr?.subAdminArea ?: addr?.adminArea
        }.getOrNull()
    }

    fun scheduleBatteryConsciousLocationCheck(enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled || !PermissionHelper.hasCoarseLocationPermission(context)) {
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
        if (!prefs.locationRemindersEnabled || !PermissionHelper.hasCoarseLocationPermission(applicationContext)) {
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
