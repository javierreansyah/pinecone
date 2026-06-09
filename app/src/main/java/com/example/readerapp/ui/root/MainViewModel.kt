package com.example.readerapp.ui.root

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.example.readerapp.R
import com.example.readerapp.data.local.preferences.ReaderPreferences
import com.example.readerapp.data.local.preferences.ReaderSettings
import com.example.readerapp.data.repository.backup.LibraryBackupRepository
import com.example.readerapp.data.repository.library.LibraryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    application: Application,
    private val libraryRepository: LibraryRepository,
    private val readerPreferences: ReaderPreferences
) : AndroidViewModel(application) {

    val settings: StateFlow<ReaderSettings> = readerPreferences.readerSettings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ReaderSettings()
        )

    fun importBook(uri: Uri) {
        viewModelScope.launch {
            val result = libraryRepository.importBook(uri)
            if (result != null) {
                Toast.makeText(getApplication(), "Import complete", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun importBooks(uris: List<Uri>) {
        if (uris.isEmpty()) return
        Toast.makeText(getApplication(), "Importing ${uris.size} files...", Toast.LENGTH_SHORT)
            .show()
        viewModelScope.launch {
            uris.forEach { uri ->
                libraryRepository.importBook(uri)
            }
            Toast.makeText(getApplication(), "Import complete", Toast.LENGTH_SHORT).show()
        }
    }

    fun scanFolder(uri: Uri) {
        Toast.makeText(getApplication(), "Scanning folder for books...", Toast.LENGTH_SHORT).show()
        viewModelScope.launch {
            val root = DocumentFile.fromTreeUri(getApplication(), uri)
            if (root != null) {
                importFromDocumentFile(root)
            }
            Toast.makeText(getApplication(), "Folder scan complete", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun importFromDocumentFile(file: DocumentFile) {
        if (file.isDirectory) {
            file.listFiles().forEach { child ->
                importFromDocumentFile(child)
            }
        } else {
            val name = file.name?.lowercase() ?: ""
            val supportedExtensions = listOf(".epub", ".cbz", ".cbr", ".cb7", ".cbt", ".webpub")
            if (supportedExtensions.any { name.endsWith(it) }) {
                libraryRepository.importBook(file.uri)
            }
        }
    }

    fun updateBackupFolderUri(uri: Uri) {
        val takeFlags: Int =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                takeFlags
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        viewModelScope.launch {
            val currentSettings = settings.value
            val newSettings = currentSettings.copy(backupFolderUri = uri.toString())
            readerPreferences.updateSettings(newSettings)
            Toast.makeText(getApplication(), "Backup Location Selected", Toast.LENGTH_SHORT).show()
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    fun restoreBackup(uri: Uri) {
        val context = getApplication<Application>()
        val restoringMsg = context.getString(R.string.nav_restoring_backup)
        val successMsg = context.getString(R.string.nav_restore_success)
        val failedMsg = context.getString(R.string.nav_restore_failed)

        Toast.makeText(context, restoringMsg, Toast.LENGTH_SHORT).show()
        viewModelScope.launch {
            val success = LibraryBackupRepository(context).restoreBackup(uri)
            if (success) {
                context.imageLoader.memoryCache?.clear()
                context.imageLoader.diskCache?.clear()
                Toast.makeText(context, successMsg, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, failedMsg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    class Factory(
        private val application: Application,
        private val libraryRepository: LibraryRepository,
        private val readerPreferences: ReaderPreferences
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(application, libraryRepository, readerPreferences) as T
        }
    }
}