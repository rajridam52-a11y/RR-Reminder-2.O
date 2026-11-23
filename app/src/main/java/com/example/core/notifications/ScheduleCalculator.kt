package com.example.core.notifications

import com.example.core.database.AlarmEntity
import com.example.core.database.ReminderEntity
import com.example.core.database.RepeatType
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object ScheduleCalculator {

    /**
     * Computes the next trigger timestamp in epoch millis for a recurring reminder after [afterMillis],
     * respecting time-zone rules and Daylight Saving Time transitions via [ZonedDateTime].
     */
    fun computeNextReminderTrigger(
        reminder: ReminderEntity,
        afterMillis: Long = System.currentTimeMillis(),
        targetZone: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        val repeat = RepeatType.fromName(reminder.repeatType)
        if (repeat == RepeatType.NONE) return null

        val interval = reminder.repeatIntervalValue.coerceAtLeast(1).toLong()
        val origZone = runCatching { ZoneId.of(reminder.zoneId) }.getOrDefault(targetZone)
        var candidate = Instant.ofEpochMilli(reminder.triggerAtMillis)
            .atZone(origZone)
            .withZoneSameInstant(targetZone)

        val cutoff = Instant.ofEpochMilli(afterMillis).atZone(targetZone)

        var safety = 0
        while (!candidate.isAfter(cutoff) && safety < 5000) {
            safety++
            candidate = when (repeat) {
                RepeatType.NONE -> return null
                RepeatType.DAILY -> candidate.plusDays(interval)
                RepeatType.WEEKLY -> candidate.plusWeeks(interval)
                RepeatType.MONTHLY -> candidate.plusMonths(interval)
                RepeatType.YEARLY -> candidate.plusYears(interval)
                RepeatType.CUSTOM_HOURS -> candidate.plusHours(interval)
                RepeatType.CUSTOM_DAYS -> candidate.plusDays(interval)
                RepeatType.WEEKDAYS -> {
                    val mask = reminder.repeatWeekdaysMask
                    if (mask == 0) {
                        candidate.plusDays(1)
                    } else {
                        var next = candidate.plusDays(1)
                        var daySearch = 0
                        while (!isDayInMask(next.dayOfWeek, mask) && daySearch < 8) {
                            next = next.plusDays(1)
                            daySearch++
                        }
                        next
                    }
                }
            }
        }
        return candidate.toInstant().toEpochMilli()
    }

    /**
     * Computes the next trigger epoch millis for an alarm at [hour]:[minute] after [nowMillis].
     * Bit 0 = Monday .. Bit 6 = Sunday. Mask 0 = one-time alarm.
     */
    fun computeNextAlarmTrigger(
        hour: Int,
        minute: Int,
        repeatDaysMask: Int,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        var candidate = now
            .withHour(hour.coerceIn(0, 23))
            .withMinute(minute.coerceIn(0, 59))
            .withSecond(0)
            .withNano(0)

        if (!candidate.isAfter(now)) {
            candidate = candidate.plusDays(1)
        }

        if (repeatDaysMask == 0) {
            return candidate.toInstant().toEpochMilli()
        }

        var checked = 0
        while (!isDayInMask(candidate.dayOfWeek, repeatDaysMask) && checked < 8) {
            candidate = candidate.plusDays(1)
            checked++
        }
        return candidate.toInstant().toEpochMilli()
    }

    fun computeNextAlarmTrigger(alarm: AlarmEntity, nowMillis: Long = System.currentTimeMillis()): Long =
        computeNextAlarmTrigger(alarm.hour, alarm.minute, alarm.repeatDaysMask, nowMillis)

    fun isDayInMask(dayOfWeek: DayOfWeek, mask: Int): Boolean {
        val bit = dayOfWeek.value - 1 // Mon=0 .. Sun=6
        return (mask and (1 shl bit)) != 0
    }

    fun formatWeekdaysMask(mask: Int): String {
        if (mask == 0) return "Once"
        if (mask == 0b1111111) return "Every day"
        if (mask == 0b0011111) return "Weekdays"
        if (mask == 0b1100000) return "Weekends"
        val labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        return labels.filterIndexed { idx, _ -> (mask and (1 shl idx)) != 0 }.joinToString(", ")
    }

    fun formatTime(
        millis: Long,
        use24Hour: Boolean,
        zoneId: ZoneId = ZoneId.systemDefault(),
        includeSeconds: Boolean = false,
    ): String {
        val zdt = Instant.ofEpochMilli(millis).atZone(zoneId)
        val pattern = when {
            use24Hour && includeSeconds -> "HH:mm:ss"
            use24Hour -> "HH:mm"
            includeSeconds -> "h:mm:ss a"
            else -> "h:mm a"
        }
        return zdt.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }

    fun formatHourMinute(hour: Int, minute: Int, use24Hour: Boolean): String {
        val zdt = ZonedDateTime.now().withHour(hour.coerceIn(0, 23)).withMinute(minute.coerceIn(0, 59))
        val pattern = if (use24Hour) "HH:mm" else "h:mm a"
        return zdt.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }

    fun formatDate(millis: Long, pattern: String = "MMM d, yyyy", zoneId: ZoneId = ZoneId.systemDefault()): String {
        val zdt = Instant.ofEpochMilli(millis).atZone(zoneId)
        return zdt.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }

    fun formatDurationMillis(millis: Long): String {
        val totalSeconds = (millis.coerceAtLeast(0L) / 1000L)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatStopwatchMillis(millis: Long): String {
        val safe = millis.coerceAtLeast(0L)
        val hours = safe / 3_600_000L
        val minutes = (safe % 3_600_000L) / 60_000L
        val seconds = (safe % 60_000L) / 1000L
        val centis = (safe % 1000L) / 10L
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d.%02d", hours, minutes, seconds, centis)
        } else {
            String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, centis)
        }
    }

    fun formatCountdownDelta(targetMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val diff = targetMillis - nowMillis
        if (diff <= 0L) return "Due now"
        val totalMinutes = diff / 60_000L
        val days = totalMinutes / (60 * 24)
        val hours = (totalMinutes % (60 * 24)) / 60
        val mins = totalMinutes % 60
        return when {
            days > 0 -> "in ${days}d ${hours}h"
            hours > 0 -> "in ${hours}h ${mins}m"
            mins > 0 -> "in ${mins}m"
            else -> "in < 1m"
        }
    }
}
