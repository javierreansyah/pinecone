package com.javierreansyah.pinecone.data.repository.backup

sealed interface BackupResult {
    data class Success(
        val snapshotId: String? = null,
        val bytesWritten: Long = 0,
        val bytesReused: Long = 0,
        val durationMillis: Long = 0,
        val phaseDurationsMillis: Map<String, Long> = emptyMap(),
        val warnings: List<BackupFailure> = emptyList()
    ) : BackupResult

    data object Skipped : BackupResult
    data class Partial(val warning: BackupFailure) : BackupResult
    data class Failure(val reason: BackupFailure, val cause: Throwable? = null) : BackupResult

    val isSuccess: Boolean
        get() = this is Success || this is Skipped
}

enum class BackupFailure {
    BACKUP_LOCATION_MISSING,
    PERMISSION_DENIED,
    MALFORMED_ARCHIVE,
    CHECKSUM_MISMATCH,
    UNSUPPORTED_VERSION,
    MISSING_BOOK_FILE,
    MISSING_DICTIONARY,
    INVALID_DATABASE,
    CONCURRENT_CHANGE,
    IO_ERROR
}
