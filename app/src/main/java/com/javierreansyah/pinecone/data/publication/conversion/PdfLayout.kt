package com.javierreansyah.pinecone.data.publication.conversion

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Serializable
internal data class PdfTextRun(
    val text: String, val bounds: FigureBounds, val baseline: Float, val size: Float,
    val bold: Boolean = false, val italic: Boolean = false, val mathFont: Boolean = false,
)
@Serializable
internal data class PdfTextLine(val runs: List<PdfTextRun>)
@Serializable
internal data class PdfLayoutBlock(
    val anchor: String, val bounds: FigureBounds,
    val lines: List<PdfTextLine> = emptyList(), val image: Boolean = false,
) {
    @kotlinx.serialization.Transient
    val text: String = PdfReadModeConverter.joinLines(lines.joinToString("\n") { it.runs.joinToString("") { run -> run.text } })
    @kotlinx.serialization.Transient
    val runs: List<PdfTextRun> = lines.flatMap { it.runs }
    @kotlinx.serialization.Transient
    val size: Float = runs.groupBy { (it.size * 4).roundToInt() }
        .maxByOrNull { (_, group) -> group.sumOf { it.text.length } }?.key?.div(4f) ?: 12f
    @kotlinx.serialization.Transient
    val bold: Boolean = runs.filter { it.bold }.sumOf { it.text.length } >= max(1, text.length / 2)
    @kotlinx.serialization.Transient
    val baseline: Float = lines.firstOrNull()?.runs?.maxByOrNull { it.text.length }?.baseline ?: bounds.bottom
}
@Serializable
internal data class PdfPageLayout(
    val bounds: FigureBounds, val blocks: List<PdfLayoutBlock>, val rules: List<FigureBounds> = emptyList(),
)

/** Only bounded histograms and repeated margin strings survive between pages. */
internal class PdfDocumentProfile {
    private val bodySizes = mutableMapOf<Int, Long>()
    private val allSizes = mutableMapOf<Int, Long>()
    private val marginCounts = mutableMapOf<String, Int>()
    private val shortBoldStyles = mutableMapOf<Int, Int>()
    private var pageCount = 0
    val bodySize: Float get() = (bodySizes.ifEmpty { allSizes }.maxByOrNull { it.value }?.key ?: 48) / 4f
    fun observe(page: PdfPageLayout) {
        pageCount++
        page.blocks.forEach { block ->
            block.runs.forEach { run ->
                val key = (run.size.coerceIn(1f, 200f) * 4).roundToInt()
                val weight = run.text.count { !it.isWhitespace() }.toLong()
                allSizes[key] = (allSizes[key] ?: 0) + weight
                if (block.text.length >= 60 && !run.bold && !run.mathFont) bodySizes[key] = (bodySizes[key] ?: 0) + weight
            }
            if (block.bold && block.text.length in 3..160 && block.lines.size <= 3) {
                val key = (block.size * 4).roundToInt()
                shortBoldStyles[key] = (shortBoldStyles[key] ?: 0) + 1
            }
        }
        page.blocks.filter { inMargin(it.bounds, page.bounds) }.map { it.text }.filter { it.isNotBlank() }
            .distinct().forEach { if (marginCounts.size < 2048 || it in marginCounts) marginCounts[it] = (marginCounts[it] ?: 0) + 1 }
    }
    fun repeatedMargin(text: String) = (marginCounts[text] ?: 0) >= max(3, pageCount / 3)
    fun recurringBold(size: Float) = (shortBoldStyles[(size * 4).roundToInt()] ?: 0) >= 2
    companion object {
        fun inMargin(box: FigureBounds, page: FigureBounds) =
            box.bottom < page.top + page.height * .08f || box.top > page.bottom - page.height * .08f
    }
}

/** Offsets address the old normalized source text, independently of output paragraph merges. */
data class ReflowRun(
    val text: String, val start: Int, val end: Int,
    val bold: Boolean = false, val italic: Boolean = false, val script: Int = 0,
    val crop: FigureBounds? = null, val image: String? = null, val heightEm: Float? = null, val hidden: Boolean = false,
    val bounds: FigureBounds? = null,
    val math: ReflowMath? = null,
)

