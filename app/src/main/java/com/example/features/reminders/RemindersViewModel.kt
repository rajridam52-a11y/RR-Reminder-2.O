package com.example.features.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.database.ReminderEntity
import com.example.core.database.ReminderPriority
import com.example.core.di.AppContainer
import com.example.core.location.LocationFetchResult
import com.example.core.location.SimpleLocation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class ReminderFilter(val label: String) {
    ALL("All"),
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    OVERDUE("Overdue"),
    HIGH_PRIORITY("High / Urgent"),
    RECURRING("Recurring"),
    LOCATION("Location"),
    COMPLETED("History"),
}

class RemindersViewModel(private val container: AppContainer) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(ReminderFilter.ALL)
    val selectedFilter: StateFlow<ReminderFilter> = _selectedFilter.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val tickerFlow: Flow<Long> = container.sharedNowMillis
        .map { it / 15_000L }
        .distinctUntilChanged()
        .map { container.networkTimeProvider.currentEpochMillis() }

    val allReminders: StateFlow<List<ReminderEntity>> = container.reminderRepository.reminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredReminders: StateFlow<List<ReminderEntity>> = combine(
        allReminders,
        _searchQuery,
        _selectedFilter,
        _selectedCategory,
        combine(tickerFlow, container.effectivePreferences) { now, prefs -> now to prefs },
    ) { list, query, filter, cat, (now, prefs) ->
        val q = query.trim().lowercase()
        val zone = prefs.primaryClockZone.toZoneId()
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val weekEnd = today.plusDays(7)
        list.filter { item ->
            val targetMs = item.snoozedUntilMillis ?: item.triggerAtMillis
            val itemDate = java.time.Instant.ofEpochMilli(targetMs).atZone(zone).toLocalDate()
            val matchesFilter = when (filter) {
                ReminderFilter.ALL -> !item.isCompleted
                ReminderFilter.TODAY -> !item.isCompleted && itemDate == today
                ReminderFilter.THIS_WEEK -> !item.isCompleted && !itemDate.isBefore(today) && !itemDate.isAfter(weekEnd)
                ReminderFilter.THIS_MONTH -> !item.isCompleted && itemDate.year == today.year && itemDate.month == today.month
                ReminderFilter.OVERDUE -> !item.isCompleted && targetMs < now
                ReminderFilter.HIGH_PRIORITY -> !item.isCompleted &&
                    (item.priority == ReminderPriority.HIGH.name || item.priority == ReminderPriority.URGENT.name)
                ReminderFilter.RECURRING -> !item.isCompleted && item.repeatType != "NONE"
                ReminderFilter.LOCATION -> !item.isCompleted && item.locationTriggerType != "NONE"
                ReminderFilter.COMPLETED -> item.isCompleted
            }
            val matchesCategory = cat == "All" || item.category.equals(cat, ignoreCase = true)
            val matchesQuery = q.isBlank() ||
                item.title.lowercase().contains(q) ||
                item.description.lowercase().contains(q) ||
                item.tagsCsv.lowercase().contains(q) ||
                item.notes.lowercase().contains(q)
            matchesFilter && matchesCategory && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _capturedLocation = MutableStateFlow<SimpleLocation?>(null)
    val capturedLocation: StateFlow<SimpleLocation?> = _capturedLocation.asStateFlow()

    private val _locationMessage = MutableStateFlow<String?>(null)
    val locationMessage: StateFlow<String?> = _locationMessage.asStateFlow()

    fun setSearchQuery(q: String) { _searchQuery.value = q }
    fun setFilter(filter: ReminderFilter) { _selectedFilter.value = filter }
    fun setCategory(category: String) { _selectedCategory.value = category }

    fun saveReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            container.reminderRepository.saveReminder(reminder)
            if (reminder.locationTriggerType != "NONE") {
                container.locationHelper.scheduleBatteryConsciousLocationCheck(true)
            }
        }
    }

    fun toggleComplete(reminder: ReminderEntity) {
        viewModelScope.launch { container.reminderRepository.toggleComplete(reminder) }
    }

    fun snoozeReminder(reminder: ReminderEntity, minutes: Int) {
        viewModelScope.launch { container.reminderRepository.snoozeReminder(reminder, minutes) }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch { container.reminderRepository.deleteReminder(id) }
    }

    fun captureCurrentLocation() {
        viewModelScope.launch {
            _locationMessage.value = "Reading GPS fix..."
            when (val res = container.locationHelper.getCurrentLocationDetailed()) {
                is LocationFetchResult.Success -> {
                    _capturedLocation.value = res.location
                    _locationMessage.value = "Pinned: ${res.location.displayName}"
                }
                is LocationFetchResult.PermissionDenied -> {
                    _locationMessage.value = "Location permission denied."
                }
                is LocationFetchResult.GpsDisabled -> {
                    _locationMessage.value = "Turn on device GPS / Location Services first."
                }
                is LocationFetchResult.Unavailable -> {
                    _locationMessage.value = res.reason
                }
            }
        }
    }

    fun clearCapturedLocation() {
        _capturedLocation.value = null
        _locationMessage.value = null
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = RemindersViewModel(container) as T
    }
}
