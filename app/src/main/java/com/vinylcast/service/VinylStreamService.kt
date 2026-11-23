package com.vinylcast.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.vinylcast.MainActivity
import com.vinylcast.R
import com.vinylcast.audio.AudioSignalDetector
import com.vinylcast.audio.AudioStreamServer
import com.vinylcast.audio.UsbAudioDeviceHelper
import com.vinylcast.audio.WavStreamHeader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.sqrt

class VinylStreamService : Service() {

    companion object {
        private const val TAG = "VinylStreamService"
        private const val CHANNEL_ID = "vinylcast_stream"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.vinylcast.action.START"
        const val ACTION_STOP = "com.vinylcast.action.STOP"
        const val EXTRA_PORT = "extra_port"
        const val EXTRA_DEVICE_ID = "extra_device_id"
        const val EXTRA_ROOM_NAME = "extra_room_name"

        fun startService(
            context: Context,
            port: Int,
            preferredDeviceId: Int?,
            roomName: String?
        ) {
            val intent = Intent(context, VinylStreamService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_DEVICE_ID, preferredDeviceId ?: -1)
                putExtra(EXTRA_ROOM_NAME, roomName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, VinylStreamService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val isRunning = AtomicBoolean(false)
    private var httpServer: AudioStreamServer? = null
    private var captureThread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val signalDetector = AudioSignalDetector()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopCaptureAndServer()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val port = intent.getIntExtra(EXTRA_PORT, 8765)
                val deviceId = intent.getIntExtra(EXTRA_DEVICE_ID, -1).takeIf { it > 0 }
                val roomName = intent.getStringExtra(EXTRA_ROOM_NAME) ?: "Sonos Playbar"
                startForeground(NOTIFICATION_ID, buildNotification("Streamt Line-In an $roomName"))
                startCaptureAndServer(port, deviceId)
            }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startCaptureAndServer(port: Int, preferredDeviceId: Int?) {
        if (isRunning.getAndSet(true)) return

        acquireLocks()
        signalDetector.reset()

        val server = AudioStreamServer(port).also {
            it.start()
            httpServer = it
        }

        val ip = server.getLocalWifiIpAddress()
        val url = if (ip != null) "http://$ip:$port/stream.wav" else null
        StreamController.setStreaming(true, url)

        captureThread = thread(name = "VinylAudioCapture", priority = Thread.MAX_PRIORITY) {
            val sampleRate = WavStreamHeader.SAMPLE_RATE
            val channelConfig = AudioFormat.CHANNEL_IN_STEREO
            val encoding = AudioFormat.ENCODING_PCM_16BIT
            val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
                .coerceAtLeast(8192)
            val bufferSize = minBuffer * 2

            var recorder = createAudioRecord(MediaRecorder.AudioSource.UNPROCESSED, sampleRate, channelConfig, encoding, bufferSize)
            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder?.release()
                recorder = createAudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, encoding, bufferSize)
            }

            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                StreamController.setError("Audio-Eingang konnte nicht initialisiert werden.")
                stopCaptureAndServer()
                stopSelf()
                return@thread
            }

            val usbHelper = UsbAudioDeviceHelper(this)
            val targetDevice = usbHelper.findBestInputDeviceInfo(preferredDeviceId)
            if (targetDevice != null) {
                val routed = recorder.setPreferredDevice(targetDevice)
                Log.i(TAG, "Preferred audio input: ${targetDevice.productName} (routed=$routed)")
            }

            val chunk = ByteArray(4096)
            try {
                recorder.startRecording()
                StreamController.setAudioCapturing(true)

                while (isRunning.get()) {
                    val read = recorder.read(chunk, 0, chunk.size)
                    if (read > 0) {
                        val validBytes = if (read == chunk.size) chunk else chunk.copyOf(read)
                        server.broadcastPcm(validBytes)
                        updateVuAndSignal(validBytes, read)
                    } else if (read < 0) {
                        Log.w(TAG, "AudioRecord read error: $read")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Capture loop error: ${e.message}", e)
                StreamController.setError("Aufnahme-Fehler: ${e.message}")
            } finally {
                StreamController.setAudioCapturing(false)
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(
        source: Int,
        sampleRate: Int,
        channelConfig: Int,
        encoding: Int,
        bufferSize: Int
    ): AudioRecord? {
        return try {
            AudioRecord(source, sampleRate, channelConfig, encoding, bufferSize)
        } catch (_: Exception) {
            null
        }
    }

    private fun updateVuAndSignal(pcm: ByteArray, length: Int) {
        val samples = length / 4 // 2 channels * 2 bytes
        if (samples <= 0) return
        val bb = ByteBuffer.wrap(pcm, 0, length).order(ByteOrder.LITTLE_ENDIAN)
        var sumSqL = 0.0
        var sumSqR = 0.0
        for (i in 0 until samples) {
            val l = bb.short / 32768.0
            val r = bb.short / 32768.0
            sumSqL += l * l
            sumSqR += r * r
        }
        val rmsL = sqrt(sumSqL / samples).toFloat().coerceIn(0f, 1f)
        val rmsR = sqrt(sumSqR / samples).toFloat().coerceIn(0f, 1f)
        val detected = signalDetector.update(rmsL, rmsR)
        StreamController.updateLevels(rmsL, rmsR, detected)
    }

    private fun stopCaptureAndServer() {
        if (!isRunning.getAndSet(false)) return
        signalDetector.reset()
        StreamController.setStreaming(false, null)
        StreamController.setAudioCapturing(false)
        httpServer?.stop()
        httpServer = null
        captureThread?.interrupt()
        captureThread = null
        releaseLocks()
    }

    private fun acquireLocks() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VinylCast::StreamWakeLock").apply {
            acquire(6 * 60 * 60 * 1000L)
        }
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "VinylCast::WifiLock").apply {
            acquire()
        }
    }

    private fun releaseLocks() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
        runCatching { if (wifiLock?.isHeld == true) wifiLock?.release() }
        wifiLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, VinylStreamService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VinylCast")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openIntent)
            .addAction(0, "Stopp", stopIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopCaptureAndServer()
        super.onDestroy()
    }
}
