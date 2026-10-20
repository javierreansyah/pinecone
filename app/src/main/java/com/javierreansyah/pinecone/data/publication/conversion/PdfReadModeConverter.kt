package com.javierreansyah.pinecone.data.publication.conversion

import com.artifex.mupdf.fitz.Document
import com.javierreansyah.pinecone.reader.pdf.MuPdfDocument
import java.io.File
import java.util.UUID
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

/** MuPDF exports page-sized XHTML/structure records; Pinecone packages them into one EPUB spine item. */
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
            val profile = MuPdfReflowProfile()
            pages.forEachIndexed { index, (source, page) ->
                context.ensureActive()
                val record = MuPdfPageExporter.read(source.document, page) { context.ensureActive() }
                profile.observe(record)
                val file = File(records, "$index.json")
                pageFiles += file
                file.writeText(Json.encodeToString(record))
                progress(index + 1, pages.size * 2)
            }
            fun flatten(nodes: List<org.readium.r2.shared.util.pdf.PdfDocument.OutlineNode>): List<Pair<String, Int>> =
                nodes.flatMap { node -> listOfNotNull(node.pageNumber?.let { node.title.orEmpty() to it }) + flatten(node.children) }
            var offset = 0
            val outline = sources.flatMap { source -> flatten(source.document.outline).map { (name, page) -> name to page + offset }
                .also { offset += source.document.pageCount } }
            val cleaner = MuPdfReflowCleaner(profile)
            val sourcePages = mutableListOf<SourcePage>()
            EpubWriter(destination.outputStream(), identifier, title, language.ifBlank { "und" }, strings,
                File.createTempFile("pinecone-content-", ".xhtml", destination.parentFile)).use { writer ->
                pages.forEachIndexed { index, (source, pdfPage) ->
                    context.ensureActive()
                    val record = Json.decodeFromString<MuPdfPageRecord>(pageFiles[index].readText())
                    val svgNames = mutableMapOf<FigureBounds, MuPdfVisual>()
                    val stored = mutableSetOf<String>()
                    val result = MuPdfPageExporter.withSvgExporter(source.document, pdfPage) { exportSvg ->
                        cleaner.clean(record, index + 1,
                        previousSourceMap.firstOrNull { it.page == index + 1 }?.blocks.orEmpty(),
                        outline.filter { it.second == index + 1 }.map { it.first }.toSet(),
                        svg = { region, standalone ->
                            val visual = svgNames.getOrPut(region) {
                                context.ensureActive()
                                val name = "page-${index + 1}-visual-${svgNames.size}.svg"
                                MuPdfVisual(name, exportSvg(region))
                            }
                            if (standalone && stored.add(visual.name)) writer.asset(visual.name, "image/svg+xml", visual.markup.toByteArray(Charsets.UTF_8))
                            visual
                        }, asset = writer::asset)
                    }
                    writer.nativePage(index + 1, result.body, result.scanned)
                    sourcePages += SourcePage(index + 1, GeneratedEpubLocations.pageHref(index + 1),
                        result.sources, source.href, pdfPage + 1)
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
    companion object {
        const val VERSION: Int = 10
        fun joinLines(text: String): String = text.replace(Regex("(?<=\\p{L})-\\r?\\n(?=\\p{Ll})"), "")
            .replace(Regex("[\\r\\n]+"), " ").replace(Regex("[ \\t]+"), " ").trim()
    }
}
