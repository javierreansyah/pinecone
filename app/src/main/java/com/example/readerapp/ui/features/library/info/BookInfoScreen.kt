package com.example.readerapp.ui.features.library.info

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.composables.icons.materialsymbols.outlined.Edit
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.readerapp.R
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.example.readerapp.ui.theme.spacing
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BookInfoScreen(
    bookId: String, onNavigateBack: () -> Unit, onNavigateToEdit: (String) -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: BookInfoViewModel = viewModel(
        factory = BookInfoViewModel.Factory(application, bookId)
    )

    val uiState by viewModel.uiState.collectAsState()
    val book = uiState.book
    val isLoading = uiState.isLoading

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (book == null) {
                Box(
                    modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.book_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(spacing.screenPadding),
                    verticalArrangement = Arrangement.spacedBy(spacing.space24),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Cover Image
                    if (book.coverPath != null) {
                        AsyncImage(
                            model = File(book.coverPath),
                            contentDescription = stringResource(R.string.book_info_title),
                            modifier = Modifier
                                .width(200.dp)
                                .shadow(8.dp, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .width(200.dp)
                                .aspectRatio(1f / 1.5f)
                                .shadow(8.dp, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            MaterialTheme.colorScheme.secondaryContainer
                                        )
                                    )
                                ), contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = book.title.take(1).uppercase(),
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // Book Header Details & Tags Container
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (book.authors.isNotEmpty()) book.authors.joinToString(", ") else stringResource(
                                R.string.book_unknown_author
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Tags / Categories section
                        val tagsList = book.tags
                        if (tagsList.isNotEmpty()) {
                            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(spacing.space8),
                                    verticalArrangement = Arrangement.spacedBy(spacing.space8),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = spacing.space8)
                                ) {
                                    tagsList.forEach { tag ->
                                        SuggestionChip(onClick = {}, label = {
                                            Text(
                                                tag, style = MaterialTheme.typography.labelMedium
                                            )
                                        })
                                    }
                                }
                            }
                        }
                    }

                    // Book Progress
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(spacing.space8)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.book_reading_progress),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${(book.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { book.progress.toFloat() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }

                    // Book Description
                    if (!book.description.isNullOrBlank()) {
                        var isExpanded by remember { mutableStateOf(false) }
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(spacing.space8)
                        ) {
                            Text(
                                text = stringResource(R.string.book_description),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            HtmlText(
                                html = book.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (isExpanded) Int.MAX_VALUE else 4
                            )
                            if (book.description.length > 200) {
                                TextButton(
                                    onClick = { isExpanded = !isExpanded },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    val labelText =
                                        if (isExpanded) stringResource(R.string.book_read_less) else stringResource(
                                            R.string.book_read_more
                                        )
                                    Text(
                                        labelText,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // Metadata details
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(spacing.space16)
                    ) {
                        Text(
                            text = stringResource(R.string.book_publication_details),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        val notAvailable = stringResource(R.string.book_not_available)
                        MetadataRow(
                            label = stringResource(R.string.book_publisher),
                            value = book.publisher ?: notAvailable
                        )
                        MetadataRow(
                            label = stringResource(R.string.book_published_date),
                            value = formatPublishedDate(
                                book.published, notAvailable
                            )
                        )
                        MetadataRow(
                            label = stringResource(R.string.book_language),
                            value = book.language ?: notAvailable
                        )
                        MetadataRow(
                            label = stringResource(R.string.book_identifier),
                            value = book.identifier ?: notAvailable
                        )
                        MetadataRow(
                            label = stringResource(R.string.book_format),
                            value = when (book.mediaType) {
                                "application/epub+zip" -> "EPUB"
                                "application/x-cbz" -> "CBZ"
                                "application/x-cbr" -> "CBR"
                                "application/pdf" -> "PDF"
                                else -> book.mediaType?.substringAfterLast('/')?.uppercase()
                                    ?: stringResource(R.string.book_unknown)
                            }
                        )
                    }
                }
            }

            // Floating Top Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.space8, vertical = spacing.space8),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = onNavigateBack,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Icon(
                        MaterialSymbols.Outlined.Arrow_back,
                        contentDescription = stringResource(R.string.action_back)
                    )
                }

                FilledTonalIconButton(
                    onClick = { onNavigateToEdit(bookId) },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Icon(
                        MaterialSymbols.Outlined.Edit,
                        contentDescription = stringResource(R.string.action_edit),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HtmlText(
    html: String, style: TextStyle, color: Color, maxLines: Int = Int.MAX_VALUE
) {
    val annotatedString = remember(html) {
        val formattedHtml = html.replace("</p>", "</p><br>").trim()
        AnnotatedString.fromHtml(formattedHtml)
    }

    Text(
        text = annotatedString,
        style = style.copy(lineHeight = style.fontSize * 1.5f),
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun formatPublishedDate(dateString: String?, defaultValue: String): String {
    if (dateString.isNullOrBlank()) return defaultValue
    return try {
        val odt = OffsetDateTime.parse(dateString)
        odt.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()))
    } catch (_: Exception) {
        try {
            val ld = LocalDate.parse(dateString.substringBefore('T'))
            ld.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()))
        } catch (_: Exception) {
            dateString
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.space4)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}
