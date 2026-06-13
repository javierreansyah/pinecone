package com.example.readerapp.ui.features.reader.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberContainedSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Chevron_backward
import com.composables.icons.materialsymbols.outlined.Close
import com.composables.icons.materialsymbols.outlined.Mic
import com.composables.icons.materialsymbols.outlined.Search
import com.example.readerapp.R
import com.example.readerapp.ui.components.PredictiveBackOverlay
import com.example.readerapp.ui.components.rememberVoiceSearchLauncher
import com.example.readerapp.ui.features.reader.SearchResultItem
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReaderSearch(
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

    val textFieldState = rememberTextFieldState(query)
    val searchBarState = rememberContainedSearchBarState(initialValue = SearchBarValue.Expanded)

    // Sync external query changes
    LaunchedEffect(query) {
        if (textFieldState.text.toString() != query) {
            textFieldState.edit {
                replace(0, length, query)
            }
        }
    }

    // Sync internal query changes back to parent
    LaunchedEffect(textFieldState.text) {
        onQueryChange(textFieldState.text.toString())
    }

    val launchVoiceSearch = rememberVoiceSearchLauncher { spokenText ->
        textFieldState.edit { replace(0, length, spokenText) }
        onQueryChange(spokenText)
        onSearch(spokenText)
        keyboardController?.hide()
    }

    LaunchedEffect(Unit) {
        if (query.isEmpty()) {
            focusRequester.requestFocus()
        }
    }

    var backProgress by remember { mutableFloatStateOf(0f) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }

    PredictiveBackHandler(enabled = true) { progressFlow ->
        var isCompleted = false
        try {
            progressFlow.collect { backEvent ->
                backProgress = backEvent.progress
                swipeEdge = backEvent.swipeEdge
            }
            isCompleted = true
            onClose()
        } catch (_: CancellationException) {
            // Cancelled
        } finally {
            if (!isCompleted) {
                backProgress = 0f
            }
        }
    }

    val backgroundColor = MaterialTheme.colorScheme.surface

    PredictiveBackOverlay(
        backProgress = backProgress,
        swipeEdge = swipeEdge,
        modifier = modifier,
        backgroundColor = backgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 8.dp)
        ) {
            // ── Contained Search Bar Capsule ──────────────────────────────────────
            SearchBarDefaults.InputField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .focusRequester(focusRequester),
                textFieldState = textFieldState,
                searchBarState = searchBarState,
                colors = SearchBarDefaults.inputFieldColors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                onSearch = {
                    onSearch(textFieldState.text.toString())
                    keyboardController?.hide()
                },
                placeholder = {
                    Text(
                        stringResource(R.string.reader_search_in_book),
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                leadingIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            MaterialSymbols.Outlined.Chevron_backward,
                            contentDescription = stringResource(R.string.action_close),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        if (textFieldState.text.isNotEmpty()) {
                            IconButton(onClick = {
                                textFieldState.edit { replace(0, length, "") }
                                onQueryChange("")
                            }) {
                                Icon(
                                    MaterialSymbols.Outlined.Close,
                                    contentDescription = stringResource(R.string.action_clear),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            IconButton(onClick = { launchVoiceSearch() }) {
                                Icon(
                                    MaterialSymbols.Outlined.Mic,
                                    contentDescription = stringResource(R.string.action_voice_search),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            )

            val enterTransition: EnterTransition = fadeIn(
                animationSpec = tween(durationMillis = 150, delayMillis = 50)
            ) + expandVertically(
                animationSpec = tween(durationMillis = 200)
            )

            val exitTransition: ExitTransition = fadeOut(
                animationSpec = tween(durationMillis = 150)
            ) + shrinkVertically(
                animationSpec = tween(durationMillis = 200)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Loading indicator container (fixes height to prevent layout shifts)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                contentAlignment = Alignment.Center
            ) {
                this@Column.AnimatedVisibility(
                    visible = isLoading,
                    enter = fadeIn(animationSpec = tween(durationMillis = 150)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 150))
                ) {
                    LinearWavyProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            // ── Results count bar ────────────────────────────────────────────────
            // Only show if a search was performed, and we have results or are not loading
            AnimatedVisibility(
                searchPerformed && (results.isNotEmpty() || !isLoading),
                enter = enterTransition,
                exit = exitTransition
            ) {
                Surface(
                    color = backgroundColor, modifier = Modifier.fillMaxWidth()
                ) {
                    val resultsText = if (results.isEmpty()) {
                        stringResource(R.string.reader_search_no_results)
                    } else {
                        androidx.compose.ui.res.pluralStringResource(
                            R.plurals.reader_search_results_count, results.size, results.size
                        )
                    }
                    Text(
                        text = resultsText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            start = 20.dp,
                            end = 20.dp,
                            bottom = 16.dp,
                        )
                    )
                }
            }


            // ── Result list ───────────────────────────────────────────────────────
            LazyColumn(
                contentPadding = PaddingValues(bottom = 8.dp), modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(results) { index, item ->
                    SearchResultCard(
                        item = item, query = query, onClick = { onResultClick(index) })
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
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    MaterialSymbols.Outlined.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                                Text(
                                    stringResource(R.string.reader_search_no_results_for, query),
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
}

@Composable
fun SearchResultCard(
    item: SearchResultItem, query: String, onClick: () -> Unit, modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column {
            // Line 1: chapter name
            Text(
                text = item.chapterTitle ?: "Unknown Chapter",
                style = MaterialTheme.typography.titleMedium,
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
            text = snippet, style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp
        )

    }
}
