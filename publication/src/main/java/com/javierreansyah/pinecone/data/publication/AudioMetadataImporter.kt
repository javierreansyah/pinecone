package com.javierreansyah.pinecone.data.publication

import android.media.MediaMetadataRetriever
import com.javierreansyah.pinecone.data.model.PublicationCredit
import java.io.File

/** Read embedded audio tags which Readium's standalone audio parser does not import. */
internal object AudioMetadataImporter {
    fun read(bookId: String, file: File, base: ImportedMetadata): ImportedMetadata {
        // Packaged audiobooks carry a manifest; do not pass their ZIP container to the media reader.
        if (file.extension.lowercase() !in PublicationFormats.audioFileExtensions) return base
        return try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(file.absolutePath)
                fun tag(key: Int) =
                    retriever.extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() }
                AudioTags(
                    title = tag(MediaMetadataRetriever.METADATA_KEY_TITLE),
                    writer = tag(MediaMetadataRetriever.METADATA_KEY_WRITER),
                    artist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                    genre = tag(MediaMetadataRetriever.METADATA_KEY_GENRE),
                    year = tag(MediaMetadataRetriever.METADATA_KEY_YEAR),
                    durationSeconds = tag(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toDoubleOrNull()?.div(1000.0),
                ).merge(bookId, base)
            }
        } catch (_: Exception) {
            // Missing or unsupported tags must not prevent importing a playable publication.
            base
        }
    }


}

internal data class AudioTags(
    val title: String? = null,
    val writer: String? = null,
    val artist: String? = null,
    val genre: String? = null,
    val year: String? = null,
    val durationSeconds: Double? = null,
) {
    fun merge(bookId: String, base: ImportedMetadata): ImportedMetadata {
        fun String?.tagValue() = this?.trim()?.takeIf { it.isNotEmpty() }
        val author = writer.tagValue() ?: artist.tagValue()
        val hasAuthors = base.credits.any {
            (it.role == "author" || it.role == "writer") && it.name.isNotBlank()
        }
        val credits = if (!hasAuthors && author != null) {
            base.credits + PublicationCredit(
                bookId = bookId, role = "author", position = 0, name = author,
            )
        } else base.credits
        return base.copy(
            title = base.title.tagValue() ?: title.tagValue(),
            credits = credits,
            subjects = (base.subjects + listOfNotNull(genre.tagValue())).distinct(),
            metadata = base.metadata.copy(
                published = base.metadata.published.ifBlank { year.tagValue().orEmpty() },
                duration = base.metadata.duration
                    ?: durationSeconds?.takeIf { it.isFinite() && it > 0 },
            ),
        )
    }
}
