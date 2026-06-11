package com.example.readerapp

import android.app.Application
import androidx.room.Room
import com.example.readerapp.data.local.database.library.AppDatabase
import com.example.readerapp.data.local.preferences.ReaderPreferences
import com.example.readerapp.data.repository.dictionary.DictionaryBackupManager
import com.example.readerapp.data.repository.dictionary.DictionaryImportManager
import com.example.readerapp.data.repository.dictionary.DictionaryRepository
import com.example.readerapp.data.repository.library.LibraryRepository
import com.example.readerapp.worker.WorkerUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

class ReaderApplication : Application() {

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
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_8_9
            )
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
            publicationOpener = publicationOpener,
            assetRetriever = assetRetriever
        )

        // Schedule initial backup based on preferences
        readerPreferences = ReaderPreferences(applicationContext)

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
            val initialFrequency = readerPreferences.readerSettings.first().autoBackupFrequency
            WorkerUtils.scheduleBackupWork(applicationContext, initialFrequency)
        }
    }
}
