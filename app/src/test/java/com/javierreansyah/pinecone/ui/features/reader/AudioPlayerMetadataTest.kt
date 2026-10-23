package com.javierreansyah.pinecone.ui.features.reader

import com.javierreansyah.pinecone.reader.audio.AudioPlaybackState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPlayerMetadataTest {
    @Test
    fun `player waits for metadata from the correct book`() {
        assertFalse(
            audioPlayerMetadataReady(
                "book",
                AudioPlaybackState(bookId = "book", loading = true)
            )
        )
        assertFalse(
            audioPlayerMetadataReady(
                "book",
                AudioPlaybackState(bookId = "other", metadataLoaded = true)
            )
        )
        assertTrue(
            audioPlayerMetadataReady(
                "book",
                AudioPlaybackState(bookId = "book", metadataLoaded = true)
            )
        )
    }

    @Test
    fun `missing author and buffering do not hide loaded content`() {
        assertTrue(
            audioPlayerMetadataReady(
                "book", AudioPlaybackState(
                    bookId = "book",
                    title = "Title",
                    metadataLoaded = true,
                    loading = true,
                )
            )
        )
    }

    @Test
    fun `opening errors are displayed instead of a perpetual loading indicator`() {
        assertTrue(
            audioPlayerMetadataReady(
                "book",
                AudioPlaybackState(bookId = "book", error = "Could not open")
            )
        )
    }
}
