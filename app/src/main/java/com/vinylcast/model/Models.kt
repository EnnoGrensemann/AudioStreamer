package com.vinylcast.model

enum class AudioSourceOption(val label: String) {
    AUTO("Automatisch (MIC → Default)"),
    MIC("Mikrofon (MIC)"),
    UNPROCESSED("Unbearbeitet / Line-In (UNPROCESSED)"),
    CAMCORDER("Camcorder (CAMCORDER)"),
    DEFAULT("System-Standard (DEFAULT)")
}

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

data class AudioLevels(
    val leftRms: Float = 0f,
    val rightRms: Float = 0f
)

data class AppSettings(
    val streamPort: Int = 8765,
    val preferredInputDeviceId: Int? = null,
    val keepScreenOn: Boolean = false,
    val darkMode: Boolean = true,
    val audioSource: AudioSourceOption = AudioSourceOption.AUTO,
    val gainMultiplier: Float = 1f
)
