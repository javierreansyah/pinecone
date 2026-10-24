package com.javierreansyah.pinecone.ui.features.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterSortPreferencesTest {
    @Test
    fun `changing sort resets direction and selecting it again reverses direction`() {
        for (sort in SortType.entries) {
            val initial = FilterSortPreferences(sortType = SortType.Title)
            val other = initial.copy(sortType = SortType.entries.first { it != sort })
            val selected = other.selectSort(sort)
            assertEquals(sort, selected.sortType)
            assertEquals(sort != SortType.LastRead, selected.isAscending)
            assertEquals(!selected.isAscending, selected.selectSort(sort).isAscending)
            assertEquals(selected, selected.selectSort(sort).selectSort(sort))
        }
    }

    @Test
    fun `filters allow an empty selection and toggling twice restores preferences`() {
        val prefs = FilterSortPreferences(
            layoutMode = LayoutMode.BigList,
            sortType = SortType.Author,
            selectedFormats = setOf(FileFilter.PDF),
            selectedStatus = setOf(StatusFilter.Finished),
            selectedShelfFilter = setOf(ShelfFilter.Unshelved),
        )
        assertTrue(prefs.toggleFormat(FileFilter.PDF).selectedFormats.isEmpty())
        assertTrue(prefs.toggleStatus(StatusFilter.Finished).selectedStatus.isEmpty())
        assertTrue(prefs.toggleShelf(ShelfFilter.Unshelved).selectedShelfFilter.isEmpty())
        assertEquals(prefs, prefs.toggleFormat(FileFilter.Audio).toggleFormat(FileFilter.Audio))
        assertEquals(
            prefs,
            prefs.toggleStatus(StatusFilter.Reading).toggleStatus(StatusFilter.Reading)
        )
        assertEquals(prefs, prefs.toggleShelf(ShelfFilter.Shelves).toggleShelf(ShelfFilter.Shelves))
        assertFalse(FileFilter.Audio in prefs.selectedFormats)
    }
}
