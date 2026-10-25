package com.javierreansyah.pinecone.ui.root

import androidx.lifecycle.ViewModel
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import com.javierreansyah.pinecone.reader.audio.AudioReadingSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Dependencies shared by route-level navigation and the persistent audio controls. */
@HiltViewModel
class AppServicesViewModel @Inject constructor(
    val readerPreferences: ReaderPreferences,
    val libraryRepository: LibraryRepository,
    val audioSession: AudioReadingSession,
) : ViewModel()
