package com.javierreansyah.pinecone.ui.features.reader

import com.javierreansyah.pinecone.ui.features.reader.components.SortOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.readium.r2.shared.publication.Link

class AnnotationUtilsTest {
    private data class Entry(val id: String, val json: String, val createdAt: Long)

    @Test
    fun sortingKeepsInvalidLocatorsAndDateTieBreaksInEveryMode() {
        fun entry(id: String, progression: Double, date: Long) = Entry(
            id,
            """{"href":"chapter.xhtml","type":"application/xhtml+xml","locations":{"totalProgression":$progression}}""",
            date,
        )

        val items = listOf(
            entry("late", .8, 10), entry("tie-new", .2, 30),
            Entry("invalid", "broken json", 40), entry("tie-old", .2, 20)
        )
        val expected = mapOf(
            SortOption.BOOK_ORDER_ASC to listOf("tie-old", "tie-new", "late", "invalid"),
            SortOption.BOOK_ORDER_DESC to listOf("invalid", "late", "tie-new", "tie-old"),
            SortOption.CREATION_DATE_ASC to listOf("late", "tie-old", "tie-new", "invalid"),
            SortOption.CREATION_DATE_DESC to listOf("invalid", "tie-new", "tie-old", "late"),
        )
        expected.forEach { (sort, ids) ->
            assertEquals(
                ids, sortAnnotations(
                    items, sort, emptyList(),
                { it.json.toLocatorOrNull() }, { it.createdAt }).map { it.id })
        }
        assertNull("broken json".toLocatorOrNull())
    }

    @Test
    fun readingOrderUsesChaptersThenProgressAndKeepsEqualEntriesStable() {
        val toc = listOf("z.xhtml", "a.xhtml").map { href ->
            requireNotNull(Link.fromJSON(org.json.JSONObject("""{"href":"$href","type":"application/xhtml+xml"}""")))
        }
        val items = listOf("a.xhtml" to .1, "z.xhtml" to .8, "z.xhtml" to .2, "z.xhtml" to .2)
        val sorted = sortAnnotations(items, SortOption.BOOK_ORDER_ASC, toc, { (href, progress) ->
            """{"href":"$href","type":"application/xhtml+xml","locations":{"progression":$progress}}""".toLocatorOrNull()
        }, { 1L })
        assertEquals(listOf(items[2], items[3], items[1], items[0]), sorted)
    }
}
