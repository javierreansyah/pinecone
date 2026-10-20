package com.javierreansyah.pinecone.data.publication.conversion

import android.graphics.Bitmap
import com.artifex.mupdf.fitz.Font
import com.artifex.mupdf.fitz.Image
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Point
import com.artifex.mupdf.fitz.Quad
import com.artifex.mupdf.fitz.Rect
import com.artifex.mupdf.fitz.StructuredTextWalker
import com.javierreansyah.pinecone.reader.pdf.MuPdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt
import android.graphics.Rect as AndroidRect

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
    val height: Float
)

data class PdfSource(val document: MuPdfDocument, val href: String)

/** No OCR or cloud dependency. The generated rendition is immutable once published. */
class PdfReadModeConverter {
    suspend fun convert(
        sources: List<PdfSource>,
        destination: File,
        identifier: String,
        title: String,
        language: String,
        strings: GeneratedPublicationStrings,
        progress: suspend (Int, Int) -> Unit,
    ): String = withContext(Dispatchers.Default) {
        require(sources.isNotEmpty())
        sources.forEach { source -> source.document.access { require(it.hasPermission(com.artifex.mupdf.fitz.Document.PERMISSION_COPY)) { "This PDF does not permit text extraction" } } }
        val pages =
            sources.flatMap { source -> (0 until source.document.pageCount).map { source to it } }
        val totalPages = pages.size
        val marginTexts = mutableMapOf<String, Int>()
        val sampleCount = minOf(totalPages, 20)
        for (index in 0 until sampleCount) {
            currentCoroutineContext().ensureActive()
            val (source, page) = pages[index]
            extract(source.document, page).filter { it.margin && it.text.isNotBlank() }
                .map { it.text }.distinct().forEach {
                marginTexts[it] = (marginTexts[it] ?: 0) + 1
            }
        }
        val repeated = marginTexts.filterValues { it >= maxOf(3, sampleCount / 3) }.keys
        val sourcePages = mutableListOf<SourcePage>()
        EpubWriter(
            destination.outputStream(),
            identifier,
            title,
            language.ifBlank { "und" },
            strings
        ).use { writer ->
            for (index in 0 until totalPages) {
                currentCoroutineContext().ensureActive()
                val (source, pdfPage) = pages[index]
                val pdf = source.document
                val blocks = extract(
                    pdf,
                    pdfPage
                ).filterNot { it.margin && (it.text in repeated || it.text.matches(Regex("\\d+"))) }
                val textBlocks = blocks.filter { it.text.isNotBlank() }
                val median =
                    textBlocks.map { it.fontSize }.sorted().let { it.getOrNull(it.size / 2) ?: 12f }
                val output = mutableListOf<ReflowBlock>()
                val sourceBlocks = mutableListOf<SourceBlock>()
                val scanned = textBlocks.sumOf { it.text.length } < 5
                var rendered: Bitmap? = null
                try {
                    if (scanned || blocks.any { it.image }) rendered = pdf.render(pdfPage, 1800)
                    if (scanned) {
                        val name = "page-${index + 1}.png"
                        writer.image(name, requireNotNull(rendered).png())
                        output += ReflowBlock(image = name)
                    } else {
                        val bounds = pdf.access { doc ->
                            doc.loadPage(pdfPage).let { page ->
                                try {
                                    page.bounds
                                } finally {
                                    page.destroy()
                                }
                            }
                        }
                        blocks.forEachIndexed { blockIndex, block ->
                            currentCoroutineContext().ensureActive()
                            if (block.image) {
                                if (block.originalPage) output += ReflowBlock(strings.originalPageNotice)
                                val bitmap = requireNotNull(rendered)
                                val rect = AndroidRect(
                                    ((block.bounds.x0 - bounds.x0) / (bounds.x1 - bounds.x0) * bitmap.width).roundToInt()
                                        .coerceIn(0, bitmap.width - 1),
                                    ((block.bounds.y0 - bounds.y0) / (bounds.y1 - bounds.y0) * bitmap.height).roundToInt()
                                        .coerceIn(0, bitmap.height - 1),
                                    ((block.bounds.x1 - bounds.x0) / (bounds.x1 - bounds.x0) * bitmap.width).roundToInt()
                                        .coerceIn(1, bitmap.width),
                                    ((block.bounds.y1 - bounds.y0) / (bounds.y1 - bounds.y0) * bitmap.height).roundToInt()
                                        .coerceIn(1, bitmap.height),
                                )
                                if (rect.width() > 1 && rect.height() > 1) {
                                    val crop = Bitmap.createBitmap(
                                        bitmap,
                                        rect.left,
                                        rect.top,
                                        rect.width(),
                                        rect.height()
                                    )
                                    try {
                                        val name = "page-${index + 1}-figure-$blockIndex.png"
                                        sourceBlocks += SourceBlock(
                                            "block-${output.size}",
                                            block.bounds.x0,
                                            block.bounds.y0,
                                            block.bounds.x1 - block.bounds.x0,
                                            block.bounds.y1 - block.bounds.y0
                                        )
                                        writer.image(
                                            name,
                                            crop.png()
                                        ); output += ReflowBlock(image = name)
                                    } finally {
                                        if (crop !== bitmap) crop.recycle()
                                    }
                                }
                            } else if (block.text.isNotBlank()) {
                                sourceBlocks += SourceBlock(
                                    "block-${output.size}",
                                    block.bounds.x0,
                                    block.bounds.y0,
                                    block.bounds.x1 - block.bounds.x0,
                                    block.bounds.y1 - block.bounds.y0
                                )
                                output += ReflowBlock(
                                    joinLines(block.text),
                                    block.fontSize > median * 1.2f && block.text.length < 200
                                )
                            }
                        }
                    }
                    writer.page(index + 1, output, scanned)
                    sourcePages += SourcePage(
                        index + 1,
                        "text/page-${index + 1}.xhtml",
                        sourceBlocks,
                        source.href,
                        pdfPage + 1
                    )
                } finally {
                    rendered?.recycle()
                }
                progress(index + 1, totalPages)
            }
            fun flatten(nodes: List<org.readium.r2.shared.util.pdf.PdfDocument.OutlineNode>): List<Pair<String, Int>> =
                nodes.flatMap {
                    listOfNotNull(it.pageNumber?.let { page -> it.title.orEmpty() to page }) + flatten(
                        it.children
                    )
                }

            var offset = 0
            val outline = sources.flatMap { source ->
                flatten(source.document.outline).map { (title, page) -> title to page + offset }
                    .also { offset += source.document.pageCount }
            }
            writer.finish(outline)
        }
        Json.encodeToString(sourcePages)
    }

