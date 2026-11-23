package com.vinylcast.recognition

import android.util.Base64
import com.vinylcast.audio.WavStreamHeader
import com.vinylcast.model.AppSettings
import com.vinylcast.model.TrackInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class AcrCloudRecognizer(
    private val coverArtResolver: CoverArtResolver = CoverArtResolver(),
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val HTTP_METHOD = "POST"
        private const val HTTP_URI = "/v1/identify"
        private const val DATA_TYPE = "audio"
        private const val SIGNATURE_VERSION = "1"

        fun buildSignature(
            accessKey: String,
            accessSecret: String,
            timestampSec: Long
        ): String {
            val stringToSign = "$HTTP_METHOD\n$HTTP_URI\n$accessKey\n$DATA_TYPE\n$SIGNATURE_VERSION\n$timestampSec"
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(SecretKeySpec(accessSecret.toByteArray(Charsets.UTF_8), "HmacSHA1"))
            val rawHmac = mac.doFinal(stringToSign.toByteArray(Charsets.UTF_8))
            return Base64.encodeToString(rawHmac, Base64.NO_WRAP)
        }

        fun parseAcrResponse(jsonString: String): ParsedAcrResult {
            val root = JSONObject(jsonString)
            val status = root.optJSONObject("status")
            val code = status?.optInt("code", -1) ?: -1
            val msg = status?.optString("msg", "Unbekannter Fehler") ?: "Unbekannter Fehler"

            if (code == 1001) {
                return ParsedAcrResult.NoMatch
            }
            if (code != 0) {
                return ParsedAcrResult.ApiError("ACRCloud ($code): $msg")
            }

            val musicArray = root.optJSONObject("metadata")?.optJSONArray("music")
            if (musicArray == null || musicArray.length() == 0) {
                return ParsedAcrResult.NoMatch
            }

            val first = musicArray.getJSONObject(0)
            val title = first.optString("title", "").ifBlank { "Unbekannter Titel" }

            val artistsArr = first.optJSONArray("artists")
            val artist = buildList {
                if (artistsArr != null) {
                    for (i in 0 until artistsArr.length()) {
                        artistsArr.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                }
            }.joinToString(", ").ifBlank { "Unbekannter Interpret" }

            val album = first.optJSONObject("album")?.optString("name", "").orEmpty()
            val releaseDate = first.optString("release_date", "")
            val year = releaseDate.take(4).takeIf { it.length == 4 && it.all(Char::isDigit) }.orEmpty()

            return ParsedAcrResult.Match(
                TrackInfo(
                    title = title,
                    artist = artist,
                    album = album,
                    releaseYear = year,
                    coverUrl = null
                )
            )
        }
    }

    sealed interface ParsedAcrResult {
        data class Match(val track: TrackInfo) : ParsedAcrResult
        data object NoMatch : ParsedAcrResult
        data class ApiError(val message: String) : ParsedAcrResult
    }

    suspend fun recognize(
        pcmSnippet: ByteArray,
        settings: AppSettings
    ): Result<TrackInfo> = withContext(Dispatchers.IO) {
        runCatching {
            require(settings.hasAcrCredentials) {
                "Bitte ACRCloud Host, Access Key und Secret in den Einstellungen hinterlegen."
            }
            require(pcmSnippet.size >= WavStreamHeader.SAMPLE_RATE * 2) {
                "Zu wenig Audio-Material im Puffer. Bitte kurz warten, während die Platte läuft."
            }

            val wavBytes = WavStreamHeader.wrapPcmAsWav(pcmSnippet)
            val timestampSec = System.currentTimeMillis() / 1000L
            val signature = buildSignature(settings.acrAccessKey, settings.acrAccessSecret, timestampSec)

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "sample",
                    "vinyl_sample.wav",
                    wavBytes.toRequestBody("audio/wav".toMediaType())
                )
                .addFormDataPart("sample_bytes", wavBytes.size.toString())
                .addFormDataPart("access_key", settings.acrAccessKey)
                .addFormDataPart("data_type", DATA_TYPE)
                .addFormDataPart("signature_version", SIGNATURE_VERSION)
                .addFormDataPart("signature", signature)
                .addFormDataPart("timestamp", timestampSec.toString())
                .build()

            val request = Request.Builder()
                .url("https://${settings.acrHost}$HTTP_URI")
                .post(multipartBody)
                .build()

            val bodyString = httpClient.newCall(request).execute().use { response ->
                val str = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("HTTP ${response.code}: ${str.take(180)}")
                }
                str
            }

            when (val parsed = parseAcrResponse(bodyString)) {
                is ParsedAcrResult.Match -> {
                    val coverUrl = coverArtResolver.resolveCoverUrl(
                        artist = parsed.track.artist,
                        album = parsed.track.album,
                        title = parsed.track.title
                    )
                    parsed.track.copy(coverUrl = coverUrl)
                }
                is ParsedAcrResult.NoMatch -> error("Kein Song erkannt – evtl. etwas lauter oder während des Gesangs erneut versuchen.")
                is ParsedAcrResult.ApiError -> error(parsed.message)
            }
        }
    }
}
