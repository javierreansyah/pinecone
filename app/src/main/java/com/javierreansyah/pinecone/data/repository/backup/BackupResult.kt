package com.javierreansyah.pinecone.data.repository.backup

sealed interface BackupResult {
    data class Success(val fileName: String? = null) : BackupResult
    data object Skipped : BackupResult
    data class Partial(val warning: BackupFailure) : BackupResult
    data class Failure(val reason: BackupFailure, val cause: Throwable? = null) : BackupResult

    val isSuccess: Boolean
        get() = this is Success || this is Skipped
}

enum class BackupFailure {
    BACKUP_LOCATION_MISSING,
    PERMISSION_DENIED,
    INSUFFICIENT_SPACE,
    MALFORMED_ARCHIVE,
    CHECKSUM_MISMATCH,
    UNSUPPORTED_VERSION,
    INVALID_RELATIONSHIP,
    MISSING_BOOK_FILE,
    MISSING_DICTIONARY,
    CONCURRENT_CHANGE,
    IO_ERROR
}
