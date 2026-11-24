package com.vinylcast.model

data class AudioInputDevice(
    val id: Int,
    val name: String,
    val isUsb: Boolean,
    val typeLabel: String
)

data class SonosDevice(
    val udn: String,
    val roomName: String,
    val modelName: String,
    val ipAddress: String,
    val port: Int = 1400
) {
    val controlBaseUrl: String get() = "http://$ipAddress:$port"
}

sealed interface OutputTarget {
    data object LocalDevice : OutputTarget
    data class Sonos(val device: SonosDevice) : OutputTarget
}

data class AudioLevels(
    val leftRms: Float = 0f,
    val rightRms: Float = 0f
)

data class AppSettings(
    val streamPort: Int = 8765,
    val keepScreenOn: Boolean = false,
    val darkMode: Boolean = true,
    val gainMultiplier: Float = 1f
)
