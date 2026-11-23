package com.example

import com.example.core.common.VoiceReminderParser
import com.example.core.database.BackupPayload
import com.example.core.database.BackupReminder
import com.example.core.database.ReminderEntity
import com.example.core.database.RepeatType
import com.example.core.network.NetworkModule
import com.example.core.network.WeatherCodeInterpreter
import com.example.core.notifications.ScheduleCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class RRReminderUnitTests {

    @Test
    fun `recurring daily reminder preserves local wall time across DST spring-forward`() {
        val nyZone = ZoneId.of("America/New_York")
        // 2026-03-07 09:00 EST (day before US DST spring-forward on 2026-03-08)
        val beforeDst = ZonedDateTime.of(2026, 3, 7, 9, 0, 0, 0, nyZone)
        val reminder = ReminderEntity(
            title = "Morning Standup",
            triggerAtMillis = beforeDst.toInstant().toEpochMilli(),
            zoneId = nyZone.id,
            repeatType = RepeatType.DAILY.name,
            repeatIntervalValue = 1,
        )

        val nextMs = ScheduleCalculator.computeNextReminderTrigger(
            reminder = reminder,
            afterMillis = beforeDst.toInstant().toEpochMilli(),
            targetZone = nyZone,
        )
        assertNotNull(nextMs)
        val nextZdt = Instant.ofEpochMilli(nextMs!!).atZone(nyZone)
        assertEquals(8, nextZdt.dayOfMonth)
        assertEquals(9, nextZdt.hour)
        assertEquals(0, nextZdt.minute)
    }

    @Test
    fun `recurring reminder handles time-zone change accurately`() {
        val london = ZoneId.of("Europe/London")
        val tokyo = ZoneId.of("Asia/Tokyo")
        val triggerInLondon = ZonedDateTime.of(2026, 11, 23, 8, 0, 0, 0, london)
        val reminder = ReminderEntity(
            title = "Global Sync",
            triggerAtMillis = triggerInLondon.toInstant().toEpochMilli(),
            zoneId = london.id,
            repeatType = RepeatType.CUSTOM_HOURS.name,
            repeatIntervalValue = 4,
        )

        val nextMs = ScheduleCalculator.computeNextReminderTrigger(
            reminder = reminder,
            afterMillis = triggerInLondon.toInstant().toEpochMilli(),
            targetZone = tokyo,
        )
        assertNotNull(nextMs)
        // +4 hours from 08:00 UTC = 12:00 UTC = 21:00 JST
        val nextInTokyo = Instant.ofEpochMilli(nextMs!!).atZone(tokyo)
        assertEquals(21, nextInTokyo.hour)
    }

    @Test
    fun `weekday mask alarm advances to next selected weekday`() {
        // 2026-11-23 is Monday
        val monMorning = ZonedDateTime.of(2026, 11, 23, 8, 30, 0, 0, ZoneId.of("UTC"))
        // Mask: Wednesday only (bit 2 = 4)
        val wedMask = 1 shl 2
        val nextMs = ScheduleCalculator.computeNextAlarmTrigger(
            hour = 7,
            minute = 15,
            repeatDaysMask = wedMask,
            nowMillis = monMorning.toInstant().toEpochMilli(),
            zoneId = ZoneId.of("UTC"),
        )
        val nextZdt = Instant.ofEpochMilli(nextMs).atZone(ZoneId.of("UTC"))
        assertEquals(25, nextZdt.dayOfMonth)
        assertEquals(7, nextZdt.hour)
        assertEquals(15, nextZdt.minute)
    }

    @Test
    fun `voice parser flags ambiguous time and extracts clean title`() {
        val now = ZonedDateTime.of(2026, 11, 23, 10, 0, 0, 0, ZoneId.of("UTC"))
        val ambiguous = VoiceReminderParser.parse("Remind me to call Alex at 5", now)
        assertEquals("call Alex", ambiguous.title)
        assertTrue(ambiguous.isAmbiguousTime)

        val explicit = VoiceReminderParser.parse("Submit report tomorrow at 3:30 pm", now)
        assertEquals("Submit report", explicit.title)
        assertFalse(explicit.isAmbiguousTime)
        val zdt = Instant.ofEpochMilli(explicit.suggestedTriggerMillis).atZone(ZoneId.of("UTC"))
        assertEquals(24, zdt.dayOfMonth)
        assertEquals(15, zdt.hour)
        assertEquals(30, zdt.minute)
    }

    @Test
    fun `backup payload roundtrip serialization with Moshi`() {
        val adapter = NetworkModule.moshi.adapter(BackupPayload::class.java)
        val payload = BackupPayload(
            reminders = listOf(
                BackupReminder(
                    uuid = "uuid-123",
                    title = "Pay Electricity Bill",
                    description = "Monthly auto-check",
                    triggerAtMillis = 1_800_000_000_000L,
                    zoneId = "UTC",
                    repeatType = "MONTHLY",
                    repeatIntervalValue = 1,
                    repeatWeekdaysMask = 0,
                    priority = "HIGH",
                    category = "Finance",
                    tagsCsv = "bills,monthly",
                    notes = "",
                    isCompleted = false,
                    completedAtMillis = null,
                    locationName = null,
                    locationLat = null,
                    locationLon = null,
                    locationTriggerType = "NONE",
                    createdAtMillis = 1_795_000_000_000L,
                ),
            ),
        )
        val json = adapter.toJson(payload)
        val decoded = adapter.fromJson(json)
        assertNotNull(decoded)
        assertEquals("RR Reminder", decoded!!.app)
        assertEquals(1, decoded.reminders.size)
        assertEquals("Pay Electricity Bill", decoded.reminders.first().title)
    }

    @Test
    fun `stopwatch, timer, real-time clock seconds, and weather code interpretation`() {
        assertEquals("01:05.09", ScheduleCalculator.formatStopwatchMillis(65_090L))
        assertEquals("01:00:00", ScheduleCalculator.formatDurationMillis(3_600_000L))
        val sampleMillis = ZonedDateTime.of(2026, 11, 23, 14, 5, 9, 0, ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli()
        assertEquals("14:05:09", ScheduleCalculator.formatTime(sampleMillis, use24Hour = true, zoneId = ZoneId.of("UTC"), includeSeconds = true))
        assertEquals("14:05", ScheduleCalculator.formatTime(sampleMillis, use24Hour = true, zoneId = ZoneId.of("UTC"), includeSeconds = false))
        assertEquals("Thunderstorm", WeatherCodeInterpreter.describe(95))
        assertNotNull(WeatherCodeInterpreter.severeAlertFor(95, 20.0))
        assertNull(WeatherCodeInterpreter.severeAlertFor(0, 10.0))
    }
}
