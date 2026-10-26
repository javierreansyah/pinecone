package com.javierreansyah.pinecone.ui.features.library

import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import com.javierreansyah.pinecone.domain.DeleteBooks
import com.javierreansyah.pinecone.ui.components.MutationErrorState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Mutations use the owning ViewModel's lifetime; deletion retains playback coordination. */
class BookActions(
    private val scope: CoroutineScope,
    private val repository: LibraryRepository,
    private val deletePublications: DeleteBooks,
) {
    val mutationErrors = MutationErrorState()

    fun deleteBook(bookId: String) = deleteBooks(listOf(bookId))
    fun deleteBooks(bookIds: Collection<String>) {
        scope.launch { mutationErrors.run { deletePublications(bookIds) } }
    }

    fun toggleArchive(bookId: String) {
        scope.launch { mutationErrors.run { repository.toggleArchive(bookId) } }
    }

    fun archiveBooks(bookIds: Collection<String>) = setArchived(bookIds, true)
    fun setArchived(bookIds: Collection<String>, value: Boolean) {
        scope.launch { mutationErrors.run { repository.setArchived(bookIds, value) } }
    }

    fun toggleReadStatus(bookId: String) {
        scope.launch { mutationErrors.run { repository.toggleReadStatus(bookId) } }
    }

    fun markBooksReadStatus(bookIds: Collection<String>, markAsRead: Boolean) {
        scope.launch { mutationErrors.run { repository.setReadStatus(bookIds, markAsRead) } }
    }
}
