package com.javierreansyah.pinecone.ui.features.library.info

import com.javierreansyah.pinecone.data.model.Book

data class BookInfoUiState(
    val book: Book? = null,
    val isLoading: Boolean = true
)
