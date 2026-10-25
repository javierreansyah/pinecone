package com.javierreansyah.pinecone.di

import android.content.Context
import androidx.room.Room
import com.javierreansyah.pinecone.data.local.database.library.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "publication_library")
            .addCallback(AppDatabase.BACKUP_REVISION_CALLBACK)
            .fallbackToDestructiveMigration(dropAllTables = false)
            .build()

    @Provides fun bookDao(database: AppDatabase) = database.bookDao()
    @Provides fun bookmarkDao(database: AppDatabase) = database.bookmarkDao()
    @Provides fun shelfDao(database: AppDatabase) = database.shelfDao()
    @Provides fun noteDao(database: AppDatabase) = database.noteDao()
    @Provides fun spaceDao(database: AppDatabase) = database.spaceDao()
}
