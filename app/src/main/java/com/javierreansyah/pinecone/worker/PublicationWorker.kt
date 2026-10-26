package com.javierreansyah.pinecone.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.work.await
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.StartupCoordinator
import com.javierreansyah.pinecone.data.model.OperationSnapshot
import com.javierreansyah.pinecone.data.repository.library.OperationScheduler
import com.javierreansyah.pinecone.data.repository.library.PublicationOperations
import com.javierreansyah.pinecone.di.ApplicationScope
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Singleton
class PublicationWorkScheduler @Inject constructor(@ApplicationContext private val context: Context) : OperationScheduler {
    override suspend fun enqueue(operationId: String) {
        WorkManager.getInstance(context).enqueueUniqueWork("publication-operation:$operationId", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<PublicationWorker>().setInputData(workDataOf("operationId" to operationId))
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS).build()).await()
    }
    override suspend fun cancel(operationId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("publication-operation:$operationId").await()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PublicationWorkModule {
    @Binds abstract fun scheduler(implementation: PublicationWorkScheduler): OperationScheduler
}

@androidx.hilt.work.HiltWorker
class PublicationWorker @dagger.assisted.AssistedInject constructor(
    @dagger.assisted.Assisted context: Context,
    @dagger.assisted.Assisted parameters: WorkerParameters,
    private val operations: PublicationOperations,
    private val startup: StartupCoordinator,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val operationId = inputData.getString("operationId") ?: return Result.failure()
        return try {
            startup.initialize()
            val operation = operations.observe(operationId).first() ?: return Result.success()
            if (operation.state.terminal) return Result.success()
            setForeground(notification(operation))
            when (operations.execute(operationId, lastAttempt = runAttemptCount >= 2) { setForeground(notification(it)) }) {
                PublicationOperations.Execution.COMPLETE -> Result.success()
                PublicationOperations.Execution.RETRY -> Result.retry()
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Exception) {
            timber.log.Timber.e(error, "Publication worker could not start")
            if (runAttemptCount < 2) Result.retry() else {
                operations.fail(operationId, "background_unavailable")
                Result.failure()
            }
        }
    }

    private fun notification(operation: OperationSnapshot): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("publication-work", applicationContext.getString(R.string.publication_work_channel), NotificationManager.IMPORTANCE_LOW))
        val intent = Intent(applicationContext, CancelPublicationReceiver::class.java)
            .setData(Uri.parse("pinecone:operation:${operation.id}")).putExtra("operationId", operation.id)
        val cancel = PendingIntent.getBroadcast(applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, "publication-work")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(applicationContext.getString(if (operation.bookId == null) R.string.publication_work_importing else R.string.publication_work_converting))
            .setContentText(applicationContext.getString(R.string.publication_work_progress, operation.completed, operation.total))
            .setProgress(operation.total, operation.completed, operation.total == 0).setOngoing(true)
            .addAction(android.R.drawable.ic_delete, applicationContext.getString(android.R.string.cancel), cancel).build()
        return ForegroundInfo(operation.id.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
}

@AndroidEntryPoint
class CancelPublicationReceiver : BroadcastReceiver() {
    @Inject lateinit var operations: PublicationOperations
    @Inject @ApplicationScope lateinit var scope: CoroutineScope
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("operationId") ?: return
        val pending = goAsync()
        scope.launch {
            try { operations.cancel(id) } finally { pending.finish() }
        }
    }
}
