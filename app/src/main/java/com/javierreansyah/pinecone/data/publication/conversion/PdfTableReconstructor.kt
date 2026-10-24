package com.javierreansyah.pinecone.data.publication.conversion

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Cells refer to original runs rather than copying text or replacing saved source anchors. */
data class ReflowCellPart(val source: Int, val runs: List<Int>)
data class ReflowTableCell(
    val column: Int, val parts: List<ReflowCellPart>, val columnSpan: Int = 1, val rowSpan: Int = 1,
)

data class ReflowTable(
    val columns: Int,
    val rows: List<List<ReflowTableCell>>,
    val headerRows: Int = 0
)

internal data class ReconstructedTable(val block: ReflowBlock, val members: Set<String>)

/** Bounded, page-local geometry analysis. Ambiguous layouts keep the existing figure fallback. */
internal class PdfTableReconstructor(private val size: Float) {
    private data class Piece(
        val source: Int,
        val runs: List<Int>,
        val bounds: FigureBounds,
        val baseline: Float
    )

    private data class Row(val pieces: MutableList<Piece>, val baseline: Float) {
        val bounds get() = pieces.map { it.bounds }.reduce(FigureBounds::union)
    }

    private val caption = Regex("^Table\\s+\\d+[.:]?\\s", RegexOption.IGNORE_CASE)
    private fun overlap(a: FigureBounds, b: FigureBounds) =
        (min(a.right, b.right) - max(a.left, b.left)).coerceAtLeast(0f)

