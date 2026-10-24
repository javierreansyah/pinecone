package com.javierreansyah.pinecone.data.publication

import com.javierreansyah.pinecone.data.local.database.library.PublicationCreditEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.readium.r2.shared.publication.Contributor
import org.readium.r2.shared.publication.Metadata
import java.io.File
import java.util.zip.ZipFile

@Serializable
data class PublicationMetadata(
    val subtitle: String = "",
    val sortTitle: String = "",
    val languages: List<String> = emptyList(),
    val identifiers: Map<String, String> = emptyMap(),
    val volume: String = "",
    val issue: String = "",
    val ageRating: String = "",
    val readingDirection: String = "auto",
    val pageCount: Int? = null,
    val duration: Double? = null,
    val published: String = "",
    val extensions: JsonObject = JsonObject(emptyMap()),
) {
    fun encode(): String = Json.encodeToString(this)

    companion object {
        fun decode(value: String): PublicationMetadata =
            Json.decodeFromString(value)

        val creditRoles: List<String> = listOf(
            "author", "writer", "artist", "illustrator", "penciler", "inker", "colorist",
            "letterer", "editor", "translator", "narrator", "publisher", "imprint", "contributor",
        )
    }
}

data class ImportedMetadata(
    val metadata: PublicationMetadata,
    val credits: List<PublicationCreditEntity>,
    val title: String? = null,
    val description: String? = null,
    val subjects: List<String> = emptyList(),
)

object PublicationMetadataImporter {
    fun read(bookId: String, file: File, metadata: Metadata): ImportedMetadata {
        val roles: Map<String, List<Contributor>> = linkedMapOf(
            "author" to metadata.authors, "artist" to metadata.artists,
            "illustrator" to metadata.illustrators, "penciler" to metadata.pencilers,
            "inker" to metadata.inkers, "colorist" to metadata.colorists,
            "letterer" to metadata.letterers, "editor" to metadata.editors,
            "translator" to metadata.translators, "narrator" to metadata.narrators,
            "publisher" to metadata.publishers, "imprint" to metadata.imprints,
            "contributor" to metadata.contributors,
        )
        val credits = roles.flatMap { (role, people) ->
            people.flatMapIndexed { index, person ->
                (person.roles + role).map { creditRole ->
                    PublicationCreditEntity(
                        bookId,
                        creditRole,
                        index,
                        person.name,
                        person.identifier,
                        person.toJSON().toString()
                    )
                }
            }
        }.let { imported ->
            val identified = mutableSetOf<Pair<String, String>>()
            imported.filter { it.identifier == null || identified.add(it.role to it.identifier) }
        }
            .groupBy { it.role }
            .flatMap { (_, people) -> people.mapIndexed { index, person -> person.copy(position = index) } }
        val details = PublicationMetadata(
            subtitle = metadata.localizedSubtitle?.string.orEmpty(),
            sortTitle = metadata.sortAs.orEmpty(),
            languages = metadata.languages,
            identifiers = metadata.identifier?.let { mapOf("identifier" to it) }.orEmpty(),
            readingDirection = metadata.readingProgression?.value ?: "auto",
            pageCount = metadata.numberOfPages,
            duration = metadata.duration,
            published = metadata.published?.toString().orEmpty(),
            extensions = Json.parseToJsonElement(metadata.toJSON().toString()) as JsonObject,
        )
        val base = ImportedMetadata(
            details,
            credits,
            subjects = metadata.subjects.map { it.name })
        return if (file.extension.equals("cbz", true) || file.extension.equals("zip", true)) {
            comicInfo(bookId, file, base)
        } else if (org.readium.r2.shared.publication.Publication.Profile.AUDIOBOOK in metadata.conformsTo) {
            AudioMetadataImporter.read(
                bookId, file, base.copy(title = metadata.title?.takeIf { it.isNotBlank() }),
            )
        } else base
    }

