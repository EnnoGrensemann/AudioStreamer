package com.vinylcast.sonos

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.vinylcast.model.SonosDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.TimeUnit

class SonosSsdpDiscovery(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "SonosSsdpDiscovery"
        private const val SSDP_ADDR = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val SEARCH_TARGET = "urn:schemas-upnp-org:device:ZonePlayer:1"
    }

    suspend fun discoverSpeakers(timeoutMs: Int = 2500): List<SonosDevice> = withContext(Dispatchers.IO) {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicastLock = wifiManager?.createMulticastLock("VinylCast_SSDP")?.apply {
            setReferenceCounted(false)
            acquire()
        }

        val locations = linkedSetOf<String>()
        try {
            val query = buildString {
                append("M-SEARCH * HTTP/1.1\r\n")
                append("HOST: $SSDP_ADDR:$SSDP_PORT\r\n")
                append("MAN: \"ssdp:discover\"\r\n")
                append("MX: 2\r\n")
                append("ST: $SEARCH_TARGET\r\n")
                append("\r\n")
            }.toByteArray(Charsets.US_ASCII)

            DatagramSocket().use { socket ->
                socket.soTimeout = 500
                socket.broadcast = true
                val group = InetAddress.getByName(SSDP_ADDR)
                val packet = DatagramPacket(query, query.size, group, SSDP_PORT)
                // Send twice for UDP reliability
                socket.send(packet)
                socket.send(packet)

                val buf = ByteArray(4096)
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    val responsePacket = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(responsePacket)
                        val response = String(responsePacket.data, 0, responsePacket.length, Charsets.UTF_8)
                        if (response.contains("Sonos", ignoreCase = true) ||
                            response.contains("ZonePlayer", ignoreCase = true)
                        ) {
                            SonosXmlBuilder.parseSsdpLocation(response)?.let { locations.add(it) }
                        }
                    } catch (_: SocketTimeoutException) {
                        // Continue until deadline
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "SSDP discovery error: ${e.message}")
        } finally {
            runCatching { multicastLock?.release() }
        }

        val devices = locations.mapNotNull { fetchDeviceDescription(it) }
            .distinctBy { it.udn }
            .sortedWith(
                compareByDescending<SonosDevice> { it.modelName.contains("Playbar", ignoreCase = true) }
                    .thenBy { it.roomName }
            )

        Log.i(TAG, "Discovered ${devices.size} Sonos devices")
        devices
    }

    private fun fetchDeviceDescription(locationUrl: String): SonosDevice? {
        return try {
            val request = Request.Builder().url(locationUrl).get().build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val xml = response.body?.string() ?: return null
                if (!xml.contains("Sonos", ignoreCase = true)) return null

                val url = URL(locationUrl)
                val roomName = SonosXmlBuilder.parseXmlTag(xml, "roomName")
                    ?: SonosXmlBuilder.parseXmlTag(xml, "friendlyName")
                    ?: "Sonos Speaker"
                val modelName = SonosXmlBuilder.parseXmlTag(xml, "modelName") ?: "Sonos"
                val udn = SonosXmlBuilder.parseXmlTag(xml, "UDN") ?: url.host

                SonosDevice(
                    udn = udn,
                    roomName = roomName,
                    modelName = modelName,
                    ipAddress = url.host,
                    port = if (url.port > 0) url.port else 1400
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to parse device description $locationUrl: ${e.message}")
            null
        }
    }
}
