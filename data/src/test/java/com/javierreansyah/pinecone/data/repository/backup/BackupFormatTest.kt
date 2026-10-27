package com.javierreansyah.pinecone.data.repository.backup

import kotlinx.serialization.json.Json
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupFormatTest {
    @Test
    fun futureVersionsAreRejectedBeforeDecodingUnknownPayloads() {
        listOf(
            """{"format":"pinecone-backup-vault","formatVersion":3,"future":{}}""",
            """{"format":"pinecone-portable-backup","formatVersion":3,"future":{}}""",
            """{"format":"pinecone-vault-snapshot","formatVersion":3,"future":{}}""",
            """{"format":"pinecone-vault-snapshot","formatVersion":2,"library":{"version":3,"future":{}}}""",
            """{"format":"pinecone-portable-backup","formatVersion":2,"snapshot":{"format":"pinecone-vault-snapshot","formatVersion":3,"future":{}}}""",
        ).forEach { text ->
            val format = Json.parseToJsonElement(text).let {
                (it as kotlinx.serialization.json.JsonObject).getValue("format")
                    .toString().trim('"')
            }
            assertThrows(UnsupportedBackupException::class.java) {
                checkBackupFormat(Json, text, format)
            }
        }
    }

    @Test
    fun missingVersionsAndWrongFormatsAreMalformed() {
        assertThrows(IllegalArgumentException::class.java) {
            checkBackupFormat(Json, """{"format":"pinecone-backup-vault"}""", "pinecone-backup-vault")
        }
        assertThrows(IllegalArgumentException::class.java) {
            checkBackupFormat(Json, """{"format":"other","formatVersion":2}""", "pinecone-backup-vault")
        }
    }

    @Test
    fun currentHeadersAreAccepted() {
        checkBackupFormat(Json, """{"format":"pinecone-backup-vault","formatVersion":2}""", "pinecone-backup-vault")
        checkBackupFormat(Json, """{"format":"pinecone-vault-snapshot","formatVersion":2,"library":{"version":2}}""", "pinecone-vault-snapshot")
    }
}
