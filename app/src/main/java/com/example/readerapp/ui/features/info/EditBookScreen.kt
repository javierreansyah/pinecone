package com.example.readerapp.ui.features.info

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.composables.icons.materialsymbols.outlined.Close
import com.composables.icons.materialsymbols.outlined.Edit
import com.composables.icons.materialsymbols.outlined.Image
import com.example.readerapp.ui.theme.spacing
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditBookScreen(
    bookId: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as android.app.Application
    
    val viewModel: EditBookViewModel = viewModel(
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
            TopAppBar(
                title = { Text("Edit Book") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(MaterialSymbols.Outlined.Arrow_back, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.saveChanges() },
                        enabled = !uiState.isSaving
                    ) {
                        Text("Save", style = MaterialTheme.typography.labelLarge)
                    }
                }
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null && !uiState.isSaving) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(uiState.error ?: "Unknown error", color = MaterialTheme.colorScheme.error)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(spacing.screenPadding),
                verticalArrangement = Arrangement.spacedBy(spacing.space24),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                var isImagePickerOpen by remember { mutableStateOf(false) }
                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    isImagePickerOpen = false
                    viewModel.updateCoverUri(uri)
                }

                // Cover Image Picker
                Box(
                    modifier = Modifier
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
                        }
                ) {
                    if (uiState.coverUri != null) {
                        AsyncImage(
                            model = uiState.coverUri,
                            contentDescription = "Cover Image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else if (uiState.existingCoverPath != null) {
                        AsyncImage(
                            model = File(uiState.existingCoverPath!!),
                            contentDescription = "Cover Image",
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
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = MaterialSymbols.Outlined.Image,
                                contentDescription = "No Cover",
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
                            contentDescription = "Edit Cover",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Title
                OutlinedTextField(
                    value = uiState.title,
                    onValueChange = { viewModel.updateTitle(it) },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Authors Autocomplete
                AutocompleteChipTextField(
                    label = "Authors",
                    items = uiState.authors,
                    suggestions = allAuthors.map { it.name },
                    onAdd = { viewModel.addAuthor(it) },
                    onRemove = { viewModel.removeAuthor(it) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Tags Autocomplete
                AutocompleteChipTextField(
                    label = "Tags",
                    items = uiState.tags,
                    suggestions = allTags.map { it.name },
                    onAdd = { viewModel.addTag(it) },
                    onRemove = { viewModel.removeTag(it) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Description
                OutlinedTextField(
                    value = uiState.description,
                    onValueChange = { viewModel.updateDescription(it) },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 160.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var textFieldWidth by remember { mutableStateOf(0) }

    val filteredSuggestions = suggestions.filter { it.contains(text, ignoreCase = true) && !items.contains(it) }

    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            textFieldWidth = coordinates.size.width
        }
    ) {
        val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        
        androidx.compose.foundation.text.BasicTextField(
            value = text,
            onValueChange = { 
                text = it
                expanded = it.isNotBlank()
            },
            modifier = Modifier.fillMaxWidth(),
            interactionSource = interactionSource,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (text.isNotBlank()) {
                        onAdd(text)
                        text = ""
                        expanded = false
                    }
                }
            )
        ) { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = if (items.isNotEmpty()) " " else text,
                innerTextField = {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
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
                                        contentDescription = "Remove",
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { onRemove(item) }
                                    )
                                }
                            )
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
                visualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
                interactionSource = interactionSource,
                label = { Text(label) },
                trailingIcon = null,
                colors = OutlinedTextFieldDefaults.colors(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                container = {
                    OutlinedTextFieldDefaults.ContainerBox(
                        enabled = true,
                        isError = false,
                        interactionSource = interactionSource,
                        colors = OutlinedTextFieldDefaults.colors(),
                        shape = OutlinedTextFieldDefaults.shape
                    )
                }
            )
        }

        DropdownMenu(
            expanded = expanded && text.isNotBlank(),
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(with(androidx.compose.ui.platform.LocalDensity.current) { textFieldWidth.toDp() }),
            properties = androidx.compose.ui.window.PopupProperties(focusable = false)
        ) {
            if (filteredSuggestions.isNotEmpty()) {
                filteredSuggestions.take(5).forEach { suggestion ->
                    DropdownMenuItem(
                        text = { Text(suggestion) },
                        onClick = {
                            onAdd(suggestion)
                            text = ""
                            expanded = false
                        }
                    )
                }
            }
            
            if (text.isNotBlank() && !suggestions.any { it.equals(text, ignoreCase = true) }) {
                DropdownMenuItem(
                    text = { Text("Create new: \"$text\"") },
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
