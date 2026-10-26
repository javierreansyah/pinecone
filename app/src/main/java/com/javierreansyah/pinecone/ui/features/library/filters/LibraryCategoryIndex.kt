package com.javierreansyah.pinecone.ui.features.library.filters

import com.javierreansyah.pinecone.data.model.Book

/** Build memberships once per library snapshot, rather than scanning every book for every label. */
internal class LibraryCategoryIndex(books: List<Book>) {
    val authors: Map<String, List<Book>>
    val tags: Map<String, List<Book>>
    val spaces: Map<String, List<Book>>
    val spaceNames: Map<String, List<Book>>
    init {
        val byAuthor = linkedMapOf<String, MutableList<Book>>()
        val byTag = linkedMapOf<String, MutableList<Book>>()
        val bySpace = linkedMapOf<String, MutableList<Book>>()
        val bySpaceName = linkedMapOf<String, MutableList<Book>>()
        books.forEach { book ->
            book.authors.distinct().forEach { byAuthor.getOrPut(it) { mutableListOf() }.add(book) }
            book.tags.distinct().forEach { byTag.getOrPut(it) { mutableListOf() }.add(book) }
            if (!book.isUnsorted) book.spaceIds.distinct().forEach { bySpace.getOrPut(it) { mutableListOf() }.add(book) }
            book.spaces.map { it.name }.distinct().forEach { bySpaceName.getOrPut(it) { mutableListOf() }.add(book) }
        }
        authors = byAuthor
        tags = byTag
        spaces = bySpace
        spaceNames = bySpaceName
    }
}
