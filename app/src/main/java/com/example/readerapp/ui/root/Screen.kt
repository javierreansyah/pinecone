package com.example.readerapp.ui.root

import android.net.Uri

sealed class Screen(val route: String) {
    object Library : Screen("library")
    object Archives : Screen("archives")
    object Settings : Screen("settings")
    object ShelfDetail : Screen("shelf_detail/{shelfId}?name={name}&count={count}") {
        fun createRoute(shelfId: String, name: String = "", count: Int = 0): String {
            val encodedName = Uri.encode(name)
            return "shelf_detail/$shelfId?name=$encodedName&count=$count"
        }
    }
    object AuthorDetail : Screen("author_detail/{authorName}") {
        fun createRoute(authorName: String) = "author_detail/$authorName"
    }
    object TagDetail : Screen("tag_detail/{tagName}") {
        fun createRoute(tagName: String) = "tag_detail/$tagName"
    }
    object AllAuthors : Screen("all_authors")
    object AllTags : Screen("all_tags")
    object Dictionaries : Screen("dictionaries")
    object BookInfo : Screen("book_info/{bookId}") {
        fun createRoute(bookId: String) = "book_info/$bookId"
    }
    object EditBook : Screen("edit_book/{bookId}") {
        fun createRoute(bookId: String) = "edit_book/$bookId"
    }
}
