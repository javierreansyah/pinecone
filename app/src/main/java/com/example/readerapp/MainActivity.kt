package com.example.readerapp

import android.os.Bundle
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import android.widget.Toast
import coil.imageLoader
import com.example.readerapp.data.repository.BookRepository
import com.example.readerapp.data.local.ReaderPreferences
import com.example.readerapp.data.local.ReaderSettings
import com.example.readerapp.ui.features.library.LibraryScreen
import com.example.readerapp.ui.navigation.Screen
import com.example.readerapp.ui.navigation.AppDrawer
import com.example.readerapp.ui.features.settings.SettingsScreen
import com.example.readerapp.ui.features.library.ArchiveScreen
import com.example.readerapp.ui.features.library.ShelfDetailScreen
import com.example.readerapp.ui.features.library.FilterResultScreen
import com.example.readerapp.ui.features.info.BookInfoScreen
import com.example.readerapp.ui.features.info.EditBookScreen
import com.example.readerapp.ui.theme.AppTheme
import androidx.compose.material3.*
import kotlinx.coroutines.launch
import coil.annotation.ExperimentalCoilApi

class MainActivity : AppCompatActivity() {
    @OptIn(ExperimentalCoilApi::class)
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
                darkTheme = darkTheme,
                colorPalette = settings.colorPalette,
                themeContrast = settings.themeContrast
            ) {
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                BackHandler(enabled = drawerState.isOpen) {
                    scope.launch { drawerState.close() }
                }

                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = object : androidx.activity.result.contract.ActivityResultContract<Array<String>, List<@JvmSuppressWildcards android.net.Uri>>() {
                        override fun createIntent(context: android.content.Context, input: Array<String>): Intent {
                            return Intent(Intent.ACTION_GET_CONTENT).apply {
                                type = "*/*"
                                putExtra(Intent.EXTRA_MIME_TYPES, input)
                                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                                addCategory(Intent.CATEGORY_OPENABLE)
                                val uri = android.provider.DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download")
                                putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, uri)
                            }
                        }
                        override fun parseResult(resultCode: Int, intent: Intent?): List<android.net.Uri> {
                            if (resultCode != RESULT_OK || intent == null) return emptyList()
                            val clipData = intent.clipData
                            if (clipData != null) {
                                return (0 until clipData.itemCount).map { clipData.getItemAt(it).uri }
                            }
                            return listOfNotNull(intent.data)
                        }
                    },
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
                    contract = object : ActivityResultContracts.OpenDocumentTree() {
                        override fun createIntent(context: android.content.Context, input: android.net.Uri?): Intent {
                            val intent = super.createIntent(context, input)
                            val uri = android.provider.DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:")
                            intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, uri)
                            return intent
                        }
                    },
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

                val backupFolderPickerLauncher = rememberLauncherForActivityResult(
                    contract = object : ActivityResultContracts.OpenDocumentTree() {
                        override fun createIntent(context: android.content.Context, input: android.net.Uri?): Intent {
                            val intent = super.createIntent(context, input)
                            val pineconeDir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS), "Pinecone")
                            if (!pineconeDir.exists()) pineconeDir.mkdirs()
                            if (input != null) {
                                intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, input)
                            }
                            return intent
                        }
                    },
                    onResult = { uri ->
                        uri?.let {
                            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            context.contentResolver.takePersistableUriPermission(it, takeFlags)
                            
                            // Save to settings
                            val newSettings = settings.copy(backupFolderUri = it.toString())
                            scope.launch {
                                readerPreferences.updateSettings(newSettings)
                                Toast.makeText(context, "Backup Location Selected", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )

                val restoringBackupText = stringResource(R.string.nav_restoring_backup)
                val restoreSuccessText = stringResource(R.string.nav_restore_success)
                val restoreFailedText = stringResource(R.string.nav_restore_failed)

                val restoreBackupLauncher = rememberLauncherForActivityResult(
                    contract = object : ActivityResultContracts.OpenDocument() {
                        override fun createIntent(context: android.content.Context, input: Array<String>): Intent {
                            val intent = super.createIntent(context, input)
                            val pineconeDir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS), "Pinecone")
                            if (!pineconeDir.exists()) pineconeDir.mkdirs()

                            val uri = android.provider.DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents/Pinecone")
                            intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, uri)
                            return intent
                        }
                    },
                    onResult = { uri ->
                        uri?.let {
                            Toast.makeText(context, restoringBackupText, Toast.LENGTH_SHORT).show()
                            scope.launch {
                                val success = com.example.readerapp.data.repository.BackupRepository(context).restoreBackup(it)
                                if (success) {
                                    context.imageLoader.memoryCache?.clear()
                                    context.imageLoader.diskCache?.clear()
                                    Toast.makeText(context, restoreSuccessText, Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, restoreFailedText, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                )

                var showRestoreWarning by remember { mutableStateOf(false) }

                if (showRestoreWarning) {
                    AlertDialog(
                        onDismissRequest = { showRestoreWarning = false },
                        title = { Text(stringResource(R.string.nav_restore_backup)) },
                        text = { Text(stringResource(R.string.nav_restore_backup_warning)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showRestoreWarning = false
                                    restoreBackupLauncher.launch(arrayOf("*/*"))
                                    scope.launch { drawerState.close() }
                                }
                            ) {
                                Text(stringResource(R.string.action_proceed))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRestoreWarning = false }) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        }
                    )
                }

                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStackEntry?.destination?.route

                // Safety net: force-close drawer if it's somehow open on a non-Library route
                LaunchedEffect(currentRoute) {
                    if (currentRoute != null && currentRoute != Screen.Library.route) {
                        drawerState.snapTo(DrawerValue.Closed)
                    }
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = currentRoute == Screen.Library.route,
                    drawerContent = {
                        AppDrawer(
                            drawerState = drawerState,
                            settings = settings,
                            darkTheme = darkTheme,
                            onNavigateToArchives = {
                                navController.navigate(Screen.Archives.route) {
                                    popUpTo(Screen.Library.route)
                                    launchSingleTop = true
                                }
                                scope.launch { drawerState.close() }
                            },
                            onNavigateToSettings = {
                                navController.navigate(Screen.Settings.route) {
                                    popUpTo(Screen.Library.route)
                                    launchSingleTop = true
                                }
                                scope.launch { drawerState.close() }
                            },
                            onNavigateToDictionaries = {
                                navController.navigate(Screen.Dictionaries.route) {
                                    popUpTo(Screen.Library.route)
                                    launchSingleTop = true
                                }
                                scope.launch { drawerState.close() }
                            },
                            onImportFilesClick = {
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
                            },
                            onScanFolderClick = { folderPickerLauncher.launch(null) },
                            onBackupFolderSetupClick = {
                                val initialUri =
                                    android.provider.DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents/Pinecone")
                                backupFolderPickerLauncher.launch(initialUri)
                            },
                            onRestoreBackupClick = { showRestoreWarning = true }
                        )
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
                                // Only open if Library is fully settled (RESUMED), not mid-transition
                                val isResumed = navController.currentBackStackEntry
                                    ?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
                                if (isResumed) {
                                    scope.launch { drawerState.open() }
                                }
                            },
                            onNavigateToShelf = { shelfId, name, count ->
                                navController.navigate(Screen.ShelfDetail.createRoute(shelfId, name, count))
                            },
                            onNavigateToAuthor = { authorName ->
                                navController.navigate(Screen.AuthorDetail.createRoute(authorName))
                            },
                            onNavigateToTag = { tagName ->
                                navController.navigate(Screen.TagDetail.createRoute(tagName))
                            },
                            onNavigateToAllAuthors = {
                                navController.navigate(Screen.AllAuthors.route)
                            },
                            onNavigateToAllTags = {
                                navController.navigate(Screen.AllTags.route)
                            },
                            onNavigateToBookInfo = { bookId ->
                                navController.navigate(Screen.BookInfo.createRoute(bookId))
                            }
                        )
                    }
                    composable(Screen.Settings.route) {
                        SettingsScreen(
                            onNavigateBack = {
                                navController.popBackStack(Screen.Library.route, inclusive = false)
                            }
                        )
                    }
                    composable(Screen.Archives.route) {
                        ArchiveScreen(
                            onNavigateBack = {
                                navController.popBackStack(Screen.Library.route, inclusive = false)
                            },
                            onNavigateToReader = { bookId ->
                                val intent = Intent(context, com.example.readerapp.ui.features.reader.ReaderActivity::class.java).apply {
                                    putExtra(com.example.readerapp.ui.features.reader.ReaderActivity.EXTRA_BOOK_ID, bookId)
                                }
                                context.startActivity(intent)
                            },
                            onNavigateToBookInfo = { bookId ->
                                navController.navigate(Screen.BookInfo.createRoute(bookId))
                            }
                        )
                    }
                    composable(
                        route = Screen.ShelfDetail.route,
                        arguments = listOf(
                            androidx.navigation.navArgument("shelfId") { type = androidx.navigation.NavType.StringType },
                            androidx.navigation.navArgument("name") { type = androidx.navigation.NavType.StringType; defaultValue = "" },
                            androidx.navigation.navArgument("count") { type = androidx.navigation.NavType.IntType; defaultValue = 0 }
                        )
                    ) { backStackEntry ->
                        val shelfId = backStackEntry.arguments?.getString("shelfId") ?: return@composable
                        val name = backStackEntry.arguments?.getString("name") ?: ""
                        val count = backStackEntry.arguments?.getInt("count") ?: 0
                        ShelfDetailScreen(
                            shelfId = shelfId,
                            initialShelfName = name,
                            initialBookCount = count,
                            onNavigateBack = {
                                if (navController.previousBackStackEntry != null) navController.popBackStack()
                            },
                            onNavigateToReader = { bookId ->
                                val intent = Intent(context, com.example.readerapp.ui.features.reader.ReaderActivity::class.java).apply {
                                    putExtra(com.example.readerapp.ui.features.reader.ReaderActivity.EXTRA_BOOK_ID, bookId)
                                }
                                context.startActivity(intent)
                            },
                            onNavigateToBookInfo = { bookId ->
                                navController.navigate(Screen.BookInfo.createRoute(bookId))
                            }
                        )
                    }
                    composable(Screen.AuthorDetail.route) { backStackEntry ->
                        val authorName = backStackEntry.arguments?.getString("authorName") ?: return@composable
                        FilterResultScreen(
                            filterType = "author",
                            filterValue = authorName,
                            onNavigateBack = {
                                if (navController.previousBackStackEntry != null) navController.popBackStack()
                            },
                            onNavigateToReader = { bookId ->
                                val intent = Intent(context, com.example.readerapp.ui.features.reader.ReaderActivity::class.java).apply {
                                    putExtra(com.example.readerapp.ui.features.reader.ReaderActivity.EXTRA_BOOK_ID, bookId)
                                }
                                context.startActivity(intent)
                            },
                            onNavigateToMerged = { newName ->
                                navController.popBackStack()
                                navController.navigate(Screen.AuthorDetail.createRoute(newName))
                            },
                            onNavigateToBookInfo = { bookId ->
                                navController.navigate(Screen.BookInfo.createRoute(bookId))
                            }
                        )
                    }
                    composable(Screen.TagDetail.route) { backStackEntry ->
                        val tagName = backStackEntry.arguments?.getString("tagName") ?: return@composable
                        FilterResultScreen(
                            filterType = "tag",
                            filterValue = tagName,
                            onNavigateBack = {
                                if (navController.previousBackStackEntry != null) navController.popBackStack()
                            },
                            onNavigateToReader = { bookId ->
                                val intent = Intent(context, com.example.readerapp.ui.features.reader.ReaderActivity::class.java).apply {
                                    putExtra(com.example.readerapp.ui.features.reader.ReaderActivity.EXTRA_BOOK_ID, bookId)
                                }
                                context.startActivity(intent)
                            },
                            onNavigateToMerged = { newName ->
                                navController.popBackStack()
                                navController.navigate(Screen.TagDetail.createRoute(newName))
                            },
                            onNavigateToBookInfo = { bookId ->
                                navController.navigate(Screen.BookInfo.createRoute(bookId))
                            }
                        )
                    }
                    composable(Screen.AllAuthors.route) {
                        com.example.readerapp.ui.features.library.AllFilterItemsScreen(
                            filterType = "author",
                            onNavigateBack = {
                                if (navController.previousBackStackEntry != null) navController.popBackStack()
                            },
                            onNavigateToDetail = { authorName ->
                                navController.navigate(Screen.AuthorDetail.createRoute(authorName))
                            }
                        )
                    }
                    composable(Screen.AllTags.route) {
                        com.example.readerapp.ui.features.library.AllFilterItemsScreen(
                            filterType = "tag",
                            onNavigateBack = {
                                if (navController.previousBackStackEntry != null) navController.popBackStack()
                            },
                            onNavigateToDetail = { tagName ->
                                navController.navigate(Screen.TagDetail.createRoute(tagName))
                            }
                        )
                    }
                    composable(Screen.Dictionaries.route) {
                        val factory = com.example.readerapp.ui.features.dictionary.DictionariesViewModel.Factory(
                            (context.applicationContext as ReaderApplication).dictionaryRepository,
                            readerPreferences
                        )
                        val dictViewModel: com.example.readerapp.ui.features.dictionary.DictionariesViewModel = androidx.lifecycle.viewmodel.compose.viewModel(factory = factory)
                        com.example.readerapp.ui.features.dictionary.DictionariesScreen(
                            viewModel = dictViewModel,
                            onBack = { navController.popBackStack(Screen.Library.route, inclusive = false) }
                        )
                    }
                    composable(
                        route = Screen.BookInfo.route,
                        arguments = listOf(androidx.navigation.navArgument("bookId") { type = androidx.navigation.NavType.StringType }),
                        deepLinks = listOf(androidx.navigation.navDeepLink { uriPattern = "pinecone://book_info/{bookId}" })
                    ) { backStackEntry ->
                        val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
                        BookInfoScreen(
                            bookId = bookId,
                            onNavigateBack = {
                                if (navController.previousBackStackEntry != null) navController.popBackStack() else finish()
                            },
                            onNavigateToEdit = { id ->
                                navController.navigate(Screen.EditBook.createRoute(id))
                            }
                        )
                    }
                    composable(
                        route = Screen.EditBook.route,
                        arguments = listOf(androidx.navigation.navArgument("bookId") { type = androidx.navigation.NavType.StringType })
                    ) { backStackEntry ->
                        val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
                        EditBookScreen(
                            bookId = bookId,
                            onNavigateBack = {
                                if (navController.previousBackStackEntry != null) navController.popBackStack()
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
