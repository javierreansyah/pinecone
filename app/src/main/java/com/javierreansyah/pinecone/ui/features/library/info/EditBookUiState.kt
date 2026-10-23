package com.javierreansyah.pinecone.ui.features.library.info

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
    val error: String? = null,
    val metadata: com.javierreansyah.pinecone.data.publication.PublicationMetadata = com.javierreansyah.pinecone.data.publication.PublicationMetadata(),
    val credits: List<com.javierreansyah.pinecone.data.local.database.library.PublicationCreditEntity> = emptyList(),
    val creditDrafts: Map<String, String> = emptyMap(),
    val languageDraft: String = "",
    val identifierDraft: String = "",
    val loaded: Boolean = false,
)