/** Original PDF positions, in font-relative units at rendering time; no bitmap or math OCR. */
data class ReflowMathText(
    val text: String, val x: Float, val baseline: Float, val width: Float, val size: Float,
    val bold: Boolean = false, val italic: Boolean = false,
    val radical: Boolean = false,
)
data class ReflowRadical(val bounds: FigureBounds, val shoulder: Float, val strokeWidth: Float)
data class ReflowMath(
    val bounds: FigureBounds, val texts: List<ReflowMathText>, val rules: List<FigureBounds>,
    val bodySize: Float, val baseline: Float,
    val radicals: List<ReflowRadical> = emptyList(),
)
data class ReflowSource(
    val anchor: String, val bounds: FigureBounds, val text: String, val runs: List<ReflowRun>,
    val equationLabel: Boolean = false, val hidden: Boolean = false,
)

internal class PdfLayoutReconstructor(private val profile: PdfDocumentProfile) {
    private val bodySize = profile.bodySize
    private val bibliography = PdfBibliographyReconstructor(bodySize)
    private val sectionNumber = Regex("\\d+(?:\\.\\d+)*\\.?")
    private val numberedTitle = Regex("^(\\d+(?:\\.\\d+)*)(\\.?)\\s+(\\p{L}.*)$")
    private val mathSymbols = "=\u2248\u2260\u2264\u2265\u2211\u220f\u222b\u221a\u00b1\u00d7\u00f7\u221e\u2202\u2207\u2208\u2209\u221d\u2297\u2295\u2192\u21a6\u222a\u2229"
    private fun overlaps(a: FigureBounds, b: FigureBounds) = (min(a.right, b.right) - max(a.left, b.left)).coerceAtLeast(0f)
    private fun mathLike(run: PdfTextRun) = run.mathFont || run.text.any { it in mathSymbols }
    private fun prose(run: PdfTextRun) = Regex("\\p{L}{4,}").findAll(run.text).count() >= 2 && !run.mathFont
    private fun inside(box: FigureBounds, region: FigureBounds) =
        (box.left + box.right) / 2 in region.left..region.right && (box.top + box.bottom) / 2 in region.top..region.bottom

