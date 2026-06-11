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
import androidx.compose.material3.FloatingActionButton
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

    val uiState = remember(installedDictionaries, importState) {
        DictionariesUiState(
            installedDictionaries = installedDictionaries,
            importState = importState
        )
    }

    DictionariesContent(
        uiState = uiState,
        animatedProgress = animatedProgress,
        onBack = onBack,
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
    onImportClick: () -> Unit,
    onRenameDictionary: (String, String) -> Unit,
    onDeleteDictionary: (String) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var selectedItemForMenu by remember { mutableStateOf<String?>(null) }
    var itemToDelete by remember { mutableStateOf<InstalledDictionary?>(null) }
    var itemToRename by remember { mutableStateOf<InstalledDictionary?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            DictionariesTopAppBar(
                onBack = onBack,
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onImportClick
            ) {
                Icon(
                    MaterialSymbols.Outlined.Add,
                    contentDescription = stringResource(R.string.dictionaries_import_stardict)
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val isLoadingImport = uiState.importState is DictionaryState.Loading
            val totalCount = uiState.installedDictionaries.size +
                    (if (isLoadingImport) 1 else 0)

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
