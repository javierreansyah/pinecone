package com.javierreansyah.pinecone.ui.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.reader.audio.AudioPlaybackClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class AudioControlsViewModel @Inject constructor(private val playback: AudioPlaybackClient) : ViewModel() {
    val state = playback.state
    fun togglePlayback() = playback.togglePlayback()
    fun skip(seconds: Int) = playback.skip(seconds)
    fun dismiss() { viewModelScope.launch { playback.stop() } }
}
