package com.example.readerapp.ui.features.dictionary

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.example.readerapp.R
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.composables.icons.materialsymbols.outlined.Book
import com.composables.icons.materialsymbols.outlined.Download
import com.composables.icons.materialsymbols.outlined.Close
import com.composables.icons.materialsymbols.outlined.Add
import com.composables.icons.materialsymbols.outlined.History
import com.composables.icons.materialsymbols.outlined.More_vert
import com.composables.icons.materialsymbols.outlined.Save
import com.example.readerapp.data.repository.dictionary.DictionaryState
import com.example.readerapp.data.local.preferences.InstalledDictionary
import com.example.readerapp.ui.components.SegmentedLazyColumn
import com.example.readerapp.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DictionariesScreen(
    viewModel: DictionariesViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
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

    var selectedItemForMenu by remember { mutableStateOf<String?>(null) }
    var itemToDelete by remember { mutableStateOf<InstalledDictionary?>(null) }
    var itemToRename by remember { mutableStateOf<InstalledDictionary?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = object : ActivityResultContracts.OpenDocument() {
            override fun createIntent(context: android.content.Context, input: Array<String>): android.content.Intent {
                val intent = super.createIntent(context, input)
                val uri = android.provider.DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download")
                intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, uri)
                return intent
            }
        }
    ) { uri ->
        if (uri != null) {
            viewModel.importDictionary(uri)
        }
    }

    val invalidFormatMsg = stringResource(R.string.dictionaries_invalid_format)
    val restorePicker = rememberLauncherForActivityResult(
        contract = object : ActivityResultContracts.OpenDocument() {
            override fun createIntent(context: android.content.Context, input: Array<String>): android.content.Intent {
                val intent = super.createIntent(context, input)
                val pineconeDir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS), "Pinecone")
                if (!pineconeDir.exists()) pineconeDir.mkdirs()

                val uri = android.provider.DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents/Pinecone")
                intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, uri)
                return intent
            }
        }
    ) { uri ->
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

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.dictionaries_title)) },
                navigationIcon = {
                    FilledTonalIconButton(
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        onClick = onBack
                    ) {
                        Icon(MaterialSymbols.Outlined.Arrow_back, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            var expanded by rememberSaveable { mutableStateOf(false) }
            var showRestoreWarning by remember { mutableStateOf(false) }

            if (showRestoreWarning) {
                AlertDialog(
                    onDismissRequest = { showRestoreWarning = false },
                    title = { Text(stringResource(R.string.dictionaries_restore)) },
                    text = { Text(stringResource(R.string.dictionaries_restore_warning)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showRestoreWarning = false
                                restorePicker.launch(arrayOf("application/octet-stream"))
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

            BackHandler(expanded) { expanded = false }

            val backupNoDictMsg = stringResource(R.string.dictionaries_no_to_backup)
            FloatingActionButtonMenu(
                expanded = expanded,
                button = {
                    ToggleFloatingActionButton(
                        checked = expanded,
                        onCheckedChange = { expanded = !expanded }
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
                }
            ) {
                FloatingActionButtonMenuItem(
                    onClick = {
                        expanded = false
                        if (installedDictionaries.isEmpty()) {
                            Toast.makeText(context, backupNoDictMsg, Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.backupDictionaries()
                        }
                    },
                    icon = { Icon(MaterialSymbols.Outlined.Save, contentDescription = null) },
                    text = { Text(stringResource(R.string.dictionaries_backup)) }
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        expanded = false
                        showRestoreWarning = true
                    },
                    icon = { Icon(MaterialSymbols.Outlined.History, contentDescription = null) },
                    text = { Text(stringResource(R.string.dictionaries_restore)) }
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        expanded = false
                        filePicker.launch(arrayOf("application/zip"))
                    },
                    icon = { Icon(MaterialSymbols.Outlined.Download, contentDescription = null) },
                    text = { Text(stringResource(R.string.dictionaries_import_stardict)) }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val isLoadingImport = importState is DictionaryState.Loading
            val isLoadingRestore = restoreState is DictionaryState.Loading
            val isLoadingBackup = backupState is DictionaryState.Loading
            val totalCount = installedDictionaries.size + (if (isLoadingImport) 1 else 0) + (if (isLoadingRestore) 1 else 0) + (if (isLoadingBackup) 1 else 0)

            if (totalCount == 0) {
                EmptyState(
                    icon = MaterialSymbols.Outlined.Book,
                    text = stringResource(R.string.dictionaries_empty),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                SegmentedLazyColumn(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
                    ) {
                    installedDictionaries.forEach { dict ->
                        item(
                            key = dict.id,
                            content = { Text(dict.name) },
                            supportingContent = { Text(androidx.compose.ui.res.pluralStringResource(R.plurals.dictionaries_word_count, dict.wordCount, dict.wordCount)) },
                            trailingContent = {
                                Box {
                                    IconButton(onClick = { selectedItemForMenu = dict.id }) {
                                        Icon(MaterialSymbols.Outlined.More_vert, contentDescription = stringResource(R.string.action_more))
                                    }
                                    DropdownMenu(
                                        expanded = selectedItemForMenu == dict.id,
                                        onDismissRequest = { selectedItemForMenu = null }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_rename)) },
                                            onClick = {
                                                selectedItemForMenu = null
                                                itemToRename = dict
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_delete)) },
                                            onClick = {
                                                selectedItemForMenu = null
                                                itemToDelete = dict
                                            }
                                        )
                                    }
                                }
                            }
                        )
                    }

                    if (isLoadingImport) {
                        item(
                            key = "import",
                            content = { Text(stringResource(R.string.dictionaries_installing)) },
                            supportingContent = {
                                LinearWavyProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                )
                            },
                            trailingContent = { Text("${(animatedProgress * 100).toInt()}%") }
                        )
                    }

                    if (isLoadingRestore) {
                        item(
                            key = "restore",
                            content = { Text(stringResource(R.string.dictionaries_restoring)) },
                            supportingContent = {
                                LinearWavyProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                )
                            }
                        )
                    }

                    if (isLoadingBackup) {
                        item(
                            key = "backup",
                            content = { Text(stringResource(R.string.dictionaries_backing_up)) },
                            supportingContent = {
                                LinearWavyProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                )
                            }
                        )
                    }
                }
            }

            itemToDelete?.let { dict ->
                AlertDialog(
                    onDismissRequest = { itemToDelete = null },
                    title = { Text(stringResource(R.string.dictionaries_delete_title)) },
                    text = { Text(stringResource(R.string.dictionaries_delete_message, dict.name)) },
                    confirmButton = {
                        TextButton(onClick = {
                            val dictId = dict.id
                            itemToDelete = null
                            viewModel.deleteDictionary(dictId)
                        }) {
                            Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { itemToDelete = null }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }

            itemToRename?.let { dict ->
                var newName by remember(dict.id) { mutableStateOf(dict.name) }
                AlertDialog(
                    onDismissRequest = { itemToRename = null },
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
                        TextButton(onClick = {
                            if (newName.isNotBlank()) {
                                val dictId = dict.id
                                itemToRename = null
                                viewModel.renameDictionary(dictId, newName.trim())
                            }
                        }) {
                            Text(stringResource(R.string.action_rename))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { itemToRename = null }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }

        }
    }
}
