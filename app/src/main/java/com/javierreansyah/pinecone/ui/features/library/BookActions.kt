package com.javierreansyah.pinecone.ui.features.library

import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import com.javierreansyah.pinecone.domain.DeleteBooks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Mutations use the owning ViewModel's lifetime; deletion retains playback coordination. */
class BookActions(
    private val scope: CoroutineScope,
    private val repository: LibraryRepository,
    private val deletePublications: DeleteBooks,
) {
    fun deleteBook(bookId: String) = deleteBooks(listOf(bookId))
    fun deleteBooks(bookIds: Collection<String>) {
        scope.launch { deletePublications(bookIds) }
    }

    fun toggleArchive(bookId: String) {
        scope.launch { repository.toggleArchive(bookId) }
    }

    fun archiveBooks(bookIds: Collection<String>) = setArchived(bookIds, true)
    fun setArchived(bookIds: Collection<String>, value: Boolean) {
        scope.launch { repository.setArchived(bookIds, value) }
    }

    fun toggleReadStatus(bookId: String) {
        scope.launch { repository.toggleReadStatus(bookId) }
    }

    fun markBooksReadStatus(bookIds: Collection<String>, markAsRead: Boolean) {
        scope.launch { repository.setReadStatus(bookIds, markAsRead) }
    }
}
