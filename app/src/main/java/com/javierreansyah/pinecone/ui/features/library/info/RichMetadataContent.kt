package com.javierreansyah.pinecone.ui.features.library.info

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.database.library.PublicationCollectionEntity
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.data.publication.PublicationMetadata

@Composable
fun RichMetadataEditor(
    state: EditBookUiState,
    bookId: String,
    onMetadata: (PublicationMetadata) -> Unit,
    onCredit: (String, String) -> Unit,
    onLanguages: (String) -> Unit,
    onIdentifiers: (String) -> Unit,
    onCollections: (List<PublicationCollectionEntity>) -> Unit,
) {
    val metadata = state.metadata
    Card(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.publication_metadata),
                style = MaterialTheme.typography.titleLarge
            )
            MetadataField(
                stringResource(R.string.publication_subtitle),
                metadata.subtitle
            ) { onMetadata(metadata.copy(subtitle = it)) }
            MetadataField(
                stringResource(R.string.publication_sort_title),
                metadata.sortTitle
            ) { onMetadata(metadata.copy(sortTitle = it)) }
            MetadataField(
                stringResource(R.string.publication_languages),
                state.languageDraft,
                onLanguages
            )
            MetadataField(
                stringResource(R.string.publication_identifiers),
                state.identifierDraft,
                onIdentifiers
            )
            MetadataField(
                stringResource(R.string.publication_volume),
                metadata.volume
            ) { onMetadata(metadata.copy(volume = it)) }
            MetadataField(stringResource(R.string.publication_issue), metadata.issue) {
                onMetadata(
                    metadata.copy(issue = it)
                )
            }
            MetadataField(
                stringResource(R.string.publication_age_rating),
                metadata.ageRating
            ) { onMetadata(metadata.copy(ageRating = it)) }
            MetadataField(
                stringResource(R.string.publication_published),
                metadata.published
            ) { onMetadata(metadata.copy(published = it)) }
            Text(
                stringResource(R.string.publication_reading_direction),
                style = MaterialTheme.typography.titleSmall
            )
            val directions = linkedMapOf(
                stringResource(R.string.reader_settings_theme_auto) to "auto",
                stringResource(R.string.publication_left_to_right) to "ltr",
                stringResource(R.string.reader_right_to_left) to "rtl"
            )
            com.javierreansyah.pinecone.ui.components.SegmentedButtonGroup(
                options = directions.keys.toList(), icons = emptyList(),
                selected = directions.entries.firstOrNull { it.value == metadata.readingDirection }?.key
                    ?: directions.keys.first(),
                onSelected = { onMetadata(metadata.copy(readingDirection = directions.getValue(it))) })
        }
    }
    Card(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            var expanded by remember { mutableStateOf(false) }
            TextButton(onClick = {
                expanded = !expanded
            }) {
                Text(
                    stringResource(R.string.publication_credits),
                    style = MaterialTheme.typography.titleLarge
                )
            }
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.publication_credit_hint),
                        style = MaterialTheme.typography.bodySmall
                    )
                    (PublicationMetadata.creditRoles + state.creditDrafts.keys).distinct()
                        .filterNot { it == "author" }.forEach { role ->
                        MetadataField(
                            roleLabel(role),
                            state.creditDrafts[role].orEmpty()
                        ) { onCredit(role, it) }
                    }
                }
            }
        }
    }
    Card(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.publication_series),
                style = MaterialTheme.typography.titleLarge
            )
            state.collections.forEachIndexed { index, collection ->
                fun change(value: PublicationCollectionEntity) =
                    onCollections(state.collections.toMutableList().apply { set(index, value) })
                MetadataField(
                    stringResource(R.string.publication_collection_type),
                    collection.kind
                ) { change(collection.copy(kind = it)) }
                MetadataField(
                    stringResource(R.string.publication_collection_name),
                    collection.name
                ) { change(collection.copy(name = it)) }
                MetadataField(
                    stringResource(R.string.publication_series_number),
                    collection.number.orEmpty()
                ) { change(collection.copy(number = it.ifBlank { null })) }
                TextButton(onClick = { onCollections(state.collections.filterIndexed { position, _ -> position != index }) }) {
                    Text(
                        stringResource(R.string.action_remove)
                    )
                }
            }
            TextButton(onClick = {
                onCollections(
                    state.collections + PublicationCollectionEntity(
                        bookId,
                        "series",
                        state.collections.size,
                        ""
                    )
                )
            }) { Text(stringResource(R.string.publication_add_collection)) }
        }
    }
}

@Composable
private fun MetadataField(label: String, value: String, change: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = change,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun richMetadataFields(book: Book): List<Pair<String, String>> {
    val metadata = book.metadata
    val numbers = java.text.NumberFormat.getIntegerInstance(LocalLocale.current.platformLocale)
    val fields = buildList {
        if (metadata.subtitle.isNotBlank()) add(stringResource(R.string.publication_subtitle) to metadata.subtitle)
        if (metadata.volume.isNotBlank()) add(stringResource(R.string.publication_volume) to metadata.volume)
        if (metadata.issue.isNotBlank()) add(stringResource(R.string.publication_issue) to metadata.issue)
        if (metadata.ageRating.isNotBlank()) add(stringResource(R.string.publication_age_rating) to metadata.ageRating)
        metadata.pageCount?.let { add(stringResource(R.string.reader_pages) to numbers.format(it)) }
        metadata.duration?.let { duration ->
            val minutes = (duration / 60).toInt()
            add(
                stringResource(R.string.publication_duration) to pluralStringResource(
                    R.plurals.publication_duration_minutes,
                    minutes,
                    minutes
                )
            )
        }
        metadata.identifiers.forEach { (scheme, value) ->
            if (value.isNotBlank() && value != book.identifier) add(scheme to value)
        }
        book.collections.forEach {
            add(
                roleLabel(it.kind) to listOfNotNull(
                    it.name,
                    it.number
                ).joinToString(" · ")
            )
        }
        book.credits.groupBy { it.role }.forEach { (role, credits) ->
            add(roleLabel(role) to credits.sortedBy { it.position }.joinToString(", ") { it.name })
        }
    }
    return fields.filter { (_, value) -> value.isNotBlank() }
}

@Composable
private fun roleLabel(role: String): String = when (role) {
    "author" -> stringResource(R.string.publication_role_author)
    "writer" -> stringResource(R.string.publication_role_writer)
    "artist" -> stringResource(R.string.publication_role_artist)
    "illustrator" -> stringResource(R.string.publication_role_illustrator)
    "penciler" -> stringResource(R.string.publication_role_penciler)
    "inker" -> stringResource(R.string.publication_role_inker)
    "colorist" -> stringResource(R.string.publication_role_colorist)
    "letterer" -> stringResource(R.string.publication_role_letterer)
    "editor" -> stringResource(R.string.publication_role_editor)
    "translator" -> stringResource(R.string.publication_role_translator)
    "narrator" -> stringResource(R.string.publication_role_narrator)
    "publisher" -> stringResource(R.string.book_publisher)
    "imprint" -> stringResource(R.string.publication_role_imprint)
    "contributor" -> stringResource(R.string.publication_role_contributor)
    "series" -> stringResource(R.string.publication_series)
    "collection" -> stringResource(R.string.publication_collection)
    else -> role // Preserve unknown, publisher-provided role/collection identifiers verbatim.
}
