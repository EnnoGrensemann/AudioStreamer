package com.vinylcast.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.log10
import kotlin.math.max
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
        const val EXTRA_AUDIO_SOURCE = "extra_audio_source"
        const val EXTRA_GAIN = "extra_gain"

        fun startService(
            context: Context,
            port: Int,
            preferredDeviceId: Int?,
            roomName: String?,
            audioSource: String,
            gainMultiplier: Float
        ) {
            val intent = Intent(context, VinylStreamService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_DEVICE_ID, preferredDeviceId ?: -1)
                putExtra(EXTRA_ROOM_NAME, roomName)
                putExtra(EXTRA_AUDIO_SOURCE, audioSource)
                putExtra(EXTRA_GAIN, gainMultiplier)
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
    private val signalDetector = AudioSignalDetector(thresholdRms = 0.002f)

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
                val sourceName = intent.getStringExtra(EXTRA_AUDIO_SOURCE) ?: "AUTO"
                val gain = intent.getFloatExtra(EXTRA_GAIN, 1f).coerceIn(1f, 16f)
                startForeground(NOTIFICATION_ID, buildNotification("Streamt Line-In an $roomName"))
                startCaptureAndServer(port, deviceId, sourceName, gain)
            }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startCaptureAndServer(
        port: Int,
        preferredDeviceId: Int?,
        audioSourceName: String,
        gainMultiplier: Float
    ) {
        if (isRunning.getAndSet(true)) return

        acquireLocks()
        signalDetector.reset()

        val usbHelper = UsbAudioDeviceHelper(this)
        val targetDevice = usbHelper.findBestInputDeviceInfo(preferredDeviceId)

        val sampleRate = chooseSampleRate(targetDevice)
        val isStereo = chooseStereo(targetDevice)
        val channelConfig = if (isStereo) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO

        val server = AudioStreamServer(
            port = port,
            sampleRate = sampleRate,
            onClientCountChanged = { count -> StreamController.setConnectedClients(count) }
        ).also {
            it.start()
            httpServer = it
        }

        val ip = server.getLocalWifiIpAddress()
        val url = if (ip != null) "http://$ip:$port/stream.wav" else null
        StreamController.setStreaming(true, url)

        captureThread = thread(name = "VinylAudioCapture", priority = Thread.MAX_PRIORITY) {
            val encoding = AudioFormat.ENCODING_PCM_16BIT
            val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
                .coerceAtLeast(8192)
            val bufferSize = minBuffer * 2

            val recorder = createRecorderWithFallback(sampleRate, channelConfig, encoding, bufferSize, audioSourceName)
            if (recorder == null) {
                Log.e(TAG, "AudioRecord initialization failed")
                StreamController.setError("Audio-Eingang konnte nicht initialisiert werden ($sampleRate Hz).")
                stopCaptureAndServer()
                stopSelf()
                return@thread
            }

            if (targetDevice != null) {
                val routed = recorder.setPreferredDevice(targetDevice)
                Log.i(TAG, "Preferred audio input: ${targetDevice.productName} (routed=$routed)")
                com.vinylcast.util.DebugLog.log(
                    "Capture",
                    "target=${targetDevice.productName}(id=${targetDevice.id}) setPreferred=$routed rate=$sampleRate stereo=$isStereo src=$audioSourceName"
                )
            } else {
                com.vinylcast.util.DebugLog.log("Capture", "target=NULL rate=$sampleRate stereo=$isStereo src=$audioSourceName")
            }

            val devName = targetDevice?.productName?.toString()?.take(18) ?: "Standard"
            val chLabel = if (isStereo) "Stereo" else "Mono"
            val infoText = "${sampleRate / 1000} kHz · $chLabel · $devName"

            val chunk = ByteArray(4096)
            try {
                recorder.startRecording()
                val routedAfter = recorder.routedDevice
                com.vinylcast.util.DebugLog.log(
                    "Capture",
                    "recordingState=${recorder.recordingState} routedAfter=${routedAfter?.productName}(id=${routedAfter?.id},type=${routedAfter?.type})"
                )
                if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    StreamController.setError("AudioRecord startet nicht (Berechtigung / USB-Gerät prüfen).")
                    stopCaptureAndServer()
                    stopSelf()
                    return@thread
                }
                StreamController.setAudioCapturing(true, infoText)
                val captureStartedAtMs = System.currentTimeMillis()
                var lastPcmLogMs = 0L

                while (isRunning.get()) {
                    val read = recorder.read(chunk, 0, chunk.size)
                    if (read > 0) {
                        val validBytes = if (read == chunk.size) chunk else chunk.copyOf(read)
                        val stereoBytes = if (isStereo) validBytes else monoToStereo(validBytes)
                        val gainedBytes = if (gainMultiplier > 1.05f) applyGain(stereoBytes, gainMultiplier) else stereoBytes
                        server.broadcastPcm(gainedBytes)
                        val now = System.currentTimeMillis()
                        val detectSignal = now - captureStartedAtMs > 450L
                        updateVuAndSignal(gainedBytes, gainedBytes.size, detectSignal)
                        if (now - lastPcmLogMs >= 1500L) {
                            lastPcmLogMs = now
                            logPcmStats(gainedBytes)
                        }
                    } else if (read < 0) {
                        Log.w(TAG, "AudioRecord read error: $read")
                        StreamController.setError("Audio-Lesefehler ($read). USB-Adapter prüfen.")
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

    private fun chooseSampleRate(device: AudioDeviceInfo?): Int {
        val rates = device?.sampleRates?.toList().orEmpty()
        return when {
            48000 in rates -> 48000
            44100 in rates -> 44100
            rates.isNotEmpty() -> rates.first()
            else -> 48000
        }
    }

    private fun chooseStereo(device: AudioDeviceInfo?): Boolean {
        val counts = device?.channelCounts?.toList().orEmpty()
        return counts.isEmpty() || 2 in counts
    }

    @SuppressLint("MissingPermission")
    private fun createRecorderWithFallback(
        sampleRate: Int,
        channelConfig: Int,
        encoding: Int,
        bufferSize: Int,
        audioSourceName: String
    ): AudioRecord? {
        val sources = when (audioSourceName) {
            "MIC" -> listOf(MediaRecorder.AudioSource.MIC, MediaRecorder.AudioSource.DEFAULT)
            "UNPROCESSED" -> listOf(MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.DEFAULT)
            "CAMCORDER" -> listOf(MediaRecorder.AudioSource.CAMCORDER, MediaRecorder.AudioSource.DEFAULT)
            "DEFAULT" -> listOf(MediaRecorder.AudioSource.DEFAULT, MediaRecorder.AudioSource.MIC)
            else -> listOf(
                MediaRecorder.AudioSource.UNPROCESSED,
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.DEFAULT
            )
        }
        for (src in sources) {
            val rec = try {
                AudioRecord.Builder()
                    .setAudioSource(src)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelConfig)
                            .setEncoding(encoding)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .build()
            } catch (_: Exception) {
                null
            }
            if (rec != null && rec.state == AudioRecord.STATE_INITIALIZED) {
                Log.i(TAG, "Initialized AudioRecord with source=$src")
                return rec
            }
            rec?.release()
        }
        return null
    }

    private fun applyGain(pcm: ByteArray, gain: Float): ByteArray {
        val out = ByteArray(pcm.size)
        val inBuf = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
        val outBuf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        val count = pcm.size / 2
        for (i in 0 until count) {
            val sample = (inBuf.short * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            outBuf.putShort(sample.toShort())
        }
        return out
    }

    private fun logPcmStats(pcm: ByteArray) {
        val bb = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
        var minS = Int.MAX_VALUE
        var maxS = Int.MIN_VALUE
        val count = pcm.size / 2
        for (i in 0 until count) {
            val s = bb.short.toInt()
            if (s < minS) minS = s
            if (s > maxS) maxS = s
        }
        com.vinylcast.util.DebugLog.log("PCM", "samples=$count min=$minS max=$maxS")
    }

    private fun monoToStereo(mono: ByteArray): ByteArray {
        val samples = mono.size / 2
        val stereo = ByteArray(samples * 4)
        for (i in 0 until samples) {
            val lo = mono[i * 2]
            val hi = mono[i * 2 + 1]
            stereo[i * 4] = lo
            stereo[i * 4 + 1] = hi
            stereo[i * 4 + 2] = lo
            stereo[i * 4 + 3] = hi
        }
        return stereo
    }

    private fun updateVuAndSignal(pcm: ByteArray, length: Int, detectSignal: Boolean) {
        val samples = length / 4
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
        val peak = max(rmsL, rmsR)
        val db = if (peak > 0.0001f) 20f * log10(peak) else -60f
        val detected = if (detectSignal) signalDetector.update(rmsL, rmsR) else false
        StreamController.updateLevels(rmsL, rmsR, detected, db)
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
