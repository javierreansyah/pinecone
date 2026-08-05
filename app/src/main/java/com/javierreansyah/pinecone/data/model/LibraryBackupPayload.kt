package com.javierreansyah.pinecone.data.model


import kotlinx.serialization.Serializable

@Serializable
data class LibraryBackupPayload(
    val version: Int = 1,
    val books: List<BookBackupRecord>,
    val bookmarks: List<BookmarkBackupRecord>,
    val shelves: List<ShelfBackupRecord>,
    val shelfBookCrossRefs: List<ShelfBookBackupRecord>,
    val notes: List<NoteBackupRecord>,
    val authors: List<AuthorBackupRecord> = emptyList(),
    val tags: List<TagBackupRecord> = emptyList(),
    val bookAuthorCrossRefs: List<BookAuthorBackupRecord> = emptyList(),
    val bookTagCrossRefs: List<BookTagBackupRecord> = emptyList(),
    val spaces: List<SpaceBackupRecord> = emptyList(),
    val bookSpaceCrossRefs: List<BookSpaceBackupRecord> = emptyList()
)

@Serializable
data class BackupEntry(
    val path: String,
    val size: Long,
    val sha256: String
)

@Serializable
data class DictionaryReference(
    val id: String,
    val name: String,
    val sha256: String = "",
    val fileName: String = ""
)

@Serializable
data class LibraryBackupManifest(
    val format: String = "pinecone-library",
    val formatVersion: Int = 1,
    val createdAt: Long,
    val backupKind: String,
    val appVersion: String,
    val databaseRevision: Long,
    val recordCounts: Map<String, Int>,
    val entries: List<BackupEntry>,
    val dictionaries: List<DictionaryReference> = emptyList(),
    val activeDictionaryId: String = ""
)
