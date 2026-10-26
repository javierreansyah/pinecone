package com.javierreansyah.pinecone.ui.root

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.local.preferences.ReaderSettings
import com.javierreansyah.pinecone.data.model.OperationState
import com.javierreansyah.pinecone.data.repository.library.PublicationOperations
import com.javierreansyah.pinecone.data.repository.library.ReadingRepository
import com.javierreansyah.pinecone.ui.features.library.components.ImportReport
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
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

    private val _importReports = MutableStateFlow<List<ImportReport>>(emptyList())
    val importReport = _importReports.map { it.firstOrNull() }.stateIn(
        viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null,
    )

    fun dismissImportReport(id: String) {
        val report = _importReports.value.firstOrNull()?.takeIf { it.id == id } ?: return
        _importReports.update { reports -> reports.filterNot { it.id == report.id } }
        report.operationId?.let { id -> viewModelScope.launch { operations.acknowledge(id) } }
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
    init {
        viewModelScope.launch {
            startup.awaitReady()
            val reported = mutableSetOf<String>()
            importOperations.collect { rows ->
                reported.retainAll(rows.mapTo(mutableSetOf()) { it.id })
                rows.filter { it.state.terminal && reported.add(it.id) }.forEach { row ->
                    if (row.state == OperationState.CANCELLED || row.state == OperationState.SUCCEEDED) {
                        operations.acknowledge(row.id)
                    } else {
                        val report = ImportReport(
                            id = row.id,
                            operationId = row.id,
                            failedFiles = row.failures,
                            successCount = (row.completed - row.failures.size).coerceAtLeast(0),
                            inputRequired = row.state == OperationState.INPUT_REQUIRED || row.error == "input_required",
                        )
                        _importReports.update { it + report }
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
        val reports = _importReports
        applicationScope.launch {
            try {
                gate.initialize()
                submit(service)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                timber.log.Timber.w(error, "Publication request could not be accepted")
                reports.update {
                    it + ImportReport(
                        failedFiles = names, successCount = 0, inputRequired = true,
                    )
                }
            }
        }
    }

    fun cancelImport(id: String) {
        viewModelScope.launch { operations.cancel(id) }
    }
}
