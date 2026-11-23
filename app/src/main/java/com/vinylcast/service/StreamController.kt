package com.vinylcast.service

import com.vinylcast.audio.RollingPcmBuffer
import com.vinylcast.model.AudioLevels
import com.vinylcast.model.SonosDevice
import com.vinylcast.model.TrackInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object StreamController {
    val rollingPcmBuffer = RollingPcmBuffer()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _isAudioCapturing = MutableStateFlow(false)
    val isAudioCapturing: StateFlow<Boolean> = _isAudioCapturing.asStateFlow()

    private val _audioLevels = MutableStateFlow(AudioLevels())
    val audioLevels: StateFlow<AudioLevels> = _audioLevels.asStateFlow()

    private val _streamUrl = MutableStateFlow<String?>(null)
    val streamUrl: StateFlow<String?> = _streamUrl.asStateFlow()

    private val _activeSonos = MutableStateFlow<SonosDevice?>(null)
    val activeSonos: StateFlow<SonosDevice?> = _activeSonos.asStateFlow()

    private val _currentTrack = MutableStateFlow<TrackInfo?>(null)
    val currentTrack: StateFlow<TrackInfo?> = _currentTrack.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun setStreaming(streaming: Boolean, url: String? = _streamUrl.value) {
        _isStreaming.value = streaming
        _streamUrl.value = if (streaming) url else null
    }

    fun setAudioCapturing(capturing: Boolean) {
        _isAudioCapturing.value = capturing
        if (!capturing) {
            _audioLevels.value = AudioLevels()
        }
    }

    fun updateLevels(left: Float, right: Float) {
        _audioLevels.value = AudioLevels(left, right)
    }

    fun setActiveSonos(device: SonosDevice?) {
        _activeSonos.value = device
    }

    fun setCurrentTrack(track: TrackInfo?) {
        _currentTrack.value = track
    }

    fun setError(message: String?) {
        _lastError.value = message
    }
}
