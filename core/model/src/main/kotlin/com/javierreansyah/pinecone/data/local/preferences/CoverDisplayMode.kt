package com.javierreansyah.pinecone.data.local.preferences

import kotlinx.serialization.Serializable

/** Display choice is stored independently of the image loading implementation. */
@Serializable
enum class CoverDisplayMode {
    Contain,
    Cover,
}