    private fun comicInfo(bookId: String, file: File, base: ImportedMetadata): ImportedMetadata =
        ZipFile(file).use { zip ->
            val entry = zip.entries().asSequence().firstOrNull {
                it.name.substringAfterLast('/').equals("ComicInfo.xml", true)
            } ?: return base
            require(entry.size in 0..MAX_COMIC_METADATA) { "ComicInfo.xml is too large" }
            val bytes =
                zip.getInputStream(entry).use { it.readNBytes(MAX_COMIC_METADATA.toInt() + 1) }
            require(bytes.size <= MAX_COMIC_METADATA) { "ComicInfo.xml is too large" }
            // Android's DOM implementation does not support the Xerces security-feature URIs.
            // A token parser with DTD processing disabled lets us reject every DOCTYPE explicitly.
            val parser = org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser().apply {
                setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
                setInput(bytes.inputStream(), null)
            }
            val fields = mutableMapOf<String, String>()
            var field: String? = null
            var value = StringBuilder()
            var token = parser.eventType
            while (token != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                require(token != org.xmlpull.v1.XmlPullParser.DOCDECL) { "ComicInfo.xml must not contain a DOCTYPE" }
                when (token) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> {
                        if (parser.depth == 1) require(parser.name == "ComicInfo") { "Invalid comic metadata root" }
                        if (parser.depth == 2) {
                            field = parser.name; value = StringBuilder()
                        }
                    }

                    org.xmlpull.v1.XmlPullParser.TEXT, org.xmlpull.v1.XmlPullParser.CDSECT -> if (field != null) value.append(
                        parser.text
                    )

                    org.xmlpull.v1.XmlPullParser.ENTITY_REF -> {
                        require(
                            parser.name in setOf(
                                "amp",
                                "lt",
                                "gt",
                                "apos",
                                "quot"
                            ) || parser.name.startsWith('#')
                        ) { "External/custom entities are not supported" }
                        if (field != null) value.append(parser.text.orEmpty())
                    }

                    org.xmlpull.v1.XmlPullParser.END_TAG -> if (parser.depth == 2) {
                        field?.let { fields[it] = value.toString().trim() }; field = null
                    }
                }
                token = parser.nextToken()
            }
            fun text(name: String): String = fields[name].orEmpty()
            val roleTags = mapOf(
                "writer" to "Writer",
                "penciler" to "Penciller",
                "inker" to "Inker",
                "colorist" to "Colorist",
                "letterer" to "Letterer",
                "artist" to "CoverArtist",
                "editor" to "Editor",
                "translator" to "Translator",
                "publisher" to "Publisher",
                "imprint" to "Imprint",
            )
            val comicCredits = roleTags.flatMap { (role, tag) ->
                text(tag).split(',').map(String::trim).filter(String::isNotBlank)
                    .mapIndexed { index, name ->
                        PublicationCreditEntity(bookId, role, index, name)
                    }
            }
            val allFields = fields.mapValues { kotlinx.serialization.json.JsonPrimitive(it.value) }
            val rawXml =
                bytes.toString(java.nio.charset.Charset.forName(parser.inputEncoding ?: "UTF-8"))
            base.copy(
                metadata = base.metadata.copy(
                    languages = text("LanguageISO").takeIf(String::isNotBlank)?.let(::listOf)
                        ?: base.metadata.languages,
                    volume = text("Volume"), issue = text("Number"), ageRating = text("AgeRating"),
                    pageCount = text("PageCount").toIntOrNull() ?: base.metadata.pageCount,
                    readingDirection = if (text("Manga").equals(
                            "YesAndRightToLeft",
                            true
                        )
                    ) "rtl" else base.metadata.readingDirection,
                    published = text("Year").toIntOrNull()?.takeIf { it > 0 }?.let { year ->
                        val month = text("Month").toIntOrNull()?.takeIf { it in 1..12 }
                        val day = text("Day").toIntOrNull()?.takeIf { it in 1..31 }
                        if (month == null) year.toString()
                        else if (day == null) "%04d-%02d".format(java.util.Locale.ROOT, year, month)
                        else "%04d-%02d-%02d".format(java.util.Locale.ROOT, year, month, day)
                    } ?: base.metadata.published,
                    extensions = JsonObject(
                        base.metadata.extensions + mapOf(
                            "comicInfo" to JsonObject(allFields),
                            "comicInfoXml" to kotlinx.serialization.json.JsonPrimitive(rawXml)
                        )
                    ),
                ),
                credits = base.credits.filterNot { it.role in roleTags } + comicCredits,
                title = text("Title").ifBlank { null },
                description = text("Summary").ifBlank { null },
                subjects = (base.subjects + text("Genre").split(',').map(String::trim)
                    .filter(String::isNotBlank)).distinct(),
            )
        }

    private const val MAX_COMIC_METADATA = 2L * 1024 * 1024
}
