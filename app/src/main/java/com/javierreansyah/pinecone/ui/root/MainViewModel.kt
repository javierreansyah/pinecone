package com.javierreansyah.pinecone.ui.root

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.local.preferences.ReaderSettings
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val application: Application,
    private val libraryRepository: LibraryRepository,
    readerPreferences: ReaderPreferences,
) : ViewModel() {

    private val _isReady = MutableStateFlow(value = false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _settings = MutableStateFlow(ReaderSettings())
    val settings: StateFlow<ReaderSettings> = _settings.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private val _importErrorReport = MutableStateFlow<ImportErrorReport?>(null)
    val importErrorReport: StateFlow<ImportErrorReport?> = _importErrorReport.asStateFlow()

    fun dismissImportErrorReport() {
        _importErrorReport.value = null
    }

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
            val app = application
            _toastMessage.emit(
                app.resources.getQuantityString(
                    R.plurals.nav_importing_files,
                    1,
                    1,
                ),
            )
            val fileName = getFileNameFromUri(uri)
            val result = libraryRepository.importBook(uri)
            if (result != null) {
                _toastMessage.emit(app.getString(R.string.nav_import_complete))
            } else {
                _importErrorReport.value = ImportErrorReport(
                    failedFiles = listOf(fileName),
                    totalCount = 1,
                    successCount = 0,
                )
            }
        }
    }

    fun importBooks(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val app = application
            _toastMessage.emit(
                app.resources.getQuantityString(
                    R.plurals.nav_importing_files,
                    uris.size,
                    uris.size,
                ),
            )
            var successCount = 0
            val failedFiles = mutableListOf<String>()
            uris.forEach { uri ->
                val fileName = getFileNameFromUri(uri)
                val result = libraryRepository.importBook(uri)
                if (result != null) {
                    successCount++
                } else {
                    failedFiles.add(fileName)
                }
            }
            if (failedFiles.isEmpty()) {
                _toastMessage.emit(app.getString(R.string.nav_import_complete))
            } else {
                _importErrorReport.value = ImportErrorReport(
                    failedFiles = failedFiles,
                    totalCount = uris.size,
                    successCount = successCount,
                )
            }
        }
    }

    fun scanFolder(uri: Uri) {
        viewModelScope.launch {
            val app = application
            _toastMessage.emit(app.getString(R.string.nav_scanning_folder))
            val root = DocumentFile.fromTreeUri(app, uri)
            val publicationFiles =
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    mutableListOf<DocumentFile>().also { files ->
                        root?.let {
                            collectPublicationFiles(
                                it,
                                files
                            )
                        }
                    }
                }
            if (publicationFiles.isEmpty()) {
                _toastMessage.emit(app.getString(R.string.import_no_files_found))
                return@launch
            }
            var successCount = 0
            val failedFiles = mutableListOf<String>()
            publicationFiles.forEach { file ->
                val result =
                    if (file.isDirectory) libraryRepository.importPublicationFolder(file) else libraryRepository.importBook(
                        file.uri
                    )
                if (result != null) {
                    successCount++
                } else {
                    failedFiles.add(file.name ?: app.getString(R.string.publication_unknown))
                }
            }
            if (failedFiles.isEmpty()) {
                _toastMessage.emit(app.getString(R.string.nav_folder_scan_complete))
            } else {
                _importErrorReport.value = ImportErrorReport(
                    failedFiles = failedFiles,
                    totalCount = publicationFiles.size,
                    successCount = successCount,
                )
            }
        }
    }

    private fun collectPublicationFiles(file: DocumentFile, result: MutableList<DocumentFile>) {
        if (file.isDirectory) {
            if (file.findFile("manifest.json") != null) {
                result.add(file)
                return
            }
            file.listFiles().forEach { child ->
                collectPublicationFiles(child, result)
            }
        } else {
            val name = file.name?.lowercase() ?: ""
            if (name.substringAfterLast('.') in com.javierreansyah.pinecone.data.publication.PublicationFormats.extensions) {
                result.add(file)
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        val context = application
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index =
                            cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            val name = cursor.getString(index)
                            if (!name.isNullOrBlank()) return name
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString()
    }


}

data class ImportErrorReport(
    val failedFiles: List<String>,
    val totalCount: Int,
    val successCount: Int,
)
