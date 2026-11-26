package com.vinylcast.ui.tv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vinylcast.audio.AudioOutputDeviceHelper
import com.vinylcast.audio.UsbAudioControlHelper
import com.vinylcast.audio.UsbAudioDeviceHelper
import com.vinylcast.model.AppSettings
import com.vinylcast.model.AudioInputDevice
import com.vinylcast.model.AudioOutputDevice
import com.vinylcast.model.EqPreset
import com.vinylcast.model.EqSettings
import com.vinylcast.service.StreamController
import com.vinylcast.service.VinylStreamService
import com.vinylcast.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TvViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val usbInputHelper = UsbAudioDeviceHelper(application)
    private val usbControlHelper = UsbAudioControlHelper(application)
    private val outputHelper = AudioOutputDeviceHelper(application)

    val settings: StateFlow<AppSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val inputDevices: StateFlow<List<AudioInputDevice>> = usbInputHelper.observeInputDevices()
        .stateIn(viewModelScope, SharingStarted.Eagerly, usbInputHelper.getInputDevices())

    val outputDevices: StateFlow<List<AudioOutputDevice>> = outputHelper.observeTvOutputDevices()
        .stateIn(viewModelScope, SharingStarted.Eagerly, outputHelper.getTvOutputDevices())

    val isStreaming = StreamController.isStreaming
    val isAudioDetected = StreamController.isAudioDetected
    val audioLevels = StreamController.audioLevels
    val activeOutputLabel = StreamController.activeOutputLabel
    val lastError = StreamController.lastError

    private var autoStartAttempted = false

    init {
        viewModelScope.launch {
            settingsRepo.settingsFlow.collect { s ->
                StreamController.setEqSettings(s.eq)
                StreamController.setGainMultiplier(s.gainMultiplier)
            }
        }
    }

    fun tryAutoStart() {
        if (autoStartAttempted || isStreaming.value) return
        autoStartAttempted = true
        val usbDev = usbControlHelper.findUsbAudioDevice()
        if (usbDev != null || inputDevices.value.any { it.isUsb }) {
            startPlayback()
        }
    }

    fun onUsbDeviceAttached() {
        if (!isStreaming.value) {
            startPlayback()
        }
    }

    fun togglePlayback() {
        if (isStreaming.value) {
            stopPlayback()
        } else {
            startPlayback()
        }
    }

    fun startPlayback() {
        val currentSettings = settings.value
        StreamController.setError(null)

        viewModelScope.launch {
            usbControlHelper.findUsbAudioDevice()?.let { usbDev ->
                val granted = usbControlHelper.ensureUsbPermission(usbDev)
                if (!granted) {
                    StreamController.setError("USB-Berechtigung für den Line-In Adapter wurde nicht erteilt.")
                    return@launch
                }
            }

            VinylStreamService.startService(
                context = getApplication(),
                port = currentSettings.streamPort,
                roomName = "TOSLINK",
                gainMultiplier = currentSettings.gainMultiplier,
                localPlayback = true,
                preferredOutputDeviceId = currentSettings.preferredOutputDeviceId,
                avoidUsbOutput = true
            )
        }
    }

    fun stopPlayback() {
        VinylStreamService.stopService(getApplication())
    }

    fun selectOutputDevice(deviceId: Int?) {
        viewModelScope.launch {
            settingsRepo.updatePreferredOutputDevice(deviceId)
            if (isStreaming.value) {
                VinylStreamService.updateOutputDevice(getApplication(), deviceId)
            }
        }
    }

    fun selectEqPreset(preset: EqPreset) {
        val next = EqSettings(
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
        val next = EqSettings(
            preset = EqPreset.CUSTOM,
            bassDb = bassDb.coerceIn(-12f, 12f),
            midDb = midDb.coerceIn(-12f, 12f),
            trebleDb = trebleDb.coerceIn(-12f, 12f)
        )
        StreamController.setEqSettings(next)
        viewModelScope.launch {
            settingsRepo.updateEqSettings(next)
        }
    }

    fun setGainMultiplier(gain: Float) {
        val clamped = gain.coerceIn(1f, 16f)
        StreamController.setGainMultiplier(clamped)
        viewModelScope.launch {
            settingsRepo.updateGainMultiplier(clamped)
        }
    }

    fun toggleDarkMode() {
        viewModelScope.launch {
            settingsRepo.updateDarkMode(!settings.value.darkMode)
        }
    }

    fun toggleKeepScreenOn() {
        viewModelScope.launch {
            settingsRepo.updateKeepScreenOn(!settings.value.keepScreenOn)
        }
    }

    fun dismissError() {
        StreamController.setError(null)
    }
}
