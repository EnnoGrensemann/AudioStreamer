package com.vinylcast.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.vinylcast.model.AudioInputDevice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class UsbAudioDeviceHelper(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun getInputDevices(): List<AudioInputDevice> {
        return audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .map { info ->
                val isUsb = isUsbInput(info)
                val typeLabel = when (info.type) {
                    AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio"
                    AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset/Line"
                    AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB Accessory"
                    AudioDeviceInfo.TYPE_LINE_ANALOG -> "Analog Line-In"
                    AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Pixel Mikrofon"
                    else -> "Eingang (${info.type})"
                }
                val productName = info.productName?.toString()?.takeIf { it.isNotBlank() } ?: typeLabel
                AudioInputDevice(
                    id = info.id,
                    name = productName,
                    isUsb = isUsb,
                    typeLabel = typeLabel
                )
            }
            .sortedByDescending { it.isUsb }
    }

    fun findBestInputDeviceInfo(preferredDeviceId: Int? = null): AudioDeviceInfo? {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        if (preferredDeviceId != null) {
            devices.firstOrNull { it.id == preferredDeviceId }?.let { return it }
        }
        return devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_DEVICE }
            ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
            ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY }
            ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_LINE_ANALOG }
    }

    fun observeInputDevices(): Flow<List<AudioInputDevice>> = callbackFlow {
        trySend(getInputDevices())
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                trySend(getInputDevices())
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                trySend(getInputDevices())
            }
        }
        audioManager.registerAudioDeviceCallback(callback, null)
        awaitClose {
            audioManager.unregisterAudioDeviceCallback(callback)
        }
    }

    private fun isUsbInput(info: AudioDeviceInfo): Boolean {
        return info.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            info.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            info.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
    }
}
