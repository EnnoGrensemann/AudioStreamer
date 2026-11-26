package com.vinylcast.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.vinylcast.model.AudioOutputDevice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Enumerates Android TV audio outputs and bypasses USB headphone/soundcard outputs
 * (like Cubilux HLMS-C4) so AudioTrack routes cleanly to TOSLINK / Optical Out.
 */
class AudioOutputDeviceHelper(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    companion object {
        // Explicit constants so logic works uniformly across API 28..35 and JVM unit tests
        const val TYPE_BUILTIN_SPEAKER = 2
        const val TYPE_LINE_ANALOG = 5
        const val TYPE_LINE_DIGITAL = 6 // TOSLINK / Optical S/PDIF
        const val TYPE_HDMI = 9
        const val TYPE_HDMI_ARC = 10
        const val TYPE_USB_DEVICE = 11
        const val TYPE_USB_ACCESSORY = 12
        const val TYPE_USB_HEADSET = 22
        const val TYPE_HDMI_EARC = 29

        fun isUsbOutputType(type: Int): Boolean {
            return type == TYPE_USB_DEVICE ||
                type == TYPE_USB_HEADSET ||
                type == TYPE_USB_ACCESSORY
        }

        fun isOpticalType(type: Int): Boolean = type == TYPE_LINE_DIGITAL

        fun priorityForTvOutput(type: Int): Int = when (type) {
            TYPE_LINE_DIGITAL -> 100 // TOSLINK / Optical S/PDIF
            TYPE_HDMI_EARC -> 85
            TYPE_HDMI_ARC -> 80
            TYPE_HDMI -> 70
            TYPE_BUILTIN_SPEAKER -> 60 // Many TVs route system/internal output to Optical
            TYPE_LINE_ANALOG -> 40
            else -> if (isUsbOutputType(type)) -100 else 20
        }

        fun labelForOutputType(type: Int): String = when (type) {
            TYPE_LINE_DIGITAL -> "TOSLINK / Optisch"
            TYPE_HDMI_EARC -> "HDMI eARC"
            TYPE_HDMI_ARC -> "HDMI ARC"
            TYPE_HDMI -> "HDMI Audio"
            TYPE_BUILTIN_SPEAKER -> "TV-System / Lautsprecher"
            TYPE_LINE_ANALOG -> "Analog Line-Out"
            TYPE_USB_DEVICE, TYPE_USB_HEADSET, TYPE_USB_ACCESSORY -> "USB-Audio"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
            else -> "Ausgang ($type)"
        }

        fun pickBestTvOutput(
            devices: List<AudioOutputDevice>,
            preferredDeviceId: Int? = null
        ): AudioOutputDevice? {
            if (preferredDeviceId != null) {
                devices.firstOrNull { it.id == preferredDeviceId }?.let { return it }
            }
            return devices
                .filterNot { it.isUsb }
                .maxByOrNull { priorityForTvOutput(it.type) }
        }
    }

    fun getTvOutputDevices(): List<AudioOutputDevice> {
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .map { info ->
                val isUsb = isUsbOutputType(info.type)
                val isOptical = isOpticalType(info.type)
                val typeLabel = labelForOutputType(info.type)
                val productName = info.productName?.toString()?.takeIf { it.isNotBlank() } ?: typeLabel
                AudioOutputDevice(
                    id = info.id,
                    name = productName,
                    type = info.type,
                    typeLabel = typeLabel,
                    isOptical = isOptical,
                    isUsb = isUsb
                )
            }
            .filterNot { it.isUsb }
            .sortedByDescending { priorityForTvOutput(it.type) }
    }

    fun findBestTvOutputInfo(preferredDeviceId: Int? = null): AudioDeviceInfo? {
        val rawDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        if (preferredDeviceId != null) {
            rawDevices.firstOrNull { it.id == preferredDeviceId }?.let { return it }
        }
        return rawDevices
            .filterNot { isUsbOutputType(it.type) }
            .maxByOrNull { priorityForTvOutput(it.type) }
    }

    fun observeTvOutputDevices(): Flow<List<AudioOutputDevice>> = callbackFlow {
        trySend(getTvOutputDevices())
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                trySend(getTvOutputDevices())
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                trySend(getTvOutputDevices())
            }
        }
        audioManager.registerAudioDeviceCallback(callback, null)
        awaitClose {
            audioManager.unregisterAudioDeviceCallback(callback)
        }
    }
}
