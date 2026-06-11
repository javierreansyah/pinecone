package com.example.readerapp.ui.root

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.readerapp.data.local.preferences.ReaderPreferences
import com.example.readerapp.data.local.preferences.ReaderSettings
import com.example.readerapp.data.repository.library.LibraryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    application: Application,
    private val libraryRepository: LibraryRepository,
    readerPreferences: ReaderPreferences
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