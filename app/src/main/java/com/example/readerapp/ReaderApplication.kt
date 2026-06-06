package com.example.readerapp

import android.app.Application
import androidx.room.Room
import com.example.readerapp.data.local.AppDatabase
import com.example.readerapp.data.repository.BookRepository
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.streamer.parser.DefaultPublicationParser
import com.example.readerapp.worker.WorkerUtils
import com.example.readerapp.data.local.ReaderPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReaderApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var bookRepository: BookRepository
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
            AppDatabase.MIGRATION_5_6
        )
        .fallbackToDestructiveMigration()
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

        bookRepository = BookRepository(
            context = applicationContext,
            bookDao = database.bookDao(),
            bookmarkDao = database.bookmarkDao(),
            shelfDao = database.shelfDao(),
            noteDao = database.noteDao(),
            publicationOpener = publicationOpener,
            assetRetriever = assetRetriever
        )

        // Schedule initial backup based on preferences
        val readerPreferences = ReaderPreferences(applicationContext)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val initialFrequency = readerPreferences.readerSettings.first().autoBackupFrequency
            WorkerUtils.scheduleBackupWork(applicationContext, initialFrequency)
        }
    }
}