    fun reconstruct(page: PdfPageLayout, outlineTitles: Set<String> = emptySet()): List<ReflowBlock> {
        val raw = page.blocks.filter { it.image || it.text.isNotBlank() }
        val tables = PdfTableReconstructor(bodySize).reconstruct(page) { source(it, emptyList(), positioned = true) }
        val tableMembers = tables.flatMap { it.members }.toSet()
        val tableAt = tables.associateBy { table -> raw.first { it.anchor in table.members }.anchor }
        val remaining = raw.filter { it.anchor !in tableMembers }
        val figures = remaining.filter { it.image && it.bounds.width >= bodySize * 5 && it.bounds.height >= bodySize * 4 &&
            it.bounds.width * it.bounds.height < page.bounds.width * page.bounds.height * .85f }
        val owners = remaining.filter { !it.image && !Regex("^(?:Figure|Fig\\.|Table)\\s+\\d", RegexOption.IGNORE_CASE).containsMatchIn(it.text) }
            .mapNotNull { text ->
                val figure = figures.filter { figure ->
                    val area = max(1f, text.bounds.width * text.bounds.height)
                    val overlap = overlaps(text.bounds, figure.bounds) *
                        (min(text.bounds.bottom, figure.bounds.bottom) - max(text.bounds.top, figure.bounds.top)).coerceAtLeast(0f)
                    val title = text.text.length <= 100 && text.lines.size <= 2 &&
                        text.bounds.bottom <= figure.bounds.top && figure.bounds.top - text.bounds.bottom <= bodySize * 2 &&
                        text.bounds.width <= figure.bounds.width + bodySize * 6 &&
                        overlaps(text.bounds, figure.bounds) >= min(text.bounds.width, figure.bounds.width) * .7f
                    overlap / area >= .85f || title
                }.minByOrNull { it.bounds.width * it.bounds.height }
                figure?.let { text.anchor to it.anchor }
            }.toMap()
        val figureMembers = owners.entries.groupBy({ it.value }, { entry -> raw.first { it.anchor == entry.key } })
        val placed = mutableSetOf<String>()
        val blocks = raw.mapNotNull { block ->
            val figure = owners[block.anchor]?.let { id -> figures.first { it.anchor == id } }
                ?: block.takeIf { it.anchor in figureMembers }
            if (figure == null) block else if (placed.add(figure.anchor)) figure else null
        }
        val middle = (page.bounds.left + page.bounds.right) / 2
        val columns = blocks.filter { it.text.length > 80 && it.bounds.width < page.bounds.width * .65f }
        val twoColumns = columns.any { it.bounds.right <= middle + 6 } && columns.any { it.bounds.left >= middle - 6 }
        val consumed = mutableSetOf<String>()
        val headings = mutableMapOf<String, PdfLayoutBlock>()
        blocks.filter { sectionNumber.matches(it.text) }.forEach { number ->
            val title = blocks.filter { candidate ->
                !candidate.image && candidate.anchor != number.anchor && candidate.anchor !in consumed &&
                    candidate.text.length in 3..150 && candidate.lines.size <= 3 &&
                    candidate.bounds.left >= number.bounds.right - 1 && candidate.bounds.left - number.bounds.right <= number.size * 3 &&
                    abs(candidate.baseline - number.baseline) <= max(number.size, candidate.size) * .3f && candidate.size / number.size in .7f..1.4f &&
                    (!twoColumns || (candidate.bounds.left < middle) == (number.bounds.left < middle)) &&
                    (candidate.bold || candidate.size >= bodySize * 1.08f || "${number.text} ${candidate.text}" in outlineTitles ||
                        (!number.text.endsWith('.') && candidate.text.length <= 100 && candidate.text.first().isUpperCase() &&
                            blocks.asSequence().filter { !it.image && it.bounds.top >= candidate.bounds.bottom && overlaps(it.bounds, candidate.bounds) > 0 }
                                .minOfOrNull { it.bounds.top - candidate.bounds.bottom }?.let { it >= candidate.size * .6f } == true))
            }.minByOrNull { it.bounds.left - number.bounds.right }
            if (title != null) { headings[number.anchor] = title; consumed += title.anchor }
        }
        val result = mutableListOf<ReflowBlock>()
        for (block in blocks) {
            tableAt[block.anchor]?.let { result += it.block }
            if (block.anchor in tableMembers) continue
            if (block.anchor in consumed) continue
            if (!block.image && block.bounds.width < block.size * 2 && block.bounds.height > block.size * 4 &&
                (block.bounds.right < page.bounds.left + page.bounds.width * .08f ||
                    block.bounds.left > page.bounds.right - page.bounds.width * .08f)) continue
            val title = headings[block.anchor]
            val members = listOfNotNull(block, title)
            val text = members.joinToString(" ") { it.text }
            val heading = if (PdfListReconstructor.marker.containsMatchIn(text)) null
                else headingLevel(text, title ?: block, outlineTitles, blocks)
            if (heading == null && !block.image && PdfDocumentProfile.inMargin(block.bounds, page.bounds)) {
                val pageNumber = sectionNumber.matches(text) && (block.bounds.top > page.bounds.bottom - page.bounds.height * .08f ||
                    (block.bounds.bottom < page.bounds.top + page.bounds.height * .04f &&
                        abs((block.bounds.left + block.bounds.right) / 2 - (page.bounds.left + page.bounds.right) / 2) < page.bounds.width * .12f))
                if (pageNumber || profile.repeatedMargin(text)) continue
            }
            val figureText = if (block.image) figureMembers[block.anchor].orEmpty() else emptyList()
            val sources = if (figureText.isNotEmpty()) {
                val expanded = figureText.fold(block.bounds) { box, item -> box.union(item.bounds) }
                listOf(source(block, page.rules).let { it.copy(runs = it.runs.map { run -> run.copy(crop = expanded) }) }) +
                    figureText.map { source(it, page.rules).copy(hidden = true) }
            } else members.map { source(it, page.rules) }
            val caption = Regex("^(?:Figure|Fig\\.|Table)\\s+\\d", RegexOption.IGNORE_CASE).containsMatchIn(text)
            val list = Regex("^(?:\\d+[.)]|[\u2022\u25e6\u25aa\u2013])\\s+").containsMatchIn(text)
            val equation = heading == null && !block.image && sources.any { it.runs.any { run -> run.math != null } } && members.flatMap { it.runs }.none(::prose)
            result += ReflowBlock(headingLevel = heading,
                kind = when { block.image -> "figure"; equation -> "equation"; caption -> "caption"; list -> "list"; else -> "paragraph" }, sources = sources)
        }
        return PdfListReconstructor(bodySize).reconstruct(
            bibliography.reconstruct(mergeEquationFragments(result, remaining, page.rules), page.bounds, outlineTitles), remaining)
    }

