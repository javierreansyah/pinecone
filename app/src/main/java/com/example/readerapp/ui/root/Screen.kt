package com.example.readerapp.ui.root

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Screen : NavKey {
    @Serializable
    data object Library : Screen

    @Serializable
    data object Archives : Screen

    @Serializable
    data object Settings : Screen

    @Serializable
    data class ShelfDetail(
        val shelfId: String,
        val name: String = "",
        val count: Int = 0
    ) : Screen

    @Serializable
    data class AuthorDetail(val authorName: String) : Screen

    @Serializable
    data class TagDetail(val tagName: String) : Screen

    @Serializable
    data object AllAuthors : Screen

    @Serializable
    data object AllTags : Screen

    @Serializable
    data object Dictionaries : Screen

    @Serializable
    data class BookInfo(val bookId: String) : Screen

    @Serializable
    data class EditBook(val bookId: String) : Screen
}
