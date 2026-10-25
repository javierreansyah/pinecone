package com.javierreansyah.pinecone.ui.features.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.imageLoader
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.local.preferences.ReaderSettings
import com.javierreansyah.pinecone.data.repository.backup.BackupRepository
import com.javierreansyah.pinecone.data.repository.backup.BackupResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class BackupFile(
    val uri: Uri,
    val name: String,
    val timestamp: Long,
    val isManual: Boolean,
    val formattedDate: String,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: Application,
    private val readerPreferences: ReaderPreferences,
    private val backupRepository: BackupRepository,
    private val restoreBackup: com.javierreansyah.pinecone.domain.RestoreBackup,
) : ViewModel() {
    private val _settings = MutableStateFlow(ReaderSettings())
    val settings: StateFlow<ReaderSettings> = _settings.asStateFlow()
    private val _isBackingUp = MutableStateFlow(value = false)
    val isBackingUp: StateFlow<Boolean> = _isBackingUp.asStateFlow()
    private val _isRestoring = MutableStateFlow(value = false)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()
    private val _availableBackups = MutableStateFlow<List<BackupFile>>(emptyList())
    val availableBackups: StateFlow<List<BackupFile>> = _availableBackups.asStateFlow()

    init {
        viewModelScope.launch { readerPreferences.readerSettings.collect { _settings.value = it } }
    }

    fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        viewModelScope.launch { readerPreferences.updateSettings(transform) }
    }

    suspend fun updateSettingsSuspended(transform: (ReaderSettings) -> ReaderSettings): Unit =
        readerPreferences.updateSettings(transform)

    fun performFullBackup(onStart: () -> Unit, onSuccess: () -> Unit, onFailure: () -> Unit) {
        viewModelScope.launch {
            _isBackingUp.value = true; onStart()
            val result = backupRepository.createSnapshot(manual = true)
            _isBackingUp.value = false
            if (result.isSuccess) {
                loadBackups(); onSuccess()
            } else onFailure()
        }
    }

    fun loadBackups() {
        viewModelScope.launch {
            val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            _availableBackups.value =
                backupRepository.listSnapshots().map { (itUri, itId, itTimestamp, itIsManual) ->
                    BackupFile(
                        itUri, itId, itTimestamp, itIsManual,
                        formatter.format(Date(itTimestamp)),
                    )
                }
        }
    }

    fun restoreFullBackup(
        uri: Uri,
        onStart: () -> Unit,
        onSuccess: () -> Unit,
        onWarning: () -> Unit,
        onFailure: () -> Unit,
    ) {
        viewModelScope.launch {
            _isRestoring.value = true; onStart()
            when (restoreBackup(uri)) {
                is BackupResult.Success, BackupResult.Skipped -> {
                    application.imageLoader.memoryCache?.clear()
                    application.imageLoader.diskCache?.clear()
                    onSuccess()
                }

                is BackupResult.Partial -> {
                    onWarning(); onSuccess()
                }

                is BackupResult.Failure -> onFailure()
            }
            _isRestoring.value = false
        }
    }

    fun exportBackup(uri: Uri, destination: Uri, onSuccess: () -> Unit, onFailure: () -> Unit) {
        viewModelScope.launch {
            if (backupRepository.exportSnapshot(
                    uri,
                    destination,
                ).isSuccess
            ) onSuccess() else onFailure()
        }
    }

    fun importBackup(uri: Uri, onSuccess: () -> Unit, onFailure: () -> Unit) {
        viewModelScope.launch {
            val result = backupRepository.importPortable(uri)
            if (result.isSuccess) {
                loadBackups(); onSuccess()
            } else onFailure()
        }
    }


}
