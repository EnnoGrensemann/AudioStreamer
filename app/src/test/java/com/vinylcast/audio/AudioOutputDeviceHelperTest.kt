package com.vinylcast.audio

import com.vinylcast.model.AudioOutputDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioOutputDeviceHelperTest {

    @Test
    fun `pickBestTvOutput prefers TOSLINK optical over HDMI, speaker, and skips USB headset`() {
        val devices = listOf(
            AudioOutputDevice(1, "CUBILUX HLMS-C4", AudioOutputDeviceHelper.TYPE_USB_HEADSET, "USB-Audio", false, true),
            AudioOutputDevice(2, "TV Speaker", AudioOutputDeviceHelper.TYPE_BUILTIN_SPEAKER, "TV-System", false, false),
            AudioOutputDevice(3, "HDMI Out", AudioOutputDeviceHelper.TYPE_HDMI, "HDMI Audio", false, false),
            AudioOutputDevice(4, "Optical S/PDIF", AudioOutputDeviceHelper.TYPE_LINE_DIGITAL, "TOSLINK / Optisch", true, false)
        )

        val picked = AudioOutputDeviceHelper.pickBestTvOutput(devices)
        assertEquals(4, picked?.id)
    }

    @Test
    fun `pickBestTvOutput falls back to TV speaker when only USB and speaker exist`() {
        val devices = listOf(
            AudioOutputDevice(1, "CUBILUX HLMS-C4", AudioOutputDeviceHelper.TYPE_USB_HEADSET, "USB-Audio", false, true),
            AudioOutputDevice(2, "TV Speaker", AudioOutputDeviceHelper.TYPE_BUILTIN_SPEAKER, "TV-System", false, false)
        )

        val picked = AudioOutputDeviceHelper.pickBestTvOutput(devices)
        assertEquals(2, picked?.id)
    }

    @Test
    fun `pickBestTvOutput honors explicit user preferredDeviceId`() {
        val devices = listOf(
            AudioOutputDevice(2, "TV Speaker", AudioOutputDeviceHelper.TYPE_BUILTIN_SPEAKER, "TV-System", false, false),
            AudioOutputDevice(4, "Optical S/PDIF", AudioOutputDeviceHelper.TYPE_LINE_DIGITAL, "TOSLINK / Optisch", true, false)
        )

        val picked = AudioOutputDeviceHelper.pickBestTvOutput(devices, preferredDeviceId = 2)
        assertEquals(2, picked?.id)
    }

    @Test
    fun `pickBestTvOutput returns null if only USB output is available`() {
        val devices = listOf(
            AudioOutputDevice(1, "CUBILUX HLMS-C4", AudioOutputDeviceHelper.TYPE_USB_HEADSET, "USB-Audio", false, true)
        )

        assertNull(AudioOutputDeviceHelper.pickBestTvOutput(devices))
    }
}
