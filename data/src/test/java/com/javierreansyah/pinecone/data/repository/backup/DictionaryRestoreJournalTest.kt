package com.javierreansyah.pinecone.data.repository.backup

import com.javierreansyah.pinecone.data.local.preferences.InstalledDictionary
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DictionaryRestoreJournalTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun processRestartBeforeDatabaseCommitRestoresPreviousFiles() {
        val files = temporary.newFolder("files")
        val cache = temporary.newFolder("cache")
        val databases = temporary.newFolder("databases")
        val target = File(databases, "dict_old.db").apply { writeText("previous") }
        val staged = File(cache, "replacement.db").apply { writeText("replacement") }
        val added = File(cache, "added.db").apply { writeText("added") }
        val journal = DictionaryRestoreJournal(files, cache) { File(databases, it) }
        journal.swap(listOf(stage("old", staged), stage("new", added)), setOf("old", "new"), setOf("old"))
        assertEquals("replacement", target.readText())

        // Recreate the owner without the in-memory rollback callback, as after process death.
        DictionaryRestoreJournal(files, cache) { File(databases, it) }.recover(false)
        assertEquals("previous", target.readText())
        assertFalse(File(databases, "dict_new.db").exists())
        assertEquals(listOf("dict_old.db"), databases.listFiles()!!.map { it.name })
        assertFalse(File(files, "backup_restore_journal.json").exists())
    }

    @Test fun processRestartAfterDatabaseCommitKeepsNewFilesAndFinalizesRemoval() {
        val files = temporary.newFolder("files")
        val cache = temporary.newFolder("cache")
        val databases = temporary.newFolder("databases")
        File(databases, "dict_removed.db").writeText("removed")
        val staged = File(cache, "replacement.db").apply { writeText("replacement") }
        val journal = DictionaryRestoreJournal(files, cache) { File(databases, it) }
        journal.swap(listOf(stage("new", staged)), setOf("new"), setOf("removed"))
        DictionaryRestoreJournal(files, cache) { File(databases, it) }.recover(true)
        assertEquals("replacement", File(databases, "dict_new.db").readText())
        assertEquals(listOf("dict_new.db"), databases.listFiles()!!.map { it.name })
    }

    @Test fun malformedJournalIsRetainedAndRequiresRecoveryInsteadOfDiscardingEvidence() {
        val files = temporary.newFolder("files")
        val cache = temporary.newFolder("cache")
        val databases = temporary.newFolder("databases")
        val stored = File(files, "backup_restore_journal.json").apply { writeText("damaged") }
        val journal = DictionaryRestoreJournal(files, cache) { File(databases, it) }
        assertThrows(IllegalArgumentException::class.java) { journal.recover(false) }
        assertEquals("damaged", stored.readText())
    }

    @Test fun invalidDictionaryIdentityIsRejectedBeforeAnyFileIsMoved() {
        val files = temporary.newFolder("files")
        val cache = temporary.newFolder("cache")
        val databases = temporary.newFolder("databases")
        val staged = File(cache, "replacement.db").apply { writeText("replacement") }
        val journal = DictionaryRestoreJournal(files, cache) { File(databases, it) }
        assertThrows(IllegalArgumentException::class.java) {
            journal.swap(listOf(stage("../escape", staged)), setOf("../escape"), emptySet())
        }
        assertTrue(staged.isFile)
        assertTrue(databases.listFiles()!!.isEmpty())
        assertFalse(File(files, "backup_restore_journal.json").exists())
    }

    private fun stage(id: String, file: File) = DictionaryStage(InstalledDictionary(id, id, 1), file)
}
