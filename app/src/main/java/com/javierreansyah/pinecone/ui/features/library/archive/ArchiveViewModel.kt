package com.javierreansyah.pinecone.ui.features.library.archive

import com.javierreansyah.pinecone.data.local.preferences.LibraryPreferencesManager
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import com.javierreansyah.pinecone.ui.features.library.LibraryCollectionViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ArchiveViewModel @Inject constructor(
    repository: LibraryRepository,
    preferences: LibraryPreferencesManager,
) : LibraryCollectionViewModel(
    bookRepository = repository,
    prefsManager = preferences,
    screenKey = "archive",
    source = LibraryRepository::getArchivedBooks,
)
