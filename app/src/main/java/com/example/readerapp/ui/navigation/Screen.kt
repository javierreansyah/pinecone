package com.example.readerapp.ui.navigation

sealed class Screen(val route: String) {
    object Library : Screen("library")
    object Settings : Screen("settings")
}
