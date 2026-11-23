package com.vinylcast.sonos

import com.vinylcast.model.SonosDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class SonosUpnpClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private val XML_MEDIA_TYPE = "text/xml; charset=\"utf-8\"".toMediaType()
    }

    suspend fun startStream(
        device: SonosDevice,
        streamUrl: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val didl = SonosXmlBuilder.buildDidlLiteMetadata(streamUrl)
            val setUriBody = SonosXmlBuilder.buildSetAvTransportUriSoap(streamUrl, didl)
            sendSoapAction(
                url = "${device.controlBaseUrl}/MediaRenderer/AVTransport/Control",
                soapAction = "\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"",
                xmlBody = setUriBody
            )

            val playBody = SonosXmlBuilder.buildPlaySoap()
            sendSoapAction(
                url = "${device.controlBaseUrl}/MediaRenderer/AVTransport/Control",
                soapAction = "\"urn:schemas-upnp-org:service:AVTransport:1#Play\"",
                xmlBody = playBody
            )
            Unit
        }
    }

    suspend fun stopStream(device: SonosDevice): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            sendSoapAction(
                url = "${device.controlBaseUrl}/MediaRenderer/AVTransport/Control",
                soapAction = "\"urn:schemas-upnp-org:service:AVTransport:1#Stop\"",
                xmlBody = SonosXmlBuilder.buildStopSoap()
            )
            Unit
        }
    }

    suspend fun getVolume(device: SonosDevice): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val responseXml = sendSoapAction(
                url = "${device.controlBaseUrl}/MediaRenderer/RenderingControl/Control",
                soapAction = "\"urn:schemas-upnp-org:service:RenderingControl:1#GetVolume\"",
                xmlBody = SonosXmlBuilder.buildGetVolumeSoap()
            )
            SonosXmlBuilder.parseXmlTag(responseXml, "CurrentVolume")?.toIntOrNull()
                ?: error("Volume nicht lesbar")
        }
    }

    suspend fun setVolume(device: SonosDevice, volume: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            sendSoapAction(
                url = "${device.controlBaseUrl}/MediaRenderer/RenderingControl/Control",
                soapAction = "\"urn:schemas-upnp-org:service:RenderingControl:1#SetVolume\"",
                xmlBody = SonosXmlBuilder.buildSetVolumeSoap(volume)
            )
            Unit
        }
    }

    private fun sendSoapAction(url: String, soapAction: String, xmlBody: String): String {
        val request = Request.Builder()
            .url(url)
            .post(xmlBody.toRequestBody(XML_MEDIA_TYPE))
            .header("SOAPACTION", soapAction)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Sonos UPnP Fehler (${response.code}): ${body.take(200)}")
            }
            return body
        }
    }
}
