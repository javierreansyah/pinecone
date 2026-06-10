package com.example.readerapp.ui.features.library.info

import com.example.readerapp.data.model.Book

data class BookInfoUiState(
    val book: Book? = null,
    val isLoading: Boolean = true
)
