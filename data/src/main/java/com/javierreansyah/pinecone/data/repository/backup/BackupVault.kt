package com.javierreansyah.pinecone.data.repository.backup

import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.model.VaultFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

internal class UnsupportedBackupException : IllegalArgumentException()

internal data class VaultDirs(
    val vault: DocumentFile,
    val snapshots: DocumentFile,
    val books: DocumentFile,
    val covers: DocumentFile,
    val dictionaries: DocumentFile,
)

@Singleton
class BackupVault @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: ReaderPreferences,
) {
    private val resolver get() = context.contentResolver
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false; isLenient = false }

    internal suspend fun open(create: Boolean): VaultDirs? {
        val settings = preferences.readerSettings.first()
        val root = settings.backupFolderUri.takeIf { it.isNotBlank() }?.toUri()
            ?.let { DocumentFile.fromTreeUri(context, it) } ?: return null
        if (!root.canRead() || create && !root.canWrite()) return null
        val vault = childDir(root, VAULT_DIR, create) ?: return null
        val snapshots = childDir(vault, "snapshots", create) ?: return null
        val objects = childDir(vault, "objects", create) ?: return null
        val books = childDir(objects, "books", create) ?: return null
        val covers = childDir(objects, "covers", create) ?: return null
        val dictionaries = childDir(objects, "dictionaries", create) ?: return null
        if (create) ensureFormat(vault)
        else validateFormat(vault)
        return VaultDirs(vault, snapshots, books, covers, dictionaries)
    }

    private fun childDir(parent: DocumentFile, name: String, create: Boolean): DocumentFile? =
        parent.findFile(name)?.takeIf { it.isDirectory }
            ?: if (create) parent.createDirectory(name) else null

    private fun ensureFormat(vault: DocumentFile) {
        val existing = vault.findFile("format.json")
        if (existing != null) {
            validateFormat(vault)
            return
        }
        val bytes = json.encodeToString(VaultFormat()).toByteArray()
        val partial = vault.createFile("application/json", "format.json.partial")
            ?: error("Cannot create vault format")
        try {
            resolver.openOutputStream(partial.uri, "wt")?.use { it.write(bytes) }
                ?: error("Cannot write vault format")
            val decoded = resolver.openInputStream(partial.uri)?.use {
                json.decodeFromString<VaultFormat>(it.readBytes().toString(Charsets.UTF_8))
            } ?: error("Cannot verify vault format")
            require(decoded == VaultFormat())
            check(partial.renameTo("format.json"))
        } finally {
            vault.findFile("format.json.partial")?.delete()
        }
    }

    private fun validateFormat(vault: DocumentFile) {
        val file = vault.findFile("format.json") ?: error("Missing vault format")
        val (formatFormat, formatFormatVersion) = resolver.openInputStream(file.uri)?.use {
            json.decodeFromString<VaultFormat>(it.readBytes().toString(Charsets.UTF_8))
        } ?: error("Cannot read vault format")
        require(formatFormat == VAULT_FORMAT)
        if (formatFormatVersion != FORMAT_VERSION) throw UnsupportedBackupException()
    }


    private companion object {
        const val VAULT_DIR = "PineconeBackup"
        const val VAULT_FORMAT = "pinecone-backup-vault"
        const val FORMAT_VERSION = 2
    }
}
