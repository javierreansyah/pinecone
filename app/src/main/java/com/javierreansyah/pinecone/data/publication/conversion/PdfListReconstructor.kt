package com.javierreansyah.pinecone.data.publication.conversion

import kotlin.math.abs

data class ReflowListMarker(val text: String, val value: Int? = null, val depth: Int = 0)

/** Real list items: the marker is outside the text column, including wrapped source blocks. */
internal class PdfListReconstructor(private val size: Float) {
    fun reconstruct(input: List<ReflowBlock>, original: List<PdfLayoutBlock>): List<ReflowBlock> {
        val output = mutableListOf<ReflowBlock>()
        var index = 0
        val indents = mutableListOf<Float>()
        while (index < input.size) {
            var block = input[index++]
            val first = block.sources.firstOrNull()
            val match = first?.text?.let { marker.find(it) }
            if (block.kind !in setOf("paragraph", "list") || match == null) {
                output += block; indents.clear(); continue
            }
            val bullet = match.groupValues[1]
            val left = first.bounds.left
            val native = original.firstOrNull { it.anchor == first.anchor }
            val contentLeft = native?.lines?.getOrNull(1)?.runs?.firstOrNull()?.bounds?.left
                ?: (left + (match.value.length * size * .5f).coerceAtLeast(size))
            while (index < input.size) {
                val next = input[index]
                val source = next.sources.firstOrNull() ?: break
                val previous = block.sources.last()
                if (next.headingLevel != null || next.kind !in setOf(
                        "paragraph",
                        "list"
                    ) || marker.containsMatchIn(source.text)
                ) break
                val sameLine = abs(source.bounds.top - previous.bounds.top) < size * .4f &&
                        source.bounds.left - previous.bounds.right in -size * .3f..size * 3
                val continuedLine =
                    source.bounds.top - previous.bounds.bottom in -size * .2f..size * 1.3f &&
                            abs(source.bounds.left - contentLeft) <= size * 1.2f
                if (!sameLine && !continuedLine) break
                block = block.copy(
                    sources = PdfBibliographyReconstructor.join(
                        block.sources,
                        next.sources
                    )
                )
                index++
            }
            while (indents.isNotEmpty() && left < indents.last() - size) indents.removeAt(indents.lastIndex)
            if (indents.isEmpty() || left > indents.last() + size * 1.5f) indents += left
            val sources = block.sources.mapIndexed { sourceIndex, source ->
                if (sourceIndex != 0) source else source.copy(runs = source.runs.flatMap { run ->
                    if (run.start >= match.value.length) listOf(run) else {
                        val cut = minOf(run.end, match.value.length)
                        val count = cut - run.start
                        listOfNotNull(
                            run.copy(
                                text = run.text.take(count),
                                end = cut,
                                hidden = true
                            ),
                            run.takeIf { run.end > cut }
                                ?.copy(text = run.text.drop(count), start = cut)
                        )
                    }
                })
            }
            output += block.copy(
                kind = "list", headingLevel = null, sources = sources,
                listMarker = ReflowListMarker(
                    bullet,
                    bullet.takeWhile(Char::isDigit).toIntOrNull(),
                    (indents.size - 1).coerceAtMost(4)
                )
            )
        }
        return output
    }

    companion object {
        val marker = Regex("^((?:\\d{1,4}[.)])|[\u2022\u25e6\u25aa\u25cf\u2013])(?:\\s+|$)")
    }
}
