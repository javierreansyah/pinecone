package com.javierreansyah.pinecone.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.javierreansyah.pinecone.PineconeApplication
import com.javierreansyah.pinecone.data.repository.backup.LibraryBackupRepository
import com.javierreansyah.pinecone.data.repository.backup.BackupResult

class BackupWorker(
    appContext: Context, workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val libraryBackupRepository = LibraryBackupRepository(applicationContext)
        val dictionaryBackupManager =
            (applicationContext as PineconeApplication).dictionaryBackupManager

        val dictionaryResult = dictionaryBackupManager.backupDictionariesResult()
        if (dictionaryResult is BackupResult.Failure) return retryOrFail(dictionaryResult)
        val libraryResult = libraryBackupRepository.performBackupResult(force = false)
        return when (libraryResult) {
            is BackupResult.Success, BackupResult.Skipped -> Result.success()
            is BackupResult.Partial -> Result.success()
            is BackupResult.Failure -> retryOrFail(libraryResult)
        }
    }

    private fun retryOrFail(failure: BackupResult.Failure): Result = when (failure.reason) {
        com.javierreansyah.pinecone.data.repository.backup.BackupFailure.IO_ERROR,
        com.javierreansyah.pinecone.data.repository.backup.BackupFailure.CONCURRENT_CHANGE ->
            Result.retry()
        else -> Result.failure()
    }
}