    private fun headingLevel(text: String, block: PdfLayoutBlock, outline: Set<String>, neighbors: List<PdfLayoutBlock>): Int? {
        if (block.image || text.length !in 3..180 || block.lines.size > 3 || Regex("[.!?;,:]$").containsMatchIn(text)) return null
        if (text !in outline && (text.any { it in mathSymbols } || block.runs.any { it.mathFont })) return null
        numberedTitle.matchEntire(text)?.let { match ->
            val title = match.groupValues[3]
            val styled = block.bold || block.size >= bodySize * 1.08f || text in outline
            if (styled || (match.groupValues[2].isEmpty() && title.first().isUpperCase() && title.length <= 100))
                return (match.groupValues[1].count { it == '.' } + 2).coerceAtMost(6)
        }
        if (sectionNumber.matches(text)) return null
        if (text in outline) return 2
        if (text.length > 100) return null
        val separated = neighbors.asSequence().filter { !it.image && it.anchor != block.anchor &&
            it.bounds.top >= block.bounds.bottom && overlaps(it.bounds, block.bounds) > 0 }
            .minOfOrNull { it.bounds.top - block.bounds.bottom }?.let { it >= bodySize * .5f } ?: true
        if (separated && (block.size >= bodySize * 1.12f ||
                (block.bold && block.size >= bodySize * .95f && profile.recurringBold(block.size))))
            return if (block.size >= bodySize * 1.5f) 1 else 2
        return null
    }

