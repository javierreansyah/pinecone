package com.javierreansyah.pinecone

import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.javierreansyah.pinecone.ui.features.library.components.book.LocalCoverDisplayMode
import com.javierreansyah.pinecone.ui.root.MainViewModel
import com.javierreansyah.pinecone.ui.root.NavGraph
import com.javierreansyah.pinecone.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Keep splash screen visible until the real settings are loaded from DataStore,
        // preventing a flicker from default theme (Dynamic) to the user's actual theme.
        splashScreen.setKeepOnScreenCondition {
            !viewModel.isReady.value && viewModel.startupState.value !is com.javierreansyah.pinecone.data.StartupState.Failed
        }

        handleIntent(intent)

        enableEdgeToEdge()
        setContent {
            val isReady by viewModel.isReady.collectAsStateWithLifecycle()
            val startup by viewModel.startupState.collectAsStateWithLifecycle()
            if (!isReady) {
                if (startup is com.javierreansyah.pinecone.data.StartupState.Failed) {
                    AppTheme {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = {},
                            title = { androidx.compose.material3.Text(getString(R.string.startup_failed)) },
                            text = { androidx.compose.material3.Text(getString(R.string.startup_retry_description)) },
                            confirmButton = {
                                androidx.compose.material3.TextButton(onClick = viewModel::retryStartup) {
                                    androidx.compose.material3.Text(getString(R.string.reader_audio_retry))
                                }
                            },
                        )
                    }
                }
                return@setContent
            }

            val settings = viewModel.settings.collectAsStateWithLifecycle()
            val darkTheme = when (settings.value.themeMode) {
                "Light" -> false
                "Dark" -> true
                else -> isSystemInDarkTheme()
            }

            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { darkTheme },
                )
                onDispose {}
            }

            AppTheme(
                darkTheme = darkTheme,
                colorPalette = settings.value.colorPalette,
                themeContrast = settings.value.themeContrast,
            ) {
                val initialBackStack = androidx.compose.runtime.remember {
                    com.javierreansyah.pinecone.ui.root.getInitialBackStackFromIntent(
                        intent,
                        settings.value.unsortedEnabled
                    )
                }

                val backStack =
                    androidx.navigation3.runtime.rememberNavBackStack(*initialBackStack.toTypedArray())

                com.javierreansyah.pinecone.ui.root.HandleDeepLinks(
                    backStack = backStack,
                    unsortedEnabled = settings.value.unsortedEnabled,
                    addOnNewIntentListener = { addOnNewIntentListener(it) },
                ) { removeOnNewIntentListener(it) }

                com.javierreansyah.pinecone.ui.root.AudioPlayerSheetHost(
                    backStack = backStack,
                ) {
                    CompositionLocalProvider(LocalCoverDisplayMode provides settings.value.coverDisplayMode) {
                        NavGraph(
                            backStack = backStack,
                            mainViewModel = viewModel,
                        )
                    }
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
