package com.javierreansyah.pinecone

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.StartupCoordinator
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import com.javierreansyah.pinecone.di.ApplicationScope
import com.javierreansyah.pinecone.di.IoDispatcher
import com.javierreansyah.pinecone.worker.WorkerUtils
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class PineconeApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var libraryRepository: LibraryRepository
    @Inject
    lateinit var readerPreferences: ReaderPreferences
    @Inject
    lateinit var startup: StartupCoordinator
    @Inject lateinit var operations: com.javierreansyah.pinecone.data.repository.library.PublicationOperations
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope
    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Timber.plant(Timber.DebugTree())
        }
        applicationScope.launch {
            try {
                startup.initialize()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Entry points expose recovery/retry; never continue cleanup after failed recovery.
            }
        }
        applicationScope.launch {
            startup.awaitReady()
            operations.reconcile()
            libraryRepository.cleanupOrphanedFiles()
        }
        applicationScope.launch {
            startup.awaitReady()
            readerPreferences.readerSettings.map { it.autoBackupFrequency }.distinctUntilChanged()
                .collect { WorkerUtils.scheduleBackupWork(applicationContext, it) }
        }
        applicationScope.launch {
            startup.awaitReady()
            readerPreferences.readerSettings.map { it.unsortedEnabled }.distinctUntilChanged()
                .collect { enabled ->
                    if (!enabled) withContext(ioDispatcher) { libraryRepository.releaseUnsortedBooks() }
                }
        }
    }
}
