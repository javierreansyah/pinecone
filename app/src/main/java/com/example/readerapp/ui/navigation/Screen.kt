package com.example.readerapp.ui.navigation

sealed class Screen(val route: String) {
    object Library : Screen("library")
    object Archives : Screen("archives")
    object Settings : Screen("settings")
    object ShelfDetail : Screen("shelf_detail/{shelfId}") {
        fun createRoute(shelfId: String) = "shelf_detail/$shelfId"
    }
    object AuthorDetail : Screen("author_detail/{authorName}") {
        fun createRoute(authorName: String) = "author_detail/$authorName"
    }
    object TagDetail : Screen("tag_detail/{tagName}") {
        fun createRoute(tagName: String) = "tag_detail/$tagName"
    }
    object AllAuthors : Screen("all_authors")
    object AllTags : Screen("all_tags")
}
