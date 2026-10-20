package com.javierreansyah.pinecone.reader.pdf

import android.content.Context
import android.graphics.Bitmap
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Outline
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.data.ReadError
import org.readium.r2.shared.util.data.ReadTry
import org.readium.r2.shared.util.pdf.PdfDocument
import org.readium.r2.shared.util.pdf.PdfDocumentFactory
import org.readium.r2.shared.util.resource.Resource
import kotlin.reflect.KClass

/** Owns a native document. All access, including destruction, uses the same lock. */
class MuPdfDocument(private val native: Document) : PdfDocument {
    private var closed = false
    fun <T> access(block: (Document) -> T): T = synchronized(this) {
        check(!closed) { "PDF is closed" }
        block(native)
    }

    override val pageCount: Int = access { it.countPages() }
    override val title: String? = access { it.getMetaData(Document.META_INFO_TITLE) }
    override val author: String? = access { it.getMetaData(Document.META_INFO_AUTHOR) }
    override val subject: String? = access { it.getMetaData(Document.META_INFO_SUBJECT) }
    override val keywords: List<String> = access {
        it.getMetaData(Document.META_INFO_KEYWORDS).orEmpty().split(',', ';').map(String::trim)
            .filter(String::isNotBlank)
    }
    override val outline: List<PdfDocument.OutlineNode> = access { doc ->
        fun convert(nodes: Array<Outline>?): List<PdfDocument.OutlineNode> =
            nodes.orEmpty().map { node ->
                val page = node.uri?.takeUnless { it.startsWith("http", true) }?.let {
                    runCatching { doc.pageNumberFromLocation(doc.resolveLink(it)) + 1 }.getOrNull()
                        ?.takeIf { it -> it > 0 }
                }
                PdfDocument.OutlineNode(node.title, page, convert(node.down))
            }
        convert(doc.loadOutline())
    }

    suspend fun render(index: Int, width: Int = 1400): Bitmap = withContext(Dispatchers.Default) {
        access { doc ->
            val page = doc.loadPage(index)
            try {
                AndroidDrawDevice.drawPageFit(page, width.coerceIn(1, 2400), 3200)
            } finally {
                page.destroy()
            }
        }
    }

    override suspend fun cover(context: Context): Bitmap? =
        if (pageCount > 0) render(0, 600) else null

    override fun close(): Unit = synchronized(this) {
        if (!closed) {
            closed = true
            native.destroy()
        }
    }
}

class MuPdfDocumentFactory : PdfDocumentFactory<MuPdfDocument> {
    override val documentType: KClass<MuPdfDocument> = MuPdfDocument::class

    override suspend fun open(resource: Resource, password: String?): ReadTry<MuPdfDocument> {
        var acquired: MuPdfDocument? = null
        return try {
            withContext(Dispatchers.IO) {
                var document: Document? = null
                try {
                    val file = resource.sourceUrl?.toFile()
                    document = if (file != null) Document.openDocument(file.absolutePath) else {
                        val bytes = resource.read().getOrNull() ?: error("Cannot read PDF resource")
                        Document.openDocument(bytes, "application/pdf")
                    }
                    require(
                        !document.needsPassword() || (password != null && document.authenticatePassword(
                            password
                        ))
                    ) {
                        "Password-protected PDFs must be unlocked before importing"
                    }
                    val result = MuPdfDocument(document)
                    acquired = result
                    Try.success(result)
                } catch (cancelled: CancellationException) {
                    document?.destroy(); throw cancelled
                } catch (error: Exception) {
                    document?.destroy(); Try.failure(ReadError.Decoding(error))
                }
            }
        } catch (cancelled: CancellationException) {
            // withContext can discard a successfully acquired native value on return to a cancelled
            // caller. Keep ownership here until that dispatcher hand-off has completed.
            acquired?.close()
            throw cancelled
        }
    }
}
