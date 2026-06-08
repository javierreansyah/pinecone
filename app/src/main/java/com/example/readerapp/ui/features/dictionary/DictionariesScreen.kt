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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.composables.icons.materialsymbols.outlined.Book
import com.composables.icons.materialsymbols.outlined.Download
import com.composables.icons.materialsymbols.outlined.Close
import com.composables.icons.materialsymbols.outlined.Add
import com.composables.icons.materialsymbols.outlined.History
import com.composables.icons.materialsymbols.outlined.More_vert
import com.composables.icons.materialsymbols.outlined.Save
import com.example.readerapp.data.local.dictionary.ImportState
import com.example.readerapp.data.local.InstalledDictionary
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
        is ImportState.Loading -> state.progress / 100f
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
                Toast.makeText(context, "Invalid file format. Please select a .pinedict file.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(importState) {
        if (importState is ImportState.Success) {
            Toast.makeText(context, "Dictionary installed successfully", Toast.LENGTH_SHORT).show()
            viewModel.resetImportState()
        } else if (importState is ImportState.Error) {
            Toast.makeText(context, "Error: ${(importState as ImportState.Error).message}", Toast.LENGTH_LONG).show()
            viewModel.resetImportState()
        }
    }

    LaunchedEffect(restoreState) {
        if (restoreState is ImportState.Success) {
            Toast.makeText(context, "Dictionaries restored successfully", Toast.LENGTH_SHORT).show()
            viewModel.resetRestoreState()
        } else if (restoreState is ImportState.Error) {
            Toast.makeText(context, "Restore Error: ${(restoreState as ImportState.Error).message}", Toast.LENGTH_LONG).show()
            viewModel.resetRestoreState()
        }
    }

    LaunchedEffect(backupState) {
        if (backupState is ImportState.Success) {
            Toast.makeText(context, "Dictionaries backed up successfully", Toast.LENGTH_SHORT).show()
            viewModel.resetBackupState()
        } else if (backupState is ImportState.Error) {
            Toast.makeText(context, "Backup Error: ${(backupState as ImportState.Error).message}", Toast.LENGTH_LONG).show()
            viewModel.resetBackupState()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Dictionaries") },
                navigationIcon = {
                    FilledTonalIconButton(
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        onClick = onBack
                    ) {
                        Icon(MaterialSymbols.Outlined.Arrow_back, contentDescription = "Back")
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
                    title = { Text("Restore Backup") },
                    text = { Text("Warning: Restoring a backup will completely overwrite your current dictionaries with the contents of the backup. This action cannot be undone.\n\nDo you want to proceed?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showRestoreWarning = false
                                restorePicker.launch(arrayOf("application/octet-stream"))
                            }
                        ) {
                            Text("Proceed")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRestoreWarning = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            BackHandler(expanded) { expanded = false }

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
                            contentDescription = "Options",
                            modifier = Modifier.animateIcon({ checkedProgress })
                        )
                    }
                }
            ) {
                FloatingActionButtonMenuItem(
                    onClick = {
                        expanded = false
                        if (installedDictionaries.isEmpty()) {
                            Toast.makeText(context, "No dictionaries installed to backup", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.backupDictionaries()
                        }
                    },
                    icon = { Icon(MaterialSymbols.Outlined.Save, contentDescription = null) },
                    text = { Text("Backup Dictionaries") }
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        expanded = false
                        showRestoreWarning = true
                    },
                    icon = { Icon(MaterialSymbols.Outlined.History, contentDescription = null) },
                    text = { Text("Restore Backup") }
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        expanded = false
                        filePicker.launch(arrayOf("application/zip"))
                    },
                    icon = { Icon(MaterialSymbols.Outlined.Download, contentDescription = null) },
                    text = { Text("Import Stardict") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val isLoadingImport = importState is ImportState.Loading
            val isLoadingRestore = restoreState is ImportState.Loading
            val isLoadingBackup = backupState is ImportState.Loading
            val totalCount = installedDictionaries.size + (if (isLoadingImport) 1 else 0) + (if (isLoadingRestore) 1 else 0) + (if (isLoadingBackup) 1 else 0)

            if (totalCount == 0) {
                EmptyState(
                    icon = MaterialSymbols.Outlined.Book,
                    text = "No dictionaries installed\nTap + to import a Stardict .zip file",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                SegmentedLazyColumn(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
                ) {
                    installedDictionaries.forEach { dict ->
                        item(
                            content = { Text(dict.name) },
                            supportingContent = { Text("${dict.wordCount} words") },
                            trailingContent = {
                                Box {
                                    IconButton(onClick = { selectedItemForMenu = dict.id }) {
                                        Icon(MaterialSymbols.Outlined.More_vert, contentDescription = "More options")
                                    }
                                    DropdownMenu(
                                        expanded = selectedItemForMenu == dict.id,
                                        onDismissRequest = { selectedItemForMenu = null }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Rename") },
                                            onClick = {
                                                selectedItemForMenu = null
                                                itemToRename = dict
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete") },
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
                            content = { Text("Installing Dictionary...") },
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
                            content = { Text("Restoring Dictionaries...") },
                            supportingContent = {
                                LinearWavyProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                )
                            }
                        )
                    }

                    if (isLoadingBackup) {
                        item(
                            content = { Text("Backing up Dictionaries...") },
                            supportingContent = {
                                LinearWavyProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                )
                            }
                        )
                    }
                }
            }

            if (itemToDelete != null) {
                AlertDialog(
                    onDismissRequest = { itemToDelete = null },
                    title = { Text("Delete Dictionary") },
                    text = { Text("Are you sure you want to delete '${itemToDelete!!.name}'?") },
                    confirmButton = {
                        TextButton(onClick = {
                            val dictId = itemToDelete!!.id
                            itemToDelete = null
                            viewModel.deleteDictionary(dictId)
                        }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { itemToDelete = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (itemToRename != null) {
                var newName by remember { mutableStateOf(itemToRename!!.name) }
                AlertDialog(
                    onDismissRequest = { itemToRename = null },
                    title = { Text("Rename Dictionary") },
                    text = {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            singleLine = true,
                            label = { Text("Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (newName.isNotBlank()) {
                                val dictId = itemToRename!!.id
                                itemToRename = null
                                viewModel.renameDictionary(dictId, newName.trim())
                            }
                        }) {
                            Text("Rename")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { itemToRename = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }

        }
    }
}
