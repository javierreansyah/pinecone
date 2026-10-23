package com.javierreansyah.pinecone.data.publication.conversion

import com.artifex.mupdf.fitz.Document
import com.javierreansyah.pinecone.reader.pdf.MuPdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

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
    val href: String = "",
)

@Serializable
data class SourceTextRun(
    val anchor: String,
    val start: Int,
    val end: Int,
    val image: Boolean = false
)

data class PdfSource(val document: MuPdfDocument, val href: String)

/** MuPDF exports page-sized records; Pinecone streams them into 25-source-page EPUB resources. */
class PdfReadModeConverter {
    suspend fun convert(
        sources: List<PdfSource>, destination: File, identifier: String, title: String,
        language: String, strings: GeneratedPublicationStrings,
        previousSourceMap: List<SourcePage> = emptyList(),
        progress: suspend (Int, Int) -> Unit,
    ): String = withContext(Dispatchers.Default) {
        require(sources.isNotEmpty())
        sources.forEach { source ->
            source.document.access {
                require(it.hasPermission(Document.PERMISSION_COPY)) { "This PDF does not permit text extraction" }
            }
        }
        val pages =
            sources.flatMap { source -> (0 until source.document.pageCount).map { source to it } }
        require(pages.isNotEmpty())
        val context = currentCoroutineContext()
        val records = File(destination.parentFile, "layout-${UUID.randomUUID()}")
        check(records.mkdir())
        val pageFiles = pages.indices.map { File(records, "$it.json") }
        try {
            val profile = MuPdfReflowProfile()
            // Keep native working sets bounded even on devices with many cores. Profile updates
            // remain ordered because its capped margin vocabulary depends on source-page order.
            val workers = Runtime.getRuntime().availableProcessors().coerceIn(1, 2)
            forEachPreparedPage(pages.size, workers, prepare = { index ->
                val workerContext = currentCoroutineContext()
                val (source, page) = pages[index]
                val record =
                    MuPdfPageExporter.read(source.document, page) { workerContext.ensureActive() }
                workerContext.ensureActive()
                val json = Json.encodeToString(record)
                withContext(Dispatchers.IO) { pageFiles[index].writeText(json) }
                record
            }) { index, record ->
                profile.observe(record)
                progress(index + 1, pages.size * 2)
            }
            fun flatten(nodes: List<org.readium.r2.shared.util.pdf.PdfDocument.OutlineNode>): List<Pair<String, Int>> =
                nodes.flatMap { node ->
                    listOfNotNull(node.pageNumber?.let { node.title.orEmpty() to it }) + flatten(
                        node.children
                    )
                }

            var offset = 0
            val outline = sources.flatMap { source ->
                flatten(source.document.outline).map { (name, page) -> name to page + offset }
                    .also { offset += source.document.pageCount }
            }
            val cleaner = MuPdfReflowCleaner(profile)
            val previousByPage = previousSourceMap.groupBy { it.page }
            val outlineByPage =
                outline.groupBy({ it.second }, { it.first }).mapValues { it.value.toSet() }
            val sourcePages = mutableListOf<SourcePage>()
            EpubWriter(
                destination.outputStream(), identifier, title, language.ifBlank { "und" }, strings,
                withContext(Dispatchers.IO) {
                    File.createTempFile("pinecone-content-", ".xhtml", destination.parentFile)
                }
            ).use { writer ->
                // Decode ahead while the single ordered cleaner/writer handles the current page.
                // Bibliography continuations, asset numbering and anchors retain their order.
                forEachPreparedPage(pages.size, workers, prepare = { index ->
                    val json = withContext(Dispatchers.IO) { pageFiles[index].readText() }
                    Json.decodeFromString<MuPdfPageRecord>(json)
                }) { index, record ->
                    context.ensureActive()
                    val (source, pdfPage) = pages[index]
                    val svgNames = mutableMapOf<FigureBounds, MuPdfVisual>()
                    val stored = mutableSetOf<String>()
                    val result =
                        MuPdfPageExporter.withSvgExporter(source.document, pdfPage) { exportSvg ->
                            cleaner.clean(
                                record, index + 1,
                                previousByPage[index + 1]?.firstOrNull()?.blocks.orEmpty(),
                                outlineByPage[index + 1].orEmpty(),
                                svg = { region, standalone ->
                                    val visual = svgNames.getOrPut(region) {
                                        context.ensureActive()
                                        val name = "page-${index + 1}-visual-${svgNames.size}.svg"
                                        MuPdfVisual(name, exportSvg(region))
                                    }
                                    if (standalone && stored.add(visual.name)) writer.asset(
                                        visual.name,
                                        "image/svg+xml",
                                        visual.markup.toByteArray(Charsets.UTF_8)
                                    )
                                    visual
                                }, asset = writer::asset
                            )
                        }
                    writer.nativePage(index + 1, result.body, result.scanned)
                    sourcePages += SourcePage(
                        index + 1, GeneratedEpubLocations.pageHref(index + 1),
                        result.sources, source.href, pdfPage + 1
                    )
                    pageFiles[index].delete()
                    progress(pages.size + index + 1, pages.size * 2)
                }
                writer.finish(outline)
                sourcePages.indices.forEach { index ->
                    val page = sourcePages[index]
                    sourcePages[index] = page.copy(
                        href = writer.hrefForAnchor("source-page-${page.page}"),
                        blocks = page.blocks.map { block ->
                            block.copy(
                                href = writer.resourceForAnchor(block.anchor),
                            )
                        },
                    )
                }
            }
            Json.encodeToString(sourcePages)
        } finally {
            pageFiles.forEach { it.delete() }
            records.delete()
        }
    }

    companion object {
        const val VERSION: Int = 14
        fun joinLines(text: String): String =
            text.replace(Regex("(?<=\\p{L})-\\r?\\n(?=\\p{Ll})"), "")
                .replace(Regex("[\\r\\n]+"), " ").replace(Regex("[ \\t]+"), " ").trim()
    }
}
