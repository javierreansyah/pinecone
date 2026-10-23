package com.javierreansyah.pinecone.data.publication.conversion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfFormulaAndListTest {
    private fun run(text: String, x: Float, y: Float, size: Float = 10f, math: Boolean = false) =
        PdfTextRun(
            text,
            FigureBounds(x, y - size * .8f, x + text.length * size * .5f, y + size * .2f),
            y,
            size,
            mathFont = math
        )

    private fun block(id: String, vararg rows: List<PdfTextRun>) = PdfLayoutBlock(
        id,
        rows.flatMap { it }.map { it.bounds }.reduce(FigureBounds::union), rows.map(::PdfTextLine)
    )

    private fun reconstruct(vararg blocks: PdfLayoutBlock): List<ReflowBlock> {
        val page = PdfPageLayout(FigureBounds(0f, 0f, 600f, 800f), blocks.toList())
        return PdfLayoutReconstructor(PdfDocumentProfile().apply { observe(page) }).reconstruct(page)
    }

    @Test
    fun optimizerEquationCombinesOverlappingBlocksAndPreservesBothScripts() {
        val output = reconstruct(
            block(
                "prose",
                listOf(
                    run(
                        "We vary the learning rate according to the following formula.",
                        50f,
                        80f
                    )
                )
            ),
            block(
                "prefix",
                listOf(run("lrate = d", 110f, 110f, math = true), run("-0.5", 155f, 106f, 7f))
            ),
            block(
                "rest",
                listOf(
                    run("model", 155f, 113f, 7f),
                    run(" min(step_num, warmup_steps)", 175f, 110f, math = true)
                )
            ),
            block("label", listOf(run("(3)", 480f, 110f))),
            block(
                "after",
                listOf(
                    run(
                        "Then we decrease the rate for the remaining training steps.",
                        50f,
                        150f
                    )
                )
            ),
        )
        val equation = output.single { it.kind == "equation" }
        assertEquals(setOf("prefix", "rest", "label"), equation.sources.map { it.anchor }.toSet())
        val math = equation.sources.flatMap { it.runs }.mapNotNull { it.math }.single()
        assertTrue(math.texts.single { it.text == "model" }.baseline > math.texts.first().baseline)
        assertTrue(math.texts.single { it.text == "-0.5" }.baseline < math.texts.first().baseline)
        assertTrue(output.flatMap { it.sources }.flatMap { it.runs }.none { it.crop != null })
    }

    @Test
    fun bulletContinuationJoinsItsItemAndMarkerOffsetsRemainCanonical() {
        val output = reconstruct(
            block(
                "one",
                listOf(run("\u2022 First item with a line", 80f, 100f)),
                listOf(run("and another line", 90f, 112f))
            ),
            block("tail", listOf(run("that continues in a separate PDF block.", 90f, 124f))),
            block("two", listOf(run("\u2022 Second item.", 80f, 145f))),
            block(
                "body",
                listOf(run("The next ordinary paragraph starts at the body margin.", 50f, 180f))
            ),
        )
        val lists = output.filter { it.kind == "list" }
        assertEquals(2, lists.size)
        assertEquals(listOf("one", "tail"), lists.first().sources.map { it.anchor })
        val source = lists.first().sources.first()
        assertTrue(source.text.startsWith("\u2022 "))
        assertEquals(0, source.runs.first().start)
        assertEquals(2, source.runs.first().end)
        assertTrue(source.runs.first().hidden)
        assertEquals("paragraph", output.last().kind)
    }

    @Test
    fun separateBulletAndTextBecomeOneItemAndNumberingIsPreserved() {
        val output = reconstruct(
            block("marker", listOf(run("\u2022", 80f, 100f))),
            block("text", listOf(run("An item whose marker is separate.", 90f, 100f))),
            block("nine", listOf(run("9. Numbered item", 80f, 130f))),
            block("ten", listOf(run("10. Next item", 80f, 150f)))
        )
        assertEquals(3, output.size)
        assertEquals(listOf("marker", "text"), output.first().sources.map { it.anchor })
        assertEquals(listOf(9, 10), output.drop(1).map { it.listMarker?.value })
        assertTrue(output.first().sources.first().runs.all { it.hidden })
    }

    @Test
    fun bibliographyNumbersRemainVisibleAndDoNotBecomeListMarkers() {
        val output = reconstruct(
            block("header", listOf(run("References", 50f, 80f))),
            block("ref", listOf(run("1. Author. A paper, 2024.", 50f, 110f)))
        )
        val entry = output.last()
        assertEquals("reference", entry.kind)
        assertNull(entry.listMarker)
        assertFalse(entry.sources.first().runs.first().hidden)
    }

    @Test
    fun inlineVariablesOnConsecutiveProseLinesDoNotBecomeStackedMath() {
        val paragraph = block(
            "paragraph",
            listOf(
                run("The layers are more expensive by a factor of ", 50f, 100f),
                run("k", 260f, 100f, math = true),
                run(". Separable convolutions decrease the complexity", 265f, 100f)
            ),
            listOf(
                run("considerably, to O(", 50f, 111f),
                run("n", 260f, 111f, math = true),
                run(" dot d", 265f, 111f, math = true),
                run("). The next sentence explains the improvement.", 300f, 111f)
            )
        )
        val output = reconstruct(paragraph)
        val sources = output.flatMap { it.sources }
        assertEquals(1, output.size)
        assertEquals("paragraph", output.single().kind)
        assertTrue(sources.flatMap { it.runs }.none { it.math != null || it.hidden })
        assertEquals(paragraph.text, sources.single().runs.joinToString("") { it.text })
        assertTrue(sources.single().text.contains("factor of k. Separable convolutions"))
    }

    @Test
    fun radicalUsesItsOverbarAndRadicandRatherThanTheTexGlyphBaseline() {
        val numerator = block("numerator", listOf(run("QK", 120f, 104f, math = true)))
        val denominator = block(
            "denominator", listOf(
                run("\u221a", 120f, 110f, math = true),
                run("d", 128f, 118f, math = true), run("k", 133f, 120f, 7f, math = true)
            )
        )
        val divider = FigureBounds(119f, 108f, 141f, 108.4f)
        val rootBar = FigureBounds(128f, 110f, 140f, 110.4f)
        val page = PdfPageLayout(
            FigureBounds(0f, 0f, 600f, 800f),
            listOf(numerator, denominator),
            listOf(divider, rootBar)
        )
        val output =
            PdfLayoutReconstructor(PdfDocumentProfile().apply { observe(page) }).reconstruct(page)
        val math = output.flatMap { it.sources }.flatMap { it.runs }.mapNotNull { it.math }
            .first { it.radicals.isNotEmpty() }
        val root = math.radicals.single()
        assertTrue(root.bounds.top > divider.bottom)
        assertEquals(110.2f, root.bounds.top, .01f)
        assertTrue(root.bounds.bottom >= denominator.runs.last().bounds.bottom)
        assertTrue(math.texts.single { it.text == "\u221a" }.radical)
        assertFalse(rootBar in math.rules)
        assertTrue(math.bounds.bottom > root.bounds.bottom + root.strokeWidth / 2)
    }
}
