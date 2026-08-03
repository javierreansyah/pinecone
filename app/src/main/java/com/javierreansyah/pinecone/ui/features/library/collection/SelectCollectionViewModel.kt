package com.javierreansyah.pinecone.ui.features.library.collection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.PineconeApplication
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SelectCollectionViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as PineconeApplication).libraryRepository

    val collections = repository.getAllCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addBookToCollection(collectionId: String, bookIds: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            val ids = bookIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            ids.forEach { id ->
                repository.addBookToCollection(collectionId, id)
            }
            onComplete()
        }
    }

    fun createCollectionAndAddBook(name: String, bookIds: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            val collectionId = repository.createCollection(name)
            val ids = bookIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            ids.forEach { id ->
                repository.addBookToCollection(collectionId, id)
            }
            onComplete()
        }
    }
}
