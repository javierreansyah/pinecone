package com.javierreansyah.pinecone.ui.root

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioPlayerNavigationTest {
    @Test
    fun `opening the player preserves the originating screen`() {
        val stack = mutableListOf<NavKey>(Screen.Library, Screen.Archives)
        stack.openAudioPlayer("audio")
        assertEquals(listOf(Screen.Library, Screen.Archives, Screen.AudioPlayer("audio")), stack)
    }

    @Test
    fun `media controls expand the player without changing the underlying screen`() {
        val stack = mutableListOf<NavKey>(
            Screen.Library,
            Screen.AudioPlayer("audio"),
            Screen.BookInfo("audio")
        )
        stack.openAudioPlayer("audio")
        assertEquals(
            listOf(Screen.Library, Screen.BookInfo("audio"), Screen.AudioPlayer("audio")),
            stack
        )
        stack.openAudioPlayer("audio")
        assertEquals(3, stack.size)
    }

    @Test
    fun `a different audiobook opens a different destination`() {
        val stack = mutableListOf<NavKey>(Screen.Library, Screen.AudioPlayer("first"))
        stack.openAudioPlayer("second")
        assertEquals(Screen.AudioPlayer("second"), stack.last())
        assertEquals(2, stack.size)
    }

    @Test
    fun `a bookmark jump updates the player destination without a duplicate`() {
        val stack = mutableListOf<NavKey>(
            Screen.Library,
            Screen.AudioPlayer("audio"),
            Screen.BookInfo("audio")
        )
        stack.openAudioPlayer("audio", "locator-json")
        assertEquals(
            listOf(
                Screen.Library,
                Screen.BookInfo("audio"),
                Screen.AudioPlayer("audio", "locator-json")
            ), stack
        )
    }

    @Test
    fun `collapsing removes only the sheet request`() {
        val stack =
            mutableListOf<NavKey>(Screen.Library, Screen.Archives, Screen.AudioPlayer("audio"))
        stack.collapseAudioPlayer()
        assertEquals(listOf(Screen.Library, Screen.Archives), stack)
    }
}
