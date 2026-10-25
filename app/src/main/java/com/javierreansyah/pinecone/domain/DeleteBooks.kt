package com.javierreansyah.pinecone.domain

import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import com.javierreansyah.pinecone.reader.audio.AudioPlaybackClient
import javax.inject.Inject

class DeleteBooks @Inject constructor(
    private val repository: LibraryRepository,
    private val playback: AudioPlaybackClient,
) {
    suspend operator fun invoke(bookIds: Collection<String>) = playback.withPlaybackStopped(bookIds) {
        bookIds.forEach { repository.deleteBook(it) }
    }
}
