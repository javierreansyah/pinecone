package com.javierreansyah.pinecone.di

import android.content.Context
import com.javierreansyah.pinecone.data.local.preferences.LibraryPreferencesManager
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
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
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @MainDispatcher
    fun mainDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate

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
    @ApplicationScope
    fun applicationScope(@MainDispatcher dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(
            SupervisorJob() + dispatcher + CoroutineExceptionHandler { _, error ->
                Timber.e(error, "Application background operation failed")
            },
        )

}
