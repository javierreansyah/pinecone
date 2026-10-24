package com.javierreansyah.pinecone.ui.features.library.unsorted

import android.app.Application
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import com.javierreansyah.pinecone.ui.features.library.LibraryCollectionViewModel

class UnsortedViewModel(application: Application) : LibraryCollectionViewModel(
    application = application,
    screenKey = "unsorted",
    source = LibraryRepository::getUnsortedBooks,
)
