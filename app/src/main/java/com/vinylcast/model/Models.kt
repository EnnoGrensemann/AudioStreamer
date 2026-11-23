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

data class TrackInfo(
    val title: String,
    val artist: String,
    val album: String = "",
    val releaseYear: String = "",
    val coverUrl: String? = null,
    val recognizedAtMs: Long = System.currentTimeMillis()
)

data class AudioLevels(
    val leftRms: Float = 0f,
    val rightRms: Float = 0f
)

data class AppSettings(
    val acrHost: String = "",
    val acrAccessKey: String = "",
    val acrAccessSecret: String = "",
    val streamPort: Int = 8765,
    val preferredInputDeviceId: Int? = null,
    val keepScreenOn: Boolean = false
) {
    val hasAcrCredentials: Boolean
        get() = acrHost.isNotBlank() && acrAccessKey.isNotBlank() && acrAccessSecret.isNotBlank()
}

sealed interface RecognitionState {
    data object Idle : RecognitionState
    data object Recognizing : RecognitionState
    data class Success(val track: TrackInfo) : RecognitionState
    data class Error(val message: String) : RecognitionState
}
