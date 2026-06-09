package com.example.readerapp.ui.root

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.readerapp.R
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun MainDrawerWrapper(
    navController: NavHostController,
    drawerState: DrawerState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsState()
    val darkTheme = when (settings.themeMode) {
        "Light" -> false
        "Dark" -> true
        else -> isSystemInDarkTheme()
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = object :
            ActivityResultContract<Array<String>, List<@JvmSuppressWildcards Uri>>() {
            override fun createIntent(context: Context, input: Array<String>): Intent {
                return Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, input)
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    addCategory(Intent.CATEGORY_OPENABLE)
                    val uri = DocumentsContract.buildDocumentUri(
                        "com.android.externalstorage.documents",
                        "primary:Download"
                    )
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
                }
            }

            override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
                if (resultCode != Activity.RESULT_OK || intent == null) return emptyList()
                val clipData = intent.clipData
                if (clipData != null) {
                    return (0 until clipData.itemCount).map { clipData.getItemAt(it).uri }
                }
                return listOfNotNull(intent.data)
            }
        },
        onResult = { uris ->
            viewModel.importBooks(uris)
        }
    )

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = object : ActivityResultContracts.OpenDocumentTree() {
            override fun createIntent(context: Context, input: Uri?): Intent {
                val intent = super.createIntent(context, input)
                val uri = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:"
                )
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
                return intent
            }
        },
        onResult = { uri ->
            uri?.let {
                viewModel.scanFolder(it)
            }
        }
    )

    val backupFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = object : ActivityResultContracts.OpenDocumentTree() {
            override fun createIntent(context: Context, input: Uri?): Intent {
                val intent = super.createIntent(context, input)
                val pineconeDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "Pinecone"
                )
                if (!pineconeDir.exists()) pineconeDir.mkdirs()
                if (input != null) {
                    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, input)
                }
                return intent
            }
        },
        onResult = { uri ->
            uri?.let {
                viewModel.updateBackupFolderUri(it)
            }
        }
    )

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = object : ActivityResultContracts.OpenDocument() {
            override fun createIntent(context: Context, input: Array<String>): Intent {
                val intent = super.createIntent(context, input)
                val pineconeDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "Pinecone"
                )
                if (!pineconeDir.exists()) pineconeDir.mkdirs()

                val uri = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:Documents/Pinecone"
                )
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
                return intent
            }
        },
        onResult = { uri ->
            uri?.let {
                viewModel.restoreBackup(it)
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
        modifier = modifier,
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
                    val initialUri = DocumentsContract.buildDocumentUri(
                        "com.android.externalstorage.documents",
                        "primary:Documents/Pinecone"
                    )
                    backupFolderPickerLauncher.launch(initialUri)
                },
                onRestoreBackupClick = { showRestoreWarning = true }
            )
        }
    ) {
        content()
    }
}
