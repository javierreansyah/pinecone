package com.javierreansyah.pinecone

import android.app.Application
import androidx.room.Room
import com.javierreansyah.pinecone.data.local.database.library.AppDatabase
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.repository.dictionary.DictionaryBackupManager
import com.javierreansyah.pinecone.data.repository.backup.LibraryBackupRepository
import com.javierreansyah.pinecone.data.repository.dictionary.DictionaryImportManager
import com.javierreansyah.pinecone.data.repository.dictionary.DictionaryRepository
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import com.javierreansyah.pinecone.worker.WorkerUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

class PineconeApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var libraryRepository: LibraryRepository
        private set

    lateinit var dictionaryRepository: DictionaryRepository
        private set

    lateinit var dictionaryImportManager: DictionaryImportManager
        private set

    lateinit var dictionaryBackupManager: DictionaryBackupManager
        private set

    lateinit var readerPreferences: ReaderPreferences
        private set

    lateinit var publicationOpener: PublicationOpener
        private set

    lateinit var assetRetriever: AssetRetriever
        private set

    override fun onCreate() {
        super.onCreate()

        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "reader_database"
        )
            .addCallback(AppDatabase.BACKUP_REVISION_CALLBACK)
            .fallbackToDestructiveMigration(false)
            .build()

        val httpClient = DefaultHttpClient()

        assetRetriever = AssetRetriever(
            contentResolver = contentResolver,
            httpClient = httpClient
        )

        publicationOpener = PublicationOpener(
            publicationParser = DefaultPublicationParser(
                context = this,
                httpClient = httpClient,
                assetRetriever = assetRetriever,
                pdfFactory = null
            )
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
            assetRetriever = assetRetriever
        )

        // Schedule initial backup based on preferences
        readerPreferences = ReaderPreferences(applicationContext)

        DictionaryBackupManager.recoverInterruptedRestores(applicationContext)

        dictionaryRepository = DictionaryRepository(
            context = applicationContext,
            preferences = readerPreferences
        )

        dictionaryImportManager = DictionaryImportManager(
            context = applicationContext,
            preferences = readerPreferences
        )

        dictionaryBackupManager = DictionaryBackupManager(
            context = applicationContext,
            preferences = readerPreferences
        )

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                LibraryBackupRepository(applicationContext).completePendingSettingsRestore()
            } catch (error: Exception) {
                error.printStackTrace()
            }
            libraryRepository.cleanupOrphanedFiles()
            val initialFrequency = readerPreferences.readerSettings.first().autoBackupFrequency
            WorkerUtils.scheduleBackupWork(applicationContext, initialFrequency)
        }
    }
}
