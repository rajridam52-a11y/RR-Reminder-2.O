package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.core.app.ApplicationProvider
import com.example.core.datastore.TimeFormatMode
import com.example.core.datastore.UserPreferences
import com.example.core.di.AppContainer
import com.example.features.alarms.AlarmsTabContent
import com.example.features.alarms.AlarmsViewModel
import com.example.features.health.SystemHealthViewModel
import com.example.features.home.MainViewModel
import com.example.features.productivity.ProductivityViewModel
import com.example.features.reminders.RemindersScreen
import com.example.features.reminders.RemindersViewModel
import com.example.features.settings.SettingsViewModel
import com.example.features.timer.TimerStopwatchViewModel
import com.example.features.timer.TimerTabContent
import com.example.features.weather.WeatherWorldClockViewModel
import com.example.features.weather.WorldClockTabContent
import com.example.ui.theme.RRReminderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.lifecycle.viewModelScope

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RRReminderUiFlowTests {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        container = AppContainer(context)
        runBlocking(Dispatchers.IO) {
            container.database.clearAllTables()
        }
    }

    @After
    fun tearDown() {
        runBlocking(Dispatchers.IO) {
            container.database.clearAllTables()
        }
    }

    @Test
    fun allShellViewModelsWithDistinctKeysRemainActiveTogether() {
        val store = ViewModelStore()
        val storeOwner = object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = store
        }
        val uid = "test-uid-123"
        lateinit var mainVm: MainViewModel
        lateinit var remindersVm: RemindersViewModel
        lateinit var alarmsVm: AlarmsViewModel
        lateinit var weatherClockVm: WeatherWorldClockViewModel
        lateinit var timerSwVm: TimerStopwatchViewModel
        lateinit var prodVm: ProductivityViewModel
        lateinit var settingsVm: SettingsViewModel
        lateinit var healthVm: SystemHealthViewModel

        composeRule.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides storeOwner) {
                mainVm = viewModel(key = "main_$uid", factory = MainViewModel.Factory(container))
                remindersVm = viewModel(key = "reminders_$uid", factory = RemindersViewModel.Factory(container))
                alarmsVm = viewModel(key = "alarms_$uid", factory = AlarmsViewModel.Factory(container))
                weatherClockVm = viewModel(key = "weather_clock_$uid", factory = WeatherWorldClockViewModel.Factory(container))
                timerSwVm = viewModel(key = "timer_sw_$uid", factory = TimerStopwatchViewModel.Factory(container))
                prodVm = viewModel(key = "productivity_$uid", factory = ProductivityViewModel.Factory(container))
                settingsVm = viewModel(key = "settings_$uid", factory = SettingsViewModel.Factory(container))
                healthVm = viewModel(key = "health_$uid", factory = SystemHealthViewModel.Factory(container))
            }
        }
        composeRule.waitForIdle()

        assertTrue("MainViewModel scope cancelled", mainVm.viewModelScope.isActive)
        assertTrue("RemindersViewModel scope cancelled", remindersVm.viewModelScope.isActive)
        assertTrue("AlarmsViewModel scope cancelled", alarmsVm.viewModelScope.isActive)
        assertTrue("WeatherWorldClockViewModel scope cancelled", weatherClockVm.viewModelScope.isActive)
        assertTrue("TimerStopwatchViewModel scope cancelled", timerSwVm.viewModelScope.isActive)
        assertTrue("ProductivityViewModel scope cancelled", prodVm.viewModelScope.isActive)
        assertTrue("SettingsViewModel scope cancelled", settingsVm.viewModelScope.isActive)
        assertTrue("SystemHealthViewModel scope cancelled", healthVm.viewModelScope.isActive)
        assertEquals(8, store.keys().size)
        store.clear()
    }

    @Test
    fun reminderSaveFlowPersistsAndRendersInRemindersScreen() {
        val remindersVm = RemindersViewModel(container)
        composeRule.setContent {
            RRReminderTheme {
                val reminders by remindersVm.filteredReminders.collectAsStateWithLifecycle()
                val query by remindersVm.searchQuery.collectAsStateWithLifecycle()
                val filter by remindersVm.selectedFilter.collectAsStateWithLifecycle()
                val cat by remindersVm.selectedCategory.collectAsStateWithLifecycle()
                val captured by remindersVm.capturedLocation.collectAsStateWithLifecycle()
                val msg by remindersVm.locationMessage.collectAsStateWithLifecycle()

                RemindersScreen(
                    reminders = reminders,
                    nowMillis = System.currentTimeMillis(),
                    searchQuery = query,
                    selectedFilter = filter,
                    selectedCategory = cat,
                    preferences = UserPreferences(),
                    capturedLocation = captured,
                    locationMessage = msg,
                    onSearchQueryChange = remindersVm::setSearchQuery,
                    onSelectFilter = remindersVm::setFilter,
                    onSelectCategory = remindersVm::setCategory,
                    onSaveReminder = remindersVm::saveReminder,
                    onToggleComplete = remindersVm::toggleComplete,
                    onSnooze = remindersVm::snoozeReminder,
                    onDeleteReminder = remindersVm::deleteReminder,
                    onCaptureLocation = remindersVm::captureCurrentLocation,
                    onClearCapturedLocation = remindersVm::clearCapturedLocation,
                    onNavigateNotifications = {},
                )
            }
        }

        composeRule.onNodeWithTag("reminders_add_top_btn").performClick()
        composeRule.onNodeWithTag("reminder_title_input").performTextInput("Call Dentist")
        composeRule.onNodeWithTag("reminder_save_btn").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("Call Dentist").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Call Dentist").assertIsDisplayed()

        val stored = runBlocking(Dispatchers.IO) { container.database.reminderDao().getAllActiveSync() }
        assertTrue(stored.any { it.title == "Call Dentist" })
    }

    @Test
    fun alarmSaveAndToggleFlowPersistsAndUpdatesUi() {
        val alarmsVm = AlarmsViewModel(container)
        composeRule.setContent {
            RRReminderTheme {
                val alarms by alarmsVm.alarms.collectAsStateWithLifecycle()
                val history by alarmsVm.history.collectAsStateWithLifecycle()
                AlarmsTabContent(
                    alarms = alarms,
                    history = history,
                    preferences = UserPreferences(),
                    onSaveAlarm = alarmsVm::saveAlarm,
                    onToggleAlarm = alarmsVm::toggleAlarm,
                    onDeleteAlarm = alarmsVm::deleteAlarm,
                    onClearHistory = alarmsVm::clearHistory,
                )
            }
        }

        composeRule.onNodeWithTag("alarms_add_cta").performClick()
        composeRule.onNodeWithTag("alarm_label_input").performScrollTo()
        composeRule.onNodeWithTag("alarm_label_input").performTextClearance()
        composeRule.onNodeWithTag("alarm_label_input").performTextInput("Morning Run")
        composeRule.onNodeWithTag("alarm_save_btn").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            runBlocking(Dispatchers.IO) { container.database.alarmDao().getAllSync().any { it.label == "Morning Run" } }
        }
        val savedAlarm = runBlocking(Dispatchers.IO) { container.database.alarmDao().getAllSync().first { it.label == "Morning Run" } }
        assertTrue(savedAlarm.isEnabled)

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("Morning Run • Default Alarm").fetchSemanticsNodes().isNotEmpty()
        }
        val toggleNode = composeRule.onNodeWithTag("alarm_toggle_${savedAlarm.id}")
        toggleNode.performScrollTo()
        composeRule.waitForIdle()
        toggleNode.performClick()
        composeRule.waitForIdle()

        val hasEditDialog = composeRule.onAllNodesWithText("Edit Alarm").fetchSemanticsNodes().isNotEmpty()
        val updatedAlarm = runBlocking(Dispatchers.IO) {
            container.database.alarmDao().getById(savedAlarm.id)
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            val updated = runBlocking(Dispatchers.IO) { container.database.alarmDao().getById(savedAlarm.id) }
            updated != null && !updated.isEnabled
        }
        val toggledAlarm = runBlocking(Dispatchers.IO) { container.database.alarmDao().getById(savedAlarm.id) }!!
        assertFalse("Expected alarm disabled (hasEditDialog=$hasEditDialog, beforeWait=$updatedAlarm)", toggledAlarm.isEnabled)
    }

    @Test
    fun worldClockAddCityFlowPersistsAndDisplaysCity() {
        val weatherClockVm = WeatherWorldClockViewModel(container)
        composeRule.setContent {
            RRReminderTheme {
                val cities by weatherClockVm.worldCities.collectAsStateWithLifecycle()
                val avail by weatherClockVm.availableCitiesToAdd.collectAsStateWithLifecycle()
                val query by weatherClockVm.zoneQuery.collectAsStateWithLifecycle()
                val now by weatherClockVm.nowMillis.collectAsStateWithLifecycle()

                WorldClockTabContent(
                    cities = cities,
                    availableToAdd = avail,
                    zoneQuery = query,
                    nowMillis = now,
                    preferences = UserPreferences(),
                    onZoneQueryChange = weatherClockVm::setZoneSearchQuery,
                    onAddCity = weatherClockVm::addWorldCity,
                    onRemoveCity = weatherClockVm::removeWorldCity,
                )
            }
        }

        composeRule.onNodeWithTag("worldclock_add_fab").performClick()
        composeRule.onNodeWithTag("worldclock_search_input").performTextInput("Berlin")
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("Berlin").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("worldclock_option_berlin").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("Berlin").fetchSemanticsNodes().isNotEmpty() &&
                runBlocking(Dispatchers.IO) { container.database.worldClockDao().getAllSync().any { it.cityName == "Berlin" } }
        }
        composeRule.onNodeWithText("Berlin").assertIsDisplayed()
    }

    @Test
    fun timerStartAndPauseFlowStartsCountdownAndPersists() {
        val timerVm = TimerStopwatchViewModel(container)
        composeRule.setContent {
            RRReminderTheme {
                val timers by timerVm.timers.collectAsStateWithLifecycle()
                val presets by timerVm.presets.collectAsStateWithLifecycle()
                val history by timerVm.timerHistory.collectAsStateWithLifecycle()
                val tick by timerVm.timerTickNow.collectAsStateWithLifecycle()

                TimerTabContent(
                    timers = timers,
                    presets = presets,
                    history = history,
                    tickNowMillis = tick,
                    preferences = UserPreferences(timeFormatMode = TimeFormatMode.H12),
                    onCreateTimer = timerVm::createTimer,
                    onPauseTimer = timerVm::pauseTimer,
                    onResumeTimer = timerVm::resumeTimer,
                    onResetTimer = timerVm::resetTimer,
                    onDeleteTimer = timerVm::deleteTimer,
                    onAddPreset = timerVm::addPreset,
                    onClearHistory = timerVm::clearTimerHistory,
                )
            }
        }

        composeRule.onNodeWithTag("timer_hero_start_btn").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("Pause").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("timer_hero_toggle_btn").assertIsDisplayed()

        val timersDb = runBlocking(Dispatchers.IO) { container.timerRepository.timers.first() }
        assertTrue(timersDb.any { it.isRunning && it.label == "15 min" })

        composeRule.onNodeWithTag("timer_hero_toggle_btn").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("Resume").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("Start").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
