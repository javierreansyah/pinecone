package com.javierreansyah.pinecone.testing

import com.javierreansyah.pinecone.data.repository.library.ReadingRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Debug-only access for device tests exercising the real application graph. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReadingTestEntryPoint {
    fun readingRepository(): ReadingRepository
    fun publications(): com.javierreansyah.pinecone.data.repository.library.PublicationRepository
    fun operations(): com.javierreansyah.pinecone.data.repository.library.PublicationOperations
    fun database(): com.javierreansyah.pinecone.data.local.database.library.AppDatabase
}
