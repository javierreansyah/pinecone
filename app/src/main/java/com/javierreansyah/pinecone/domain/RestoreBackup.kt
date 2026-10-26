package com.javierreansyah.pinecone.domain

import android.net.Uri
import com.javierreansyah.pinecone.data.repository.backup.BackupFailure
import com.javierreansyah.pinecone.data.repository.backup.BackupRepository
import com.javierreansyah.pinecone.data.repository.backup.BackupResult
import com.javierreansyah.pinecone.reader.audio.AudioPlaybackClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import javax.inject.Inject

class RestoreBackup @Inject constructor(
    private val repository: BackupRepository,
    private val playback: AudioPlaybackClient,
) {
    suspend operator fun invoke(uri: Uri): BackupResult = try {
        playback.withPlaybackStopped { repository.restoreSnapshot(uri) }
    } catch (timeout: TimeoutCancellationException) {
        BackupResult.Failure(BackupFailure.IO_ERROR, timeout)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        BackupResult.Failure(BackupFailure.IO_ERROR, error)
    }
}
