package com.example.readerapp.ui.features.info

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import com.example.readerapp.ReaderApplication
import com.example.readerapp.data.local.ReaderPreferences
import com.example.readerapp.data.local.ReaderSettings
import com.example.readerapp.data.model.Book
import com.example.readerapp.ui.theme.AppTheme
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import com.example.readerapp.ui.theme.spacing

class BookInfoActivity : ComponentActivity() {

    companion object {
        const val EXTRA_BOOK_ID = "bookId"
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val bookId = intent.getStringExtra(EXTRA_BOOK_ID) ?: ""

        setContent {
            val context = LocalContext.current
            val app = context.applicationContext as ReaderApplication
            val repository = app.bookRepository
            val readerPreferences = remember { ReaderPreferences(context) }
            val settings by readerPreferences.readerSettings.collectAsState(initial = ReaderSettings())

            val scope = rememberCoroutineScope()
            var book by remember { mutableStateOf<Book?>(null) }
            var isLoading by remember { mutableStateOf(true) }

            LaunchedEffect(bookId) {
                scope.launch {
                    val entity = repository.getBook(bookId)
                    if (entity != null) {
                        book = Book.fromEntity(entity)
                    }
                    isLoading = false
                }
            }

            val isDarkTheme = when (settings.themeMode) {
                "Dark" -> true
                "Light" -> false
                else -> isSystemInDarkTheme()
            }

            AppTheme(
                darkTheme = isDarkTheme,
                dynamicColor = settings.colorPalette == "Dynamic"
            ) {
                Scaffold { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (book == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Book not found", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        val currentBook = book!!
                        SelectionContainer {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                                    .verticalScroll(rememberScrollState())
                                    .padding(spacing.screenPadding),
                                verticalArrangement = Arrangement.spacedBy(spacing.space24),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Cover Image with Drop Shadow & Smooth Gradient Border
                                Box(
                                    modifier = Modifier
                                        .width(160.dp)
                                        .aspectRatio(1f / 1.5f)
                                        .shadow(8.dp, RoundedCornerShape(12.dp))
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    if (currentBook.coverPath != null) {
                                        AsyncImage(
                                            model = File(currentBook.coverPath),
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
                                            Text(
                                                text = currentBook.title.take(1).uppercase(),
                                                style = MaterialTheme.typography.headlineLarge,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }

                                // Book Header Details & Tags Container
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.Start,
                                ) {
                                    Text(
                                        text = currentBook.title,
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = currentBook.author ?: "Unknown Author",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    // Tags / Categories section
                                    val tagsList = currentBook.tags?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                                    if (tagsList.isNotEmpty()) {
                                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(spacing.space8),
                                                verticalArrangement = Arrangement.spacedBy(spacing.space8),
                                                modifier = Modifier.fillMaxWidth().padding(top = spacing.space8)
                                            ) {
                                                tagsList.forEach { tag ->
                                                    SuggestionChip(
                                                        onClick = {},
                                                        label = { Text(tag, style = MaterialTheme.typography.labelMedium) }
                                                    )
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
                                            text = "Reading Progress",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${(currentBook.progress * 100).toInt()}%",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { currentBook.progress.toFloat() },
                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                                    )
                                }

                                // Book Description
                                if (!currentBook.description.isNullOrBlank()) {
                                    var isExpanded by remember { mutableStateOf(false) }
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(spacing.space8)
                                    ) {
                                        Text(
                                            text = "Description",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        HtmlText(
                                            html = currentBook.description,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = if (isExpanded) Int.MAX_VALUE else 4
                                        )
                                        if (currentBook.description.length > 200) {
                                            TextButton(
                                                onClick = { isExpanded = !isExpanded },
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text(if (isExpanded) "Read Less" else "Read More", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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
                                        text = "Publication Details",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    MetadataRow(label = "Publisher", value = currentBook.publisher ?: "N/A")
                                    MetadataRow(label = "Published Date", value = formatPublishedDate(currentBook.published))
                                    MetadataRow(label = "Language", value = currentBook.language ?: "N/A")
                                    MetadataRow(label = "Identifier (ISBN/ID)", value = currentBook.identifier ?: "N/A")
                                    MetadataRow(
                                        label = "Format",
                                        value = when (currentBook.mediaType) {
                                            "application/epub+zip" -> "EPUB"
                                            "application/x-cbz" -> "CBZ"
                                            "application/x-cbr" -> "CBR"
                                            "application/pdf" -> "PDF"
                                            else -> currentBook.mediaType?.substringAfterLast('/')?.uppercase() ?: "Unknown"
                                        }
                                    )
                                }
                            }
                        }
                    }

                    FilledTonalIconButton(
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        onClick = { finish() },
                        modifier = Modifier
                            .padding(top = innerPadding.calculateTopPadding() + 8.dp, start = 16.dp)
                            .align(Alignment.TopStart)
                    ) {
                        Icon(MaterialSymbols.Outlined.Arrow_back, contentDescription = "Back")
                    }
                }
                }
            }
        }
    }

    @Composable
    private fun HtmlText(
        html: String,
        style: androidx.compose.ui.text.TextStyle,
        color: Color,
        maxLines: Int = Int.MAX_VALUE
    ) {
        val annotatedString = remember(html) {
            // Standardize paragraph spacing without doubling every line break.
            // Most HTML-to-AnnotatedString parsers put a single newline after </p>.
            // We add one <br> to create a single empty line of space.
            val formattedHtml = html
                .replace("</p>", "</p><br>")
                .trim()
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

    private fun formatPublishedDate(dateString: String?): String {
        if (dateString.isNullOrBlank()) return "N/A"
        return try {
            val odt = OffsetDateTime.parse(dateString)
            odt.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()))
        } catch (_: Exception) {
            try {
                // Handle cases like "2015-07-20"
                val ld = java.time.LocalDate.parse(dateString.substringBefore('T'))
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
}
