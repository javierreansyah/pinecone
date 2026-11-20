package com.javierreansyah.pinecone.ui.features.reader.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Search
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.ui.components.ContainedSearchOverlay
import com.javierreansyah.pinecone.ui.components.InsetListItemDefaults
import com.javierreansyah.pinecone.ui.features.library.PublicationWording
import com.javierreansyah.pinecone.ui.features.reader.SearchResultItem

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
    modifier: Modifier = Modifier,
    wording: PublicationWording = PublicationWording.Items,
) {
    val textFieldState = remember { TextFieldState(query) }

    LaunchedEffect(query) {
        if (textFieldState.text.toString() != query) {
            textFieldState.edit {
                replace(0, length, query)
            }
        }
    }

    LaunchedEffect(textFieldState.text) {
        val currentText = textFieldState.text.toString()
        if (currentText != query) {
            onQueryChange(currentText)
        }
    }

    val backgroundColor = MaterialTheme.colorScheme.surface

    ContainedSearchOverlay(
        textFieldState = textFieldState,
        placeholderText = stringResource(wording.searchHint),
        onSearch = onSearch,
        onClear = { onQueryChange("") },
        onVoiceResult = { spokenText ->
            onQueryChange(spokenText)
            onSearch(spokenText)
        },
        onClose = onClose,
        modifier = modifier,
        backgroundColor = backgroundColor,
    ) {
        val listState = rememberLazyListState()
        val fadeHeight = 16.dp
        val fadeHeightPx = with(LocalDensity.current) { fadeHeight.toPx() }
        val topFadeAlpha by remember(listState, fadeHeightPx) {
            derivedStateOf {
                if (listState.firstVisibleItemIndex > 0) {
                    1f
                } else {
                    (listState.firstVisibleItemScrollOffset / fadeHeightPx).coerceIn(0f, 1f)
                }
            }
        }

        val actionsEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(animationSpec = actionsEffectsSpec),
            exit = fadeOut(animationSpec = actionsEffectsSpec),
        ) {
            LinearWavyProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (searchPerformed && results.isNotEmpty()) {
                    item {
                        val resultsText = pluralStringResource(
                            R.plurals.reader_search_results_count,
                            results.size,
                            results.size,
                        )
                        Text(
                            text = resultsText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                start = 20.dp,
                                end = 20.dp,
                                bottom = 16.dp,
                            ),
                        )
                    }
                }

                itemsIndexed(results) { index, item ->
                    SearchResultCard(
                        item = item,
                        index = index,
                        query = query,
                        onClick = { onResultClick(index) },
                    )
                }

                if (searchPerformed && !isLoading && results.isEmpty()) {
                    item {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 64.dp),
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Icon(
                                    MaterialSymbols.Outlined.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                )
                                Text(
                                    stringResource(
                                        R.string.reader_search_no_results_for,
                                        query,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fadeHeight)
                    .align(Alignment.TopCenter)
                    .graphicsLayer { alpha = topFadeAlpha }
                    .background(
                        Brush.verticalGradient(
                            listOf(backgroundColor, backgroundColor.copy(alpha = 0f)),
                        ),
                    ),
            )
        }
    }
}

@Composable
fun SearchResultCard(
    item: SearchResultItem,
    index: Int,
    query: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val subtitleText = if (item.positionLabel.isNotBlank()) {
        "${index + 1} | ${item.positionLabel}"
    } else {
        (index + 1).toString()
    }
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
                background = primary.copy(alpha = 0.12f),
            ),
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

    ListItem(
        onClick = onClick,
        modifier = modifier.padding(horizontal = InsetListItemDefaults.OuterHorizontalPadding),
        shapes = InsetListItemDefaults.shapes(),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = primary,
        ),
        contentPadding = InsetListItemDefaults.ContentPadding,
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = subtitleText)
                Text(
                    text = snippet,
                    lineHeight = 20.sp,
                    modifier = Modifier.widthIn(max = 600.dp),
                )
            }
        },
        content = {
            Text(
                text = item.chapterTitle ?: stringResource(R.string.reader_unknown_chapter),
            )
        },
    )
}
