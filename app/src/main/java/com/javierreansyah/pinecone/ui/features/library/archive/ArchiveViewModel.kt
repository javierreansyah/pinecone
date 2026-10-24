package com.javierreansyah.pinecone.ui.features.library.archive

import android.app.Application
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import com.javierreansyah.pinecone.ui.features.library.LibraryCollectionViewModel

class ArchiveViewModel(application: Application) : LibraryCollectionViewModel(
    application = application,
    screenKey = "archive",
    source = LibraryRepository::getArchivedBooks,
)
