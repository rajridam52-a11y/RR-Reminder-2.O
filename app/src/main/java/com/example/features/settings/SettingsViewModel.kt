package com.example.features.settings

import android.content.Context
import android.net.Uri
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.datastore.ClockFaceStyle
import com.example.core.datastore.DateFormatStyle
import com.example.core.datastore.PrimaryClockZone
import com.example.core.datastore.TimeFormatMode
import com.example.core.datastore.UserPreferences
import com.example.core.di.AppContainer
import com.example.core.firebase.GlobalAppConfig
import com.example.core.health.SystemHealthReport
import com.example.features.auth.signOutFromGoogle
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = container.effectivePreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    val remoteConfig: StateFlow<GlobalAppConfig?> = container.remoteGlobalConfig

    val nowMillis: StateFlow<Long> = container.sharedNowMillis

    val healthReport: StateFlow<SystemHealthReport> = container.systemHealthChecker.report

    private val _backupStatus = MutableStateFlow<String?>(null)
    val backupStatus: StateFlow<String?> = _backupStatus.asStateFlow()

    private val _isSavingOwnerConfig = MutableStateFlow(false)
    val isSavingOwnerConfig: StateFlow<Boolean> = _isSavingOwnerConfig.asStateFlow()

    private val _ownerSaveStatus = MutableStateFlow<String?>(null)
    val ownerSaveStatus: StateFlow<String?> = _ownerSaveStatus.asStateFlow()

    private val _ownerSaveError = MutableStateFlow<String?>(null)
    val ownerSaveError: StateFlow<String?> = _ownerSaveError.asStateFlow()

    fun currentBaseNetworkMillis(): Long = container.networkTimeProvider.baseNetworkEpochMillis()

    fun publishOwnerConfig(config: GlobalAppConfig) {
        viewModelScope.launch {
            _isSavingOwnerConfig.value = true
            _ownerSaveStatus.value = null
            _ownerSaveError.value = null
            val res = container.ownerConfigRepository.saveGlobalConfig(config)
            _isSavingOwnerConfig.value = false
            res.onSuccess {
                _ownerSaveStatus.value = "Published live! All signed-in user apps update automatically."
            }.onFailure { err ->
                _ownerSaveError.value = err.localizedMessage ?: "Failed to publish Owner settings."
            }
        }
    }

    fun resetOwnerConfig() {
        viewModelScope.launch {
            _isSavingOwnerConfig.value = true
            _ownerSaveStatus.value = null
            _ownerSaveError.value = null
            val res = container.ownerConfigRepository.resetGlobalConfig()
            _isSavingOwnerConfig.value = false
            res.onSuccess {
                _ownerSaveStatus.value = "Owner overrides reset. User apps now use live Google time & local preferences."
            }.onFailure { err ->
                _ownerSaveError.value = err.localizedMessage ?: "Failed to reset Owner settings."
            }
        }
    }

    fun clearOwnerMessages() {
        _ownerSaveStatus.value = null
        _ownerSaveError.value = null
    }

    fun signOut(credentialManager: CredentialManager) {
        signOutFromGoogle(
            credentialManager = credentialManager,
            scope = viewModelScope,
            beforeFirebaseSignOut = {
                container.clearRemoteOverrides()
            },
        )
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { container.preferencesRepository.setThemeMode(mode) }
    }

    fun setAccentPreset(preset: AccentPreset) {
        viewModelScope.launch { container.preferencesRepository.setAccentPreset(preset) }
    }

    fun setClockFaceStyle(style: ClockFaceStyle) {
        viewModelScope.launch { container.preferencesRepository.setClockFaceStyle(style) }
    }

    fun setPrimaryClockZone(zone: PrimaryClockZone) {
        viewModelScope.launch { container.preferencesRepository.setPrimaryClockZone(zone) }
    }

    fun setTimeFormatMode(mode: TimeFormatMode) {
        viewModelScope.launch { container.preferencesRepository.setTimeFormatMode(mode) }
    }

    fun setUse24Hour(use24: Boolean) {
        viewModelScope.launch { container.preferencesRepository.setUse24Hour(use24) }
    }

    fun setDateFormat(style: DateFormatStyle) {
        viewModelScope.launch { container.preferencesRepository.setDateFormat(style) }
    }

    fun setFirstDayOfWeek(day: Int) {
        viewModelScope.launch { container.preferencesRepository.setFirstDayOfWeek(day) }
    }

    fun setUseFahrenheit(useFahrenheit: Boolean) {
        viewModelScope.launch { container.preferencesRepository.setUseFahrenheit(useFahrenheit) }
    }

    fun setUseMph(useMph: Boolean) {
        viewModelScope.launch { container.preferencesRepository.setUseMph(useMph) }
    }

    fun setDefaultReminderLead(minutes: Int) {
        viewModelScope.launch {
            container.preferencesRepository.setDefaultReminderLead(minutes)
            container.reminderRepository.rescheduleAllReminders(minutes)
        }
    }

    fun setDefaultAlarmSnooze(minutes: Int) {
        viewModelScope.launch { container.preferencesRepository.setDefaultAlarmSnooze(minutes) }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch { container.preferencesRepository.setVibrationEnabled(enabled) }
    }

    fun setNotificationSoundUri(uri: String?) {
        viewModelScope.launch { container.preferencesRepository.setNotificationSoundUri(uri) }
    }

    fun setLocationRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.preferencesRepository.setLocationRemindersEnabled(enabled)
            container.locationHelper.scheduleBatteryConsciousLocationCheck(enabled)
        }
    }

    fun setUseDeviceCalendar(enabled: Boolean) {
        viewModelScope.launch { container.preferencesRepository.setUseDeviceCalendar(enabled) }
    }

    fun exportBackupToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _backupStatus.value = "Exporting encrypted-safe local backup..."
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val json = container.backupManager.exportToJson()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("Unable to open destination file.")
                }
            }
            _backupStatus.value = result.fold(
                onSuccess = { "Backup exported successfully." },
                onFailure = { "Export failed: ${it.localizedMessage}" },
            )
        }
    }

    fun importBackupFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _backupStatus.value = "Validating and restoring backup..."
            val raw = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: throw IllegalStateException("Unable to read backup file.")
                }
            }
            if (raw.isFailure) {
                _backupStatus.value = "Restore failed: ${raw.exceptionOrNull()?.localizedMessage}"
                return@launch
            }
            val importRes = container.backupManager.importFromJson(raw.getOrThrow())
            importRes.onSuccess { res ->
                container.scheduler.rescheduleAll(container.database)
                _backupStatus.value = res.summaryMessage
            }.onFailure { err ->
                _backupStatus.value = "Validation error: ${err.localizedMessage}"
            }
        }
    }

    fun clearBackupStatus() {
        _backupStatus.value = null
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(container) as T
    }
}
