package com.javierreansyah.pinecone.ui.features.library.archive

import com.javierreansyah.pinecone.data.local.preferences.LibraryPreferencesManager
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import com.javierreansyah.pinecone.ui.features.library.LibraryCollectionViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ArchiveViewModel @Inject constructor(
    repository: LibraryRepository,
    deletePublications: com.javierreansyah.pinecone.domain.DeleteBooks,
    preferences: LibraryPreferencesManager,
    @com.javierreansyah.pinecone.di.DefaultDispatcher computation: kotlinx.coroutines.CoroutineDispatcher,
) : LibraryCollectionViewModel(
    bookRepository = repository,
    deletePublications = deletePublications,
    prefsManager = preferences,
    screenKey = "archive",
    computation = computation,
    source = LibraryRepository::getArchivedBooks,
)
