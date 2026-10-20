package com.javierreansyah.pinecone.data.publication.conversion

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artifex.mupdf.fitz.Document
import com.javierreansyah.pinecone.reader.pdf.MuPdfDocument
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfReadModeConverterTest {
    @Test
    fun convertsFilledVectorArtWithoutNullStrokeFailureOrFullPageDuplication() = runBlocking {
        val pdf = MuPdfDocument(Document.openDocument(vectorPdf(), "application/pdf"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val destination = File.createTempFile("vector-conversion-", ".epub", context.cacheDir)
        try {
            var completed = 0
            PdfReadModeConverter().convert(
                listOf(PdfSource(pdf, "original.pdf")), destination, "test:vector", "Vector PDF", "en",
                GeneratedPublicationStrings("Contents", "Scanned page", "Original page") { "Page $it" },
            ) { page, total ->
                assertEquals(2, total)
                completed = page
            }
            assertEquals(2, completed)
            val entries = mutableMapOf<String, ByteArray>()
            ZipInputStream(destination.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries[entry.name] = zip.readBytes()
                }
            }
            val page = entries.getValue("EPUB/text/content.xhtml").toString(Charsets.UTF_8)
            assertTrue(page.contains("A PDF with extractable text."))
            val images = entries.filterKeys { it.startsWith("EPUB/images/") }
            assertEquals(1, images.size)
            val bytes = images.values.single()
            val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            try {
                // The original renders at 1800px wide; this must be a figure crop.
                assertTrue(image.width in 100..999)
                assertTrue(image.height in 100..999)
            } finally {
                image.recycle()
            }
        } finally {
            pdf.close()
            destination.delete()
        }
    }

    @Test
    fun upgradingLegacyRenditionPreservesBookmarkIdsNotesPreferencesAndReadingState() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context,
            com.javierreansyah.pinecone.data.local.database.library.AppDatabase::class.java).build()
        val http = org.readium.r2.shared.util.http.DefaultHttpClient()
        val retriever = org.readium.r2.shared.util.asset.AssetRetriever(context.contentResolver, http)
        val opener = org.readium.r2.streamer.PublicationOpener(
            publicationParser = org.readium.r2.streamer.parser.DefaultPublicationParser(
                context = context, httpClient = http, assetRetriever = retriever,
                pdfFactory = com.javierreansyah.pinecone.reader.pdf.MuPdfDocumentFactory(),
            ),
        )
        val repository = com.javierreansyah.pinecone.data.repository.library.LibraryRepository(
            context, db, db.bookDao(), db.bookmarkDao(), db.shelfDao(), db.noteDao(), db.spaceDao(), opener, retriever,
        )
        val source = File.createTempFile("migration-source-", ".pdf", context.cacheDir).apply { writeBytes(vectorPdf()) }
        val oldFile = File.createTempFile("migration-legacy-", ".epub", context.cacheDir)
        var generated: File? = null
        try {
            val id = java.util.UUID.randomUUID().toString()
            val legacy = """{"href":"text/page-1.xhtml","type":"application/xhtml+xml","title":"Chapter one",
                "text":{"highlight":"A PDF with extractable text."},"locations":{"cssSelector":"#block-0","totalProgression":0.2}}"""
            val originalLocator = """{"href":"original.pdf","type":"application/pdf","locations":{"fragments":["page=1"]}}"""
            db.bookDao().insert(com.javierreansyah.pinecone.data.local.database.library.BookEntity(
                id = id, title = "Migration", filePath = source.absolutePath,
            ))
            val old = com.javierreansyah.pinecone.data.local.database.library.PublicationRenditionEntity(
                id, "read-mode", oldFile.absolutePath, "application/epub+zip", "EPUB", "legacy",
                converterVersion = 2, lastLocatorJson = legacy, furthestLocatorJson = legacy,
                jumpOriginLocatorJson = legacy, preferencesJson = "{\"fontSize\":1.3}", progression = .2,
            )
            db.publicationDao().putRenditions(listOf(old,
                com.javierreansyah.pinecone.data.local.database.library.PublicationRenditionEntity(
                    id, "original", source.absolutePath, "application/pdf", "PDF", "original",
                )))
            val bookmark = com.javierreansyah.pinecone.data.local.database.library.BookmarkEntity(
                bookId = id, locatorJson = legacy, chapterTitle = "Chapter one", renditionKey = "read-mode",
            )
            val bookmarkId = db.bookmarkDao().insert(bookmark)
            val originalId = db.bookmarkDao().insert(bookmark.copy(locatorJson = originalLocator, renditionKey = "original"))
            val note = com.javierreansyah.pinecone.data.local.database.library.NoteEntity(
                bookId = id, locatorJson = legacy, chapterTitle = "Chapter one", noteText = "Keep this note",
                color = 123, renditionKey = "read-mode",
            )
            val noteId = db.noteDao().insert(note)
            val updated = repository.createReadMode(id) { _, _ -> }
            generated = File(updated.filePath)
            val expected = requireNotNull(GeneratedEpubLocations.migrate(legacy))
            assertEquals(bookmark.copy(id = bookmarkId, locatorJson = expected), db.bookmarkDao().getByRendition(id, "read-mode").single())
            assertEquals(note.copy(id = noteId, locatorJson = expected), db.noteDao().getByRendition(id, "read-mode").single())
            assertEquals(originalId, db.bookmarkDao().getByRendition(id, "original").single().id)
            assertEquals(originalLocator, db.bookmarkDao().getByRendition(id, "original").single().locatorJson)
            assertEquals(old.preferencesJson, updated.preferencesJson)
            assertEquals(old.progression, updated.progression, 0.0)
            assertEquals(expected, updated.lastLocatorJson)
            assertEquals(expected, updated.furthestLocatorJson)
            assertEquals(expected, updated.jumpOriginLocatorJson)
            assertEquals(updated, repository.createReadMode(id, onConversionStart = { error("Cached rendition reconverted") }) { _, _ -> })
            repository.activateRendition(id, "read-mode")
            val publication = requireNotNull(repository.openPublication(requireNotNull(repository.getBook(id))))
            try {
                assertEquals(1, publication.readingOrder.size)
                assertEquals("source-page-1", publication.locatorFromLink(publication.tableOfContents.single())?.locations?.fragments?.single())
            } finally {
                publication.close()
            }
        } finally {
            generated?.delete()
            oldFile.delete()
            source.delete()
            db.close()
        }
    }


    @Test
    fun optionalResearchPaperReconstructionAudit() = runBlocking {
        val path = InstrumentationRegistry.getArguments().getString("pdfPath")
        org.junit.Assume.assumeTrue("Pass pdfPath to audit a local research paper", path != null)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val native = MuPdfDocument(Document.openDocument(requireNotNull(path)))
        val output = File(context.cacheDir, "pdf-reconstruction-audit.epub")
        val started = System.nanoTime()
        try {
            if (InstrumentationRegistry.getArguments().getString("dumpLayout") == "true") {
                val extractor = PdfLayoutExtractor()
                (0 until native.pageCount).forEach { index ->
                    File(context.cacheDir, "pdf-layout-${index + 1}.json").writeText(
                        kotlinx.serialization.json.Json.encodeToString(extractor.extract(native, index) {}))
                }
            }
            val map = PdfReadModeConverter().convert(listOf(PdfSource(native, "original.pdf")), output,
                "test:research-paper", "Research paper audit", "en",
                GeneratedPublicationStrings("Contents", "Scanned page", "Original page") { "Page $it" }) { _, _ -> }
            File(context.cacheDir, "pdf-reconstruction-audit-map.json").writeText(map)
            val entries = mutableMapOf<String, ByteArray>()
            ZipInputStream(output.inputStream()).use { zip ->
                while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes() }
            }
            val xml = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
                .parse(entries.getValue("EPUB/text/content.xhtml").inputStream())
            val headings = (1..6).flatMap { level ->
                val nodes = xml.getElementsByTagNameNS("*", "h$level")
                (0 until nodes.length).map { "h$level" to nodes.item(it).textContent.trim().replace(Regex("\\s+"), " ") }
            }
            File(context.cacheDir, "pdf-reconstruction-audit-headings.txt").writeText(headings.joinToString("\n") { "${it.first}: ${it.second}" })
            assertTrue("Introduction must be a single numbered heading: $headings", headings.any { it.second == "1 Introduction" })
            assertTrue("Attention must be a single subsection heading: $headings", headings.any { it.first == "h3" && it.second == "3.2 Attention" })
            assertTrue("Watermarks and table labels must not become headings", headings.none {
                it.second.startsWith("arXiv:") || it.second.startsWith("Provided proper attribution") ||
                    it.second == "Parser Training" || it.second == "WSJ 23 F1"
            })
            val introduction = headings.first { it.second == "1 Introduction" }.first
            assertEquals(introduction, headings.first { it.second.startsWith("4 Why Self-Attention") }.first)
            val tables = xml.getElementsByTagNameNS("*", "table")
            assertEquals("All four research-paper tables should contain selectable HTML cells", 4, tables.length)
            assertTrue(tables.item(0).textContent.contains("Sequential Operations"))
            val tableTwo = tables.item(1) as org.w3c.dom.Element
            val tableTwoHeaders = tableTwo.getElementsByTagNameNS("*", "th")
            assertTrue((0 until tableTwoHeaders.length).any {
                val cell = tableTwoHeaders.item(it) as org.w3c.dom.Element
                cell.textContent.trim() == "BLEU" && cell.getAttribute("colspan") == "2"
            })
            val paragraphs = xml.getElementsByTagNameNS("*", "p")
            val references = (0 until paragraphs.length).map { paragraphs.item(it) as org.w3c.dom.Element }
                .filter { it.getAttribute("class") == "reference" }
            assertEquals("Line fragments should form the original 40 bibliography entries", 40, references.size)
            assertTrue(references.first().textContent.contains("arXiv:1607.06450, 2016."))
            assertTrue(references[8].textContent.contains("sequence to sequence learning."))
            val all = xml.getElementsByTagName("*")
            val ids = (0 until all.length).mapNotNull { all.item(it).attributes.getNamedItem("id")?.nodeValue }
            assertEquals("Source and run anchors must remain unique inside tables", ids.size, ids.toSet().size)
            val sourceMap = kotlinx.serialization.json.Json.decodeFromString<List<SourcePage>>(map)
            sourceMap.flatMap { it.blocks }.forEach { block ->
                assertTrue("Missing source anchor ${block.anchor}", block.anchor in ids)
                block.textRuns.forEach { assertTrue("Missing text run ${it.anchor}", it.anchor in ids) }
            }
            println("PDF_RECONSTRUCTION: pages=${native.pageCount}, elapsedMs=${(System.nanoTime() - started) / 1_000_000}, outputBytes=${output.length()}")
        } finally { native.close() }
    }


    @Test
    fun nativeInlineSuperscriptsAndSubscriptsStayTextAndNeedNoBitmaps() = runBlocking {
        val content = "BT /F1 12 Tf 1 0 0 1 50 700 Tm (We compute ) Tj " +
            "1 0 0 1 116 700 Tm (x) Tj /F1 8 Tf 1 0 0 1 122 705 Tm (2) Tj " +
            "/F1 12 Tf 1 0 0 1 126 700 Tm ( + y) Tj /F1 8 Tf 1 0 0 1 152 697 Tm (i) Tj " +
            "/F1 12 Tf 1 0 0 1 158 700 Tm ( for each input in this example.) Tj ET\n"
        val pdf = MuPdfDocument(Document.openDocument(vectorPdf(content), "application/pdf"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("inline-text-", ".epub", context.cacheDir)
        try {
            PdfReadModeConverter().convert(listOf(PdfSource(pdf, "inline.pdf")), file, "test:inline", "Inline", "en",
                GeneratedPublicationStrings("Contents", "Scanned", "Original") { "Page $it" }) { _, _ -> }
            val entries = mutableMapOf<String, ByteArray>()
            ZipInputStream(file.inputStream()).use { zip ->
                while (true) { val e = zip.nextEntry ?: break; entries[e.name] = zip.readBytes() }
            }
            assertTrue("Text-only math must not rasterize its page", entries.keys.none { it.startsWith("EPUB/images/") })
            val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
                .parse(entries.getValue("EPUB/text/content.xhtml").inputStream())
            val sup = doc.getElementsByTagNameNS("*", "sup")
            val sub = doc.getElementsByTagNameNS("*", "sub")
            assertTrue((0 until sup.length).any { sup.item(it).textContent == "2" })
            assertTrue((0 until sub.length).any { sub.item(it).textContent == "i" })
        } finally { pdf.close(); file.delete() }
    }

    @Test
    fun cancellationRemovesTemporaryPageRecords() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val before = context.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("layout-") }.map { it.name }.toSet()
        val pdf = MuPdfDocument(Document.openDocument(vectorPdf(), "application/pdf"))
        val file = File.createTempFile("cancelled-layout-", ".epub", context.cacheDir)
        try {
            var cancelled = false
            try {
                PdfReadModeConverter().convert(listOf(PdfSource(pdf, "original.pdf")), file, "test:cancel", "Cancel", "en",
                    GeneratedPublicationStrings("Contents", "Scanned", "Original") { "Page $it" }) { _, _ ->
                    throw kotlinx.coroutines.CancellationException("Test cancellation after extraction")
                }
            } catch (_: kotlinx.coroutines.CancellationException) { cancelled = true }
            assertTrue(cancelled)
            assertEquals(before, context.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("layout-") }.map { it.name }.toSet())
        } finally { pdf.close(); file.delete() }
    }

    private fun vectorPdf(customContent: String? = null): ByteArray {
        val content = customContent ?: "1 1 1 rg 0 0 600 800 re f\n" +
            "0 0 0 rg 100 300 150 100 re f\n" +
            "BT /F1 12 Tf 50 700 Td (A PDF with extractable text.) Tj ET\n"
        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 600 800] " +
                "/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
            "<< /Length ${content.length} >>\nstream\n${content}endstream",
        )
        val output = ByteArrayOutputStream()
        fun write(value: String) = output.write(value.toByteArray(Charsets.US_ASCII))
        write("%PDF-1.4\n")
        val offsets = objects.mapIndexed { index, value ->
            output.size().also { write("${index + 1} 0 obj\n$value\nendobj\n") }
        }
        val xref = output.size()
        write("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { write(String.format(Locale.ROOT, "%010d 00000 n \n", it)) }
        write("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return output.toByteArray()
    }
}
