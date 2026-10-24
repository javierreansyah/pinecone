package com.javierreansyah.pinecone.ui.features.library.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.data.publication.PublicationMetadata

@Composable
fun RichMetadataEditor(
    state: EditBookUiState,
    onMetadata: (PublicationMetadata) -> Unit,
    onCredit: (String, String) -> Unit,
    onLanguages: (String) -> Unit,
    onIdentifiers: (String) -> Unit,
) {
    val metadata = state.metadata
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
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
            change = onLanguages
        )
        MetadataField(
            stringResource(R.string.publication_identifiers),
            state.identifierDraft,
            change = onIdentifiers
        )
        MetadataField(
            stringResource(R.string.publication_volume),
            metadata.volume
        ) { onMetadata(metadata.copy(volume = it)) }
        MetadataField(stringResource(R.string.publication_issue), metadata.issue) {
            onMetadata(metadata.copy(issue = it))
        }
        MetadataField(
            stringResource(R.string.publication_age_rating),
            metadata.ageRating
        ) { onMetadata(metadata.copy(ageRating = it)) }
        MetadataField(
            stringResource(R.string.publication_published),
            metadata.published
        ) { onMetadata(metadata.copy(published = it)) }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        (PublicationMetadata.creditRoles + state.creditDrafts.keys).distinct()
            .filterNot { it == "author" }.forEach { role ->
                MetadataField(
                    roleLabel(role),
                    state.creditDrafts[role].orEmpty()
                ) { onCredit(role, it) }
            }
    }
}

@Composable
private fun MetadataField(
    label: String,
    value: String,
    change: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = change,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
internal fun richMetadataFields(book: Book): List<Pair<String, String>> {
    val metadata = book.metadata
    val numbers = java.text.NumberFormat.getIntegerInstance(LocalLocale.current.platformLocale)
    val fields = buildList {
        metadataCredits(book).forEach { (role, names) ->
            add(roleLabel(role) to names.joinToString(", "))
        }
        if (metadata.subtitle.isNotBlank()) add(stringResource(R.string.publication_subtitle) to metadata.subtitle)
        if (metadata.volume.isNotBlank()) add(stringResource(R.string.publication_volume) to metadata.volume)
        if (metadata.issue.isNotBlank()) add(stringResource(R.string.publication_issue) to metadata.issue)
        if (metadata.ageRating.isNotBlank()) add(stringResource(R.string.publication_age_rating) to metadata.ageRating)
        metadata.pageCount?.takeIf { it > 0 }
            ?.let { add(stringResource(R.string.reader_pages) to numbers.format(it)) }
        metadata.duration?.takeIf { it.isFinite() && it > 0 }?.let { duration ->
            val minutes = (duration / 60).toInt()
            add(
                stringResource(R.string.publication_duration) to pluralStringResource(
                    R.plurals.publication_duration_minutes,
                    minutes,
                    minutes
                )
            )
        }
        val seenIdentifiers = mutableSetOf<String>()
        metadataValue(book.identifier)?.let { seenIdentifiers.add(it.lowercase(java.util.Locale.ROOT)) }
        metadata.identifiers.forEach { (scheme, value) ->
            metadataValue(value)?.let {
                if (seenIdentifiers.add(it.lowercase(java.util.Locale.ROOT))) add(scheme to it)
            }
        }
    }
    return fields.mapNotNull { (label, value) -> metadataValue(value)?.let { label to it } }
        .distinct()
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
    "publisher" -> stringResource(R.string.publication_publisher)
    "imprint" -> stringResource(R.string.publication_role_imprint)
    "contributor" -> stringResource(R.string.publication_role_contributor)
    else -> role // Preserve unknown, publisher-provided role identifiers verbatim.
}