    private data class Block(
        val bounds: Rect,
        val text: String,
        val fontSize: Float,
        val image: Boolean,
        val margin: Boolean,
        val originalPage: Boolean = false
    )

    private fun extract(pdf: MuPdfDocument, index: Int): List<Block> = pdf.access { doc ->
        val page = doc.loadPage(index)
        try {
            val bounds = page.bounds
            val height = bounds.y1 - bounds.y0
            val blocks = mutableListOf<Block>()
            // MuPDF 1.28.5's vector walker passes bit flags directly to JNI SetBooleanField,
            // which aborts under CheckJNI. Never walk a page containing vector blocks.
            val structured =
                page.toStructuredText("preserve-images=yes,vectors=no,segment=yes,paragraph-break=yes")
            try {
                structured.walk(object : StructuredTextWalker {
                    // These full callback signatures are required by MuPDF's Java interface.
                    // Unused parameters must not be removed by IDE signature cleanup.
                    var text = StringBuilder()
                    var box = bounds
                    var size = 0f
                    var count = 0
                    override fun beginTextBlock(bbox: Rect, flags: Int) {
                        box = bbox; text = StringBuilder(); size = 0f; count = 0
                    }

                    override fun beginLine(bbox: Rect, wmode: Int, dir: Point) = Unit
                    override fun onChar(
                        c: Int,
                        origin: Point,
                        font: Font,
                        size: Float,
                        q: Quad,
                        argb: Int,
                        flags: Int,
                        bidi: Int
                    ) {
                        if (Character.isValidCodePoint(c)) text.appendCodePoint(c)
                        this.size += size; count++
                    }

                    override fun onImageBlock(bbox: Rect, transform: Matrix, image: Image) {
                        blocks += Block(bbox, "", 0f, image = true, margin = false)
                    }

                    override fun beginStruct(standard: String, raw: String?, index: Int) = Unit
                    override fun onVector(
                        bbox: Rect,
                        info: StructuredTextWalker.VectorInfo,
                        argb: Int
                    ) = Unit

                    override fun endTextBlock() {
                        blocks += Block(
                            box,
                            text.toString().trim(),
                            if (count > 0) size / count else 12f,
                            false,
                            box.y1 < bounds.y0 + height * .1f || box.y0 > bounds.y1 - height * .1f
                        )
                    }

                    override fun endLine() {
                        text.append('\n')
                    }

                    override fun endStruct() = Unit
                })
            } finally {
                structured.destroy()
            }
            // JSON serialization stays inside C, bypassing the unsafe vector JNI callback. Its
            // vector records do not expose bounds, so retain the original page for complex art.
            val graphics = page.toStructuredText("vectors=yes")
            val hasGraphics = try {
                fun containsVector(element: kotlinx.serialization.json.JsonElement): Boolean =
                    when (element) {
                        is kotlinx.serialization.json.JsonArray -> element.any(::containsVector)
                        is kotlinx.serialization.json.JsonObject -> {
                            val type =
                                (element["type"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                            val color =
                                (element["argb"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                            (type == "vector" && color != "-1") || element.values.any(::containsVector)
                        }

                        else -> false
                    }
                containsVector(Json.parseToJsonElement(graphics.asJSON()))
            } finally {
                graphics.destroy()
            }
            if (hasGraphics) blocks += Block(
                bounds, "", 0f,
                image = true,
                margin = false,
                originalPage = true
            )
            // MuPDF's segment option establishes region/column order; retain its traversal order.
            blocks
        } finally {
            page.destroy()
        }
    }

    private fun Bitmap.png(): ByteArray = ByteArrayOutputStream().use { stream ->
        check(compress(Bitmap.CompressFormat.PNG, 100, stream)); stream.toByteArray()
    }

    companion object {
        const val VERSION: Int = 1
        fun joinLines(text: String): String =
            text.replace(Regex("(?<=\\p{L})-\\r?\\n(?=\\p{Ll})"), "")
                .replace(Regex("[\\r\\n]+"), " ").replace(Regex("[ \\t]+"), " ").trim()
    }
}
