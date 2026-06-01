package com.example.readerapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.readerapp.data.local.ReaderPreferences
import com.example.readerapp.data.local.ReaderSettings
import com.example.readerapp.ui.features.library.LibraryScreen
import com.example.readerapp.ui.navigation.Screen
import com.example.readerapp.ui.features.settings.SettingsScreen
import com.example.readerapp.ui.features.library.ArchiveScreen
import com.example.readerapp.ui.features.library.ShelfDetailScreen
import com.example.readerapp.ui.theme.AppTheme
import androidx.compose.material3.*
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

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

            AppTheme(
                darkTheme = darkTheme
            ) {
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            Text("Reader App", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                            HorizontalDivider()
                            NavigationDrawerItem(
                                label = { Text("Library") },
                                selected = false,
                                onClick = {
                                    navController.navigate(Screen.Library.route) {
                                        popUpTo(navController.graph.startDestinationId)
                                        launchSingleTop = true
                                    }
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                            NavigationDrawerItem(
                                label = { Text("Archives") },
                                selected = false,
                                onClick = {
                                    navController.navigate(Screen.Archives.route)
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                            NavigationDrawerItem(
                                label = { Text("Settings") },
                                selected = false,
                                onClick = {
                                    navController.navigate(Screen.Settings.route)
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                        }
                    }
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Library.route
                    ) {
                    composable(Screen.Library.route) {
                        LibraryScreen(
                            onNavigateToReader = { bookId ->
                                val intent = android.content.Intent(context, com.example.readerapp.ui.features.reader.ReaderActivity::class.java).apply {
                                    putExtra(com.example.readerapp.ui.features.reader.ReaderActivity.EXTRA_BOOK_ID, bookId)
                                }
                                context.startActivity(intent)
                            },
                            onOpenDrawerClick = {
                                scope.launch { drawerState.open() }
                            },
                            onNavigateToShelf = { shelfId ->
                                navController.navigate(Screen.ShelfDetail.createRoute(shelfId))
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
                    composable(Screen.Archives.route) {
                        ArchiveScreen(
                            onNavigateBack = {
                                navController.popBackStack()
                            },
                            onNavigateToReader = { bookId ->
                                val intent = android.content.Intent(context, com.example.readerapp.ui.features.reader.ReaderActivity::class.java).apply {
                                    putExtra(com.example.readerapp.ui.features.reader.ReaderActivity.EXTRA_BOOK_ID, bookId)
                                }
                                context.startActivity(intent)
                            }
                        )
                    }
                    composable(Screen.ShelfDetail.route) { backStackEntry ->
                        val shelfId = backStackEntry.arguments?.getString("shelfId") ?: return@composable
                        ShelfDetailScreen(
                            shelfId = shelfId,
                            onNavigateBack = {
                                navController.popBackStack()
                            },
                            onNavigateToReader = { bookId ->
                                val intent = android.content.Intent(context, com.example.readerapp.ui.features.reader.ReaderActivity::class.java).apply {
                                    putExtra(com.example.readerapp.ui.features.reader.ReaderActivity.EXTRA_BOOK_ID, bookId)
                                }
                                context.startActivity(intent)
                            }
                        )
                    }
                    }
                }
            }
        }
    }
}