    fun reconstruct(
        page: PdfPageLayout,
        source: (PdfLayoutBlock) -> ReflowSource
    ): List<ReconstructedTable> {
        val used = mutableSetOf<String>()
        val output = mutableListOf<ReconstructedTable>()
        val text = page.blocks.filter { !it.image && it.text.isNotBlank() }
        val captions = text.filter { caption.containsMatchIn(it.text) }
        val regions = mutableListOf<FigureBounds>()
        for ((_, bounds, _, _) in captions) {
            // A caption must be adjacent; prose mentioning a table is not a table region.
            val after = text.filter {
                it.bounds.top >= bounds.bottom &&
                        it.bounds.top - bounds.bottom < size * 5
            }
                .minByOrNull { it.bounds.top }
            if (after != null) {
                val below = text.filter { it.bounds.top >= after.bounds.top }
                    .sortedBy { it.bounds.top }
                var bottom = after.bounds.bottom
                var region = after.bounds
                for (block in below) {
                    if (block.bounds.top - bottom > size * 2.2f || caption.containsMatchIn(block.text)) break
                    if (block.text.length > 120 && block.lines.any { line ->
                            line.runs.sumOf { it.text.length } > 100 &&
                                    line.runs.last().bounds.right - line.runs.first().bounds.left > page.bounds.width * .55f
                        }) break
                    region = region.union(block.bounds)
                    bottom = max(bottom, block.bounds.bottom)
                }
                regions += region
            }
            // Captions below tables are common too. Prefer an explicit vector/image region.
            page.blocks.filter {
                it.image && it.bounds.bottom <= bounds.top &&
                        bounds.top - it.bounds.bottom < size * 3 && overlap(
                    it.bounds,
                    bounds
                ) > 0
            }
                .maxByOrNull { it.bounds.bottom }?.let { regions += it.bounds }
        }
        // Uncaptioned grids need strong evidence; diagrams and two prose columns must not become tables.
        page.blocks.filter {
            it.image && page.rules.count { rule ->
                rule.top in it.bounds.top..it.bounds.bottom && overlap(
                    rule,
                    it.bounds
                ) >= it.bounds.width * .7f
            } >= 3
        }
            .forEach { regions += it.bounds }
        for (region in regions) {
            val members = text.filter {
                it.anchor !in used && !caption.containsMatchIn(it.text) &&
                        it.bounds.left >= region.left - 2 && it.bounds.right <= region.right + 2 &&
                        it.bounds.top >= region.top - 2 && it.bounds.bottom <= region.bottom + 2
            }
            if (members.isEmpty() || members.size > 2048) continue
            val sources = members.map(source)
            val pieces = sources.flatMapIndexed { sourceIndex, part ->
                val native = members[sourceIndex].lines
                // Styled fragments, subscripts and superscripts stay in the same geometric cell.
                part.runs.mapIndexedNotNull { index, run ->
                    val box = run.bounds ?: return@mapIndexedNotNull null
                    if (run.text.isBlank()) null else {
                        val line = native.firstOrNull { line -> line.runs.any { it.bounds == box } }
                            ?: return@mapIndexedNotNull null
                        val base =
                            line.runs.maxByOrNull { it.size } ?: return@mapIndexedNotNull null
                        Piece(sourceIndex, listOf(index), box, base.baseline)
                    }
                }.sortedWith(compareBy<Piece> { it.bounds.top }.thenBy { it.bounds.left })
            }
            if (pieces.size > 8192) continue
            val rows = mutableListOf<Row>()
            // Larger text establishes baselines before small raised/lowered glyphs are assigned.
            pieces.sortedByDescending { it.bounds.height }.forEach { part ->
                val row = rows.filter { abs(it.baseline - part.baseline) <= size * .48f }
                    .minByOrNull { abs(it.baseline - part.baseline) }
                if (row == null) rows += Row(
                    mutableListOf(part),
                    part.baseline
                ) else row.pieces += part
            }
            rows.sortBy { it.baseline }
            if (rows.size !in 3..256) continue
            val cells = rows.map { row ->
                val grouped = mutableListOf<MutableList<Piece>>()
                row.pieces.sortedBy { it.bounds.left }.forEach { piece ->
                    val previous = grouped.lastOrNull()
                    val right = previous?.maxOf { it.bounds.right }
                    if (right == null || piece.bounds.left - right > size * .65f) grouped += mutableListOf(
                        piece
                    )
                    else previous += piece
                }
                grouped.map { group ->
                    group to group.map { it.bounds }.reduce(FigureBounds::union)
                }
            }
            val dense = cells.filter { it.size >= 2 }
            if (dense.size < 3 || dense.size < rows.size / 2) continue
            val template =
                dense.maxWithOrNull(compareBy<List<Pair<List<Piece>, FigureBounds>>> { it.size }
                    .thenBy { row -> row.sumOf { it.second.width.toDouble() } }) ?: continue
            if (template.size !in 2..16) continue
            val gaps = template.zipWithNext { a, b -> a.second.right to b.second.left }
            val separators = gaps.map { (left, right) ->
                var low = left
                var high = right
                // Complete rows constrain gutters. Incomplete rows may contain spanning cells.
                dense.filter { it.size == template.size }.forEach { row ->
                    row.zipWithNext().map { it.first.second.right to it.second.second.left }
                        .firstOrNull { (a, b) -> min(high, b) - max(low, a) > size * .25f }
                        ?.let { (a, b) ->
                            low = max(low, a); high = min(high, b)
                        }
                }
                (low + high) / 2
            }
            if (separators.zipWithNext().any { (a, b) -> b - a < size }) continue
            // Each gutter needs repeated evidence rather than a single wide word space.
            if (separators.any { x ->
                    dense.count { row ->
                        row.zipWithNext().any { (a, b) ->
                            x > a.second.right && x < b.second.left
                        }
                    } < 2
                }) continue
            val centers = template.map { (_, box) -> (box.left + box.right) / 2 }
            val grid = cells.map { row ->
                row.map { (group, box) ->
                    val covering =
                        centers.indices.filter { centers[it] in box.left - size * .9f..box.right + size * .9f }
                    val nearest =
                        centers.indices.minBy { abs(centers[it] - (box.left + box.right) / 2) }
                    val start = covering.firstOrNull() ?: nearest
                    val end = covering.lastOrNull() ?: nearest
                    ReflowTableCell(start, group.groupBy { it.source }.map { (index, fragments) ->
                        ReflowCellPart(index, fragments.flatMap { it.runs }.sorted())
                    }, end - start + 1)
                }
            }
            if (grid.any { row ->
                    row.zipWithNext().any { (a, b) -> a.column + a.columnSpan > b.column }
                }) continue
            val delimiter = page.rules.filter {
                it.top >= rows.first().bounds.bottom &&
                        it.top < rows.first().bounds.bottom + size * 3.5f && overlap(
                    it,
                    region
                ) >= region.width * .7f
            }
                .minByOrNull { it.top }
            var headers =
                delimiter?.let { rule -> rows.count { it.bounds.top < rule.top } }?.coerceAtMost(3)
                    ?: if (members.first().bold) 1 else 0
            val normalized = grid.map { it.toMutableList() }.toMutableList()
            // A centered group label belongs to the ruled band, not an extra mostly empty row.
            // Blank numeric cells remain blank; no default values are inferred.
            normalized.forEachIndexed { rowIndex, row ->
                row.toList().filter { cell ->
                    cell.column == 0 && cell.columnSpan == 1 &&
                            Regex("\\([A-Z]\\)").matches(cellText(cell, sources))
                }.forEach { label ->
                    val y = rows[rowIndex].baseline
                    val boundaries = page.rules.filter { overlap(it, region) >= region.width * .7f }
                    val top = boundaries.filter { it.bottom < y }.maxOfOrNull { it.bottom }
                    val bottom = boundaries.filter { it.top > y }.minOfOrNull { it.top }
                    if (top != null && bottom != null) {
                        val band = rows.indices.filter { rows[it].baseline in top..bottom }
                        if (band.isNotEmpty() && band.all { index -> normalized[index].none { it !== label && it.column == 0 } }) {
                            row.remove(label)
                            normalized[band.first()].add(0, label.copy(rowSpan = band.size))
                        }
                    }
                }
            }
            // Remove rows that held only a vertically centered label and adjust its span.
            normalized.indices.reversed().filter { normalized[it].isEmpty() }.forEach { removed ->
                for (index in 0..<removed) normalized[index].replaceAll { cell ->
                    if (index + cell.rowSpan > removed) cell.copy(rowSpan = cell.rowSpan - 1) else cell
                }
                normalized.removeAt(removed)
                if (removed < headers) headers--
            }
            if (headers > 1 && normalized.take(headers).flatten().none { it.columnSpan > 1 }) {
                val combined =
                    normalized.take(headers).flatten().groupBy { it.column }.toSortedMap()
                        .map { (column, parts) ->
                            ReflowTableCell(column, parts.flatMap { it.parts })
                        }
                repeat(headers) { normalized.removeAt(0) }
                normalized.add(0, combined.toMutableList())
                headers = 1
            } else if (headers > 1) {
                // A lone header centered beside nested column groups spans those header rows.
                val standalone = normalized.take(headers)
                    .flatMapIndexed { row, items -> items.map { row to it } }
                    .filter { (_, cell) ->
                        cell.columnSpan == 1 && normalized.take(headers).flatten().count {
                            cell.column in it.column..<it.column + it.columnSpan
                        } == 1
                    }
                standalone.forEach { (row, cell) ->
                    normalized[row].remove(cell)
                    normalized[0].add(cell.copy(rowSpan = headers))
                }
                val empty = (0..<headers).filter { normalized[it].isEmpty() }
                empty.reversed().forEach { normalized.removeAt(it) }
                headers -= empty.size
                normalized[0].replaceAll { if (it.rowSpan > 1) it.copy(rowSpan = headers) else it }
            }
            normalized.forEach { it.sortBy { cell -> cell.column } }
            val images = page.blocks.filter {
                it.image && overlap(it.bounds, region) >= region.width * .85f &&
                        it.bounds.top >= region.top - size && it.bounds.bottom <= region.bottom + size
            }
            val ids = (members + images).map { it.anchor }.toSet()
            // Preserve old image anchors as well, without adding duplicate table text.
            val anchors =
                images.map { ReflowSource(it.anchor, it.bounds, "", emptyList(), hidden = true) }
            output += ReconstructedTable(
                ReflowBlock(
                    kind = "table", sources = sources + anchors,
                    table = ReflowTable(separators.size + 1, normalized, headers)
                ), ids
            )
            used += ids
        }
        return output
    }

    private fun cellText(cell: ReflowTableCell, sources: List<ReflowSource>) =
        cell.parts.joinToString("") { part ->
            part.runs.joinToString("") { sources[part.source].runs[it].text }
        }.trim()
}
