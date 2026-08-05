package com.javierreansyah.pinecone.ui.features.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.local.preferences.ReaderSettings
import com.javierreansyah.pinecone.data.repository.backup.BackupRepository
import com.javierreansyah.pinecone.data.repository.backup.BackupResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupFile(
    val uri: Uri,
    val name: String,
    val timestamp: Long,
    val isManual: Boolean,
    val formattedDate: String
)

class SettingsViewModel(
    application: Application,
    private val readerPreferences: ReaderPreferences
) : AndroidViewModel(application) {
    private val backupRepository = BackupRepository(application)
    private val _settings = MutableStateFlow(ReaderSettings())
    val settings: StateFlow<ReaderSettings> = _settings.asStateFlow()
    private val _isBackingUp = MutableStateFlow(false)
    val isBackingUp: StateFlow<Boolean> = _isBackingUp.asStateFlow()
    private val _isRestoring = MutableStateFlow(false)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()
    private val _availableBackups = MutableStateFlow<List<BackupFile>>(emptyList())
    val availableBackups: StateFlow<List<BackupFile>> = _availableBackups.asStateFlow()

    init {
        viewModelScope.launch { readerPreferences.readerSettings.collect { _settings.value = it } }
    }

    fun updateSettings(settings: ReaderSettings) {
        viewModelScope.launch { readerPreferences.updateAllSettings(settings) }
    }

    suspend fun updateSettingsSuspended(settings: ReaderSettings) =
        readerPreferences.updateAllSettings(settings)

    fun performFullBackup(onStart: () -> Unit, onSuccess: () -> Unit, onFailure: () -> Unit) {
        viewModelScope.launch {
            _isBackingUp.value = true; onStart()
            val result = backupRepository.createSnapshot(manual = true)
            _isBackingUp.value = false
            if (result.isSuccess) { loadBackups(); onSuccess() } else onFailure()
        }
    }

    fun loadBackups() {
        viewModelScope.launch {
            val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            _availableBackups.value = backupRepository.listSnapshots().map {
                BackupFile(it.uri, it.id, it.timestamp, it.isManual,
                    formatter.format(Date(it.timestamp)))
            }
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    fun restoreFullBackup(
        uri: Uri,
        onStart: () -> Unit,
        onSuccess: () -> Unit,
        onWarning: () -> Unit,
        onFailure: () -> Unit
    ) {
        viewModelScope.launch {
            _isRestoring.value = true; onStart()
            when (backupRepository.restoreSnapshot(uri)) {
                is BackupResult.Success, BackupResult.Skipped -> {
                    getApplication<Application>().imageLoader.memoryCache?.clear()
                    getApplication<Application>().imageLoader.diskCache?.clear()
                    onSuccess()
                }
                is BackupResult.Partial -> { onWarning(); onSuccess() }
                is BackupResult.Failure -> onFailure()
            }
            _isRestoring.value = false
        }
    }

    fun exportBackup(uri: Uri, destination: Uri, onSuccess: () -> Unit, onFailure: () -> Unit) {
        viewModelScope.launch {
            if (backupRepository.exportSnapshot(uri, destination).isSuccess) onSuccess() else onFailure()
        }
    }

    fun importBackup(uri: Uri, onSuccess: () -> Unit, onFailure: () -> Unit) {
        viewModelScope.launch {
            val result = backupRepository.importPortable(uri)
            if (result.isSuccess) { loadBackups(); onSuccess() } else onFailure()
        }
    }

    class Factory(
        private val application: Application,
        private val readerPreferences: ReaderPreferences
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                return SettingsViewModel(application, readerPreferences) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
