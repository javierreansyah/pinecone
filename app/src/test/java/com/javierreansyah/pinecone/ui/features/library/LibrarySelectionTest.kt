package com.javierreansyah.pinecone.ui.features.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySelectionTest {
    @Test
    fun clearingKeepsSelectionModeWhileClosingResetsIt() {
        val selection = LibrarySelection()
        selection.enter(setOf("first"))
        selection.toggle("second")
        selection.toggle("first")
        assertEquals(setOf("second"), selection.ids)
        val actionIds = selection.ids
        selection.clear()
        assertTrue(selection.active)
        assertTrue(selection.ids.isEmpty())
        assertEquals(setOf("second"), actionIds)
        selection.close()
        assertFalse(selection.active)
        selection.enter()
        assertTrue(selection.active)
        assertTrue(selection.ids.isEmpty())
    }
}
