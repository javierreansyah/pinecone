package com.javierreansyah.pinecone.ui.features.settings

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.local.preferences.ReaderSettings
import com.javierreansyah.pinecone.data.repository.backup.LibraryBackupRepository
import com.javierreansyah.pinecone.data.repository.dictionary.DictionaryBackupManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

    private val _availableBackups = MutableStateFlow<List<BackupFile>>(emptyList())
    val availableBackups: StateFlow<List<BackupFile>> = _availableBackups.asStateFlow()

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

    fun loadBackups() {
        viewModelScope.launch {
            val currentSettings = readerPreferences.readerSettings.first()
            val backupFolderUriString = currentSettings.backupFolderUri
            if (backupFolderUriString.isEmpty()) {
                _availableBackups.value = emptyList()
                return@launch
            }

            try {
                val backupFolderUri = backupFolderUriString.toUri()
                val backupFolder = DocumentFile.fromTreeUri(getApplication(), backupFolderUri)

                if (backupFolder != null && backupFolder.canRead()) {
                    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

                    val files = backupFolder.listFiles()
                        .filter { it.name?.endsWith(".pine") == true }
                        .mapNotNull { file ->
                            val name = file.name ?: return@mapNotNull null

                            // Try to parse yyMMdd_HHmmss from filename, fallback to lastModified
                            var timestamp = file.lastModified()
                            val parts = name.split("_")
                            if (parts.size >= 2) {
                                try {
                                    val dateStr = "${parts[0]}_${parts[1]}"
                                    val parsedDate =
                                        SimpleDateFormat("yyMMdd_HHmmss", Locale.US).parse(dateStr)
                                    if (parsedDate != null) {
                                        timestamp = parsedDate.time
                                    }
                                } catch (e: Exception) {
                                    // Ignore and use lastModified
                                }
                            }

                            val isManual = name.contains("_M.pine")

                            BackupFile(
                                uri = file.uri,
                                name = name,
                                timestamp = timestamp,
                                isManual = isManual,
                                formattedDate = dateFormat.format(Date(timestamp))
                            )
                        }
                        .sortedByDescending { it.timestamp }

                    _availableBackups.value = files
                } else {
                    _availableBackups.value = emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _availableBackups.value = emptyList()
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

    class Factory(
        private val application: Application,
        private val readerPreferences: ReaderPreferences,
        private val dictionaryBackupManager: DictionaryBackupManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                return SettingsViewModel(
                    application,
                    readerPreferences,
                    dictionaryBackupManager
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
