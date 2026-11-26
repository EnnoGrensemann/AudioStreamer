package com.vinylcast.ui

import android.app.Application
import android.content.Context
import android.media.AudioManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vinylcast.audio.UsbAudioControlHelper
import com.vinylcast.audio.UsbAudioDeviceHelper
import com.vinylcast.model.AppSettings
import com.vinylcast.model.AudioInputDevice
import com.vinylcast.model.OutputTarget
import com.vinylcast.model.SonosDevice
import com.vinylcast.service.StreamController
import com.vinylcast.service.VinylStreamService
import com.vinylcast.settings.SettingsRepository
import com.vinylcast.sonos.SonosSsdpDiscovery
import com.vinylcast.sonos.SonosUpnpClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val usbHelper = UsbAudioDeviceHelper(application)
    private val usbControlHelper = UsbAudioControlHelper(application)
    private val sonosDiscovery = SonosSsdpDiscovery(application)
    private val sonosClient = SonosUpnpClient()
    private val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val settings: StateFlow<AppSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val inputDevices: StateFlow<List<AudioInputDevice>> = usbHelper.observeInputDevices()
        .stateIn(viewModelScope, SharingStarted.Eagerly, usbHelper.getInputDevices())

    val isStreaming = StreamController.isStreaming
    val isAudioDetected = StreamController.isAudioDetected
    val audioLevels = StreamController.audioLevels
    val peakDb = StreamController.peakDb
    val captureInfo = StreamController.captureInfo
    val connectedClients = StreamController.connectedClients
    val lastError = StreamController.lastError

    private val _sonosDevices = MutableStateFlow<List<SonosDevice>>(emptyList())
    val sonosDevices: StateFlow<List<SonosDevice>> = _sonosDevices.asStateFlow()

    private val _selectedOutput = MutableStateFlow<OutputTarget>(OutputTarget.LocalDevice)
    val selectedOutput: StateFlow<OutputTarget> = _selectedOutput.asStateFlow()

    private val _isDiscoveringSonos = MutableStateFlow(false)
    val isDiscoveringSonos: StateFlow<Boolean> = _isDiscoveringSonos.asStateFlow()

    private val _volume = MutableStateFlow(35)
    val volume: StateFlow<Int> = _volume.asStateFlow()

    init {
        discoverSonos()
        viewModelScope.launch {
            settingsRepo.settingsFlow.collect { s ->
                StreamController.setEqSettings(s.eq)
                StreamController.setGainMultiplier(s.gainMultiplier)
            }
        }
    }

    fun discoverSonos() {
        if (_isDiscoveringSonos.value) return
        viewModelScope.launch {
            _isDiscoveringSonos.value = true
            StreamController.setError(null)
            val found = sonosDiscovery.discoverSpeakers()
            _sonosDevices.value = found
            val lastUdn = settingsRepo.lastSonosUdnFlow.first()
            val preferred = found.firstOrNull { it.udn == lastUdn } ?: found.firstOrNull()
            if (preferred != null && _selectedOutput.value is OutputTarget.LocalDevice) {
                selectOutput(OutputTarget.Sonos(preferred))
            }
            _isDiscoveringSonos.value = false
        }
    }

    fun selectOutput(target: OutputTarget) {
        _selectedOutput.value = target
        when (target) {
            is OutputTarget.Sonos -> {
                StreamController.setActiveSonos(target.device)
                viewModelScope.launch {
                    settingsRepo.updateLastSonosUdn(target.device.udn)
                    sonosClient.getVolume(target.device).onSuccess { vol ->
                        _volume.value = vol
                    }
                }
            }
            OutputTarget.LocalDevice -> {
                StreamController.setActiveSonos(null)
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                _volume.value = ((curVol.toFloat() / maxVol) * 100f).roundToInt().coerceIn(0, 100)
            }
        }
    }

    fun toggleStream() {
        if (isStreaming.value) {
            stopStream()
        } else {
            startStream()
        }
    }

    private fun startStream() {
        val target = _selectedOutput.value
        val currentSettings = settings.value
        StreamController.setError(null)

        viewModelScope.launch {
            usbControlHelper.findUsbAudioDevice()?.let { usbDev ->
                usbControlHelper.ensureUsbPermission(usbDev)
            }

            val isLocal = target is OutputTarget.LocalDevice
            val roomName = (target as? OutputTarget.Sonos)?.device?.roomName ?: "Lokal"

            VinylStreamService.startService(
                context = getApplication(),
                port = currentSettings.streamPort,
                roomName = roomName,
                gainMultiplier = currentSettings.gainMultiplier,
                localPlayback = isLocal
            )

            if (target is OutputTarget.Sonos) {
                var url: String? = null
                repeat(20) {
                    url = StreamController.streamUrl.value
                    if (url != null) return@repeat
                    delay(100)
                }
                val streamUrl = url
                if (streamUrl == null) {
                    StreamController.setError("Keine WLAN-IP gefunden. Ist das Handy im WLAN?")
                    VinylStreamService.stopService(getApplication())
                    return@launch
                }

                sonosClient.startStream(target.device, streamUrl)
                    .onFailure { err ->
                        StreamController.setError("Sonos Start fehlgeschlagen: ${err.message}")
                        VinylStreamService.stopService(getApplication())
                    }
            }
        }
    }

    private fun stopStream() {
        val target = _selectedOutput.value
        VinylStreamService.stopService(getApplication())
        if (target is OutputTarget.Sonos) {
            viewModelScope.launch {
                sonosClient.stopStream(target.device)
            }
        }
    }

    fun setOutputVolume(volumePercent: Int) {
        val clamped = volumePercent.coerceIn(0, 100)
        _volume.value = clamped
        when (val target = _selectedOutput.value) {
            is OutputTarget.Sonos -> {
                viewModelScope.launch {
                    sonosClient.setVolume(target.device, clamped)
                }
            }
            OutputTarget.LocalDevice -> {
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                val targetVol = ((clamped / 100f) * maxVol).roundToInt().coerceIn(0, maxVol)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
            }
        }
    }

    fun toggleKeepScreenOn() {
        viewModelScope.launch {
            settingsRepo.updateKeepScreenOn(!settings.value.keepScreenOn)
        }
    }

    fun toggleDarkMode() {
        viewModelScope.launch {
            settingsRepo.updateDarkMode(!settings.value.darkMode)
        }
    }

    fun saveStreamPort(port: Int) {
        viewModelScope.launch {
            settingsRepo.updateStreamPort(port)
        }
    }

    fun setGainMultiplier(gain: Float) {
        viewModelScope.launch {
            settingsRepo.updateGainMultiplier(gain)
        }
    }

    fun selectEqPreset(preset: com.vinylcast.model.EqPreset) {
        val next = com.vinylcast.model.EqSettings(
            preset = preset,
            bassDb = preset.bassDb,
            midDb = preset.midDb,
            trebleDb = preset.trebleDb
        )
        StreamController.setEqSettings(next)
        viewModelScope.launch {
            settingsRepo.updateEqSettings(next)
        }
    }

    fun updateEqBands(bassDb: Float, midDb: Float, trebleDb: Float) {
        val next = com.vinylcast.model.EqSettings(
            preset = com.vinylcast.model.EqPreset.CUSTOM,
            bassDb = bassDb.coerceIn(-12f, 12f),
            midDb = midDb.coerceIn(-12f, 12f),
            trebleDb = trebleDb.coerceIn(-12f, 12f)
        )
        StreamController.setEqSettings(next)
        viewModelScope.launch {
            settingsRepo.updateEqSettings(next)
        }
    }

    fun dismissError() {
        StreamController.setError(null)
    }
}
