package com.javierreansyah.pinecone.di

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.javierreansyah.pinecone.data.local.database.library.AppDatabase
import com.javierreansyah.pinecone.data.local.preferences.LibraryPreferencesManager
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.repository.backup.BackupRepository
import com.javierreansyah.pinecone.data.repository.dictionary.DictionaryImportManager
import com.javierreansyah.pinecone.data.repository.dictionary.DictionaryRepository
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import com.javierreansyah.pinecone.reader.audio.AudioReadingSession
import com.javierreansyah.pinecone.reader.pdf.MuPdfDocumentFactory
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import timber.log.Timber
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @MainDispatcher
    fun mainDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "publication_library")
            .addCallback(AppDatabase.BACKUP_REVISION_CALLBACK)
            .fallbackToDestructiveMigration(dropAllTables = false)
            .build()

    @Provides
    @Singleton
    fun readerPreferences(@ApplicationContext context: Context): ReaderPreferences =
        ReaderPreferences(context)

    @Provides
    @Singleton
    fun libraryPreferences(
        @ApplicationContext context: Context,
        preferences: ReaderPreferences,
    ): LibraryPreferencesManager = LibraryPreferencesManager(context, preferences)

    @Provides
    @Singleton
    fun httpClient(): DefaultHttpClient = DefaultHttpClient()

    @Provides
    @Singleton
    fun assetRetriever(
        @ApplicationContext context: Context,
        httpClient: DefaultHttpClient
    ): AssetRetriever =
        AssetRetriever(contentResolver = context.contentResolver, httpClient = httpClient)

    @Provides
    @Singleton
    fun publicationOpener(
        @ApplicationContext context: Context,
        httpClient: DefaultHttpClient,
        assetRetriever: AssetRetriever,
    ): PublicationOpener = PublicationOpener(
        publicationParser = DefaultPublicationParser(
            context = context,
            httpClient = httpClient,
            assetRetriever = assetRetriever,
            pdfFactory = MuPdfDocumentFactory(),
        ),
    )

    @Provides
    @Singleton
    fun libraryRepository(
        @ApplicationContext context: Context,
        database: AppDatabase,
        publicationOpener: PublicationOpener,
        assetRetriever: AssetRetriever,
        preferences: ReaderPreferences,
        audioSession: Lazy<AudioReadingSession>,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        @MainDispatcher mainDispatcher: CoroutineDispatcher,
    ): LibraryRepository = LibraryRepository(
        context,
        database,
        database.bookDao(),
        database.bookmarkDao(),
        database.shelfDao(),
        database.noteDao(),
        database.spaceDao(),
        publicationOpener,
        assetRetriever,
        preferences,
        audioSession,
        ioDispatcher,
        mainDispatcher,
    )

    @Provides
    @Singleton
    fun dictionaryRepository(
        @ApplicationContext context: Context,
        preferences: ReaderPreferences
    ): DictionaryRepository =
        DictionaryRepository(context, preferences)

    @Provides
    @Singleton
    fun dictionaryImportManager(
        @ApplicationContext context: Context,
        preferences: ReaderPreferences
    ): DictionaryImportManager =
        DictionaryImportManager(context, preferences)

    @Provides
    @Singleton
    fun backupRepository(
        @ApplicationContext context: Context,
        preferences: ReaderPreferences,
        database: AppDatabase,
        libraryRepository: LibraryRepository,
        audioSession: AudioReadingSession,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        @MainDispatcher mainDispatcher: CoroutineDispatcher,
    ): BackupRepository = BackupRepository(
        context,
        preferences,
        database,
        libraryRepository,
        audioSession,
        ioDispatcher,
        mainDispatcher,
    )

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(@MainDispatcher dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(
            SupervisorJob() + dispatcher + CoroutineExceptionHandler { _, error ->
                Timber.e(error, "Application background operation failed")
            },
        )

    @Provides
    @Singleton
    fun audioSession(
        application: Application,
        libraryRepository: LibraryRepository,
        preferences: ReaderPreferences,
        @ApplicationScope scope: CoroutineScope,
    ): AudioReadingSession = AudioReadingSession(application, libraryRepository, preferences, scope)
}
