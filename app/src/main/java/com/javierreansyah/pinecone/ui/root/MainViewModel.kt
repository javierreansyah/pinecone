package com.javierreansyah.pinecone.ui.root

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.local.preferences.ReaderSettings
import com.javierreansyah.pinecone.data.repository.library.PublicationOperations
import com.javierreansyah.pinecone.data.repository.library.ReadingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val application: Application,
    private val operations: PublicationOperations,
    @com.javierreansyah.pinecone.di.ApplicationScope private val applicationScope: kotlinx.coroutines.CoroutineScope,
    private val reading: ReadingRepository,
    readerPreferences: ReaderPreferences,
    private val startup: com.javierreansyah.pinecone.data.StartupCoordinator,
) : ViewModel() {

    private val _isReady = MutableStateFlow(value = false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()
    val startupState = startup.state

    fun retryStartup() {
        viewModelScope.launch {
            try {
                startup.initialize()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The coordinator publishes the recoverable failure without resetting stored data.
            }
        }
    }

    private val _settings = MutableStateFlow(ReaderSettings())
    val settings: StateFlow<ReaderSettings> = _settings.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private val _importErrorReport = MutableStateFlow<ImportErrorReport?>(null)
    val importErrorReport: StateFlow<ImportErrorReport?> = _importErrorReport.asStateFlow()

    fun dismissImportErrorReport() {
        viewModelScope.launch {
            val dismissed = errorOperation
            _importErrorReport.value = null
            dismissed?.let { operations.acknowledge(it) }
            if (errorOperation == dismissed) errorOperation = null
        }
    }

    init {
        retryStartup()
        viewModelScope.launch {
            startup.awaitReady()
            readerPreferences.readerSettings.collect { newSettings ->
                _settings.value = newSettings

                if (!_isReady.value) {
                    _isReady.value = true
                }
            }
        }
    }

    suspend fun isAudiobook(bookId: String): Boolean =
        reading.rendition(
            bookId,
            "original"
        )?.profile == com.javierreansyah.pinecone.data.publication.ReaderProfile.AUDIO.name

    val importOperations = operations.imports.stateIn(
        viewModelScope,
        kotlinx.coroutines.flow.SharingStarted.Eagerly,
        emptyList()
    )
    val libraryState = operations.libraryState.stateIn(
        viewModelScope,
        kotlinx.coroutines.flow.SharingStarted.Eagerly,
        null
    )
    private var errorOperation: String? = null

    init {
        viewModelScope.launch {
            startup.awaitReady()
            importOperations.collect { rows ->
                rows.filter { it.state.terminal && it.id != errorOperation }.forEach { row ->
                    if (row.state == com.javierreansyah.pinecone.data.model.OperationState.SUCCEEDED) {
                        _toastMessage.emit(application.getString(if (row.total == 0) R.string.import_no_files_found else R.string.nav_import_complete))
                        operations.acknowledge(row.id)
                    } else if (row.state == com.javierreansyah.pinecone.data.model.OperationState.CANCELLED) {
                        operations.acknowledge(row.id)
                    } else if (_importErrorReport.value == null) {
                        errorOperation = row.id
                        _importErrorReport.value = ImportErrorReport(
                            row.failures.ifEmpty { listOf(application.getString(R.string.publication_unknown)) },
                            row.total.coerceAtLeast(1),
                            (row.completed - row.failures.size).coerceAtLeast(0),
                            inputRequired = row.state == com.javierreansyah.pinecone.data.model.OperationState.INPUT_REQUIRED || row.error == "input_required",
                        )
                    }
                }
            }
        }
    }

    fun importBook(uri: Uri) = importBooks(listOf(uri))
    fun importBooks(uris: List<Uri>) {
        if (uris.isEmpty()) return
        enqueue(uris.map { it.lastPathSegment.orEmpty() }) { it.enqueueImports(uris) }
    }

    fun scanFolder(uri: Uri) {
        enqueue(listOf(uri.lastPathSegment.orEmpty())) { it.enqueueScan(uri) }
    }

    private fun enqueue(names: List<String>, submit: suspend (PublicationOperations) -> Unit) {
        val service = operations
        val gate = startup
        val errors = _importErrorReport
        applicationScope.launch {
            try {
                gate.initialize()
                submit(service)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                timber.log.Timber.w(error, "Publication request could not be accepted")
                errors.compareAndSet(
                    null,
                    ImportErrorReport(names, names.size, 0, inputRequired = true)
                )
            }
        }
    }

    fun cancelImport(id: String) {
        viewModelScope.launch { operations.cancel(id) }
    }
}

data class ImportErrorReport(
    val failedFiles: List<String>,
    val totalCount: Int,
    val successCount: Int,
    val inputRequired: Boolean = false,
)
