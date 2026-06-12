package com.example.readerapp.ui.features.settings

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.example.readerapp.data.local.preferences.ReaderPreferences
import com.example.readerapp.data.local.preferences.ReaderSettings
import com.example.readerapp.data.repository.backup.LibraryBackupRepository
import com.example.readerapp.data.repository.dictionary.DictionaryBackupManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(
    application: Application,
    private val readerPreferences: ReaderPreferences,
    val dictionaryBackupManager: DictionaryBackupManager
) : AndroidViewModel(application) {

    private val libraryBackupRepository = LibraryBackupRepository(application)

    private val _settings = MutableStateFlow(ReaderSettings())
    val settings: StateFlow<ReaderSettings> = _settings.asStateFlow()

    private val _isBackingUp = MutableStateFlow(false)
    val isBackingUp: StateFlow<Boolean> = _isBackingUp.asStateFlow()

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

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

    fun performFullBackup(
        onStart: () -> Unit,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        viewModelScope.launch {
            _isBackingUp.value = true
            onStart()
            val libSuccess = libraryBackupRepository.performBackup(force = true)
            val dictSuccess = dictionaryBackupManager.backupDictionaries()
            _isBackingUp.value = false
            if (libSuccess && dictSuccess) {
                onSuccess()
            } else {
                onFailure()
            }
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    fun restoreFullBackup(
        uri: Uri,
        onStart: () -> Unit,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        viewModelScope.launch {
            _isRestoring.value = true
            onStart()
            val libSuccess = libraryBackupRepository.restoreBackup(uri)

            var dictSuccess = true
            val settingsVal = readerPreferences.readerSettings.first()
            val backupFolderUriString = settingsVal.backupFolderUri
            if (backupFolderUriString.isNotEmpty()) {
                val backupFolderUri = backupFolderUriString.toUri()
                val backupFolder = androidx.documentfile.provider.DocumentFile.fromTreeUri(
                    getApplication(),
                    backupFolderUri
                )
                val dictBackupFile = backupFolder?.findFile("dictionary_backup.pinedict")
                if (dictBackupFile != null) {
                    dictSuccess = dictionaryBackupManager.restoreDictionaries(dictBackupFile.uri)
                }
            }

            _isRestoring.value = false
            if (libSuccess && dictSuccess) {
                getApplication<Application>().imageLoader.memoryCache?.clear()
                getApplication<Application>().imageLoader.diskCache?.clear()
                onSuccess()
            } else {
                onFailure()
            }
        }
    }
}
