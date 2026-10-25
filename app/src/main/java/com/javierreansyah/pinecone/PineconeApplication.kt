package com.javierreansyah.pinecone

import android.app.Application
import androidx.room.Room
import com.javierreansyah.pinecone.data.local.database.library.AppDatabase
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.repository.backup.BackupRepository
import com.javierreansyah.pinecone.data.repository.dictionary.DictionaryImportManager
import com.javierreansyah.pinecone.data.repository.dictionary.DictionaryRepository
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import com.javierreansyah.pinecone.reader.audio.AudioReadingSession
import com.javierreansyah.pinecone.worker.WorkerUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

class PineconeApplication : Application() {
    val audioSession: AudioReadingSession by lazy { AudioReadingSession(this) }

    lateinit var database: AppDatabase
        private set

    lateinit var libraryRepository: LibraryRepository
        private set

    lateinit var dictionaryRepository: DictionaryRepository
        private set

    lateinit var dictionaryImportManager: DictionaryImportManager
        private set

    lateinit var readerPreferences: ReaderPreferences
        private set

    private lateinit var publicationOpener: PublicationOpener

    private lateinit var assetRetriever: AssetRetriever

    override fun onCreate() {
        super.onCreate()
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            timber.log.Timber.plant(timber.log.Timber.DebugTree())
        }

        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "publication_library",
        )
            .addCallback(AppDatabase.BACKUP_REVISION_CALLBACK)
            .fallbackToDestructiveMigration(dropAllTables = false)
            .build()

        val httpClient = DefaultHttpClient()

        assetRetriever = AssetRetriever(
            contentResolver = contentResolver,
            httpClient = httpClient,
        )

        publicationOpener = PublicationOpener(
            publicationParser = DefaultPublicationParser(
                context = this,
                httpClient = httpClient,
                assetRetriever = assetRetriever,
                pdfFactory = com.javierreansyah.pinecone.reader.pdf.MuPdfDocumentFactory(),
            ),
        )

        libraryRepository = LibraryRepository(
            context = applicationContext,
            database = database,
            bookDao = database.bookDao(),
            bookmarkDao = database.bookmarkDao(),
            shelfDao = database.shelfDao(),
            noteDao = database.noteDao(),
            spaceDao = database.spaceDao(),
            publicationOpener = publicationOpener,
            assetRetriever = assetRetriever,
        )

        readerPreferences = ReaderPreferences(applicationContext)

        dictionaryRepository = DictionaryRepository(
            context = applicationContext,
            preferences = readerPreferences,
        )

        dictionaryImportManager = DictionaryImportManager(
            context = applicationContext,
            preferences = readerPreferences,
        )

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                BackupRepository(applicationContext).completePendingSettingsRestore()
            } catch (error: Exception) {
                timber.log.Timber.e(error)
            }
            libraryRepository.cleanupOrphanedFiles()
            val initialFrequency = readerPreferences.readerSettings.first().autoBackupFrequency
            WorkerUtils.scheduleBackupWork(applicationContext, initialFrequency)
            readerPreferences.readerSettings.collect { settings ->
                if (!settings.unsortedEnabled) libraryRepository.releaseUnsortedBooks()
            }
        }
    }
}
