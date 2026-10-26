package com.javierreansyah.pinecone.reader.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import javax.inject.Inject
import javax.inject.Singleton

data class AudioPlaybackState(
    val bookId: String? = null,
    val title: String = "",
    val chapter: String = "",
    val playing: Boolean = false,
    val loading: Boolean = false,
    val index: Int = 0,
    val elapsedSeconds: Long = 0,
    val durationSeconds: Long? = null,
    val error: String? = null,
    val author: String = "",
    val metadataLoaded: Boolean = false,
    val ready: Boolean = false,
    val generation: String? = null,
    val coverPath: String? = null,
    val locator: Locator? = null,
    val tableOfContents: List<Link> = emptyList(),
)

/** Process-local presentation data only. Native playback resources belong to the service. */
@Singleton
class AudioPlaybackStore @Inject constructor() {
    private val mutableState = MutableStateFlow(AudioPlaybackState())
    val state = mutableState.asStateFlow()

    internal fun publish(state: AudioPlaybackState) {
        mutableState.value = state
    }

    internal fun update(transform: (AudioPlaybackState) -> AudioPlaybackState) {
        mutableState.value = transform(mutableState.value)
    }
}
