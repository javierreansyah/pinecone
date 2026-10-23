package com.javierreansyah.pinecone.data.publication.conversion

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Bibliography context survives page breaks; only the final entry's geometry is retained. */
internal class PdfBibliographyReconstructor(private val size: Float) {
    private var active = false
    private var previousEntry: FigureBounds? = null
    private val heading = Regex(
        "^(?:References|Bibliography|Works Cited|Literature Cited)[:.]?$",
        RegexOption.IGNORE_CASE
    )
    private val marker = Regex("^(?:\\[\\d{1,4}]|\\d{1,4}[.)])(?:\\s|$)")
    private val year = Regex("\\b(?:18|19|20)\\d{2}[a-z]?\\b")

    fun reconstruct(
        blocks: List<ReflowBlock>,
        page: FigureBounds,
        outline: Set<String>
    ): List<ReflowBlock> {
        val output = mutableListOf<ReflowBlock>()
        var acrossPage = previousEntry
        previousEntry = null
        var entryStart: FigureBounds? = null
        for (block in blocks) {
            val text = block.sources.joinToString(" ") { it.text }.trim()
            val box = block.sources.filterNot { it.hidden }.map { it.bounds }
                .reduceOrNull(FigureBounds::union)
            if (heading.matches(text)) {
                active = true; acrossPage = null; entryStart = null
                output += block.copy(headingLevel = block.headingLevel ?: 2)
                continue
            }
            val numbered = marker.containsMatchIn(text)
            if (active && block.headingLevel != null && !numbered && !year.containsMatchIn(text) &&
                (text in outline || block.headingLevel <= 2)
            ) active = false
            if (!active || box == null || block.kind !in setOf("paragraph", "list")) {
                output += block
                acrossPage = null; entryStart = null
                continue
            }
            val previous = output.lastOrNull()
                ?.takeIf { it.kind == "reference" || it.kind == "reference-continuation" }
            val lastBox = previous?.sources?.lastOrNull()?.bounds
            val start = entryStart ?: acrossPage
            val sameColumn = start != null && box.left >= start.left - size * .4f &&
                    min(box.right, start.right) - max(box.left, start.left) > 0
            val nextEntry = !numbered && start != null && abs(box.left - start.left) < size * .5f &&
                    lastBox != null && box.top - lastBox.bottom >= size * .65f
            val sameLine = lastBox != null && abs(box.top - lastBox.top) <= size * .4f &&
                    box.left - lastBox.right in -size * .3f..size * 3
            val continuation =
                !numbered && !nextEntry && (sameLine || (sameColumn && lastBox != null &&
                        box.top - lastBox.bottom in -size * .3f..size * 1.6f))
            val continuedPage = !numbered && sameColumn && acrossPage != null &&
                    output.isEmpty() && box.top < page.top + page.height * .25f
            if (continuation) {
                val merged = join(previous.sources, block.sources)
                output[output.lastIndex] = previous.copy(sources = merged)
                entryStart = start?.union(box) ?: box
            } else {
                output += block.copy(
                    headingLevel = null,
                    kind = if (continuedPage) "reference-continuation" else "reference"
                )
                entryStart = box
            }
            acrossPage = null
        }
        previousEntry = output.lastOrNull()?.takeIf { it.kind.startsWith("reference") }?.sources
            ?.map { it.bounds }?.reduceOrNull(FigureBounds::union)
        return output
    }

    companion object {
        /** Hide only the line-wrap hyphen; offsets continue addressing the original PDF text. */
        fun join(before: List<ReflowSource>, after: List<ReflowSource>): List<ReflowSource> {
            val last = before.lastOrNull() ?: return after
            if (!last.text.matches(Regex(".*\\p{L}-$", RegexOption.DOT_MATCHES_ALL)) ||
                after.firstOrNull()?.text?.firstOrNull()?.isLowerCase() != true
            ) return before + after
            val finalRun = last.runs.lastOrNull() ?: return before + after
            if (!finalRun.text.endsWith('-') || finalRun.image != null) return before + after
            val runs = last.runs.dropLast(1) + listOfNotNull(
                finalRun.takeIf { it.text.length > 1 }
                    ?.copy(text = finalRun.text.dropLast(1), end = finalRun.end - 1),
                finalRun.copy(text = "-", start = finalRun.end - 1, hidden = true),
            )
            return before.dropLast(1) + last.copy(runs = runs) + after
        }
    }
}
