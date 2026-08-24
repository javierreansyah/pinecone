package com.javierreansyah.pinecone.data.model

import com.javierreansyah.pinecone.data.local.preferences.InstalledDictionary
import com.javierreansyah.pinecone.data.local.preferences.ReaderSettings
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
enum class VaultObjectKind { BOOK, COVER, DICTIONARY }

@Serializable
data class VaultObject(
    val kind: VaultObjectKind,
    val sha256: String,
    val size: Long,
    val crc32: Long,
    val storedSize: Long,
    val storedCrc32: Long,
    val fileName: String,
    val sourcePath: String,
    val sourceSize: Long,
    val sourceLastModified: Long
)

@Serializable
data class BookObjectReference(
    val bookId: String,
    val book: VaultObject,
    val cover: VaultObject? = null
)

@Serializable
data class DictionaryObjectReference(
    val dictionary: InstalledDictionary,
    val objectInfo: VaultObject
)

@Serializable
data class VaultSnapshot(
    val format: String = "pinecone-vault-snapshot",
    val formatVersion: Int = 1,
    val id: String,
    val createdAt: Long,
    val backupKind: String,
    val appVersion: String,
    val databaseRevision: Long,
    val stateFingerprint: String,
    val recordCounts: Map<String, Int>,
    val library: LibraryBackupPayload,
    val settings: ReaderSettings,
    val books: List<BookObjectReference>,
    val dictionaries: List<DictionaryObjectReference>,
    val activeDictionaryId: String
)

@Serializable
data class VaultFormat(
    val format: String = "pinecone-backup-vault",
    val formatVersion: Int = 1
)

@Serializable
data class PortableBackupManifest(
    val format: String = "pinecone-portable-backup",
    val formatVersion: Int = 1,
    val snapshot: VaultSnapshot
)
