package com.javierreansyah.pinecone.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.javierreansyah.pinecone.PineconeApplication
import com.javierreansyah.pinecone.data.repository.backup.LibraryBackupRepository

class BackupWorker(
    appContext: Context, workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val libraryBackupRepository = LibraryBackupRepository(applicationContext)
        val dictionaryBackupManager =
            (applicationContext as PineconeApplication).dictionaryBackupManager

        val libSuccess = libraryBackupRepository.performBackup(force = false)
        val dictSuccess = dictionaryBackupManager.backupDictionaries()

        return if (libSuccess && dictSuccess) {
            Result.success()
        } else {
            Result.failure()
        }
    }
}
