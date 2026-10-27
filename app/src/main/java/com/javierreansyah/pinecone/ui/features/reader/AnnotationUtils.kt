package com.javierreansyah.pinecone.ui.features.reader

import com.javierreansyah.pinecone.ui.features.reader.components.SortOption
import org.json.JSONObject
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator

internal fun String.toLocatorOrNull(): Locator? = try {
    Locator.fromJSON(JSONObject(this))
} catch (_: Exception) {
    null
}

internal fun <T> sortAnnotations(
    items: List<T>, sort: SortOption, toc: List<Link>,
    locator: (T) -> Locator?, createdAt: (T) -> Long,
): List<T> = items.map { it to locator(it) }.sortedWith { (a, first), (b, second) ->
    when (sort) {
        SortOption.CREATION_DATE_ASC -> createdAt(a).compareTo(createdAt(b))
        SortOption.CREATION_DATE_DESC -> createdAt(b).compareTo(createdAt(a))
        SortOption.BOOK_ORDER_ASC -> compareLocatorsWithFallback(first, second, toc)
            ?: createdAt(a).compareTo(createdAt(b))

        SortOption.BOOK_ORDER_DESC -> compareLocatorsWithFallback(second, first, toc)
            ?: createdAt(b).compareTo(createdAt(a))
    }
}.map { it.first }

private fun compareLocatorsWithFallback(loc1: Locator?, loc2: Locator?, toc: List<Link>): Int? {
    if (loc1 != null && loc2 != null) {
        val comp = compareLocators(loc1, loc2, toc)
        if (comp != 0) return comp
        return null
    }
    if (loc1 != null) return -1
    if (loc2 != null) return 1
    return null
}

private fun compareLocators(loc1: Locator, loc2: Locator, tableOfContents: List<Link>): Int {
    val tp1 = loc1.locations.totalProgression
    val tp2 = loc2.locations.totalProgression
    if (tp1 != null && tp2 != null) return tp1.compareTo(tp2)

    val href1 = loc1.href.toString().substringBefore("#")
    val href2 = loc2.href.toString().substringBefore("#")
    if (href1 != href2) {
        val idx1 =
            tableOfContents.indexOfFirst { link ->
                link.href.toString().substringBefore("#") == href1
            }
        val idx2 =
            tableOfContents.indexOfFirst { link ->
                link.href.toString().substringBefore("#") == href2
            }
        if (idx1 != -1 && idx2 != -1) return idx1.compareTo(idx2)
        if (idx1 != -1) return -1
        if (idx2 != -1) return 1
        return href1.compareTo(href2)
    }

    val p1 = loc1.locations.progression
    val p2 = loc2.locations.progression
    if (p1 != null && p2 != null) return p1.compareTo(p2)

    val pos1 = loc1.locations.position
    val pos2 = loc2.locations.position
    if (pos1 != null && pos2 != null) return pos1.compareTo(pos2)

    return 0
}

