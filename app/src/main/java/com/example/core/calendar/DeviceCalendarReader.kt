package com.example.core.calendar

import android.content.Context
import android.provider.CalendarContract
import com.example.core.permissions.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeviceCalendarEvent(
    val id: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val location: String?,
)

class DeviceCalendarReader(private val context: Context) {

    suspend fun getEventsInRange(startMillis: Long, endMillis: Long): List<DeviceCalendarEvent> =
        withContext(Dispatchers.IO) {
            if (!PermissionHelper.hasCalendarReadPermission(context)) {
                return@withContext emptyList()
            }
            val events = mutableListOf<DeviceCalendarEvent>()
            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.EVENT_LOCATION,
            )
            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            android.content.ContentUris.appendId(builder, startMillis)
            android.content.ContentUris.appendId(builder, endMillis)

            runCatching {
                context.contentResolver.query(
                    builder.build(),
                    projection,
                    null,
                    null,
                    "${CalendarContract.Instances.BEGIN} ASC",
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                    val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                    val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                    val endIdx = cursor.getColumnIndex(CalendarContract.Instances.END)
                    val locIdx = cursor.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                    while (cursor.moveToNext() && events.size < 100) {
                        val title = cursor.getString(titleIdx) ?: continue
                        events.add(
                            DeviceCalendarEvent(
                                id = cursor.getLong(idIdx),
                                title = title,
                                startMillis = cursor.getLong(beginIdx),
                                endMillis = cursor.getLong(endIdx),
                                location = cursor.getString(locIdx),
                            ),
                        )
                    }
                }
            }
            events
        }
}
