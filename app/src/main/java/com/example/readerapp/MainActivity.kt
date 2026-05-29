package com.example.readerapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.readerapp.data.local.ReaderPreferences
import com.example.readerapp.data.local.ReaderSettings
import com.example.readerapp.ui.library.LibraryScreen
import com.example.readerapp.ui.navigation.Screen
import com.example.readerapp.ui.settings.SettingsScreen
import com.example.readerapp.ui.theme.ReaderAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val readerPreferences = remember { ReaderPreferences(context) }
            val settings by readerPreferences.readerSettings.collectAsState(initial = ReaderSettings())
            val darkTheme = when (settings.themeMode) {
                "Light" -> false
                "Dark" -> true
                else -> isSystemInDarkTheme()
            }
            val useDynamic = settings.colorPalette == "Dynamic"

            ReaderAppTheme(
                darkTheme = darkTheme,
                dynamicColor = useDynamic,
                colorPalette = settings.colorPalette
            ) {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = Screen.Library.route
                ) {
                    composable(Screen.Library.route) {
                        LibraryScreen(
                            onNavigateToReader = { bookId ->
                                val intent = android.content.Intent(context, com.example.readerapp.ui.reader.ReaderActivity::class.java).apply {
                                    putExtra(com.example.readerapp.ui.reader.ReaderActivity.EXTRA_BOOK_ID, bookId)
                                }
                                context.startActivity(intent)
                            },
                            onNavigateToSettings = {
                                navController.navigate(Screen.Settings.route)
                            }
                        )
                    }
                    composable(Screen.Settings.route) {
                        SettingsScreen(
                            onNavigateBack = {
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }
}
