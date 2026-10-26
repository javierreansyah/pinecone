package com.javierreansyah.pinecone.data.model

enum class OperationKind { IMPORT, SCAN, READ_MODE }
enum class OperationState(val terminal: Boolean = false) {
    PREPARING, QUEUED, RUNNING, SUCCEEDED(true), PARTIAL(true), FAILED(true), CANCELLED(true), INPUT_REQUIRED(
        true
    );
}

data class OperationSnapshot(
    val id: String,
    val kind: OperationKind,
    val state: OperationState,
    val bookId: String?,
    val completed: Int,
    val total: Int,
    val failures: List<String>,
    val error: String?,
)

/** Identifies the immutable publication and library that produced a reader callback. */
data class ReadingSessionToken(
    val generation: Long,
    val bookId: String,
    val rendition: String,
    val sha256: String
)
