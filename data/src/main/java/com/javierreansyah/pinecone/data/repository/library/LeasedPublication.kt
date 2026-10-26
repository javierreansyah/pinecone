package com.javierreansyah.pinecone.data.repository.library

import com.javierreansyah.pinecone.data.model.ReadingSessionToken
import org.readium.r2.shared.publication.Publication

internal class LeasedPublication(private val lease: AutoCloseable, val token: ReadingSessionToken) :
    Publication.Service {
    override fun close() = lease.close()
}

fun Publication.readingSessionToken(): ReadingSessionToken? =
    findService(LeasedPublication::class)?.token
