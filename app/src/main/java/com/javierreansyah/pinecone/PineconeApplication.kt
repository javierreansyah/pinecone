package com.javierreansyah.pinecone

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.javierreansyah.pinecone.data.local.database.library.AppDatabase
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.repository.backup.BackupRepository
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import com.javierreansyah.pinecone.di.ApplicationScope
import com.javierreansyah.pinecone.reader.audio.AudioReadingSession
import com.javierreansyah.pinecone.worker.WorkerUtils
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import com.javierreansyah.pinecone.di.IoDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltAndroidApp
class PineconeApplication : Application(), Configuration.Provider {
    @Inject lateinit var database: AppDatabase
    @Inject lateinit var libraryRepository: LibraryRepository
    @Inject lateinit var readerPreferences: ReaderPreferences
    @Inject lateinit var audioSession: AudioReadingSession
    @Inject lateinit var backupRepository: BackupRepository
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope
    @Inject @IoDispatcher lateinit var ioDispatcher: CoroutineDispatcher

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Timber.plant(Timber.DebugTree())
        }
        applicationScope.launch {
            withContext(ioDispatcher) {
                try {
                    backupRepository.completePendingSettingsRestore()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Timber.e(error, "Could not complete pending settings restore")
                }
                libraryRepository.cleanupOrphanedFiles()
                WorkerUtils.scheduleBackupWork(
                    applicationContext, readerPreferences.readerSettings.first().autoBackupFrequency,
                )
                readerPreferences.readerSettings.collect { settings ->
                    if (!settings.unsortedEnabled) libraryRepository.releaseUnsortedBooks()
                }
            }
        }
    }
}
