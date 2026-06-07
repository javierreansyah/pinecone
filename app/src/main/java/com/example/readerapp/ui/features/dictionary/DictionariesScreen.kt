package com.example.readerapp.ui.features.dictionary

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.composables.icons.materialsymbols.outlined.Book
import com.composables.icons.materialsymbols.outlined.Download
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Restore
import com.example.readerapp.data.local.dictionary.ImportState
import com.example.readerapp.data.local.InstalledDictionary
import com.example.readerapp.ui.features.library.components.SegmentedListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionariesScreen(
    viewModel: DictionariesViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val installedDictionaries by viewModel.installedDictionaries.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val restoreState by viewModel.restoreState.collectAsStateWithLifecycle()

    var selectedItemForMenu by remember { mutableStateOf<String?>(null) }
    var itemToDelete by remember { mutableStateOf<InstalledDictionary?>(null) }
    var itemToRename by remember { mutableStateOf<InstalledDictionary?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importDictionary(uri)
        }
    }

    val restorePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.restoreDictionary(uri)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dictionaries") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MaterialSymbols.Outlined.Arrow_back, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            var expanded by remember { mutableStateOf(false) }
            Column(horizontalAlignment = Alignment.End) {
                if (expanded) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            expanded = false
                            restorePicker.launch("*/*")
                        },
                        icon = { Icon(Icons.Outlined.Restore, contentDescription = null) },
                        text = { Text("Restore Backup") },
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    ExtendedFloatingActionButton(
                        onClick = {
                            expanded = false
                            filePicker.launch("application/zip")
                        },
                        icon = { Icon(MaterialSymbols.Outlined.Download, contentDescription = null) },
                        text = { Text("Import Stardict") },
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                FloatingActionButton(
                    onClick = { expanded = !expanded },
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        if (expanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = "Options"
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val isLoadingImport = importState is ImportState.Loading
            val isLoadingRestore = restoreState is ImportState.Loading
            val totalCount = installedDictionaries.size + (if (isLoadingImport) 1 else 0) + (if (isLoadingRestore) 1 else 0)

            if (totalCount == 0) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Outlined.Book,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No dictionaries installed",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Tap + to import a Stardict .zip file",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(installedDictionaries) { index, dict ->
                        SegmentedListItem(
                            selected = false,
                            onClick = { },
                            index = index,
                            count = totalCount,
                            content = { Text(dict.name) },
                            supportingContent = { Text("${dict.wordCount} words") },
                            trailingContent = {
                                Box {
                                    IconButton(onClick = { selectedItemForMenu = dict.id }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
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
                        item {
                            val loadingState = importState as ImportState.Loading
                            SegmentedListItem(
                                selected = false,
                                onClick = { },
                                index = installedDictionaries.size,
                                count = totalCount,
                                content = { Text("Installing Dictionary...") },
                                supportingContent = {
                                    LinearProgressIndicator(
                                        progress = { loadingState.progress / 100f },
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                    )
                                },
                                trailingContent = { Text("${loadingState.progress}%") }
                            )
                        }
                    }

                    if (isLoadingRestore) {
                        item {
                            SegmentedListItem(
                                selected = false,
                                onClick = { },
                                index = installedDictionaries.size + (if (isLoadingImport) 1 else 0),
                                count = totalCount,
                                content = { Text("Restoring Dictionaries...") },
                                supportingContent = {
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                    )
                                }
                            )
                        }
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
