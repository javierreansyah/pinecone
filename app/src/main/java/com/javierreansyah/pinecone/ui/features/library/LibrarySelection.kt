package com.javierreansyah.pinecone.ui.features.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class LibrarySelection {
    var ids: Set<String> by mutableStateOf(emptySet())
    var active: Boolean by mutableStateOf(false)

    fun enter(ids: Set<String> = emptySet()) {
        this.ids = ids
        active = true
    }

    fun clear() {
        ids = emptySet()
    }

    fun close() {
        clear()
        active = false
    }

    fun toggle(id: String) {
        ids = if (id in ids) ids - id else ids + id
    }
}
