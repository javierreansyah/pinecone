package com.example.readerapp.ui.features.reader.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.readerapp.ui.theme.AppTheme
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.composables.icons.materialsymbols.outlined.Close
import com.composables.icons.materialsymbols.outlined.Search
import com.example.readerapp.ui.features.reader.SearchResultItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    query: String,
    results: List<SearchResultItem>,
    isLoading: Boolean,
    searchPerformed: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onResultClick: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val backgroundColor = MaterialTheme.colorScheme.surface

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // ── Search bar ────────────────────────────────────────────────────────
        Surface(
            color = backgroundColor,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            MaterialSymbols.Outlined.Arrow_back,
                            contentDescription = "Close search",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    TextField(
                        value = query,
                        onValueChange = { onQueryChange(it) },
                        placeholder = {
                            Text(
                                "Search in book…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(
                                        MaterialSymbols.Outlined.Close,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { 
                            onSearch(query)
                            keyboardController?.hide()
                        }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                    )

                    IconButton(
                        onClick = { 
                            onSearch(query)
                            keyboardController?.hide()
                        },
                        enabled = query.isNotBlank()
                    ) {
                        Icon(
                            MaterialSymbols.Outlined.Search,
                            contentDescription = "Search",
                            tint = if (query.isNotBlank())
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Loading progress bar — shown while iterating results
                AnimatedVisibility(visible = isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }

        // ── Results count bar ────────────────────────────────────────────────
        // Only show if a search was performed and we have results or are not loading
        if (searchPerformed && (results.isNotEmpty() || !isLoading)) {
            Surface(
                color = backgroundColor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (results.isEmpty()) "No results found" else "${results.size} result${if (results.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
        }

        // ── Result list ───────────────────────────────────────────────────────
        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(results) { index, item ->
                SearchResultCard(
                    item = item,
                    query = query,
                    onClick = { onResultClick(index) }
                )
            }

            // Empty state when search done with no results
            if (searchPerformed && !isLoading && results.isEmpty()) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium)
                        ) {
                            Icon(
                                MaterialSymbols.Outlined.Search,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Text(
                                "No results for \"$query\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchResultCard(
    item: SearchResultItem,
    query: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.small)
    ) {
        Column {
            // Line 1: chapter name
            Text(
            text = item.chapterTitle ?: "Unknown Chapter",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = primary
        )

        // Line 2: position
        if (item.positionLabel.isNotBlank()) {
            Text(
                text = item.positionLabel,
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceVariant
            )
        }

        }

        // Line 3: Snippet: before · highlight · after
        val snippet = buildAnnotatedString {
            val before = item.textBefore?.trim() ?: ""
            val highlight = item.highlight ?: query
            val after = item.textAfter?.trim() ?: ""

            if (before.isNotBlank()) {
                withStyle(SpanStyle(color = onSurfaceVariant, fontSize = 14.sp)) {
                    append(before)
                    if (!before.endsWith(" ")) append(" ")
                }
            }
            withStyle(
                SpanStyle(
                    color = primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    background = primary.copy(alpha = 0.12f)
                )
            ) {
                append(highlight)
            }
            if (after.isNotBlank()) {
                withStyle(SpanStyle(color = onSurfaceVariant, fontSize = 14.sp)) {
                    if (!after.startsWith(" ")) append(" ")
                    append(after)
                }
            }
        }

        Text(
            text = snippet,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 20.sp
        )

    }
}
