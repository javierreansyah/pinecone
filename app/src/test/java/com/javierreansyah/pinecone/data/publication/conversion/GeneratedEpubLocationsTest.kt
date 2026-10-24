package com.javierreansyah.pinecone.data.publication.conversion

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedEpubLocationsTest {
    @Test
    fun reparsedBlockSuffixesStillIdentifyTheOriginalPdfPage() {
        val href = "EPUB/text/content.xhtml"
        assertEquals(6, GeneratedEpubLocations.page(href, "#page-6-native-27-new"))
        assertEquals(6, GeneratedEpubLocations.page(href, "#page-6-native-27-new-run-0"))
        assertEquals(
            6,
            GeneratedEpubLocations.page(href, "#page-6-native-27-new-new > span:nth-child(2)")
        )
    }

    @Test
    fun nativeSvgDescendantsKeepTheSourcePageInsteadOfUsingTheirGlyphOrPositionNumbers() {
        val href = "EPUB/text/content-0004.xhtml"
        listOf(
            "#page-83-visual-1-clip_1", "#page-83-visual-1-font_2_621",
            "#page-83-visual-1-font_2_621 use:nth-child(154)", "#page-83-native-154-run-0",
            "#page-83 .math-layout > svg > g > path"
        ).forEach { selector ->
            assertEquals(selector, 83, GeneratedEpubLocations.page(href, selector))
        }
        assertEquals(
            83,
            GeneratedEpubLocations.page(href, fragments = listOf("page-83-visual-1-clip_1"))
        )
        assertEquals(83, GeneratedEpubLocations.page("$href#page-83-visual-1-font_2_621"))
        assertNull(GeneratedEpubLocations.page(href, "#glyph621"))
    }

    @Test
    fun packageAndContainerPathsIdentifyTheSameGeneratedResource() {
        assertTrue(
            GeneratedEpubLocations.sameContentResource(
                "text/content.xhtml",
                "EPUB/text/content.xhtml"
            )
        )
        assertTrue(
            GeneratedEpubLocations.sameContentResource(
                "text/content.xhtml#source-page-54",
                "/EPUB/text/content.xhtml"
            )
        )
        assertTrue(
            GeneratedEpubLocations.sameContentResource(
                "text/content-0002.xhtml",
                "EPUB/text/content-0002.xhtml"
            )
        )
        assertFalse(
            GeneratedEpubLocations.sameContentResource(
                "text/content.xhtml",
                "EPUB/text/content-0002.xhtml"
            )
        )
        assertFalse(
            GeneratedEpubLocations.sameContentResource(
                "chapter.xhtml",
                "EPUB/chapter.xhtml"
            )
        )
    }

    @Test
    fun locationsFromSplitRenditionsReturnToTheSingleFileWithoutLosingTheirTextRange() {
        val anchor = "page-54-block-3"
        val block = SourceBlock(
            anchor, 0f, 0f, 100f, 20f, listOf(SourceTextRun("$anchor-run-0", 0, 20)),
            href = "text/content-0007.xhtml"
        )
        val old = listOf(SourcePage(54, "text/content-0006.xhtml#source-page-54", listOf(block)))
        val fresh = listOf(
            SourcePage(
                54,
                "text/content.xhtml#source-page-54",
                listOf(block.copy(href = "text/content.xhtml"))
            )
        )
        val saved =
            """{"href":"text/content-0007.xhtml","text":{"highlight":"selected words"},"locations":{
            "position":150,"progression":0.7,"cssSelector":"#$anchor-run-0","domRange":{
            "start":{"cssSelector":"#$anchor-run-0","charOffset":2},"end":{"cssSelector":"#$anchor-run-0","charOffset":8}}}}"""
        val migrated =
            Json.parseToJsonElement(
                GeneratedEpubLocations.remap(saved, old, fresh) ?: return
            ).jsonObject
        assertEquals("text/content.xhtml", migrated.getValue("href").jsonPrimitive.content)
        assertEquals(
            "selected words",
            migrated.getValue("text").jsonObject.getValue("highlight").jsonPrimitive.content
        )
        val locations = migrated.getValue("locations").jsonObject
        assertTrue(locations.containsKey("domRange"))
        assertFalse(locations.containsKey("position")); assertFalse(locations.containsKey("progression"))
    }

    @Test
    fun chaptersOnTheSamePdfPageFollowTheVisibleBlockAcrossResources() {
        val chapters = listOf(
            "text/content.xhtml#page-2-block-0", "text/content-0002.xhtml#page-2-block-12",
            "text/content-0002.xhtml#page-2-block-20"
        )
        assertEquals(
            0,
            GeneratedEpubLocations.chapterIndex(
                chapters,
                "text/content.xhtml",
                "#page-2-block-5-run-0"
            )
        )
        assertEquals(
            1,
            GeneratedEpubLocations.chapterIndex(
                chapters,
                "text/content-0002.xhtml",
                "#page-2-block-14-run-1"
            )
        )
        assertEquals(
            2,
            GeneratedEpubLocations.chapterIndex(
                chapters,
                "text/content-0003.xhtml",
                "#page-3-block-0"
            )
        )
    }

    @Test
    fun savedLocationsFollowBlocksWhenOnePdfPageSpansSeveralResources() {
        val block = SourceBlock(
            "page-3-block-4", 0f, 0f, 100f, 20f,
            listOf(SourceTextRun("page-3-block-4-run-0", 0, 20))
        )
        val old = listOf(SourcePage(3, "text/content.xhtml#source-page-3", listOf(block)))
        val fresh = listOf(
            SourcePage(
                3, "text/content-0002.xhtml#source-page-3",
                listOf(block.copy(href = "text/content-0003.xhtml"))
            )
        )
        val saved = """{"href":"text/content.xhtml","text":{"highlight":"selected"},"locations":{
            "position":90,"progression":0.6,"totalProgression":0.4,"fragments":["source-page-3"],
            "cssSelector":"#page-3-block-4-run-0","domRange":{"start":{"cssSelector":"#page-3-block-4-run-0","charOffset":2},
            "end":{"cssSelector":"#page-3-block-4-run-0","charOffset":8}}}}"""
        val migrated =
            Json.parseToJsonElement(
                GeneratedEpubLocations.remap(saved, old, fresh) ?: return
            ).jsonObject
        assertEquals("text/content-0003.xhtml", migrated.getValue("href").jsonPrimitive.content)
        val locations = migrated.getValue("locations").jsonObject
        assertFalse(locations.containsKey("position")); assertFalse(locations.containsKey("progression"))
        assertTrue(locations.containsKey("domRange"))
        assertEquals(
            3,
            GeneratedEpubLocations.page("text/content-0003.xhtml", "#page-3-block-4-run-0")
        )
        assertEquals(
            GeneratedEpubLocations.remap(saved, old, fresh), GeneratedEpubLocations.remap(
                GeneratedEpubLocations.remap(saved, old, fresh), fresh, fresh
            )
        )
    }

    @Test
    fun aHighlightCrossingANewFileBoundaryKeepsItsQuoteAndStartAnchor() {
        val a = SourceBlock(
            "page-1-block-0",
            0f,
            0f,
            100f,
            20f,
            listOf(SourceTextRun("page-1-block-0-run-0", 0, 10))
        )
        val b = SourceBlock(
            "page-1-block-1",
            0f,
            30f,
            100f,
            20f,
            listOf(SourceTextRun("page-1-block-1-run-0", 0, 10))
        )
        val old = listOf(SourcePage(1, "text/content.xhtml", listOf(a, b)))
        val fresh = listOf(
            SourcePage(
                1,
                "text/content.xhtml",
                listOf(
                    a.copy(href = "text/content.xhtml"),
                    b.copy(href = "text/content-0002.xhtml")
                )
            )
        )
        val saved =
            """{"href":"text/content.xhtml","text":{"highlight":"selected quote"},"locations":{"cssSelector":"#page-1-block-0",
            "domRange":{"start":{"cssSelector":"#page-1-block-0","charOffset":1},"end":{"cssSelector":"#page-1-block-1","charOffset":5}}}}"""
        val migrated =
            Json.parseToJsonElement(
                GeneratedEpubLocations.remap(saved, old, fresh) ?: return
            ).jsonObject
        assertEquals(
            "selected quote",
            migrated.getValue("text").jsonObject.getValue("highlight").jsonPrimitive.content
        )
        assertFalse(migrated.getValue("locations").jsonObject.containsKey("domRange"))
        assertEquals("text/content.xhtml", migrated.getValue("href").jsonPrimitive.content)
    }

    @Test
    fun reparsedNativeBlocksStillIdentifyTheOriginalPdfPage() {
        assertEquals(
            54, GeneratedEpubLocations.page(
                GeneratedEpubLocations.CONTENT_HREF,
                "#page-54-native-3-run-2"
            )
        )
        assertEquals(
            54, GeneratedEpubLocations.page(
                GeneratedEpubLocations.CONTENT_HREF,
                fragments = listOf("source-page-54")
            )
        )
    }

    @Test
    fun rebasesBookmarkAndHighlightSelectorsWhilePreservingQuotesAndRangeOffsets() {
        val original =
            """{"href":"text/page-12.xhtml","type":"application/xhtml+xml","title":"Methods",
            "text":{"before":"before","highlight":"selected words","after":"after"},
            "locations":{"position":40,"progression":0.7,"totalProgression":0.4,"partialCfi":"/4/2",
            "cssSelector":"#block-3","domRange":{"start":{"cssSelector":"#block-3","textNodeIndex":0,"charOffset":2},
            "end":{"cssSelector":"#block-4","textNodeIndex":0,"charOffset":8}}}}"""
        val old = Json.parseToJsonElement(original).jsonObject
        val migrated =
            Json.parseToJsonElement(requireNotNull(GeneratedEpubLocations.migrate(original))).jsonObject
        assertEquals("text/content.xhtml", migrated.getValue("href").jsonPrimitive.content)
        assertEquals(old["title"], migrated["title"])
        assertEquals(old["text"], migrated["text"])
        val locations = migrated.getValue("locations").jsonObject
        assertEquals("#page-12-block-3", locations.getValue("cssSelector").jsonPrimitive.content)
        val range = locations.getValue("domRange").jsonObject
        assertEquals(
            "#page-12-block-4",
            range.getValue("end").jsonObject.getValue("cssSelector").jsonPrimitive.content
        )
        assertEquals(
            "8",
            range.getValue("end").jsonObject.getValue("charOffset").jsonPrimitive.content
        )
        assertEquals("0.4", locations.getValue("progression").jsonPrimitive.content)
        assertFalse(locations.containsKey("partialCfi"))
        assertFalse(locations.containsKey("position"))
    }

    @Test
    fun supportsBodySelectorsAndLegacyHrefFragments() {
        val body =
            GeneratedEpubLocations.migrate("""{"href":"text/page-2.xhtml","locations":{"cssSelector":"html > body > p:nth-child(3)"}}""")
                ?: return
        assertTrue("#page-2 > p:nth-child(3)" in body)
        val anchor =
            GeneratedEpubLocations.migrate("""{"href":"text/page-2.xhtml#block-7"}""") ?: return
        assertTrue("#page-2-block-7" in anchor)
        assertEquals(anchor, GeneratedEpubLocations.migrate(anchor))
    }

    @Test
    fun leavesOriginalPdfAndOtherEpubLocationsUntouched() {
        listOf(
            """{"href":"paper.pdf","locations":{"fragments":["page=2"]}}""",
            """{"href":"chapter2.xhtml","locations":{"position":4}}"""
        ).forEach {
            assertEquals(it, GeneratedEpubLocations.migrate(it))
        }
        assertNull(GeneratedEpubLocations.migrate(null))
    }

    @Test
    fun resolvesCurrentSourcePageFromVisibleBlocksBeforeStaleJumpFragments() {
        assertEquals(
            12,
            GeneratedEpubLocations.page(
                "text/content.xhtml",
                "#page-12-block-3",
                listOf("source-page-2")
            )
        )
        assertEquals(12, GeneratedEpubLocations.page("text/content.xhtml", "#page-12 > p"))
        assertEquals(2, GeneratedEpubLocations.page("text/content.xhtml#source-page-2"))
        assertEquals(3, GeneratedEpubLocations.page("text/page-3.xhtml"))
        assertNull(GeneratedEpubLocations.page("other.xhtml", "#page-12-block-3"))
    }

    @Test
    fun rebasesHighlightOffsetsIntoStyledRunsWithoutShiftingSelectedText() {
        val anchor = "page-1-block-0"
        val old = listOf(
            SourcePage(
                1,
                GeneratedEpubLocations.pageHref(1),
                listOf(SourceBlock(anchor, 0f, 0f, 100f, 20f))
            )
        )
        val fresh = listOf(
            SourcePage(
                1, GeneratedEpubLocations.pageHref(1), listOf(
                    SourceBlock(
                        anchor, 0f, 0f, 100f, 20f,
                        listOf(
                            SourceTextRun("$anchor-run-0", 0, 4),
                            SourceTextRun("$anchor-run-1", 4, 5),
                            SourceTextRun("$anchor-run-2", 5, 10)
                        )
                    )
                )
            )
        )
        val saved =
            """{"href":"text/content.xhtml","text":{"highlight":"selected"},"locations":{"cssSelector":"#page-1-block-0",
            "domRange":{"start":{"cssSelector":"#page-1-block-0","charOffset":4,"textNodeIndex":0},
            "end":{"cssSelector":"#page-1-block-0","charOffset":8,"textNodeIndex":0}}}}"""
        val result =
            Json.parseToJsonElement(
                GeneratedEpubLocations.remap(saved, old, fresh) ?: return
            ).jsonObject
        val range = result.getValue("locations").jsonObject.getValue("domRange").jsonObject
        assertEquals(
            "#$anchor-run-1",
            range.getValue("start").jsonObject.getValue("cssSelector").jsonPrimitive.content
        )
        assertEquals(
            "0",
            range.getValue("start").jsonObject.getValue("charOffset").jsonPrimitive.content
        )
        assertEquals(
            "#$anchor-run-2",
            range.getValue("end").jsonObject.getValue("cssSelector").jsonPrimitive.content
        )
        assertEquals(
            "3",
            range.getValue("end").jsonObject.getValue("charOffset").jsonPrimitive.content
        )
        assertEquals(Json.parseToJsonElement(saved).jsonObject["text"], result["text"])
        assertEquals(1, GeneratedEpubLocations.page("text/content.xhtml", "#$anchor-run-1"))
        assertEquals(2, GeneratedEpubLocations.page("text/content.xhtml", "#page-2-source-3-run-0"))
    }

    @Test
    fun imageEquationKeepsNoteAnchorButDropsUnrepresentableTextRange() {
        val anchor = "page-1-block-0"
        val old = listOf(
            SourcePage(
                1,
                "text/content.xhtml",
                listOf(SourceBlock(anchor, 0f, 0f, 10f, 10f))
            )
        )
        val fresh = listOf(
            SourcePage(
                1, "text/content.xhtml", listOf(
                    SourceBlock(
                        anchor, 0f, 0f, 10f, 10f,
                        listOf(SourceTextRun("$anchor-run-0", 0, 4, image = true))
                    )
                )
            )
        )
        val saved =
            """{"href":"text/content.xhtml","text":{"highlight":"math"},"locations":{"cssSelector":"#page-1-block-0",
            "domRange":{"start":{"cssSelector":"#page-1-block-0","charOffset":0},"end":{"cssSelector":"#page-1-block-0","charOffset":4}}}}"""
        val result =
            Json.parseToJsonElement(
                GeneratedEpubLocations.remap(saved, old, fresh) ?: return
            ).jsonObject
        assertFalse(result.getValue("locations").jsonObject.containsKey("domRange"))
        assertEquals(
            "#page-1-block-0",
            result.getValue("locations").jsonObject.getValue("cssSelector").jsonPrimitive.content
        )
        assertEquals(Json.parseToJsonElement(saved).jsonObject["text"], result["text"])
    }
}
