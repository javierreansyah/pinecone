package com.javierreansyah.pinecone.data.model

import com.javierreansyah.pinecone.data.local.database.library.PublicationCreditEntity

fun PublicationCredit.toEntity(): PublicationCreditEntity = PublicationCreditEntity(
    bookId, role, position, name, identifier, metadataJson,
)

fun PublicationCreditEntity.toModel(): PublicationCredit = PublicationCredit(
    bookId, role, position, name, identifier, metadataJson,
)
