package com.vinylcast.service

import com.vinylcast.model.AudioLevels
import com.vinylcast.model.SonosDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object StreamController {
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _isAudioCapturing = MutableStateFlow(false)
    val isAudioCapturing: StateFlow<Boolean> = _isAudioCapturing.asStateFlow()

    private val _isAudioDetected = MutableStateFlow(false)
    val isAudioDetected: StateFlow<Boolean> = _isAudioDetected.asStateFlow()

    private val _audioLevels = MutableStateFlow(AudioLevels())
    val audioLevels: StateFlow<AudioLevels> = _audioLevels.asStateFlow()

    private val _peakDb = MutableStateFlow(-60f)
    val peakDb: StateFlow<Float> = _peakDb.asStateFlow()

    private val _captureInfo = MutableStateFlow("")
    val captureInfo: StateFlow<String> = _captureInfo.asStateFlow()

    private val _connectedClients = MutableStateFlow(0)
    val connectedClients: StateFlow<Int> = _connectedClients.asStateFlow()

    private val _streamUrl = MutableStateFlow<String?>(null)
    val streamUrl: StateFlow<String?> = _streamUrl.asStateFlow()

    private val _activeSonos = MutableStateFlow<SonosDevice?>(null)
    val activeSonos: StateFlow<SonosDevice?> = _activeSonos.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun setStreaming(streaming: Boolean, url: String? = _streamUrl.value) {
        _isStreaming.value = streaming
        _streamUrl.value = if (streaming) url else null
        if (!streaming) {
            _isAudioDetected.value = false
            _peakDb.value = -60f
            _captureInfo.value = ""
            _connectedClients.value = 0
        }
    }

    fun setConnectedClients(count: Int) {
        _connectedClients.value = count
    }

    fun setAudioCapturing(capturing: Boolean, info: String = "") {
        _isAudioCapturing.value = capturing
        if (info.isNotBlank()) {
            _captureInfo.value = info
        }
        if (!capturing) {
            _audioLevels.value = AudioLevels()
            _isAudioDetected.value = false
            _peakDb.value = -60f
        }
    }

    fun updateLevels(left: Float, right: Float, signalDetected: Boolean, db: Float) {
        _audioLevels.value = AudioLevels(left, right)
        _isAudioDetected.value = signalDetected
        _peakDb.value = db
    }

    fun setActiveSonos(device: SonosDevice?) {
        _activeSonos.value = device
    }

    fun setError(message: String?) {
        _lastError.value = message
    }
}
