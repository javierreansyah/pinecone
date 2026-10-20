package com.javierreansyah.pinecone.data.publication.conversion

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class GeneratedEpubLocationsTest {
    @Test
    fun rebasesBookmarkAndHighlightSelectorsWhilePreservingQuotesAndRangeOffsets() {
        val original = """{"href":"text/page-12.xhtml","type":"application/xhtml+xml","title":"Methods",
            "text":{"before":"before","highlight":"selected words","after":"after"},
            "locations":{"position":40,"progression":0.7,"totalProgression":0.4,"partialCfi":"/4/2",
            "cssSelector":"#block-3","domRange":{"start":{"cssSelector":"#block-3","textNodeIndex":0,"charOffset":2},
            "end":{"cssSelector":"#block-4","textNodeIndex":0,"charOffset":8}}}}"""
        val old = Json.parseToJsonElement(original).jsonObject
        val migrated = Json.parseToJsonElement(requireNotNull(GeneratedEpubLocations.migrate(original))).jsonObject
        assertEquals("text/content.xhtml", migrated.getValue("href").jsonPrimitive.content)
        assertEquals(old["title"], migrated["title"])
        assertEquals(old["text"], migrated["text"])
        val locations = migrated.getValue("locations").jsonObject
        assertEquals("#page-12-block-3", locations.getValue("cssSelector").jsonPrimitive.content)
        val range = locations.getValue("domRange").jsonObject
        assertEquals("#page-12-block-4", range.getValue("end").jsonObject.getValue("cssSelector").jsonPrimitive.content)
        assertEquals("8", range.getValue("end").jsonObject.getValue("charOffset").jsonPrimitive.content)
        assertEquals("0.4", locations.getValue("progression").jsonPrimitive.content)
        assertFalse(locations.containsKey("partialCfi"))
        assertFalse(locations.containsKey("position"))
    }

    @Test
    fun supportsBodySelectorsAndLegacyHrefFragments() {
        val body = GeneratedEpubLocations.migrate("""{"href":"text/page-2.xhtml","locations":{"cssSelector":"html > body > p:nth-child(3)"}}""")!!
        assertTrue(body.contains("#page-2 > p:nth-child(3)"))
        val anchor = GeneratedEpubLocations.migrate("""{"href":"text/page-2.xhtml#block-7"}""")!!
        assertTrue(anchor.contains("#page-2-block-7"))
        assertEquals(anchor, GeneratedEpubLocations.migrate(anchor))
    }

    @Test
    fun leavesOriginalPdfAndOtherEpubLocationsUntouched() {
        listOf("""{"href":"paper.pdf","locations":{"fragments":["page=2"]}}""",
            """{"href":"chapter2.xhtml","locations":{"position":4}}""").forEach {
            assertEquals(it, GeneratedEpubLocations.migrate(it))
        }
        assertNull(GeneratedEpubLocations.migrate(null))
    }

    @Test
    fun resolvesCurrentSourcePageFromVisibleBlocksBeforeStaleJumpFragments() {
        assertEquals(12, GeneratedEpubLocations.page("text/content.xhtml", "#page-12-block-3", listOf("source-page-2")))
        assertEquals(12, GeneratedEpubLocations.page("text/content.xhtml", "#page-12 > p"))
        assertEquals(2, GeneratedEpubLocations.page("text/content.xhtml#source-page-2"))
        assertEquals(3, GeneratedEpubLocations.page("text/page-3.xhtml"))
        assertNull(GeneratedEpubLocations.page("other.xhtml", "#page-12-block-3"))
    }

    @Test
    fun rebasesHighlightOffsetsIntoStyledRunsWithoutShiftingSelectedText() {
        val anchor = "page-1-block-0"
        val old = listOf(SourcePage(1, GeneratedEpubLocations.pageHref(1), listOf(SourceBlock(anchor, 0f, 0f, 100f, 20f))))
        val fresh = listOf(SourcePage(1, GeneratedEpubLocations.pageHref(1), listOf(SourceBlock(anchor, 0f, 0f, 100f, 20f,
            listOf(SourceTextRun("$anchor-run-0", 0, 4), SourceTextRun("$anchor-run-1", 4, 5), SourceTextRun("$anchor-run-2", 5, 10))))))
        val saved = """{"href":"text/content.xhtml","text":{"highlight":"selected"},"locations":{"cssSelector":"#page-1-block-0",
            "domRange":{"start":{"cssSelector":"#page-1-block-0","charOffset":4,"textNodeIndex":0},
            "end":{"cssSelector":"#page-1-block-0","charOffset":8,"textNodeIndex":0}}}}"""
        val result = Json.parseToJsonElement(GeneratedEpubLocations.remap(saved, old, fresh)!!).jsonObject
        val range = result.getValue("locations").jsonObject.getValue("domRange").jsonObject
        assertEquals("#$anchor-run-1", range.getValue("start").jsonObject.getValue("cssSelector").jsonPrimitive.content)
        assertEquals("0", range.getValue("start").jsonObject.getValue("charOffset").jsonPrimitive.content)
        assertEquals("#$anchor-run-2", range.getValue("end").jsonObject.getValue("cssSelector").jsonPrimitive.content)
        assertEquals("3", range.getValue("end").jsonObject.getValue("charOffset").jsonPrimitive.content)
        assertEquals(Json.parseToJsonElement(saved).jsonObject["text"], result["text"])
        assertEquals(1, GeneratedEpubLocations.page("text/content.xhtml", "#$anchor-run-1"))
        assertEquals(2, GeneratedEpubLocations.page("text/content.xhtml", "#page-2-source-3-run-0"))
    }

    @Test
    fun imageEquationKeepsNoteAnchorButDropsUnrepresentableTextRange() {
        val anchor = "page-1-block-0"
        val old = listOf(SourcePage(1, "text/content.xhtml", listOf(SourceBlock(anchor, 0f, 0f, 10f, 10f))))
        val fresh = listOf(SourcePage(1, "text/content.xhtml", listOf(SourceBlock(anchor, 0f, 0f, 10f, 10f,
            listOf(SourceTextRun("$anchor-run-0", 0, 4, image = true))))))
        val saved = """{"href":"text/content.xhtml","text":{"highlight":"math"},"locations":{"cssSelector":"#page-1-block-0",
            "domRange":{"start":{"cssSelector":"#page-1-block-0","charOffset":0},"end":{"cssSelector":"#page-1-block-0","charOffset":4}}}}"""
        val result = Json.parseToJsonElement(GeneratedEpubLocations.remap(saved, old, fresh)!!).jsonObject
        assertFalse(result.getValue("locations").jsonObject.containsKey("domRange"))
        assertEquals("#page-1-block-0", result.getValue("locations").jsonObject.getValue("cssSelector").jsonPrimitive.content)
        assertEquals(Json.parseToJsonElement(saved).jsonObject["text"], result["text"])
    }
}
