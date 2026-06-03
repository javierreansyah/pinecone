package com.example.readerapp

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import android.widget.Toast
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Upload
import com.composables.icons.materialsymbols.outlined.Folder
import com.composables.icons.materialsymbols.outlined.Archive
import com.composables.icons.materialsymbols.outlined.Settings
import com.example.readerapp.data.repository.BookRepository
import com.example.readerapp.data.local.ReaderPreferences
import com.example.readerapp.data.local.ReaderSettings
import com.example.readerapp.ui.features.library.LibraryScreen
import com.example.readerapp.ui.navigation.Screen
import com.example.readerapp.ui.features.settings.SettingsScreen
import com.example.readerapp.ui.features.library.ArchiveScreen
import com.example.readerapp.ui.features.library.ShelfDetailScreen
import com.example.readerapp.ui.features.library.FilterResultScreen
import com.example.readerapp.ui.theme.AppTheme
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        handleIntent(intent)

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

                BackHandler(enabled = drawerState.isOpen) {
                    scope.launch { drawerState.close() }
                }

                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenMultipleDocuments(),
                    onResult = { uris ->
                        if (uris.isNotEmpty()) {
                            Toast.makeText(context, "Importing ${uris.size} files...", Toast.LENGTH_SHORT).show()
                            scope.launch {
                                val repository = (context.applicationContext as ReaderApplication).bookRepository
                                uris.forEach { uri ->
                                    repository.importBook(uri)
                                }
                                Toast.makeText(context, "Import complete", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )

                val folderPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocumentTree(),
                    onResult = { uri ->
                        uri?.let {
                            Toast.makeText(context, "Scanning folder for books...", Toast.LENGTH_SHORT).show()
                            scope.launch {
                                val repository = (context.applicationContext as ReaderApplication).bookRepository
                                val root = DocumentFile.fromTreeUri(context, it)
                                if (root != null) {
                                    importFromDocumentFile(root, repository)
                                }
                                Toast.makeText(context, "Folder scan complete", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(
                                        id = if (darkTheme) R.drawable.dark_mode_icon else R.drawable.light_mode_icon
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = Color.Unspecified
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(id = R.string.app_name),
                                    style = MaterialTheme.typography.headlineMedium
                                )
                            }
                            
                            NavigationDrawerItem(
                                label = { Text("Archives") },
                                icon = { Icon(MaterialSymbols.Outlined.Archive, contentDescription = null) },
                                selected = false,
                                onClick = {
                                    navController.navigate(Screen.Archives.route)
                                    scope.launch { drawerState.close() }
                                },
                                shape = RectangleShape
                            )
                            
                            HorizontalDivider()
                            
                            NavigationDrawerItem(
                                label = { Text("Import Files") },
                                icon = { Icon(MaterialSymbols.Outlined.Upload, contentDescription = null) },
                                selected = false,
                                onClick = {
                                    filePickerLauncher.launch(
                                        arrayOf(
                                            "application/epub+zip",
                                            "application/webpub+json",
                                            "application/x-cbz",
                                            "application/x-cbr",
                                            "application/x-cb7",
                                            "application/x-cbt",
                                            "application/vnd.comicbook+zip",
                                            "application/vnd.comicbook-rar"
                                        )
                                    )
                                    scope.launch { drawerState.close() }
                                },
                                shape = RectangleShape
                            )
                            NavigationDrawerItem(
                                label = { Text("Scan Folder") },
                                icon = { Icon(MaterialSymbols.Outlined.Folder, contentDescription = null) },
                                selected = false,
                                onClick = {
                                    folderPickerLauncher.launch(null)
                                    scope.launch { drawerState.close() }
                                },
                                shape = RectangleShape
                            )

                            HorizontalDivider()

                            NavigationDrawerItem(
                                label = { Text("Settings") },
                                icon = { Icon(MaterialSymbols.Outlined.Settings, contentDescription = null) },
                                selected = false,
                                onClick = {
                                    navController.navigate(Screen.Settings.route)
                                    scope.launch { drawerState.close() }
                                },
                                shape = RectangleShape
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
                                val intent = Intent(context, com.example.readerapp.ui.features.reader.ReaderActivity::class.java).apply {
                                    putExtra(com.example.readerapp.ui.features.reader.ReaderActivity.EXTRA_BOOK_ID, bookId)
                                }
                                context.startActivity(intent)
                            },
                            onOpenDrawerClick = {
                                scope.launch { drawerState.open() }
                            },
                            onNavigateToShelf = { shelfId ->
                                navController.navigate(Screen.ShelfDetail.createRoute(shelfId))
                            },
                            onNavigateToAuthor = { authorName ->
                                navController.navigate(Screen.AuthorDetail.createRoute(authorName))
                            },
                            onNavigateToTag = { tagName ->
                                navController.navigate(Screen.TagDetail.createRoute(tagName))
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
                                val intent = Intent(context, com.example.readerapp.ui.features.reader.ReaderActivity::class.java).apply {
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
                                val intent = Intent(context, com.example.readerapp.ui.features.reader.ReaderActivity::class.java).apply {
                                    putExtra(com.example.readerapp.ui.features.reader.ReaderActivity.EXTRA_BOOK_ID, bookId)
                                }
                                context.startActivity(intent)
                            }
                        )
                    }
                    composable(Screen.AuthorDetail.route) { backStackEntry ->
                        val authorName = backStackEntry.arguments?.getString("authorName") ?: return@composable
                        FilterResultScreen(
                            filterType = "author",
                            filterValue = authorName,
                            onNavigateBack = {
                                navController.popBackStack()
                            },
                            onNavigateToReader = { bookId ->
                                val intent = Intent(context, com.example.readerapp.ui.features.reader.ReaderActivity::class.java).apply {
                                    putExtra(com.example.readerapp.ui.features.reader.ReaderActivity.EXTRA_BOOK_ID, bookId)
                                }
                                context.startActivity(intent)
                            }
                        )
                    }
                    composable(Screen.TagDetail.route) { backStackEntry ->
                        val tagName = backStackEntry.arguments?.getString("tagName") ?: return@composable
                        FilterResultScreen(
                            filterType = "tag",
                            filterValue = tagName,
                            onNavigateBack = {
                                navController.popBackStack()
                            },
                            onNavigateToReader = { bookId ->
                                val intent = Intent(context, com.example.readerapp.ui.features.reader.ReaderActivity::class.java).apply {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) {
            val uri = intent.data ?: return
            val repository = (application as ReaderApplication).bookRepository
            lifecycleScope.launch {
                repository.importBook(uri)
            }
        }
    }

    private suspend fun importFromDocumentFile(file: DocumentFile, repository: BookRepository) {
        if (file.isDirectory) {
            file.listFiles().forEach { child ->
                importFromDocumentFile(child, repository)
            }
        } else {
            val name = file.name?.lowercase() ?: ""
            // Simple filter by extension, repository will do deeper validation
            val supportedExtensions = listOf(".epub", ".cbz", ".cbr", ".cb7", ".cbt", ".webpub")
            if (supportedExtensions.any { name.endsWith(it) }) {
                repository.importBook(file.uri)
            }
        }
    }
}
