package com.javierreansyah.pinecone.data.repository.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

internal const val VAULT_FORMAT_VERSION = 2
internal const val LEGACY_SNAPSHOT_FORMAT_VERSION = 2
internal const val CURRENT_SNAPSHOT_FORMAT_VERSION = 3
internal const val LIBRARY_PAYLOAD_VERSION = 2
internal val SUPPORTED_SNAPSHOT_FORMAT_VERSIONS =
    LEGACY_SNAPSHOT_FORMAT_VERSION..CURRENT_SNAPSHOT_FORMAT_VERSION

/** Inspect versions before payload decoding so newer fields cannot hide an unsupported format. */
internal fun checkBackupFormat(json: Json, text: String, format: String): JsonObject {
    val root =
        requireNotNull(json.parseToJsonElement(text) as? JsonObject) { "Expected backup object" }
    require(root["format"]?.jsonPrimitive?.content == format)
    fun readVersion(record: JsonObject, key: String): Int =
        requireNotNull(record[key]?.jsonPrimitive?.intOrNull) { "Missing $key" }

    val rootVersion = readVersion(root, "formatVersion")
    if (format == "pinecone-backup-vault") {
        if (rootVersion != VAULT_FORMAT_VERSION) throw UnsupportedBackupException()
        return root
    }
    if (rootVersion !in SUPPORTED_SNAPSHOT_FORMAT_VERSIONS) throw UnsupportedBackupException()
    val snapshot = if (format == "pinecone-portable-backup") {
        requireNotNull(root["snapshot"] as? JsonObject)
    } else root
    require(snapshot["format"]?.jsonPrimitive?.content == "pinecone-vault-snapshot")
    val snapshotVersion = readVersion(snapshot, "formatVersion")
    if (snapshotVersion !in SUPPORTED_SNAPSHOT_FORMAT_VERSIONS || snapshotVersion > rootVersion) {
        throw UnsupportedBackupException()
    }
    val libraryVersion = readVersion(requireNotNull(snapshot["library"] as? JsonObject), "version")
    if (libraryVersion != LIBRARY_PAYLOAD_VERSION) throw UnsupportedBackupException()
    return root
}
