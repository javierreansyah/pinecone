package com.javierreansyah.pinecone.ui.features.library.organize

import androidx.compose.ui.state.ToggleableState
import com.javierreansyah.pinecone.data.local.database.library.ShelfEntity
import com.javierreansyah.pinecone.data.local.database.library.SpaceEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class OrganizeShelfGroupsTest {
    @Test
    fun `checked spaces move ahead of other shelf groups without hiding unchecked groups`() {
        val all = space("All", ToggleableState.On)
        val fiction = space("Fiction", ToggleableState.Off)
        val work = space("Work", ToggleableState.Indeterminate)
        val input = listOf(all, fiction, work)

        assertEquals(listOf(all, work, fiction), input.prioritizedShelfGroups())
        val checkedFiction = fiction.copy(state = ToggleableState.On)
        assertEquals(
            listOf(all, checkedFiction, work),
            listOf(all, checkedFiction, work).prioritizedShelfGroups(),
        )
        assertEquals(listOf(all, fiction, work), input)
    }

    @Test
    fun `empty spaces have no shelf section and shelf selection does not hide a section`() {
        val fiction = space("Fiction", ToggleableState.Off)
        val empty = space("Empty", ToggleableState.On).copy(shelves = emptyList())

        assertEquals(listOf(fiction), listOf(empty, fiction).prioritizedShelfGroups())
        assertEquals(ToggleableState.Off, fiction.shelves.single().state)
    }

    private fun space(name: String, state: ToggleableState) = SpaceWithShelvesItemState(
        space = SpaceEntity(name, name),
        state = state,
        shelves = listOf(
            ShelfItemState(
                ShelfEntity("$name-shelf", name, "Shelf"),
                ToggleableState.Off
            )
        ),
    )
}
