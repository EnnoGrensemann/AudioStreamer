package com.vinylcast.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vinylcast.audio.UsbAudioDeviceHelper
import com.vinylcast.model.AppSettings
import com.vinylcast.model.AudioInputDevice
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

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val usbHelper = UsbAudioDeviceHelper(application)
    private val usbControlHelper = com.vinylcast.audio.UsbAudioControlHelper(application)
    private val sonosDiscovery = SonosSsdpDiscovery(application)
    private val sonosClient = SonosUpnpClient()

    private val _usbDiagnostic = MutableStateFlow<String?>(null)
    val usbDiagnostic: StateFlow<String?> = _usbDiagnostic.asStateFlow()

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

    private val _selectedSonos = MutableStateFlow<SonosDevice?>(null)
    val selectedSonos: StateFlow<SonosDevice?> = _selectedSonos.asStateFlow()

    private val _isDiscoveringSonos = MutableStateFlow(false)
    val isDiscoveringSonos: StateFlow<Boolean> = _isDiscoveringSonos.asStateFlow()

    private val _sonosVolume = MutableStateFlow(35)
    val sonosVolume: StateFlow<Int> = _sonosVolume.asStateFlow()

    init {
        discoverSonos()
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
            if (preferred != null) {
                selectSonos(preferred)
            }
            _isDiscoveringSonos.value = false
        }
    }

    fun selectSonos(device: SonosDevice) {
        _selectedSonos.value = device
        StreamController.setActiveSonos(device)
        viewModelScope.launch {
            settingsRepo.updateLastSonosUdn(device.udn)
            sonosClient.getVolume(device).onSuccess { vol ->
                _sonosVolume.value = vol
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
        val target = _selectedSonos.value
        if (target == null) {
            StreamController.setError("Keine Sonos Playbar ausgewählt. Bitte zuerst im WLAN suchen.")
            return
        }

        val currentSettings = settings.value
        StreamController.setError(null)

        viewModelScope.launch {
            if (currentSettings.preferredInputDeviceId == null) {
                usbControlHelper.findUsbAudioDevice()?.let { usbDev ->
                    usbControlHelper.ensureUsbPermission(usbDev)
                }
            }

            VinylStreamService.startService(
                context = getApplication(),
                port = currentSettings.streamPort,
                preferredDeviceId = currentSettings.preferredInputDeviceId,
                roomName = target.roomName,
                audioSource = currentSettings.audioSource.name,
                gainMultiplier = currentSettings.gainMultiplier
            )

            var url: String? = null
            repeat(20) {
                url = StreamController.streamUrl.value
                if (url != null) return@repeat
                delay(100)
            }
            val streamUrl = url
            if (streamUrl == null) {
                StreamController.setError("Keine WLAN-IP gefunden. Ist das Pixel 5 im WLAN?")
                VinylStreamService.stopService(getApplication())
                return@launch
            }

            sonosClient.startStream(target, streamUrl)
                .onFailure { err ->
                    StreamController.setError("Sonos Start fehlgeschlagen: ${err.message}")
                    VinylStreamService.stopService(getApplication())
                }
        }
    }

    private fun stopStream() {
        val target = _selectedSonos.value
        VinylStreamService.stopService(getApplication())
        if (target != null) {
            viewModelScope.launch {
                sonosClient.stopStream(target)
            }
        }
    }

    fun setSonosVolume(volume: Int) {
        val clamped = volume.coerceIn(0, 100)
        _sonosVolume.value = clamped
        val target = _selectedSonos.value ?: return
        viewModelScope.launch {
            sonosClient.setVolume(target, clamped)
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

    fun selectPreferredInputDevice(deviceId: Int?) {
        viewModelScope.launch {
            settingsRepo.updatePreferredInputDevice(deviceId)
        }
    }

    fun selectAudioSource(source: com.vinylcast.model.AudioSourceOption) {
        viewModelScope.launch {
            settingsRepo.updateAudioSource(source)
        }
    }

    fun setGainMultiplier(gain: Float) {
        viewModelScope.launch {
            settingsRepo.updateGainMultiplier(gain)
        }
    }

    val debugLogText = com.vinylcast.util.DebugLog.logText

    fun inspectAndSwitchUsbLineIn() {
        viewModelScope.launch {
            usbHelper.dumpInputDevicesDebug()
            _usbDiagnostic.value = "Prüfe USB-Gerät..."
            _usbDiagnostic.value = usbControlHelper.requestPermissionAndInspect()
        }
    }

    fun refreshDebugDevices() {
        usbHelper.dumpInputDevicesDebug()
    }

    fun clearDebugLog() {
        com.vinylcast.util.DebugLog.clear()
    }

    fun dismissError() {
        StreamController.setError(null)
    }
}
