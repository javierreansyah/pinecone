package com.javierreansyah.pinecone.data.repository.dictionary

sealed class DictionaryState {
    object Idle : DictionaryState()
    data class Loading(val progress: Int) : DictionaryState()
    object Success : DictionaryState()
    data class Error(val message: String) : DictionaryState()
}
