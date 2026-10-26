package com.javierreansyah.pinecone.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.javierreansyah.pinecone.data.repository.backup.BackupRepository
import com.javierreansyah.pinecone.data.repository.backup.BackupResult

@androidx.hilt.work.HiltWorker
class BackupWorker @dagger.assisted.AssistedInject constructor(
    @dagger.assisted.Assisted appContext: Context,
    @dagger.assisted.Assisted workerParams: WorkerParameters,
    private val backupRepository: BackupRepository,
    private val startup: com.javierreansyah.pinecone.data.StartupCoordinator,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        try {
            startup.initialize()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return Result.retry()
        }
        return when (val result =
            backupRepository.createSnapshot(manual = false)) {
            is BackupResult.Success, BackupResult.Skipped -> Result.success()
            is BackupResult.Partial -> Result.success()
            is BackupResult.Failure -> retryOrFail(result)
        }
    }

    private fun retryOrFail(failure: BackupResult.Failure): Result = when (failure.reason) {
        com.javierreansyah.pinecone.data.repository.backup.BackupFailure.IO_ERROR,
        com.javierreansyah.pinecone.data.repository.backup.BackupFailure.CONCURRENT_CHANGE,
            ->
            Result.retry()

        else -> Result.failure()
    }
}
