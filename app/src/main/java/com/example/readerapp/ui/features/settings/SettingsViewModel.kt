package com.example.readerapp.ui.features.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.example.readerapp.data.local.preferences.ReaderPreferences
import com.example.readerapp.data.local.preferences.ReaderSettings
import com.example.readerapp.data.repository.backup.LibraryBackupRepository
import com.example.readerapp.data.repository.dictionary.DictionaryBackupManager
import com.example.readerapp.data.repository.dictionary.DictionaryState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    application: Application,
    private val readerPreferences: ReaderPreferences,
    val dictionaryBackupManager: DictionaryBackupManager
) : AndroidViewModel(application) {

    private val libraryBackupRepository = LibraryBackupRepository(application)

    private val _settings = MutableStateFlow(ReaderSettings())
    val settings: StateFlow<ReaderSettings> = _settings.asStateFlow()

    val dictBackupState: StateFlow<DictionaryState> = dictionaryBackupManager.backupState
    val dictRestoreState: StateFlow<DictionaryState> = dictionaryBackupManager.restoreState

    private val _isLibraryBackingUp = MutableStateFlow(false)
    val isLibraryBackingUp: StateFlow<Boolean> = _isLibraryBackingUp.asStateFlow()

    private val _isLibraryRestoring = MutableStateFlow(false)
    val isLibraryRestoring: StateFlow<Boolean> = _isLibraryRestoring.asStateFlow()

    init {
        viewModelScope.launch {
            readerPreferences.readerSettings.collect {
                _settings.value = it
            }
        }
    }

    fun updateSettings(newSettings: ReaderSettings) {
        viewModelScope.launch {
            readerPreferences.updateAllSettings(newSettings)
        }
    }

    suspend fun updateSettingsSuspended(newSettings: ReaderSettings) {
        readerPreferences.updateAllSettings(newSettings)
    }

    fun performLibraryBackup(
        onStart: () -> Unit,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        viewModelScope.launch {
            _isLibraryBackingUp.value = true
            onStart()
            val success = libraryBackupRepository.performBackup(force = true)
            _isLibraryBackingUp.value = false
            if (success) {
                onSuccess()
            } else {
                onFailure()
            }
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    fun restoreLibraryBackup(
        uri: Uri,
        onStart: () -> Unit,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        viewModelScope.launch {
            _isLibraryRestoring.value = true
            onStart()
            val success = libraryBackupRepository.restoreBackup(uri)
            _isLibraryRestoring.value = false
            if (success) {
                getApplication<Application>().imageLoader.memoryCache?.clear()
                getApplication<Application>().imageLoader.diskCache?.clear()
                onSuccess()
            } else {
                onFailure()
            }
        }
    }

    fun backupDictionaries() {
        viewModelScope.launch {
            dictionaryBackupManager.backupDictionaries()
        }
    }

    fun restoreDictionaries(uri: Uri) {
        viewModelScope.launch {
            dictionaryBackupManager.restoreDictionaries(uri)
        }
    }

    fun resetDictBackupState() {
        dictionaryBackupManager.resetBackupState()
    }

    fun resetDictRestoreState() {
        dictionaryBackupManager.resetRestoreState()
    }
}
