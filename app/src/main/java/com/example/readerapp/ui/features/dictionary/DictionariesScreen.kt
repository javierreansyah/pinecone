package com.example.readerapp.ui.features.dictionary

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Add
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.composables.icons.materialsymbols.outlined.Book
import com.composables.icons.materialsymbols.outlined.Close
import com.composables.icons.materialsymbols.outlined.Download
import com.composables.icons.materialsymbols.outlined.History
import com.composables.icons.materialsymbols.outlined.More_vert
import com.composables.icons.materialsymbols.outlined.Save
import com.example.readerapp.R
import com.example.readerapp.data.local.preferences.InstalledDictionary
import com.example.readerapp.data.repository.dictionary.DictionaryState
import com.example.readerapp.ui.components.EmptyState
import com.example.readerapp.ui.components.SegmentedLazyColumn

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DictionariesScreen(
    viewModel: DictionariesViewModel, onBack: () -> Unit
) {
    val context = LocalContext.current
    val installedDictionaries by viewModel.installedDictionaries.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val restoreState by viewModel.restoreState.collectAsStateWithLifecycle()
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()

    val progressTarget = when (val state = importState) {
        is DictionaryState.Loading -> state.progress / 100f
        else -> 0f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = tween(durationMillis = 250, easing = LinearOutSlowInEasing),
        label = "ImportProgress"
    )

    val filePicker = rememberLauncherForActivityResult(contract = object :
        ActivityResultContracts.OpenDocument() {
        override fun createIntent(
            context: android.content.Context, input: Array<String>
        ): android.content.Intent {
            val intent = super.createIntent(context, input)
            val uri = android.provider.DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents", "primary:Download"
            )
            intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, uri)
            return intent
        }
    }) { uri ->
        if (uri != null) {
            viewModel.importDictionary(uri)
        }
    }

    val invalidFormatMsg = stringResource(R.string.dictionaries_invalid_format)
    val restorePicker = rememberLauncherForActivityResult(contract = object :
        ActivityResultContracts.OpenDocument() {
        override fun createIntent(
            context: android.content.Context, input: Array<String>
        ): android.content.Intent {
            val intent = super.createIntent(context, input)
            val pineconeDir = java.io.File(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS),
                "Pinecone"
            )
            if (!pineconeDir.exists()) pineconeDir.mkdirs()

            val uri = android.provider.DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents", "primary:Documents/Pinecone"
            )
            intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, uri)
            return intent
        }
    }) { uri ->
        if (uri != null) {
            val name = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)?.name
            if (name != null && name.endsWith(".pinedict")) {
                viewModel.restoreDictionary(uri)
            } else {
                Toast.makeText(context, invalidFormatMsg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val installSuccessMsg = stringResource(R.string.dictionaries_install_success)
    val commonErrorMsg = stringResource(R.string.common_error)
    LaunchedEffect(importState) {
        if (importState is DictionaryState.Success) {
            Toast.makeText(context, installSuccessMsg, Toast.LENGTH_SHORT).show()
            viewModel.resetImportState()
        } else if (importState is DictionaryState.Error) {
            val errorMsg = (importState as DictionaryState.Error).message
            val formattedMsg = String.format(commonErrorMsg, errorMsg)
            Toast.makeText(context, formattedMsg, Toast.LENGTH_LONG).show()
            viewModel.resetImportState()
        }
    }

    val commonRestoreErrorMsg = stringResource(R.string.common_restore_error)
    val restoreSuccessMsg = stringResource(R.string.dictionaries_restore_success)
    LaunchedEffect(restoreState) {
        if (restoreState is DictionaryState.Success) {
            Toast.makeText(context, restoreSuccessMsg, Toast.LENGTH_SHORT).show()
            viewModel.resetRestoreState()
        } else if (restoreState is DictionaryState.Error) {
            val errorMsg = (restoreState as DictionaryState.Error).message
            val formattedMsg = String.format(commonRestoreErrorMsg, errorMsg)
            Toast.makeText(context, formattedMsg, Toast.LENGTH_LONG).show()
            viewModel.resetRestoreState()
        }
    }

    val commonBackupErrorMsg = stringResource(R.string.common_backup_error)
    val backupSuccessMsg = stringResource(R.string.dictionaries_backup_success)
    LaunchedEffect(backupState) {
        if (backupState is DictionaryState.Success) {
            Toast.makeText(context, backupSuccessMsg, Toast.LENGTH_SHORT).show()
            viewModel.resetBackupState()
        } else if (backupState is DictionaryState.Error) {
            val errorMsg = (backupState as DictionaryState.Error).message
            val formattedMsg = String.format(commonBackupErrorMsg, errorMsg)
            Toast.makeText(context, formattedMsg, Toast.LENGTH_LONG).show()
            viewModel.resetBackupState()
        }
    }

    val backupNoDictMsg = stringResource(R.string.dictionaries_no_to_backup)

    val uiState = remember(installedDictionaries, importState, restoreState, backupState) {
        DictionariesUiState(
            installedDictionaries = installedDictionaries,
            importState = importState,
            restoreState = restoreState,
            backupState = backupState
        )
    }

    DictionariesContent(
        uiState = uiState,
        animatedProgress = animatedProgress,
        onBack = onBack,
        onBackupClick = {
            if (installedDictionaries.isEmpty()) {
                Toast.makeText(context, backupNoDictMsg, Toast.LENGTH_SHORT).show()
            } else {
                viewModel.backupDictionaries()
            }
        },
        onRestoreClick = {
            restorePicker.launch(arrayOf("application/octet-stream"))
        },
        onImportClick = {
            filePicker.launch(arrayOf("application/zip"))
        },
        onRenameDictionary = { id, name ->
            viewModel.renameDictionary(id, name)
        },
        onDeleteDictionary = { id ->
            viewModel.deleteDictionary(id)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DictionariesContent(
    uiState: DictionariesUiState,
    animatedProgress: Float,
    onBack: () -> Unit,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onImportClick: () -> Unit,
    onRenameDictionary: (String, String) -> Unit,
    onDeleteDictionary: (String) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var selectedItemForMenu by remember { mutableStateOf<String?>(null) }
    var itemToDelete by remember { mutableStateOf<InstalledDictionary?>(null) }
    var itemToRename by remember { mutableStateOf<InstalledDictionary?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showRestoreWarning by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            DictionariesTopAppBar(
                onBack = onBack,
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            DictionariesFloatingActionButton(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                onBackupClick = onBackupClick,
                onRestoreClick = { showRestoreWarning = true },
                onImportClick = onImportClick
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val isLoadingImport = uiState.importState is DictionaryState.Loading
            val isLoadingRestore = uiState.restoreState is DictionaryState.Loading
            val isLoadingBackup = uiState.backupState is DictionaryState.Loading
            val totalCount = uiState.installedDictionaries.size +
                    (if (isLoadingImport) 1 else 0) +
                    (if (isLoadingRestore) 1 else 0) +
                    (if (isLoadingBackup) 1 else 0)

            if (totalCount == 0) {
                EmptyState(
                    icon = MaterialSymbols.Outlined.Book,
                    text = stringResource(R.string.dictionaries_empty),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                DictionariesList(
                    uiState = uiState,
                    animatedProgress = animatedProgress,
                    selectedItemForMenu = selectedItemForMenu,
                    onMenuDismiss = { selectedItemForMenu = null },
                    onMoreClick = { selectedItemForMenu = it },
                    onRenameClick = { dict ->
                        selectedItemForMenu = null
                        itemToRename = dict
                    },
                    onDeleteClick = { dict ->
                        selectedItemForMenu = null
                        itemToDelete = dict
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (showRestoreWarning) {
                RestoreWarningDialog(
                    onDismissRequest = { showRestoreWarning = false },
                    onConfirm = {
                        showRestoreWarning = false
                        onRestoreClick()
                    }
                )
            }

            itemToDelete?.let { dict ->
                DeleteDictionaryDialog(
                    dictName = dict.name,
                    onDismissRequest = { itemToDelete = null },
                    onConfirm = {
                        val dictId = dict.id
                        itemToDelete = null
                        onDeleteDictionary(dictId)
                    }
                )
            }

            itemToRename?.let { dict ->
                RenameDictionaryDialog(
                    initialName = dict.name,
                    onDismissRequest = { itemToRename = null },
                    onConfirm = { newName ->
                        val dictId = dict.id
                        itemToRename = null
                        onRenameDictionary(dictId, newName)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DictionariesTopAppBar(
    onBack: () -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    modifier: Modifier = Modifier
) {
    LargeFlexibleTopAppBar(
        title = { Text(stringResource(R.string.dictionaries_title)) },
        navigationIcon = {
            FilledTonalIconButton(
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                onClick = onBack
            ) {
                Icon(
                    MaterialSymbols.Outlined.Arrow_back,
                    contentDescription = stringResource(R.string.action_back)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
        ),
        scrollBehavior = scrollBehavior,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DictionariesFloatingActionButton(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(expanded) { onExpandedChange(false) }

    FloatingActionButtonMenu(
        expanded = expanded,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = onExpandedChange
            ) {
                val imageVector by remember {
                    derivedStateOf {
                        if (checkedProgress > 0.5f) MaterialSymbols.Outlined.Close else MaterialSymbols.Outlined.Add
                    }
                }
                Icon(
                    painter = rememberVectorPainter(imageVector),
                    contentDescription = stringResource(R.string.action_options),
                    modifier = Modifier.animateIcon({ checkedProgress })
                )
            }
        },
        modifier = modifier
    ) {
        FloatingActionButtonMenuItem(
            onClick = {
                onExpandedChange(false)
                onBackupClick()
            },
            icon = { Icon(MaterialSymbols.Outlined.Save, contentDescription = null) },
            text = { Text(stringResource(R.string.dictionaries_backup)) }
        )
        FloatingActionButtonMenuItem(
            onClick = {
                onExpandedChange(false)
                onRestoreClick()
            },
            icon = { Icon(MaterialSymbols.Outlined.History, contentDescription = null) },
            text = { Text(stringResource(R.string.dictionaries_restore)) }
        )
        FloatingActionButtonMenuItem(
            onClick = {
                onExpandedChange(false)
                onImportClick()
            },
            icon = { Icon(MaterialSymbols.Outlined.Download, contentDescription = null) },
            text = { Text(stringResource(R.string.dictionaries_import_stardict)) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DictionariesList(
    uiState: DictionariesUiState,
    animatedProgress: Float,
    selectedItemForMenu: String?,
    onMenuDismiss: () -> Unit,
    onMoreClick: (String) -> Unit,
    onRenameClick: (InstalledDictionary) -> Unit,
    onDeleteClick: (InstalledDictionary) -> Unit,
    modifier: Modifier = Modifier
) {
    SegmentedLazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
    ) {
        uiState.installedDictionaries.forEach { dict ->
            item(
                key = dict.id,
                content = { Text(dict.name) },
                supportingContent = {
                    Text(
                        androidx.compose.ui.res.pluralStringResource(
                            R.plurals.dictionaries_word_count,
                            dict.wordCount,
                            dict.wordCount
                        )
                    )
                },
                trailingContent = {
                    DictionaryItemActions(
                        dict = dict,
                        isMenuExpanded = selectedItemForMenu == dict.id,
                        onMoreClick = { onMoreClick(dict.id) },
                        onMenuDismiss = onMenuDismiss,
                        onRenameClick = { onRenameClick(dict) },
                        onDeleteClick = { onDeleteClick(dict) }
                    )
                }
            )
        }

        if (uiState.importState is DictionaryState.Loading) {
            item(
                key = "import",
                content = { Text(stringResource(R.string.dictionaries_installing)) },
                supportingContent = {
                    LinearWavyProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                },
                trailingContent = { Text("${(animatedProgress * 100).toInt()}%") }
            )
        }

        if (uiState.restoreState is DictionaryState.Loading) {
            item(
                key = "restore",
                content = { Text(stringResource(R.string.dictionaries_restoring)) },
                supportingContent = {
                    LinearWavyProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            )
        }

        if (uiState.backupState is DictionaryState.Loading) {
            item(
                key = "backup",
                content = { Text(stringResource(R.string.dictionaries_backing_up)) },
                supportingContent = {
                    LinearWavyProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            )
        }
    }
}

@Composable
private fun DictionaryItemActions(
    dict: InstalledDictionary,
    isMenuExpanded: Boolean,
    onMoreClick: () -> Unit,
    onMenuDismiss: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        IconButton(onClick = onMoreClick) {
            Icon(
                MaterialSymbols.Outlined.More_vert,
                contentDescription = stringResource(R.string.action_more)
            )
        }
        DropdownMenu(
            expanded = isMenuExpanded,
            onDismissRequest = onMenuDismiss
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_rename)) },
                onClick = onRenameClick
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                onClick = onDeleteClick
            )
        }
    }
}

@Composable
private fun RestoreWarningDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.dictionaries_restore)) },
        text = { Text(stringResource(R.string.dictionaries_restore_warning)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_proceed))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun DeleteDictionaryDialog(
    dictName: String,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.dictionaries_delete_title)) },
        text = {
            Text(
                stringResource(
                    R.string.dictionaries_delete_message, dictName
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun RenameDictionaryDialog(
    initialName: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newName by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.dictionaries_rename_title)) },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                singleLine = true,
                label = { Text(stringResource(R.string.dictionaries_rename_title)) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (newName.isNotBlank()) {
                        onConfirm(newName.trim())
                    }
                }
            ) {
                Text(stringResource(R.string.action_rename))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
