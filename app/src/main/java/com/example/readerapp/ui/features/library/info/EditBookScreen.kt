package com.example.readerapp.ui.features.library.info

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults.FocusedBorderThickness
import androidx.compose.material3.OutlinedTextFieldDefaults.UnfocusedBorderThickness
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.composables.icons.materialsymbols.outlined.Check
import com.composables.icons.materialsymbols.outlined.Close
import com.composables.icons.materialsymbols.outlined.Edit
import com.composables.icons.materialsymbols.outlined.Image
import com.example.readerapp.R
import java.io.File

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun EditBookScreen(
    bookId: String, onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application

    val viewModel: EditBookViewModel = viewModel(
        key = bookId,
        factory = EditBookViewModel.Factory(application, bookId)
    )

    val uiState by viewModel.uiState.collectAsState()
    val allAuthors by viewModel.allAuthors.collectAsState()
    val allTags by viewModel.allTags.collectAsState()

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            EditBookTopAppBar(
                isSaving = uiState.isSaving,
                onNavigateBack = onNavigateBack,
                onSave = { viewModel.saveChanges() }
            )
        }) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null && !uiState.isSaving) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    uiState.error ?: stringResource(R.string.book_not_found),
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else {
            EditBookContent(
                uiState = uiState,
                allAuthors = allAuthors.map { it.name },
                allTags = allTags.map { it.name },
                onTitleChange = { viewModel.updateTitle(it) },
                onDescriptionChange = { viewModel.updateDescription(it) },
                onCoverUriChange = { viewModel.updateCoverUri(it) },
                onAddAuthor = { viewModel.addAuthor(it) },
                onRemoveAuthor = { viewModel.removeAuthor(it) },
                onAddTag = { viewModel.addTag(it) },
                onRemoveTag = { viewModel.removeTag(it) },
                innerPadding = innerPadding
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditBookTopAppBar(
    isSaving: Boolean,
    onNavigateBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = { Text(stringResource(R.string.book_edit_title)) },
        navigationIcon = {
            FilledTonalIconButton(
                shapes = IconButtonDefaults.shapes(),
                onClick = onNavigateBack,
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Icon(
                    MaterialSymbols.Outlined.Arrow_back,
                    contentDescription = stringResource(R.string.action_back)
                )
            }
        },
        actions = {
            FilledIconButton(
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(
                        IconButtonDefaults.smallContainerSize(
                            widthOption = IconButtonDefaults.IconButtonWidthOption.Wide
                        )
                    ),
                shapes = IconButtonDefaults.shapes(),
                onClick = onSave,
                enabled = !isSaving
            ) {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Check,
                    contentDescription = stringResource(R.string.action_save)
                )
            }
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditBookContent(
    uiState: EditBookUiState,
    allAuthors: List<String>,
    allTags: List<String>,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onCoverUriChange: (Uri?) -> Unit,
    onAddAuthor: (String) -> Unit,
    onRemoveAuthor: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BookCoverPicker(
            coverUri = uiState.coverUri,
            existingCoverPath = uiState.existingCoverPath,
            onCoverUriChange = onCoverUriChange
        )

        // Title
        OutlinedTextField(
            value = uiState.title,
            onValueChange = onTitleChange,
            label = { Text(stringResource(R.string.library_sort_title)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Authors Autocomplete
        AutocompleteChipTextField(
            label = stringResource(R.string.book_label_authors),
            items = uiState.authors,
            suggestions = allAuthors,
            onAdd = onAddAuthor,
            onRemove = onRemoveAuthor,
            modifier = Modifier.fillMaxWidth()
        )

        // Tags Autocomplete
        AutocompleteChipTextField(
            label = stringResource(R.string.book_label_tags),
            items = uiState.tags,
            suggestions = allTags,
            onAdd = onAddTag,
            onRemove = onRemoveTag,
            modifier = Modifier.fillMaxWidth()
        )

        // Description
        OutlinedTextField(
            value = uiState.description,
            onValueChange = onDescriptionChange,
            label = { Text(stringResource(R.string.book_description)) },
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 160.dp)
        )
    }
}

@Composable
private fun BookCoverPicker(
    coverUri: Uri?,
    existingCoverPath: String?,
    onCoverUriChange: (Uri?) -> Unit,
    modifier: Modifier = Modifier
) {
    var isImagePickerOpen by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        isImagePickerOpen = false
        onCoverUriChange(uri)
    }

    Box(
        modifier = modifier
            .width(160.dp)
            .aspectRatio(1f / 1.5f)
            .shadow(8.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable {
                if (!isImagePickerOpen) {
                    isImagePickerOpen = true
                    launcher.launch("image/*")
                }
            }) {
        if (coverUri != null) {
            AsyncImage(
                model = coverUri,
                contentDescription = stringResource(R.string.book_info_title),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (existingCoverPath != null) {
            val coverModel = remember(existingCoverPath) { File(existingCoverPath) }
            AsyncImage(
                model = coverModel,
                contentDescription = stringResource(R.string.book_info_title),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    ), contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Image,
                    contentDescription = stringResource(R.string.book_info_title),
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        // Edit Indicator Overlay
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    shape = CircleShape
                )
                .padding(6.dp)
        ) {
            Icon(
                imageVector = MaterialSymbols.Outlined.Edit,
                contentDescription = stringResource(R.string.action_edit),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
private fun AutocompleteChipTextField(
    label: String,
    items: List<String>,
    suggestions: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var textFieldWidth by remember { mutableIntStateOf(0) }

    val filteredSuggestions =
        suggestions.filter { it.contains(text, ignoreCase = true) && !items.contains(it) }

    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            textFieldWidth = coordinates.size.width
        }) {
        val interactionSource = remember { MutableInteractionSource() }

        BasicTextField(
            value = text,
            onValueChange = {
                text = it
                expanded = it.isNotBlank()
            },
            modifier = Modifier.fillMaxWidth(),
            interactionSource = interactionSource,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (text.isNotBlank()) {
                        onAdd(text)
                        text = ""
                        expanded = false
                    }
                })
        ) { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = if (items.isNotEmpty()) " " else text,
                innerTextField = {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(
                                8.dp, Alignment.CenterVertically
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items.forEach { item ->
                                InputChip(
                                    selected = false,
                                    onClick = { },
                                    label = { Text(item) },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = MaterialSymbols.Outlined.Close,
                                            contentDescription = stringResource(R.string.action_remove),
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { onRemove(item) })
                                    })
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .align(Alignment.CenterVertically)
                                    .defaultMinSize(minWidth = 60.dp)
                            ) {
                                innerTextField()
                            }
                        }
                    }
                },
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                label = { Text(label) },
                trailingIcon = null,
                colors = OutlinedTextFieldDefaults.colors(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = true,
                        isError = false,
                        interactionSource = interactionSource,
                        colors = OutlinedTextFieldDefaults.colors(),
                        shape = OutlinedTextFieldDefaults.shape,
                        focusedBorderThickness = FocusedBorderThickness,
                        unfocusedBorderThickness = UnfocusedBorderThickness,
                    )
                })
        }

        DropdownMenuPopup(
            expanded = expanded && text.isNotBlank(),
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(with(LocalDensity.current) { textFieldWidth.toDp() }),
            properties = PopupProperties(focusable = false)
        ) {
            val groupInteractionSource = remember { MutableInteractionSource() }
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShape(0, 1),
                interactionSource = groupInteractionSource
            ) {
                val finalSuggestions = filteredSuggestions.take(5)
                val showCreateNew =
                    text.isNotBlank() && !suggestions.any { it.equals(text, ignoreCase = true) }
                val totalCount = finalSuggestions.size + (if (showCreateNew) 1 else 0)

                finalSuggestions.forEachIndexed { index, suggestion ->
                    DropdownMenuItem(
                        selected = false,
                        text = { Text(suggestion) },
                        shapes = MenuDefaults.itemShape(index, totalCount),
                        onClick = {
                            onAdd(suggestion)
                            text = ""
                            expanded = false
                        }
                    )
                }

                if (showCreateNew) {
                    DropdownMenuItem(
                        selected = false,
                        text = { Text(stringResource(R.string.book_create_new, text)) },
                        shapes = MenuDefaults.itemShape(finalSuggestions.size, totalCount),
                        onClick = {
                            onAdd(text)
                            text = ""
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
