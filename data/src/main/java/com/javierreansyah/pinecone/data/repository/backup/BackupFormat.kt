package com.javierreansyah.pinecone.data.repository.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Inspect versions before payload decoding so newer fields cannot hide an unsupported format. */
internal fun checkBackupFormat(json: Json, text: String, format: String): JsonObject {
    val root = requireNotNull(json.parseToJsonElement(text) as? JsonObject) { "Expected backup object" }
    require(root["format"]?.jsonPrimitive?.content == format)
    fun checkVersion(record: JsonObject, key: String) {
        val version = requireNotNull(record[key]?.jsonPrimitive?.intOrNull) { "Missing $key" }
        if (version != 2) throw UnsupportedBackupException()
    }
    checkVersion(root, "formatVersion")
    val snapshot = if (format == "pinecone-portable-backup") {
        requireNotNull(root["snapshot"] as? JsonObject)
    } else root
    if (format != "pinecone-backup-vault") {
        require(snapshot["format"]?.jsonPrimitive?.content == "pinecone-vault-snapshot")
        checkVersion(snapshot, "formatVersion")
        checkVersion(requireNotNull(snapshot["library"] as? JsonObject), "version")
    }
    return root
}
