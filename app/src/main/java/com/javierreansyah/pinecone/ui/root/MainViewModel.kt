package com.javierreansyah.pinecone.ui.root

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.local.preferences.ReaderSettings
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    application: Application,
    private val libraryRepository: LibraryRepository,
    readerPreferences: ReaderPreferences
) : AndroidViewModel(application) {

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _settings = MutableStateFlow(ReaderSettings())
    val settings: StateFlow<ReaderSettings> = _settings.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()

    init {
        viewModelScope.launch {
            readerPreferences.readerSettings.collect { newSettings ->
                _settings.value = newSettings

                if (!_isReady.value) {
                    _isReady.value = true
                }
            }
        }
    }

    fun importBook(uri: Uri) {
        viewModelScope.launch {
            val result = libraryRepository.importBook(uri)
            if (result != null) {
                _toastMessage.emit(getApplication<Application>().getString(R.string.nav_import_complete))
            }
        }
    }

    fun importBooks(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _toastMessage.emit(
                getApplication<Application>().resources.getQuantityString(
                    R.plurals.nav_importing_files,
                    uris.size,
                    uris.size
                )
            )
            uris.forEach { uri ->
                libraryRepository.importBook(uri)
            }
            _toastMessage.emit(getApplication<Application>().getString(R.string.nav_import_complete))
        }
    }

    fun scanFolder(uri: Uri) {
        viewModelScope.launch {
            _toastMessage.emit(getApplication<Application>().getString(R.string.nav_scanning_folder))
            val root = DocumentFile.fromTreeUri(getApplication(), uri)
            if (root != null) {
                importFromDocumentFile(root)
            }
            _toastMessage.emit(getApplication<Application>().getString(R.string.nav_folder_scan_complete))
        }
    }

    private suspend fun importFromDocumentFile(file: DocumentFile) {
        if (file.isDirectory) {
            file.listFiles().forEach { child ->
                importFromDocumentFile(child)
            }
        } else {
            val name = file.name?.lowercase() ?: ""
            val supportedExtensions = listOf(".epub")
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
