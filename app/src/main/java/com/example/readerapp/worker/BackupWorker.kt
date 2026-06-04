package com.example.readerapp.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.readerapp.data.repository.BackupRepository

class BackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val backupRepository = BackupRepository(applicationContext)
        val success = backupRepository.performBackup(force = false)
        return if (success) {
            Result.success()
        } else {
            Result.failure()
        }
    }
}
