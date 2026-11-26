package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.AlarmEntity
import com.example.core.database.RRDatabase
import com.example.core.database.ReminderEntity
import com.example.core.database.RepeatType
import com.example.core.datastore.PrimaryClockZone
import com.example.core.datastore.UserPreferencesRepository
import com.example.core.network.NetworkModule
import com.example.core.network.NetworkTimeProvider
import com.example.core.notifications.AlarmForegroundService
import com.example.core.notifications.ReminderAlarmScheduler
import com.example.core.permissions.PermissionHelper
import com.example.data.AlarmRepository
import com.example.data.ReminderRepository
import com.example.data.TimerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RRReminderRobolectricTests {

    private lateinit var context: Context
    private lateinit var db: RRDatabase
    private lateinit var prefsRepo: UserPreferencesRepository
    private lateinit var scheduler: ReminderAlarmScheduler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, RRDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefsRepo = UserPreferencesRepository(context)
        val timeProvider = NetworkTimeProvider(NetworkModule.httpClient)
        scheduler = ReminderAlarmScheduler(context, timeProvider)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `reminder repository saves, toggles completion, snoozes, and deletes cleanly`() = runBlocking {
        val repo = ReminderRepository(context, db.reminderDao(), prefsRepo, scheduler)
        val initialTrigger = System.currentTimeMillis() + 3_600_000L
        val id = repo.saveReminder(
            ReminderEntity(
                title = "Doctor Visit",
                triggerAtMillis = initialTrigger,
                repeatType = RepeatType.NONE.name,
            ),
        )
        assertTrue(id > 0L)

        val saved = repo.reminders.first().first { it.id == id }
        assertFalse(saved.isCompleted)

        repo.snoozeReminder(saved, 15)
        val snoozed = repo.reminders.first().first { it.id == id }
        assertNotNull(snoozed.snoozedUntilMillis)

        repo.toggleComplete(snoozed)
        val completed = repo.reminders.first().first { it.id == id }
        assertTrue(completed.isCompleted)
        assertNull(completed.snoozedUntilMillis)

        repo.deleteReminder(id)
        assertTrue(repo.reminders.first().none { it.id == id })
    }

    @Test
    fun `alarm repository save, toggle, atomic consumeDueAlarm, and snoozeAlarm work reliably`() = runBlocking {
        prefsRepo.setPrimaryClockZone(PrimaryClockZone.INDIA_IST)
        val repo = AlarmRepository(context, db.alarmDao(), prefsRepo, scheduler)

        val id = repo.saveAlarm(
            AlarmEntity(
                label = "Wake Up",
                hour = 6,
                minute = 30,
                isEnabled = true,
                repeatDaysMask = 0,
                ringtoneUri = AlarmForegroundService.SILENT_RINGTONE_URI,
                ringtoneTitle = "Silent",
                snoozeMinutes = 10,
            ),
        )
        val stored = repo.alarms.first().first { it.id == id }
        assertNotNull(stored.nextTriggerAtMillis)
        assertEquals(AlarmForegroundService.SILENT_RINGTONE_URI, stored.ringtoneUri)

        val initialNext = stored.nextTriggerAtMillis!!
        // Not due before trigger time when called by watcher
        assertNull(repo.consumeDueAlarm(id, initialNext - 5_000L, fromReceiver = false))

        // Due right at trigger time: fires once, auto-disables one-time alarm, writes history
        val fired = repo.consumeDueAlarm(id, initialNext + 500L, fromReceiver = false)
        assertNotNull(fired)
        assertEquals(0, repo.consumeDueAlarm(id, initialNext + 500L, fromReceiver = false)?.let { 1 } ?: 0)

        val afterFire = repo.alarms.first().first { it.id == id }
        assertFalse(afterFire.isEnabled)
        assertNull(afterFire.nextTriggerAtMillis)
        assertTrue(repo.history.first().any { it.alarmId == id && it.action == "FIRED" })

        // Snoozing a one-time alarm re-enables and schedules the snooze wake
        repo.snoozeAlarm(id, 10)
        val afterSnooze = repo.alarms.first().first { it.id == id }
        assertTrue(afterSnooze.isEnabled)
        assertNotNull(afterSnooze.nextTriggerAtMillis)
        assertTrue(repo.history.first().any { it.alarmId == id && it.action == "SNOOZED" })

        repo.toggleAlarm(afterSnooze, false)
        assertFalse(repo.alarms.first().first { it.id == id }.isEnabled)
    }

    @Test
    fun `timer repository creates, pauses, resumes, resets, and finalizes expired timers cleanly`() = runBlocking {
        val repo = TimerRepository(context, db.timerDao(), scheduler)
        val timerId = repo.createAndStartTimer("Tea", 60_000L, repeat = false)

        val running = repo.timers.first().first { it.id == timerId }
        assertTrue(running.isRunning)
        assertNotNull(running.endEpochMillis)

        repo.pauseTimer(running)
        val paused = repo.timers.first().first { it.id == timerId }
        assertFalse(paused.isRunning)
        assertNull(paused.endEpochMillis)

        repo.resumeTimer(paused)
        val resumed = repo.timers.first().first { it.id == timerId }
        assertTrue(resumed.isRunning)
        assertNotNull(resumed.endEpochMillis)

        val expired = repo.finalizeExpiredTimer(
            timerId = timerId,
            nowMillis = (resumed.endEpochMillis ?: System.currentTimeMillis()) + 50L,
            fromReceiver = false,
        )
        assertNotNull(expired)
        // Second call is atomic no-op
        assertNull(repo.finalizeExpiredTimer(timerId = timerId, nowMillis = System.currentTimeMillis() + 50_000L, fromReceiver = false))

        val finished = repo.timers.first().first { it.id == timerId }
        assertFalse(finished.isRunning)
        assertEquals(0L, finished.remainingMillis)
        assertTrue(repo.history.first().any { it.label == "Tea" })

        repo.resetTimer(finished)
        val reset = repo.timers.first().first { it.id == timerId }
        assertEquals(60_000L, reset.remainingMillis)
    }

    @Test
    fun `permission helpers evaluate safely on SDK 36 without throwing`() {
        PermissionHelper.hasNotificationPermission(context)
        PermissionHelper.canScheduleExactAlarms(context)
        PermissionHelper.canUseFullScreenIntent(context)
        assertEquals("com.aistudio.rrreminder.kxmpzq", context.packageName)
    }
}