    private data class Atom(val char: Char, val run: PdfTextRun, val baseline: Float, val baseSize: Float)
    private fun source(block: PdfLayoutBlock, rules: List<FigureBounds>, positioned: Boolean = false): ReflowSource {
        if (block.image) return ReflowSource(block.anchor, block.bounds, "", listOf(ReflowRun("", 0, 0, crop = block.bounds)))
        val atoms = mutableListOf<Atom>()
        block.lines.forEachIndexed { index, line ->
            val local = line.runs.maxByOrNull { it.size } ?: return@forEachIndexed
            val base = if (local.size < bodySize * .9f) block.runs.filter {
                it.size > local.size * 1.1f && it.bounds.near(local.bounds, it.size * .8f) && abs(it.baseline - local.baseline) < it.size * .9f
            }.minByOrNull { abs(it.baseline - local.baseline) } ?: local else local
            if (index > 0) atoms += Atom('\n', local, base.baseline, base.size)
            line.runs.forEach { run -> run.text.forEach { atoms += Atom(it, run, base.baseline, base.size) } }
        }
        val raw = atoms.joinToString("") { it.char.toString() }
        val removed = BooleanArray(atoms.size)
        Regex("(?<=\\p{L})-\\r?\\n(?=\\p{Ll})").findAll(raw).forEach { match -> match.range.forEach { removed[it] = true } }
        val normalized = mutableListOf<Atom>()
        atoms.forEachIndexed { index, atom ->
            if (!removed[index]) {
                val char = if (atom.char in "\r\n\t") ' ' else atom.char
                if (char != ' ' || normalized.lastOrNull()?.char != ' ') normalized += atom.copy(char = char)
            }
        }
        while (normalized.firstOrNull()?.char?.isWhitespace() == true) normalized.removeAt(0)
        while (normalized.lastOrNull()?.char?.isWhitespace() == true) normalized.removeAt(normalized.lastIndex)
        val crops = if (positioned) emptyList() else formulaCrops(block.runs, rules)
        val output = mutableListOf<ReflowRun>()
        normalized.forEachIndexed { index, atom ->
            val crop = crops.firstOrNull { inside(atom.run.bounds, it) && !prose(atom.run) }
            val script = if (atom.run.size <= atom.baseSize * .88f) when {
                atom.run.baseline < atom.baseline - atom.baseSize * .16f -> 1
                atom.run.baseline > atom.baseline + atom.baseSize * .12f -> -1
                else -> 0
            } else 0
            val previous = output.lastOrNull()
            if (previous != null && (!positioned || previous.bounds == atom.run.bounds) && previous.crop == crop && (crop != null ||
                    (previous.bold == atom.run.bold && previous.italic == atom.run.italic && previous.script == script))) {
                output[output.lastIndex] = previous.copy(end = index + 1)
            } else output += ReflowRun("", index, index + 1, atom.run.bold, atom.run.italic, script,
                crop, heightEm = crop?.let { (it.height / bodySize).coerceIn(.7f, 8f) }, bounds = atom.run.bounds)
        }
        val text = normalized.joinToString("") { it.char.toString() }
        val seen = mutableSetOf<FigureBounds>()
        return ReflowSource(block.anchor, block.bounds, text, output.map {
            it.copy(text = text.substring(it.start, it.end), crop = null,
                math = it.crop?.let { box -> math(box, block.runs, rules) },
                hidden = it.crop != null && !seen.add(it.crop))
        })
    }

    private fun math(box: FigureBounds, runs: List<PdfTextRun>, rules: List<FigureBounds>): ReflowMath {
        val selected = runs.filter { inside(it.bounds, box) && it.text.isNotBlank() }
        val baseline = selected.filter { it.size >= bodySize * .85f }.groupBy { (it.baseline * 2).roundToInt() }
            .maxByOrNull { (_, row) -> row.sumOf { it.text.length } }?.value?.first()?.baseline ?: box.bottom
        val rootRules = mutableSetOf<FigureBounds>()
        val roots = mutableMapOf<FigureBounds, ReflowRadical>()
        selected.filter { it.text.trim() == "\u221a" }.forEach { root ->
            // TeX radical fonts can place their glyph origin above the radicand baseline.
            // Reconstruct its stroke from the actual overbar and radicand, not font metrics.
            val bar = rules.filter { it.left >= root.bounds.left + root.bounds.width * .5f &&
                abs(it.left - root.bounds.right) <= root.size * .65f && it.width >= root.size * .3f &&
                it.top in root.bounds.top..root.bounds.bottom + root.size * .5f }
                .minByOrNull { abs(it.left - root.bounds.right) + abs(it.top - root.baseline) }
            if (bar != null) {
                val radicand = selected.filter { it !== root && it.bounds.left >= bar.left - root.size * .2f &&
                    it.bounds.right <= bar.right + root.size * .2f && it.bounds.top >= bar.top - root.size * .2f &&
                    it.bounds.bottom <= bar.top + root.size * 1.8f }
                if (radicand.isNotEmpty()) {
                    val top = (bar.top + bar.bottom) / 2
                    val bottom = radicand.maxOf { it.bounds.bottom }
                    if (bottom - top >= root.size * .5f) {
                        roots[root.bounds] = ReflowRadical(FigureBounds(root.bounds.left, top, bar.right, bottom),
                            bar.left, max(bar.height, root.size * .045f))
                        rootRules += bar
                    }
                }
            }
        }
        val padded = roots.values.fold(box) { region, root ->
            val padding = root.strokeWidth / 2 + .25f
            region.union(FigureBounds(root.bounds.left - padding, root.bounds.top - padding,
                root.bounds.right + padding, root.bounds.bottom + padding))
        }
        return ReflowMath(padded, selected.map { ReflowMathText(it.text, it.bounds.left, it.baseline, it.bounds.width, it.size,
            it.bold, it.italic, it.bounds in roots) },
            rules.filter { it !in rootRules && it.top >= box.top && it.bottom <= box.bottom && it.left >= box.left && it.right <= box.right },
            bodySize, baseline, roots.values.toList())
    }

