package com.javierreansyah.pinecone.data.repository.backup

import android.content.Context
import com.javierreansyah.pinecone.data.local.database.dictionary.validDictionaryId
import com.javierreansyah.pinecone.data.local.preferences.InstalledDictionary
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

internal data class DictionaryStage(val dictionary: InstalledDictionary, val file: File)

/** Caller holds backup serialization and DictionaryStore exclusive access throughout the swap. */
@Singleton
class DictionaryRestoreJournal internal constructor(
    private val filesDirectory: File,
    private val stagingDirectory: File,
    private val databasePath: (String) -> File,
) {
    @Inject constructor(@ApplicationContext context: Context) : this(
        context.filesDir, context.cacheDir, context::getDatabasePath,
    )

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false; isLenient = false }

    @kotlinx.serialization.Serializable
    private data class RestoreJournal(
        val entries: List<RestoreJournalEntry>,
    )

    @kotlinx.serialization.Serializable
    private data class RestoreJournalEntry(
        val targetPath: String,
        val rollbackPath: String,
        val stagedPath: String?,
        val hadOriginal: Boolean,
    )

    internal class DictionaryRollback(private val finish: (Boolean) -> Unit) {
        fun commit() {
            finish(true)
        }

        fun rollback() {
            finish(false)
        }
    }

    internal fun swap(
        stages: List<DictionaryStage>,
        wantedIds: Set<String>,
        currentIds: Set<String>,
    ): DictionaryRollback {
        val allIds = currentIds + wantedIds
        require(allIds.all(::validDictionaryId)) { "Invalid dictionary identity" }
        val entries = allIds.map { id ->
            val target = databasePath("dict_$id.db")
            val stage = stages.firstOrNull { (itDictionary6, _) -> itDictionary6.id == id }?.file
            RestoreJournalEntry(
                targetPath = target.absolutePath,
                rollbackPath = File(
                    target.parentFile,
                    "${target.name}.rollback_${UUID.randomUUID()}",
                ).absolutePath,
                stagedPath = stage?.absolutePath,
                hadOriginal = target.exists(),
            )
        }
        val journal = RestoreJournal(entries = entries)
        writeRestoreJournal(journal)
        val rollback = DictionaryRollback { commit ->
            finishJournal(journal, commit)
            restoreJournalFile().delete()
        }
        return try {
            for ((entryTargetPath, entryRollbackPath, entryStagedPath, entryHadOriginal) in entries) {
                val target = File(entryTargetPath)
                if (entryHadOriginal) check(target.renameTo(File(entryRollbackPath)))
                databasePath("${target.name}-wal").delete()
                databasePath("${target.name}-shm").delete()
                entryStagedPath?.let { stagedPath ->
                    target.parentFile?.mkdirs(); check(File(stagedPath).renameTo(target))
                }
            }
            rollback
        } catch (error: Throwable) {
            rollback.rollback(); throw error
        }
    }

    internal fun recover(databaseCommitted: Boolean) {
        val file = restoreJournalFile()
        if (!file.isFile) return
        val journal = json.decodeFromString<RestoreJournal>(file.readText())
        finishJournal(journal, databaseCommitted)
        file.delete()
    }

    private fun validateJournal(journal: RestoreJournal) {
        // Validate every path before performing any recovery mutation.
        journal.entries.forEach { entry ->
            val target = File(entry.targetPath).canonicalFile
            require(target.name.startsWith("dict_") && target.name.endsWith(".db"))
            require(validDictionaryId(target.name.removePrefix("dict_").removeSuffix(".db")))
            require(target == databasePath(target.name).canonicalFile)
            val rollback = File(entry.rollbackPath).canonicalFile
            require(rollback.parentFile == target.parentFile && rollback.name.startsWith("${target.name}.rollback_"))
            entry.stagedPath?.let { path ->
                require(File(path).canonicalPath.startsWith(stagingDirectory.canonicalPath + File.separator))
            }
        }
    }

    private fun finishJournal(journal: RestoreJournal, commit: Boolean) {
        validateJournal(journal)
        journal.entries.asReversed()
            .forEach { (entryTargetPath2, entryRollbackPath2, entryStagedPath2, entryHadOriginal2) ->
                val target = File(entryTargetPath2)
                val rollback = File(entryRollbackPath2)
                val staged = entryStagedPath2?.let(::File)
                if (commit) {
                    rollback.delete()
                } else if (rollback.exists()) {
                    target.delete(); check(rollback.renameTo(target))
                } else if (!entryHadOriginal2) {
                    target.delete()
                }
                staged?.delete()
            }
    }

    private fun writeRestoreJournal(journal: RestoreJournal) {
        validateJournal(journal)
        val target = restoreJournalFile()
        check(!target.exists()) { "A previous dictionary restore still needs recovery" }
        val partial = File(target.parentFile, "${target.name}.partial")
        FileOutputStream(partial).use {
            it.write(json.encodeToString(journal).toByteArray(Charsets.UTF_8))
            it.fd.sync()
        }
        check(partial.renameTo(target))
    }

    private fun restoreJournalFile() = File(filesDirectory, "backup_restore_journal.json")

}
