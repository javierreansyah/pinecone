package com.example.readerapp.ui.navigation

sealed class Screen(val route: String) {
    object Library : Screen("library")
    object Archives : Screen("archives")
    object Settings : Screen("settings")
    object ShelfDetail : Screen("shelf_detail/{shelfId}") {
        fun createRoute(shelfId: String) = "shelf_detail/$shelfId"
    }
}
