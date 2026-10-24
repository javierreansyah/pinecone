package com.javierreansyah.pinecone.ui.features.library.info

import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.data.publication.PublicationMetadata
import java.util.Locale

internal fun metadataValue(value: String?): String? = value?.trim()?.takeUnless {
    it.isEmpty() || it.lowercase(Locale.ROOT) in setOf("n/a", "na", "not available", "null")
}

internal fun cleanMetadataValues(values: List<String>): List<String> =
    values.mapNotNull(::metadataValue).distinctBy { it.lowercase(Locale.ROOT) }

/** Merge legacy fields and rich credits once, with bibliographic roles first. */
internal fun metadataCredits(book: Book): List<Pair<String, List<String>>> {
    val credits = book.credits.groupBy { it.role.trim().lowercase(Locale.ROOT) }
    val roles = (listOf(
        "author",
        "publisher"
    ) + PublicationMetadata.creditRoles + credits.keys.sorted()).distinct()
    return roles.mapNotNull { role ->
        val primary = when (role) {
            "author" -> book.authors
            "publisher" -> listOfNotNull(book.publisher)
            else -> emptyList()
        }
        val creditNames =
            cleanMetadataValues(credits[role].orEmpty().sortedBy { it.position }.map { it.name })
        val primaryNames = cleanMetadataValues(primary)
        val names = if (primaryNames.joinToString(", ")
                .equals(creditNames.joinToString(", "), ignoreCase = true)
        ) {
            creditNames
        } else cleanMetadataValues(primaryNames + creditNames)
        names.takeIf { it.isNotEmpty() }?.let { role to it }
    }
}
