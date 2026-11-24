package com.vinylcast.model

enum class EqPreset(
    val label: String,
    val bassDb: Float,
    val midDb: Float,
    val trebleDb: Float
) {
    FLAT("Neutral", 0f, 0f, 0f),
    WARM_VINYL("Warmes Vinyl", 3.5f, 0.5f, -1.5f),
    BASS_BOOST("Bass Boost", 6f, 0f, 1f),
    VOCAL("Klar / Stimmen", -1.5f, 3.5f, 2.5f),
    LOUDNESS("Loudness", 4.5f, -1.5f, 4f),
    CUSTOM("Eigene", 0f, 0f, 0f)
}

data class EqSettings(
    val preset: EqPreset = EqPreset.FLAT,
    val bassDb: Float = 0f,
    val midDb: Float = 0f,
    val trebleDb: Float = 0f
)

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
    val gainMultiplier: Float = 1f,
    val eq: EqSettings = EqSettings()
)
