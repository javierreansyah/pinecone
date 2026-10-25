package com.javierreansyah.pinecone.ui.features.library.info

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.database.library.AuthorEntity
import com.javierreansyah.pinecone.data.local.database.library.PublicationCreditEntity
import com.javierreansyah.pinecone.data.local.database.library.TagEntity
import com.javierreansyah.pinecone.data.publication.PublicationMetadata
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import com.javierreansyah.pinecone.ui.features.library.fileFormat
import com.javierreansyah.pinecone.ui.features.library.publicationWording
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = EditBookViewModel.Factory::class)
class EditBookViewModel @AssistedInject constructor(
    private val application: Application,
    @Assisted private val bookId: String,
    private val repository: LibraryRepository,
) : ViewModel() {


    private val _uiState = MutableStateFlow(EditBookUiState())
    val uiState: StateFlow<EditBookUiState> = _uiState.asStateFlow()

    val allAuthors: StateFlow<List<AuthorEntity>> = repository.getAllAuthors()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTags: StateFlow<List<TagEntity>> = repository.getAllTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadBook()
    }

    private fun loadBook() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val bookDetails = repository.getBook(bookId)
            if (bookDetails != null) {
                val book = bookDetails.book
                val metadata = PublicationMetadata.decode(book.metadataJson)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loaded = true,
                        wording = publicationWording(listOf(book.fileFormat())),
                        title = book.title,
                        description = book.description ?: "",
                        existingCoverPath = book.coverPath,
                        tags = bookDetails.tags.map { it -> it.name },
                        authors = bookDetails.credits.filter { it -> it.role == "author" }
                            .sortedBy { it -> it.position }.map { it -> it.name },
                        metadata = metadata,
                        credits = bookDetails.credits,
                        creditDrafts = bookDetails.credits.groupBy { it -> it.role }
                            .mapValues { (_, values) ->
                                values.sortedBy { it -> it.position }
                                    .joinToString("\n") { it -> it.name }
                            },
                        languageDraft = metadata.languages.joinToString(", "),
                        identifierDraft = metadata.identifiers.entries.joinToString("\n") { entry ->
                            "${entry.key}=${entry.value}"
                        },
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = application.getString(R.string.publication_not_found)
                    )
                }
            }
        }
    }

    fun updateTitle(title: String) {
        _uiState.update { it.copy(title = title) }
    }

    fun updateDescription(description: String) {
        _uiState.update { it.copy(description = description) }
    }

    fun updateCoverUri(uri: Uri?) {
        _uiState.update { it.copy(coverUri = uri) }
    }

    fun addTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isNotEmpty() && trimmed !in _uiState.value.tags) {
            _uiState.update { it.copy(tags = it.tags + trimmed) }
        }
    }

    fun removeTag(tag: String) {
        _uiState.update { it.copy(tags = it.tags.filter { t -> t != tag }) }
    }

    fun addAuthor(author: String) {
        val trimmed = author.trim()
        if (trimmed.isNotEmpty() && trimmed !in _uiState.value.authors) {
            _uiState.update { it.copy(authors = it.authors + trimmed) }
        }
    }

    fun removeAuthor(author: String) {
        _uiState.update { it.copy(authors = it.authors.filter { a -> a != author }) }
    }

    fun saveChanges() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val state = _uiState.value
                validate(state.title.isNotBlank(), R.string.publication_title_required)
                val drafts = state.creditDrafts + ("author" to state.authors.joinToString("\n"))
                val credits = drafts.flatMap { (role, text) ->
                    text.lines().map(String::trim).filter(String::isNotBlank)
                        .mapIndexed { index, name ->
                            state.credits.firstOrNull { it.role == role && it.name == name }
                                ?.copy(position = index)
                                ?: PublicationCreditEntity(
                                    bookId,
                                    role,
                                    index,
                                    name
                                )
                        }
                }
                val identifiers =
                    state.identifierDraft.lines().filter(String::isNotBlank).associate { line ->
                        validate('=' in line, R.string.publication_identifier_format_error)
                        val scheme = line.substringBefore('=').trim()
                        val value = line.substringAfter('=').trim()
                        validate(
                            scheme.isNotBlank() && value.isNotBlank(),
                            R.string.publication_identifier_empty_error
                        )
                        scheme to value
                    }
                repository.updateBookMetadata(
                    bookId = bookId,
                    title = state.title,
                    description = state.description.ifBlank { null },
                    coverUri = state.coverUri,
                    authors = (state.authors + credits.filter { it.role == "writer" }
                        .map { it.name }).distinct(),
                    tags = state.tags,
                    metadata = state.metadata.copy(
                        languages = state.languageDraft.split(',').map(String::trim)
                            .filter(String::isNotBlank), identifiers = identifiers
                    ),
                    credits = credits,
                )
                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: MetadataValidationException) {
                _uiState.update { it.copy(isSaving = false, error = error.message) }
            } catch (error: Exception) {
                timber.log.Timber.e(error, "Could not save publication metadata")
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = application.getString(R.string.publication_save_failed)
                    )
                }
            }
        }
    }

    private class MetadataValidationException(message: String) : IllegalArgumentException(message)

    private fun validate(valid: Boolean, @androidx.annotation.StringRes message: Int) {
        if (!valid) throw MetadataValidationException(
            application.getString(
                message
            )
        )
    }

    fun updateRichMetadata(metadata: PublicationMetadata) {
        _uiState.update { it.copy(metadata = metadata) }
    }

    fun updateCredit(role: String, text: String) {
        _uiState.update { it.copy(creditDrafts = it.creditDrafts + (role to text)) }
    }

    fun updateLanguages(text: String) {
        _uiState.update { it.copy(languageDraft = text) }
    }

    fun updateIdentifiers(text: String) {
        _uiState.update { it.copy(identifierDraft = text) }
    }


    @dagger.assisted.AssistedFactory
    interface Factory {
        fun create(bookId: String): EditBookViewModel
    }
}
