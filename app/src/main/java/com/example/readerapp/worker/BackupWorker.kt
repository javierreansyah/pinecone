package com.example.readerapp.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.readerapp.data.repository.backup.LibraryBackupRepository

class BackupWorker(
    appContext: Context, workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val libraryBackupRepository = LibraryBackupRepository(applicationContext)
        val success = libraryBackupRepository.performBackup(force = false)
        return if (success) {
            Result.success()
        } else {
            Result.failure()
        }
    }
}
