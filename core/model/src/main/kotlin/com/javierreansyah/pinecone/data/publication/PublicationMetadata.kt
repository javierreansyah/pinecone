package com.javierreansyah.pinecone.data.publication

import com.javierreansyah.pinecone.data.model.PublicationCredit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Stored publication details, independent of database and reading engines. */
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
        fun decode(value: String): PublicationMetadata = Json.decodeFromString(value)

        val creditRoles: List<String> = listOf(
            "author", "writer", "artist", "illustrator", "penciler", "inker", "colorist",
            "letterer", "editor", "translator", "narrator", "publisher", "imprint", "contributor",
        )
    }
}

data class ImportedMetadata(
    val metadata: PublicationMetadata,
    val credits: List<PublicationCredit>,
    val title: String? = null,
    val description: String? = null,
    val subjects: List<String> = emptyList(),
)
