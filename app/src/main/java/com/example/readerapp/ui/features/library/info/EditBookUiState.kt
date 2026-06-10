package com.example.readerapp.ui.features.library.info

import android.net.Uri

data class EditBookUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val description: String = "",
    val coverUri: Uri? = null,
    val existingCoverPath: String? = null,
    val tags: List<String> = emptyList(),
    val authors: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null
)
