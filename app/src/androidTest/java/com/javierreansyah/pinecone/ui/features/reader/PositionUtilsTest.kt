package com.javierreansyah.pinecone.ui.features.reader

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator

class PositionUtilsTest {
    @Test
    fun generatedChaptersUseTheirOwnChunkAndSourcePage() {
        val first = locator("EPUB/text/content.xhtml", 0.0)
        val later = locator("EPUB/text/content-0002.xhtml", 0.5)
        val chapter = requireNotNull(
            Link.fromJSON(
                JSONObject(
                    """{"href":"text/content-0002.xhtml#page-30-block-2","type":"application/xhtml+xml","title":"Later"}"""
                )
            )
        )
        val positions = listOf(first, later)
        val target = positions.chapterLocator(chapter)

        assertEquals(later.href, target.href)
        assertEquals(listOf("page-30-block-2"), target.locations.fragments)
        assertEquals(30, positions.pageNumber(target, readMode = true))
        assertEquals(30, positions.chapterPageNumber(chapter))
        assertEquals(2, positions.pageNumber(later))
        assertNull(emptyList<Locator>().pageNumber(first))
        assertEquals(chapter.url(), emptyList<Locator>().chapterLocator(chapter).href)
    }

    @Test
    fun ordinaryChaptersKeepTheirExistingPositionAndPage() {
        val first = locator("chapter1.xhtml", 0.0)
        val second = locator("chapter2.xhtml", 0.5)
        val chapter = requireNotNull(
            Link.fromJSON(
                JSONObject(
                    """{"href":"chapter2.xhtml#heading","type":"application/xhtml+xml"}"""
                )
            )
        )
        val positions = listOf(first, second)
        assertEquals(second, positions.chapterLocator(chapter))
        assertEquals(2, positions.chapterPageNumber(chapter))
        assertEquals(2, positions.pageNumber(second))
    }

    private fun locator(href: String, progression: Double): Locator = requireNotNull(
        Locator.fromJSON(
            JSONObject(
                """{"href":"$href","type":"application/xhtml+xml","locations":{"totalProgression":$progression}}"""
            )
        )
    )
}
