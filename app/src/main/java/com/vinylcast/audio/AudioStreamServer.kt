package com.vinylcast.audio

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Lightweight local HTTP server streaming live PCM audio as continuous WAV to Sonos.
 */
class AudioStreamServer(
    private val port: Int = 8765,
    private val sampleRate: Int = WavStreamHeader.SAMPLE_RATE,
    private val onClientCountChanged: (Int) -> Unit = {}
) {

    companion object {
        private const val TAG = "AudioStreamServer"
        private const val MAX_QUEUE_CHUNKS = 64
    }

    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private val clients = CopyOnWriteArrayList<ClientConnection>()

    private class ClientConnection(
        val socket: Socket,
        val queue: LinkedBlockingQueue<ByteArray> = LinkedBlockingQueue(MAX_QUEUE_CHUNKS)
    )

    fun start() {
        if (running.getAndSet(true)) return
        val socket = ServerSocket(port).apply {
            reuseAddress = true
        }
        serverSocket = socket

        acceptThread = thread(name = "VinylHttpAccept", isDaemon = true) {
            Log.i(TAG, "HTTP AudioStreamServer listening on port $port")
            while (running.get()) {
                try {
                    val client = socket.accept()
                    handleClient(client)
                } catch (e: Exception) {
                    if (running.get()) {
                        Log.w(TAG, "Accept error: ${e.message}")
                    }
                }
            }
        }
    }

    private fun handleClient(socket: Socket) {
        thread(name = "VinylHttpClient-${socket.inetAddress.hostAddress}", isDaemon = true) {
            var connection: ClientConnection? = null
            try {
                socket.tcpNoDelay = true
                socket.soTimeout = 10_000
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val requestLine = reader.readLine() ?: return@thread
                com.vinylcast.util.DebugLog.log("HTTP", "${socket.inetAddress.hostAddress}: $requestLine")
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }

                val out: OutputStream = socket.getOutputStream()
                if (requestLine.startsWith("HEAD ")) {
                    writeHeaders(out)
                    out.flush()
                    return@thread
                }

                if (!requestLine.contains("/stream.wav")) {
                    val body = "VinylCast Audio Streamer\n".toByteArray()
                    out.write(
                        ("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n")
                            .toByteArray()
                    )
                    out.write(body)
                    out.flush()
                    return@thread
                }

                writeHeaders(out)
                out.write(WavStreamHeader.createHeader(sampleRate = sampleRate))
                out.flush()

                connection = ClientConnection(socket)
                clients.add(connection)
                onClientCountChanged(clients.size)
                Log.i(TAG, "Sonos/Client connected: ${socket.inetAddress.hostAddress}")

                while (running.get() && !socket.isClosed) {
                    val chunk = connection.queue.take()
                    out.write(chunk)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Client disconnected: ${e.message}")
            } finally {
                connection?.let {
                    clients.remove(it)
                    onClientCountChanged(clients.size)
                }
                runCatching { socket.close() }
            }
        }
    }

    private fun writeHeaders(out: OutputStream) {
        val headers = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: audio/wav\r\n")
            append("Accept-Ranges: none\r\n")
            append("Cache-Control: no-cache, no-store\r\n")
            append("Pragma: no-cache\r\n")
            append("icy-name: VinylCast Line-In\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        out.write(headers.toByteArray(Charsets.US_ASCII))
    }

    fun broadcastPcm(pcmChunk: ByteArray) {
        if (!running.get() || clients.isEmpty()) return
        val copy = pcmChunk.copyOf()
        for (client in clients) {
            if (!client.queue.offer(copy)) {
                // Drop oldest chunk if client lags behind so live audio stays low-latency
                client.queue.poll()
                client.queue.offer(copy)
            }
        }
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        for (client in clients) {
            runCatching { client.socket.close() }
        }
        clients.clear()
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptThread = null
    }

    fun getLocalWifiIpAddress(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()
                ?.filter { it.isUp && !it.isLoopback && !it.isVirtual }
                ?.flatMap { it.inetAddresses.toList() }
                ?.firstOrNull { addr ->
                    !addr.isLoopbackAddress && addr is java.net.Inet4Address && isPrivateLan(addr)
                }
                ?.hostAddress
        } catch (_: Exception) {
            null
        }
    }

    private fun isPrivateLan(addr: InetAddress): Boolean {
        val bytes = addr.address
        if (bytes.size != 4) return false
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        return b0 == 10 || (b0 == 192 && b1 == 168) || (b0 == 172 && b1 in 16..31)
    }
}