    private fun formulaCrops(runs: List<PdfTextRun>, rules: List<FigureBounds>): List<FigureBounds> {
        val regions = mutableListOf<FigureBounds>()
        val sorted = runs.filterNot(::prose).sortedBy { it.bounds.top }
        val proseBaselines = runs.filter { !it.mathFont && prose(it) }.map { it.baseline }.distinct()
        fun proseRow(run: PdfTextRun) = proseBaselines.any { abs(it - run.baseline) <= run.size * .25f }
        for (i in sorted.indices) {
            val a = sorted[i]
            for (j in i + 1 until min(sorted.size, i + 129)) {
                val b = sorted[j]
                if (b.bounds.top - a.bounds.bottom > max(a.size, b.size) * 2) break
                val gap = abs(a.baseline - b.baseline)
                if (a.size / b.size !in .8f..1.25f || gap !in a.size * .35f..a.size * 2.2f || overlaps(a.bounds, b.bounds) < min(a.bounds.width, b.bounds.width) * .4f) continue
                val union = a.bounds.union(b.bounds)
                val bar = rules.any { it.width >= min(a.bounds.width, b.bounds.width) * .6f && it.width <= union.width * 1.6f &&
                    it.top in min(a.baseline, b.baseline)..max(a.baseline, b.baseline) && overlaps(it, union) > 0 }
                // Repeated variables at the same x on consecutive prose lines are not a fraction.
                if (!bar && proseRow(a) && proseRow(b)) continue
                if (!bar && (!(mathLike(a) && mathLike(b)) || min(a.size, b.size) < bodySize * .85f)) continue
                var region = union
                runs.filter { mathLike(it) && !prose(it) && it.bounds.near(union, bodySize * .4f) &&
                    (!proseRow(it) || it.baseline in min(a.baseline, b.baseline) - it.size * .1f..max(a.baseline, b.baseline) + it.size * .1f) }
                    .forEach { region = region.union(it.bounds) }
                val at = regions.indexOfFirst { it.near(region, 2f) }
                if (at >= 0) regions[at] = regions[at].union(region) else regions += region
            }
        }
        return regions.map { region ->
            val fullRuns = runs.filter { inside(it.bounds, region) && !prose(it) }.fold(region) { box, run -> box.union(run.bounds) }
            FigureBounds(fullRuns.left - 1, fullRuns.top - 1, fullRuns.right + 1, fullRuns.bottom + 1)
        }
    }

