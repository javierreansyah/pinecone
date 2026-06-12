package com.example.readerapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import com.example.readerapp.ui.root.MainDrawerWrapper
import com.example.readerapp.ui.root.MainViewModel
import com.example.readerapp.ui.root.NavGraph
import com.example.readerapp.ui.theme.AppTheme

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        val app = application as ReaderApplication
        viewModel = ViewModelProvider(
            this,
            MainViewModel.Factory(
                application = app,
                libraryRepository = app.libraryRepository,
                readerPreferences = app.readerPreferences
            )
        )[MainViewModel::class.java]

        // Keep splash screen visible until the real settings are loaded from DataStore,
        // preventing a flicker from default theme (Dynamic) to the user's actual theme.
        splashScreen.setKeepOnScreenCondition { !viewModel.isReady.value }

        handleIntent(intent)

        enableEdgeToEdge()
        setContent {
            val isReady by viewModel.isReady.collectAsState()
            if (!isReady) {
                return@setContent
            }

            val settings by viewModel.settings.collectAsState()
            val darkTheme = when (settings.themeMode) {
                "Light" -> false
                "Dark" -> true
                else -> isSystemInDarkTheme()
            }

            AppTheme(
                darkTheme = darkTheme,
                colorPalette = settings.colorPalette,
                themeContrast = settings.themeContrast
            ) {
                val initialBackStack = androidx.compose.runtime.remember {
                    com.example.readerapp.ui.root.getInitialBackStackFromIntent(intent)
                }

                val backStack =
                    androidx.navigation3.runtime.rememberNavBackStack(*initialBackStack.toTypedArray())
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

                com.example.readerapp.ui.root.HandleDeepLinks(
                    backStack = backStack,
                    addOnNewIntentListener = { addOnNewIntentListener(it) },
                    removeOnNewIntentListener = { removeOnNewIntentListener(it) }
                )

                MainDrawerWrapper(
                    backStack = backStack,
                    drawerState = drawerState,
                    viewModel = viewModel
                ) {
                    NavGraph(
                        backStack = backStack,
                        drawerState = drawerState
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) {
            val uri = intent.data ?: return
            if (uri.scheme == "file" || uri.scheme == "content") {
                viewModel.importBook(uri)
            }
        }
    }
}
