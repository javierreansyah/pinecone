package com.javierreansyah.pinecone.ui.features.library.info

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.PineconeApplication
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.database.library.AuthorEntity
import com.javierreansyah.pinecone.data.local.database.library.TagEntity
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EditBookViewModel(
    application: Application, private val bookId: String,
) : AndroidViewModel(application) {

    private val repository: LibraryRepository =
        (application as PineconeApplication).libraryRepository

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
                val (_, bookTitle, bookCoverPath, _, _, _, _, _, _, _, _, _, bookDescription) = bookDetails.book
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loaded = true,
                        title = bookTitle,
                        description = bookDescription ?: "",
                        existingCoverPath = bookCoverPath,
                        tags = bookDetails.tags.map { (_, tagName) -> tagName },
                        authors = bookDetails.credits.filter { it.role == "author" }
                            .sortedBy { it.position }.map { it.name },
                        metadata = com.javierreansyah.pinecone.data.publication.PublicationMetadata.decode(
                            bookDetails.book.metadataJson
                        ),
                        credits = bookDetails.credits,
                        creditDrafts = bookDetails.credits.groupBy { it.role }
                            .mapValues { (_, values) ->
                                values.sortedBy { it.position }.joinToString("\n") { it.name }
                            },
                        languageDraft = com.javierreansyah.pinecone.data.publication.PublicationMetadata.decode(
                            bookDetails.book.metadataJson
                        ).languages.joinToString(", "),
                        identifierDraft = com.javierreansyah.pinecone.data.publication.PublicationMetadata.decode(
                            bookDetails.book.metadataJson
                        ).identifiers.entries.joinToString("\n") { entry -> "${entry.key}=${entry.value}" },
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = getApplication<Application>().getString(R.string.book_not_found)
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
        if (trimmed.isNotEmpty() && !_uiState.value.tags.contains(trimmed)) {
            _uiState.update { it.copy(tags = it.tags + trimmed) }
        }
    }

    fun removeTag(tag: String) {
        _uiState.update { it.copy(tags = it.tags.filter { t -> t != tag }) }
    }

    fun addAuthor(author: String) {
        val trimmed = author.trim()
        if (trimmed.isNotEmpty() && !_uiState.value.authors.contains(trimmed)) {
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
                val (_, stateTitle, stateDescription, stateCoverUri, _, stateTags, stateAuthors) = _uiState.value
                val state = _uiState.value
                validate(stateTitle.isNotBlank(), R.string.publication_title_required)
                val drafts = state.creditDrafts + ("author" to stateAuthors.joinToString("\n"))
                val credits = drafts.flatMap { (role, text) ->
                    text.lines().map(String::trim).filter(String::isNotBlank)
                        .mapIndexed { index, name ->
                            state.credits.firstOrNull { it.role == role && it.name == name }
                                ?.copy(position = index)
                                ?: com.javierreansyah.pinecone.data.local.database.library.PublicationCreditEntity(
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
                    title = stateTitle,
                    description = stateDescription.ifBlank { null },
                    coverUri = stateCoverUri,
                    authors = (stateAuthors + credits.filter { it.role == "writer" }
                        .map { it.name }).distinct(),
                    tags = stateTags,
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
                        error = getApplication<Application>().getString(R.string.publication_save_failed)
                    )
                }
            }
        }
    }

    private class MetadataValidationException(message: String) : IllegalArgumentException(message)

    private fun validate(valid: Boolean, @androidx.annotation.StringRes message: Int) {
        if (!valid) throw MetadataValidationException(
            getApplication<Application>().getString(
                message
            )
        )
    }

    fun updateRichMetadata(metadata: com.javierreansyah.pinecone.data.publication.PublicationMetadata) {
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

    class Factory(
        private val application: Application, private val bookId: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return EditBookViewModel(application, bookId) as T
        }
    }
}
