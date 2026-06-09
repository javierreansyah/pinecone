package com.example.readerapp.ui.features.reader

interface ReaderNavigationRouter {
    fun navigateToBookInfo(bookId: String)
    fun navigateBack()
}
