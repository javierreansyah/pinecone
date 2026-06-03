package com.example.readerapp.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkerUtils {
    const val BACKUP_WORK_NAME = "automated_backup_work"

    fun scheduleBackupWork(context: Context, frequencyString: String) {
        val workManager = WorkManager.getInstance(context)

        if (frequencyString == "never") {
            workManager.cancelUniqueWork(BACKUP_WORK_NAME)
            return
        }

        val repeatInterval = when (frequencyString) {
            "3h" -> 3L to TimeUnit.HOURS
            "6h" -> 6L to TimeUnit.HOURS
            "12h" -> 12L to TimeUnit.HOURS
            "1d" -> 1L to TimeUnit.DAYS
            "2d" -> 2L to TimeUnit.DAYS
            "3d" -> 3L to TimeUnit.DAYS
            "1w" -> 7L to TimeUnit.DAYS
            else -> 12L to TimeUnit.HOURS
        }

        val constraints = Constraints.Builder()
            .setRequiresStorageNotLow(true)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<BackupWorker>(
            repeatInterval.first, repeatInterval.second
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            BACKUP_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE, // Update if exists
            workRequest
        )
    }
}