    private fun mergeEquationFragments(output: List<ReflowBlock>, original: List<PdfLayoutBlock>, rules: List<FigureBounds>): List<ReflowBlock> {
        val eligible = output.filter { it.headingLevel == null && it.kind in setOf("paragraph", "equation") }
            .flatMap { it.sources }.filterNot { it.hidden }.map { it.anchor }.toSet()
        val candidates = original.filter { it.anchor in eligible && !it.image && it.text.length < 300 && it.runs.none(::prose) }
        val proseBlocks = original.filter { !it.image && it.runs.any(::prose) }
        val textLeft = proseBlocks.minOfOrNull { it.bounds.left } ?: original.minOfOrNull { it.bounds.left } ?: 0f
        val displays = candidates.filter { block ->
            val signal = block.text.contains('=') || block.runs.filter { it.mathFont }.sumOf { it.text.count(Char::isLetter) } >= 6
            signal && block.bounds.left > textLeft + bodySize * 2 &&
                proseBlocks.none { it.bounds.top < block.bounds.bottom && it.bounds.bottom > block.bounds.top }
        }
        val cores = formulaCrops(candidates.flatMap { it.runs }, rules) + displays.map { it.bounds }
        if (cores.isEmpty()) return output
        data class Group(var bounds: FigureBounds, val blocks: MutableSet<PdfLayoutBlock>)
        val groups = cores.mapNotNull { core ->
            val affected = candidates.filter { inside(it.bounds, core) }.toMutableSet()
            if (affected.isEmpty()) return@mapNotNull null
            var crop = affected.fold(core) { box, part -> box.union(part.bounds) }
            var changed = true
            while (changed) {
                changed = false
                candidates.filter { it !in affected && !it.text.matches(Regex("\\(\\d+[a-z]?\\)")) }.forEach { part ->
                    val nearBaseline = part.bounds.top <= crop.bottom + bodySize * .35f && part.bounds.bottom >= crop.top - bodySize * .35f
                    val horizontalGap = max(part.bounds.left - crop.right, crop.left - part.bounds.right).coerceAtLeast(0f)
                    if (nearBaseline && horizontalGap <= bodySize * 2) {
                        affected += part; crop = crop.union(part.bounds); changed = true
                    }
                }
            }
            candidates.filter { it !in affected && it.text.matches(Regex("\\(\\d+[a-z]?\\)")) &&
                it.bounds.left >= crop.right && it.bounds.left - crop.right < bodySize * 24 &&
                abs((it.bounds.top + it.bounds.bottom) / 2 - (crop.top + crop.bottom) / 2) < crop.height / 2 + bodySize }
                .minByOrNull { it.bounds.left - crop.right }?.let { affected += it }
            Group(affected.fold(crop) { box, part -> box.union(part.bounds) }, affected)
        }.toMutableList()
        // A fraction and a display-equation seed can describe the same expression.
        var merged = true
        while (merged) {
            merged = false
            loop@ for (i in groups.indices) for (j in i + 1 until groups.size) {
                if (groups[i].blocks.any { it in groups[j].blocks }) {
                    groups[i].bounds = groups[i].bounds.union(groups[j].bounds)
                    groups[i].blocks += groups[j].blocks
                    groups.removeAt(j); merged = true; break@loop
                }
            }
        }
        val emitted = mutableSetOf<Int>()
        return output.mapNotNull { block ->
            if (block.headingLevel != null || block.kind !in setOf("paragraph", "equation", "figure")) return@mapNotNull block
            if (block.kind == "figure" && groups.any { group -> block.sources.any {
                    inside(it.bounds, group.bounds) && it.bounds.width < group.bounds.width * 1.15f && it.bounds.height < group.bounds.height * 1.15f
                } }) return@mapNotNull null
            val index = groups.indexOfFirst { group -> block.sources.any { source -> group.blocks.any { it.anchor == source.anchor } } }
            if (index < 0) return@mapNotNull block
            if (!emitted.add(index)) return@mapNotNull null
            val group = groups[index]
            val region = group.bounds.let { FigureBounds(it.left - 1, it.top - 1, it.right + 1, it.bottom + 1) }
            val parts = original.filter { it in group.blocks }
            val equation = math(region, parts.flatMap { it.runs }, rules)
            val sources = parts.mapIndexed { part, item -> ReflowSource(item.anchor, item.bounds, item.text,
                listOf(ReflowRun(item.text, 0, item.text.length, math = if (part == 0) equation else null)),
                equationLabel = item.text.matches(Regex("\\(\\d+[a-z]?\\)")), hidden = part > 0) }
            block.copy(kind = "equation", sources = sources)
        }
    }
}
