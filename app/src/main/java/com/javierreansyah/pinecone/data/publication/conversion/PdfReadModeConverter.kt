package com.javierreansyah.pinecone.data.publication.conversion

import android.graphics.Bitmap
import android.graphics.Rect
import com.artifex.mupdf.fitz.Document
import com.javierreansyah.pinecone.reader.pdf.MuPdfDocument
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SourcePage(
    val page: Int,
    val href: String,
    val blocks: List<SourceBlock> = emptyList(),
    val sourceHref: String = "",
    val sourcePage: Int = page
)

@Serializable
data class SourceBlock(
    val anchor: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val textRuns: List<SourceTextRun> = emptyList(),
)

@Serializable
data class SourceTextRun(val anchor: String, val start: Int, val end: Int, val image: Boolean = false)

data class PdfSource(val document: MuPdfDocument, val href: String)

/** Text is extracted once to page-sized disk records; bitmap memory is limited to one page. */
class PdfReadModeConverter {
    suspend fun convert(
        sources: List<PdfSource>, destination: File, identifier: String, title: String,
        language: String, strings: GeneratedPublicationStrings,
        previousSourceMap: List<SourcePage> = emptyList(),
        progress: suspend (Int, Int) -> Unit,
    ): String = withContext(Dispatchers.Default) {
        require(sources.isNotEmpty())
        sources.forEach { source -> source.document.access {
            require(it.hasPermission(Document.PERMISSION_COPY)) { "This PDF does not permit text extraction" }
        } }
        val pages = sources.flatMap { source -> (0 until source.document.pageCount).map { source to it } }
        require(pages.isNotEmpty())
        val context = currentCoroutineContext()
        val records = File(destination.parentFile, "layout-${UUID.randomUUID()}")
        check(records.mkdir())
        val pageFiles = mutableListOf<File>()
        try {
            val profile = PdfDocumentProfile()
            val extractor = PdfLayoutExtractor()
            pages.forEachIndexed { index, (source, page) ->
                context.ensureActive()
                val raw = extractor.extract(source.document, page) { context.ensureActive() }
                val previous = previousSourceMap.firstOrNull { it.page == index + 1 }?.blocks.orEmpty()
                val assigned = mutableSetOf<String>()
                val layout = raw.copy(blocks = raw.blocks.mapIndexed { blockIndex, block ->
                    val matching = previous.firstOrNull { old -> old.anchor !in assigned &&
                        abs(old.x - block.bounds.left) < .75f && abs(old.y - block.bounds.top) < .75f &&
                        abs(old.width - block.bounds.width) < .75f && abs(old.height - block.bounds.height) < .75f }
                    val anchor = matching?.anchor ?: if (previous.isEmpty()) GeneratedEpubLocations.blockAnchor(index + 1, blockIndex)
                        else "page-${index + 1}-source-$blockIndex"
                    assigned += anchor
                    block.copy(anchor = anchor)
                })
                profile.observe(layout)
                val file = File(records, "$index.json")
                pageFiles += file
                file.writeText(Json.encodeToString(layout))
                progress(index + 1, pages.size * 2)
            }
            fun flatten(nodes: List<org.readium.r2.shared.util.pdf.PdfDocument.OutlineNode>): List<Pair<String, Int>> =
                nodes.flatMap { node -> listOfNotNull(node.pageNumber?.let { node.title.orEmpty() to it }) + flatten(node.children) }
            var offset = 0
            val outline = sources.flatMap { source -> flatten(source.document.outline).map { (name, page) -> name to page + offset }
                .also { offset += source.document.pageCount } }
            val reconstruction = PdfLayoutReconstructor(profile)
            val sourcePages = mutableListOf<SourcePage>()
            EpubWriter(destination.outputStream(), identifier, title, language.ifBlank { "und" }, strings,
                File.createTempFile("pinecone-content-", ".xhtml", destination.parentFile)).use { writer ->
                pages.forEachIndexed { index, (source, pdfPage) ->
                    context.ensureActive()
                    val layout = Json.decodeFromString<PdfPageLayout>(pageFiles[index].readText())
                    val scanned = layout.blocks.sumOf { it.text.length } < 5
                    var bitmap: Bitmap? = null
                    val cropNames = mutableMapOf<FigureBounds, String>()
                    suspend fun image(region: FigureBounds): String? {
                        cropNames[region]?.let { return it }
                        context.ensureActive()
                        val rendered = bitmap ?: source.document.render(pdfPage, 1800).also { bitmap = it }
                        val rect = pixelRect(region, layout.bounds, rendered)
                        if (rect.width() <= 1 || rect.height() <= 1) return null
                        val crop = Bitmap.createBitmap(rendered, rect.left, rect.top, rect.width(), rect.height())
                        try {
                            val name = "page-${index + 1}-crop-${cropNames.size}.png"
                            writer.image(name, crop.png())
                            cropNames[region] = name
                            return name
                        } finally { if (crop !== rendered) crop.recycle() }
                    }
                    try {
                        val output = if (scanned) listOf(ReflowBlock(image = image(layout.bounds))) else {
                            reconstruction.reconstruct(layout, outline.filter { it.second == index + 1 }.map { it.first }.toSet())
                                .map { block -> block.copy(sources = block.sources.map { part ->
                                    part.copy(runs = part.runs.map { run ->
                                        if (run.crop == null) run else run.copy(image = image(run.crop))
                                    })
                                }) }
                        }
                        writer.page(index + 1, output, scanned)
                        sourcePages += SourcePage(index + 1, GeneratedEpubLocations.pageHref(index + 1),
                            output.flatMap { it.sources }.map { part -> SourceBlock(part.anchor, part.bounds.left, part.bounds.top,
                                part.bounds.width, part.bounds.height, part.runs.mapIndexed { runIndex, run ->
                                    SourceTextRun("${part.anchor}-run-$runIndex", run.start, run.end, run.image != null || run.hidden || part.hidden)
                                }) }, source.href, pdfPage + 1)
                    } finally { bitmap?.recycle() }
                    pageFiles[index].delete()
                    progress(pages.size + index + 1, pages.size * 2)
                }
                writer.finish(outline)
            }
            Json.encodeToString(sourcePages)
        } finally {
            pageFiles.forEach { it.delete() }
            records.delete()
        }
    }
    private fun pixelRect(region: FigureBounds, page: FigureBounds, bitmap: Bitmap): Rect = Rect(
        ((region.left - page.left) / page.width * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1),
        ((region.top - page.top) / page.height * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1),
        ((region.right - page.left) / page.width * bitmap.width).roundToInt().coerceIn(1, bitmap.width),
        ((region.bottom - page.top) / page.height * bitmap.height).roundToInt().coerceIn(1, bitmap.height),
    )
    private fun Bitmap.png(): ByteArray = ByteArrayOutputStream().use { stream ->
        check(compress(Bitmap.CompressFormat.PNG, 100, stream)); stream.toByteArray()
    }
    companion object {
        const val VERSION: Int = 5
        fun joinLines(text: String): String = text.replace(Regex("(?<=\\p{L})-\\r?\\n(?=\\p{Ll})"), "")
            .replace(Regex("[\\r\\n]+"), " ").replace(Regex("[ \\t]+"), " ").trim()
    }
}
