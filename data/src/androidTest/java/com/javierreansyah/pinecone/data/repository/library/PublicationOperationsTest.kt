package com.javierreansyah.pinecone.data.repository.library

import android.net.Uri
import androidx.datastore.core.DataStoreFactory
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.javierreansyah.pinecone.data.local.database.library.AppDatabase
import com.javierreansyah.pinecone.data.local.preferences.JsonPreferenceSerializer
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferencesData
import com.javierreansyah.pinecone.data.model.OperationState
import com.javierreansyah.pinecone.data.publication.PublicationFormats
import com.javierreansyah.pinecone.data.publication.ReaderProfile
import com.javierreansyah.pinecone.reader.pdf.MuPdfDocumentFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PublicationOperationsTest {
    @Test
    fun folderRequestsShareOwnedGrantsAndPreservePreexistingGrants() = runBlocking {
        fixture { f ->
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val authority = "${context.packageName}.backupdocs"
            val root = android.provider.DocumentsContract.buildTreeDocumentUri(authority, "root")
            val flags =
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            context.grantUriPermission(context.packageName, root, flags)
            val directory = requireNotNull(
                androidx.documentfile.provider.DocumentFile.fromTreeUri(
                    context,
                    root
                )?.createDirectory(UUID.randomUUID().toString())
            )
            val tree = android.provider.DocumentsContract.buildTreeDocumentUri(
                authority,
                android.provider.DocumentsContract.getDocumentId(directory.uri)
            )
            context.grantUriPermission(context.packageName, tree, flags)
            try {
                val nested = requireNotNull(directory.createDirectory("Nested"))
                val document =
                    requireNotNull(nested.createFile("application/epub+zip", "Nested.epub"))
                requireNotNull(context.contentResolver.openOutputStream(document.uri)).use { output ->
                    epub(f, "Nested").inputStream().use { it.copyTo(output) }
                }
                val operations = f.operations()
                val first = operations.enqueueScan(tree)
                val second = operations.enqueueScan(tree)
                fun retained() =
                    context.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission }
                assertTrue(retained())
                operations.execute(first, true) {}
                assertEquals(
                    OperationState.SUCCEEDED, (operations.observe(first).first()
                        ?: return@fixture).state
                )
                assertTrue("The second request still owns access", retained())
                operations.execute(second, true) {}
                assertEquals(
                    OperationState.SUCCEEDED, (operations.observe(second).first()
                        ?: return@fixture).state
                )
                assertEquals(1, f.db.bookDao().getAllBooksSync().size)
                assertFalse("Last terminal request releases the job-acquired grant", retained())

                context.contentResolver.takePersistableUriPermission(
                    tree,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                val preexisting = operations.enqueueScan(tree)
                operations.execute(preexisting, true) {}
                assertEquals(
                    OperationState.SUCCEEDED,
                    (operations.observe(preexisting).first() ?: return@fixture).state
                )
                assertTrue("An existing user grant is not owned by the job", retained())
            } finally {
                repeat(context.contentResolver.persistedUriPermissions.filter { it.uri == tree }.size) {
                    context.contentResolver.releasePersistableUriPermission(
                        tree,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                directory.delete()
                context.revokeUriPermission(tree, flags)
                context.revokeUriPermission(root, flags)
            }
        }
    }

    @Test
    fun unavailableInputRetainsItsNameAndRequiresReselection() = runBlocking {
        fixture { f ->
            val operations = f.operations()
            val id =
                operations.enqueueImports(listOf(Uri.fromFile(File(f.directory, "missing.epub"))))
            val result = requireNotNull(operations.observe(id).first())
            assertEquals(OperationState.INPUT_REQUIRED, result.state)
            assertEquals(listOf("missing.epub"), result.failures)
            assertEquals("input_required", result.error)
            assertTrue(f.db.operationDao().inputs(id).all { it.stagedPath == null })
        }
    }

    @Test
    fun supersededAttemptCannotCommitAfterANewerAttemptCompletes() = runBlocking {
        fixture { f ->
            val operations = f.operations()
            val id = operations.enqueueImports(listOf(Uri.fromFile(epub(f, "Superseded"))))
            var interrupted = false
            operations.execute(id, false) {
                if (!interrupted) {
                    interrupted = true
                    // Claim a new attempt while the old one is suspended before its first input.
                    f.operations().execute(id, true) {}
                }
            }
            assertEquals(
                OperationState.SUCCEEDED, (operations.observe(id).first()
                    ?: return@fixture).state
            )
            assertEquals(1, f.db.bookDao().getAllBooksSync().size)
        }
    }

    @Test
    fun transientInputIsStagedAndAnInterruptedBatchSkipsCommittedItems() = runBlocking {
        fixture { f ->
            val first = epub(f, "First")
            val second = epub(f, "Second")
            val id =
                f.operations().enqueueImports(listOf(Uri.fromFile(first), Uri.fromFile(second)))
            first.delete(); second.delete()
            assertTrue(
                f.db.operationDao().inputs(id).all { File(requireNotNull(it.stagedPath)).isFile })
            val interrupted = runCatching {
                f.operations().execute(
                    id,
                    false
                ) { if (it.completed == 1) throw CancellationException("Simulated process interruption") }
            }.exceptionOrNull()
            assertTrue(interrupted is CancellationException)
            assertEquals(1, f.db.bookDao().getAllBooksSync().size)
            val restarted = f.operations()
            restarted.reconcile()
            restarted.execute(id, false) {}
            assertEquals(
                "${f.db.operationDao().get(id)} ${f.db.operationDao().inputs(id)}",
                2,
                f.db.bookDao().getAllBooksSync().size
            )
            assertEquals(
                OperationState.SUCCEEDED,
                (restarted.observe(id).first() ?: return@fixture).state
            )
            assertTrue(
                f.db.operationDao().inputs(id)
                    .all { it.stagedPath == null && it.state == "SUCCESS" })
            restarted.execute(id, false) { error("A completed operation ran again") }
            assertEquals(2, f.db.bookDao().getAllBooksSync().size)
        }
    }

    @Test
    fun cancellationAndLibraryReplacementCannotPublishQueuedImports() = runBlocking {
        fixture { f ->
            val source = epub(f, "Cancelled")
            val operations = f.operations()
            val cancelled = operations.enqueueImports(listOf(Uri.fromFile(source)))
            operations.cancel(cancelled)
            operations.execute(cancelled, false) {}
            assertTrue(f.db.bookDao().getAllBooksSync().isEmpty())
            val replaced = operations.enqueueImports(listOf(Uri.fromFile(source)))
            f.db.operationDao().beginRestore()
            f.db.operationDao().invalidateAll()
            f.db.operationDao().finishRestore()
            operations.execute(replaced, false) {}
            assertEquals(
                OperationState.CANCELLED, (operations.observe(replaced).first()
                    ?: return@fixture).state
            )
            assertTrue(f.db.bookDao().getAllBooksSync().isEmpty())
            assertTrue(f.db.operationDao().inputs(replaced).all { it.stagedPath == null })
        }
    }

    @Test
    fun aPendingOldImportCannotResurrectADeletedBookButANewRequestCan() = runBlocking {
        fixture { f ->
            val source = epub(f, "Deleted")
            val book = f.publications.importBook(Uri.fromFile(source))
            val operations = f.operations()
            val pending = operations.enqueueImports(listOf(Uri.fromFile(source)))
            f.library.deleteBook(book.book.id)
            operations.execute(pending, false) {}
            assertNull(f.db.bookDao().getById(book.book.id))
            assertEquals(
                OperationState.FAILED, (operations.observe(pending).first()
                    ?: return@fixture).state
            )
            val fresh = operations.enqueueImports(listOf(Uri.fromFile(source)))
            operations.execute(fresh, false) {}
            assertNotNull(f.db.bookDao().getById(book.book.id))
        }
    }

    @Test
    fun openPublicationsKeepDeletedAssetsUntilTheirLeaseCloses() = runBlocking {
        fixture { f ->
            val book = f.publications.importBook(Uri.fromFile(epub(f, "Leased")))
            val publication = requireNotNull(f.publications.openPublication(book))
            val file = File(book.book.filePath)
            try {
                f.library.deleteBook(book.book.id)
                f.library.cleanupOrphanedFiles()
                assertTrue(file.exists())
            } finally {
                publication.close()
            }
            f.library.cleanupOrphanedFiles()
            assertFalse(file.exists())
        }
    }

    @Test
    fun backgroundConversionActivatesReadModeAndRejectsRetiredReaderWrites() = runBlocking {
        fixture { f ->
            val source = File(
                f.directory,
                "source.pdf"
            ).apply { writeBytes(com.javierreansyah.pinecone.testing.syntheticPdf(2)) }
            val book = f.publications.importBook(Uri.fromFile(source))
            val original = requireNotNull(f.publications.openPublication(book))
            try {
                val locator =
                    requireNotNull(original.locatorFromLink(original.readingOrder.first())).copyWithLocations(
                        fragments = listOf("page=1"), totalProgression = .4,
                    )
                f.reading.saveReadingPosition(
                    book.book.id,
                    locator,
                    session = original.readingSessionToken()
                )
                val operations = f.operations()
                val id = operations.enqueueReadMode(book.book.id, false)
                assertEquals(id, operations.enqueueReadMode(book.book.id, true))
                var advanced = false
                operations.execute(id, true) { report ->
                    if (report.completed > 0 && !advanced) {
                        advanced = true
                        f.reading.saveReadingPosition(
                            book.book.id, locator.copyWithLocations(
                                fragments = listOf("page=2"), totalProgression = .8,
                            ), session = original.readingSessionToken()
                        )
                    }
                }
                assertTrue(advanced)
                assertEquals(
                    OperationState.SUCCEEDED, (operations.observe(id).first()
                        ?: return@fixture).state
                )
                val converted = requireNotNull(f.db.bookDao().getById(book.book.id))
                assertEquals("read-mode", converted.book.activeRendition)
                assertTrue("source-page-2" in converted.book.lastLocatorJson.orEmpty())
                val reopened = requireNotNull(f.publications.openPublication(converted))
                try {
                    assertEquals(ReaderProfile.EPUB, PublicationFormats.profile(reopened))
                } finally {
                    reopened.close()
                }
                val originalState = f.reading.rendition(book.book.id, "original")
                f.reading.saveReadingPosition(
                    book.book.id,
                    locator.copyWithLocations(totalProgression = .9),
                    session = original.readingSessionToken()
                )
                assertEquals(originalState, f.reading.rendition(book.book.id, "original"))

                val cached = requireNotNull(f.reading.rendition(book.book.id, "read-mode"))
                val cachedFile = File(cached.filePath)
                val cachedBytes = cachedFile.readBytes().toList()
                f.reading.activateMappedRendition(book.book.id, "original")
                val session = f.reading.currentSession(book.book.id)
                assertTrue(f.publications.tryReuseReadMode(book.book.id) {
                    f.reading.activateMappedRendition(book.book.id, "read-mode", session)
                })
                assertEquals("read-mode", f.db.bookDao().getById(book.book.id)?.book?.activeRendition)
                assertEquals(cached.filePath, f.reading.rendition(book.book.id, "read-mode")?.filePath)
                assertEquals(cachedBytes, cachedFile.readBytes().toList())
                assertEquals(id, operations.conversion(book.book.id).first()?.id)
                assertTrue("source-page-2" in f.db.bookDao().getById(book.book.id)?.book?.lastLocatorJson.orEmpty())

                // An invalidated reader cannot activate a cached rendition.
                f.reading.activateMappedRendition(book.book.id, "original")
                f.publications.tryReuseReadMode(book.book.id) {
                    f.reading.activateMappedRendition(book.book.id, "read-mode", session?.copy(generation = -1))
                }
                assertEquals("original", f.db.bookDao().getById(book.book.id)?.book?.activeRendition)

                f.db.publicationDao().putRenditions(listOf(cached.copy(converterVersion = -1)))
                assertFalse(f.publications.tryReuseReadMode(book.book.id) { error("Outdated cache reused") })
                f.db.publicationDao().putRenditions(listOf(cached))
                val forced = operations.enqueueReadMode(book.book.id, true)
                assertFalse(f.publications.tryReuseReadMode(book.book.id) { error("Active conversion bypassed") })
                operations.cancel(forced)
                assertTrue(cachedFile.delete())
                assertFalse(f.publications.tryReuseReadMode(book.book.id) { error("Missing cache reused") })

            } finally {
                original.close()
            }
        }
    }

    private class Fixture(
        val directory: File,
        val db: AppDatabase,
        val preferences: ReaderPreferences,
        val publications: PublicationRepository,
        val library: LibraryRepository,
        val reading: ReadingRepository
    ) {
        private val scheduler = object : OperationScheduler {
            override suspend fun enqueue(operationId: String) {}
            override suspend fun cancel(operationId: String) {}
        }

        fun operations() = PublicationOperations(
            InstrumentationRegistry.getInstrumentation().targetContext,
            db, publications, reading, scheduler, preferences, Dispatchers.IO
        )
    }

    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, UUID.randomUUID().toString()).apply { mkdirs() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val preferences = ReaderPreferences(
            DataStoreFactory.create(
                serializer = JsonPreferenceSerializer(
                    ReaderPreferencesData.serializer(),
                    ReaderPreferencesData()
                ),
                scope = scope, produceFile = { File(directory, "preferences.json") },
            )
        )
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.BACKUP_REVISION_CALLBACK).build()
        val assets = PublicationAssetStore(context)
        val http = DefaultHttpClient()
        val retriever = AssetRetriever(context.contentResolver, http)
        val opener = PublicationOpener(
            DefaultPublicationParser(
                context,
                http,
                retriever,
                pdfFactory = MuPdfDocumentFactory()
            )
        )
        val publications = PublicationRepository(
            context,
            db,
            opener,
            retriever,
            preferences,
            Dispatchers.IO,
            assets
        )
        val library = LibraryRepository(
            context,
            db,
            db.bookDao(),
            db.shelfDao(),
            db.spaceDao(),
            assets,
            Dispatchers.IO
        )
        val f = Fixture(
            directory,
            db,
            preferences,
            publications,
            library,
            ReadingRepository(db, db.bookDao(), db.bookmarkDao(), db.noteDao())
        )
        try {
            block(f)
        } finally {
            db.operationDao().resumable().forEach { f.operations().cancel(it.id) }
            db.bookDao().getAllBooksSync().forEach { library.deleteBook(it.id) }
            library.cleanupOrphanedFiles()
            db.close()
            scope.coroutineContext.job.cancelAndJoin()
            directory.deleteRecursively()
        }
    }

    private fun epub(f: Fixture, title: String): File = File(f.directory, "$title.epub").apply {
        ZipOutputStream(outputStream()).use { zip ->
            val resources = linkedMapOf(
                "mimetype" to "application/epub+zip",
                "META-INF/container.xml" to """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles><rootfile full-path="content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
                "content.opf" to """<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">${UUID.randomUUID()}</dc:identifier><dc:title>$title</dc:title><dc:language>en</dc:language></metadata><manifest><item id="page" href="page.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="page"/></spine></package>""",
                "page.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>$title</title></head><body><p>$title</p></body></html>""",
            )
            resources.forEach { (name, text) -> zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() }
        }
    }

}
