package com.vinylcast.audio

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Inspects USB Audio Class (UAC1) descriptors via UsbManager and locates
 * the dedicated Line-In AudioStreaming interface + Isochronous IN endpoint.
 */
class UsbAudioControlHelper(private val context: Context) {

    companion object {
        private const val TAG = "UsbAudioControlHelper"
        private const val ACTION_USB_PERMISSION = "com.vinylcast.USB_PERMISSION"
        private const val CS_INTERFACE = 0x24
        private const val CS_ENDPOINT = 0x25
        private const val UAC_INPUT_TERMINAL = 0x02
        private const val UAC_OUTPUT_TERMINAL = 0x03
        private const val UAC_SELECTOR_UNIT = 0x05
        private const val UAC_FEATURE_UNIT = 0x06
        private const val AS_GENERAL = 0x01
        private const val AS_FORMAT_TYPE = 0x02
        private const val SET_CUR = 0x01
        private const val GET_CUR = 0x81
    }

    data class UacInputTerminal(
        val unitId: Int,
        val terminalType: Int
    ) {
        val label: String
            get() = when (terminalType) {
                0x0201 -> "Mikrofon (0x0201)"
                0x0202 -> "Desktop-Mikrofon (0x0202)"
                0x0402 -> "Headset-Mikrofon (0x0402)"
                0x0601 -> "Analog/Line-In (0x0601)"
                0x0602 -> "Digital/Line-In (0x0602)"
                0x0603 -> "Line-Connector (0x0603)"
                else -> "Terminal 0x${terminalType.toString(16)}"
            }

        val isLineIn: Boolean
            get() = terminalType in 0x0600..0x06FF
    }

    data class LineInStreamConfig(
        val device: UsbDevice,
        val audioControlInterface: Int,
        val streamingInterfaceNum: Int,
        val alternateSetting: Int,
        val endpointAddress: Int,
        val maxPacketSize: Int,
        val channels: Int,
        val subframeSize: Int,
        val sampleRate: Int,
        val featureUnitId: Int?,
        val supportsSampleRateControl: Boolean
    )

    fun findUsbAudioDevice(): UsbDevice? {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return usbManager.deviceList.values.firstOrNull { device ->
            (0 until device.interfaceCount).any { idx ->
                device.getInterface(idx).interfaceClass == UsbConstants.USB_CLASS_AUDIO
            }
        }
    }

    fun hasUsbPermission(device: UsbDevice): Boolean {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return usbManager.hasPermission(device)
    }

    suspend fun ensureUsbPermission(device: UsbDevice): Boolean {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        if (usbManager.hasPermission(device)) return true
        return requestUsbPermission(usbManager, device)
    }

    fun findLineInStreamConfig(conn: UsbDeviceConnection, device: UsbDevice): LineInStreamConfig? {
        val raw = conn.rawDescriptors ?: return null

        val inputTerminals = mutableMapOf<Int, Int>() // unitId -> terminalType
        val outputTerminals = mutableMapOf<Int, Pair<Int, Int>>() // unitId -> (termType, sourceId)
        val featureUnits = mutableMapOf<Int, Int>() // unitId -> sourceId
        var audioControlInterface = 0

        data class StreamingAlt(
            val intfNum: Int,
            val altSetting: Int,
            var terminalLink: Int = -1,
            var channels: Int = 2,
            var subframeSize: Int = 2,
            var sampleRate: Int = 48000,
            var endpointAddr: Int = -1,
            var maxPacketSize: Int = 0,
            var supportsRateControl: Boolean = false
        )

        val streamingAlts = mutableListOf<StreamingAlt>()
        var currentIntfClass = -1
        var currentIntfSubClass = -1
        var currentAlt: StreamingAlt? = null

        var i = 0
        while (i + 1 < raw.size) {
            val len = raw[i].toInt() and 0xFF
            if (len < 2 || i + len > raw.size) break
            val type = raw[i + 1].toInt() and 0xFF

            when (type) {
                4 -> if (len >= 9) { // Standard Interface Descriptor
                    val intfNum = raw[i + 2].toInt() and 0xFF
                    val altSet = raw[i + 3].toInt() and 0xFF
                    currentIntfClass = raw[i + 5].toInt() and 0xFF
                    currentIntfSubClass = raw[i + 6].toInt() and 0xFF
                    if (currentIntfClass == 1 && currentIntfSubClass == 1) {
                        audioControlInterface = intfNum
                        currentAlt = null
                    } else if (currentIntfClass == 1 && currentIntfSubClass == 2 && altSet > 0) {
                        currentAlt = StreamingAlt(intfNum = intfNum, altSetting = altSet)
                        streamingAlts.add(currentAlt)
                    } else {
                        currentAlt = null
                    }
                }
                5 -> if (len >= 7 && currentAlt != null) { // Standard Endpoint Descriptor
                    val epAddr = raw[i + 2].toInt() and 0xFF
                    val bmAttr = raw[i + 3].toInt() and 0xFF
                    val maxPkt = (raw[i + 4].toInt() and 0xFF) or ((raw[i + 5].toInt() and 0xFF) shl 8)
                    val isIn = (epAddr and 0x80) != 0
                    val isIso = (bmAttr and 0x03) == 1
                    if (isIn && isIso) {
                        currentAlt.endpointAddr = epAddr
                        currentAlt.maxPacketSize = maxPkt and 0x07FF
                    }
                }
                CS_INTERFACE -> if (len >= 4) {
                    val subType = raw[i + 2].toInt() and 0xFF
                    if (currentIntfClass == 1 && currentIntfSubClass == 1) {
                        when (subType) {
                            UAC_INPUT_TERMINAL -> if (len >= 8) {
                                val unitId = raw[i + 3].toInt() and 0xFF
                                val termType = (raw[i + 4].toInt() and 0xFF) or ((raw[i + 5].toInt() and 0xFF) shl 8)
                                inputTerminals[unitId] = termType
                            }
                            UAC_OUTPUT_TERMINAL -> if (len >= 9) {
                                val unitId = raw[i + 3].toInt() and 0xFF
                                val termType = (raw[i + 4].toInt() and 0xFF) or ((raw[i + 5].toInt() and 0xFF) shl 8)
                                val srcId = raw[i + 7].toInt() and 0xFF
                                outputTerminals[unitId] = termType to srcId
                            }
                            UAC_FEATURE_UNIT -> if (len >= 6) {
                                val unitId = raw[i + 3].toInt() and 0xFF
                                val srcId = raw[i + 4].toInt() and 0xFF
                                featureUnits[unitId] = srcId
                            }
                        }
                    } else if (currentAlt != null) {
                        when (subType) {
                            AS_GENERAL -> if (len >= 5) {
                                currentAlt.terminalLink = raw[i + 3].toInt() and 0xFF
                            }
                            AS_FORMAT_TYPE -> if (len >= 11) {
                                val formatType = raw[i + 3].toInt() and 0xFF
                                if (formatType == 1) {
                                    currentAlt.channels = raw[i + 4].toInt() and 0xFF
                                    currentAlt.subframeSize = raw[i + 5].toInt() and 0xFF
                                    val nRates = raw[i + 7].toInt() and 0xFF
                                    if (nRates > 0) {
                                        val rates = (0 until nRates).mapNotNull { r ->
                                            val off = i + 8 + r * 3
                                            if (off + 2 < raw.size) {
                                                (raw[off].toInt() and 0xFF) or
                                                    ((raw[off + 1].toInt() and 0xFF) shl 8) or
                                                    ((raw[off + 2].toInt() and 0xFF) shl 16)
                                            } else null
                                        }
                                        currentAlt.sampleRate = when {
                                            48000 in rates -> 48000
                                            44100 in rates -> 44100
                                            rates.isNotEmpty() -> rates.first()
                                            else -> 48000
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                CS_ENDPOINT -> if (len >= 4 && currentAlt != null) {
                    val bmAttr = raw[i + 3].toInt() and 0xFF
                    currentAlt.supportsRateControl = (bmAttr and 0x01) != 0
                }
            }
            i += len
        }

        // Trace which OutputTerminal (USB Streaming 0x0101) connects back to a Line-In InputTerminal (0x06xx)
        var lineOutTermId: Int? = null
        var lineFeatureId: Int? = null
        for ((outId, pair) in outputTerminals) {
            val (outType, srcId) = pair
            if (outType != 0x0101) continue
            var cursor = srcId
            var foundFeature: Int? = null
            repeat(6) {
                val termType = inputTerminals[cursor]
                if (termType != null) {
                    if (termType in 0x0600..0x06FF) {
                        lineOutTermId = outId
                        lineFeatureId = foundFeature
                    }
                    return@repeat
                }
                val fSrc = featureUnits[cursor]
                if (fSrc != null) {
                    if (foundFeature == null) foundFeature = cursor
                    cursor = fSrc
                } else {
                    return@repeat
                }
            }
        }

        if (lineOutTermId == null) {
            Log.d("UAC", "Kein dedizierter Line-In OutputTerminal gefunden.")
            return null
        }

        // Prefer 16-bit (subframeSize == 2) stereo alt setting for lineOutTermId
        val candidates = streamingAlts.filter {
            it.terminalLink == lineOutTermId && it.endpointAddr != -1 && it.maxPacketSize > 0
        }
        val best = candidates.firstOrNull { it.channels == 2 && it.subframeSize == 2 }
            ?: candidates.firstOrNull { it.channels == 2 }
            ?: candidates.firstOrNull()
            ?: return null

        Log.d(
            "UAC",
            "Line-In Stream gefunden: intf=${best.intfNum} alt=${best.altSetting} ep=0x${best.endpointAddr.toString(16)} " +
                "pkt=${best.maxPacketSize} ch=${best.channels} sub=${best.subframeSize} rate=${best.sampleRate} feat=$lineFeatureId"
        )

        return LineInStreamConfig(
            device = device,
            audioControlInterface = audioControlInterface,
            streamingInterfaceNum = best.intfNum,
            alternateSetting = best.altSetting,
            endpointAddress = best.endpointAddr,
            maxPacketSize = best.maxPacketSize,
            channels = best.channels,
            subframeSize = best.subframeSize,
            sampleRate = best.sampleRate,
            featureUnitId = lineFeatureId,
            supportsSampleRateControl = best.supportsRateControl
        )
    }

    fun findAndroidUsbInterface(device: UsbDevice, intfNum: Int, altSetting: Int): UsbInterface? {
        for (idx in 0 until device.interfaceCount) {
            val intf = device.getInterface(idx)
            if (intf.id == intfNum && intf.alternateSetting == altSetting) {
                return intf
            }
        }
        return null
    }

    fun unmuteAndConfigureLineIn(conn: UsbDeviceConnection, cfg: LineInStreamConfig) {
        cfg.featureUnitId?.let { featId ->
            // Unmute master (channel 0) and channels 1 & 2
            for (ch in 0..2) {
                val res = conn.controlTransfer(
                    0x21,
                    SET_CUR,
                    (0x01 shl 8) or ch, // MUTE_CONTROL
                    (featId shl 8) or cfg.audioControlInterface,
                    byteArrayOf(0),
                    1,
                    500
                )
                Log.d("UAC", "Unmute feat=$featId ch=$ch res=$res")
            }
        }

        if (cfg.supportsSampleRateControl) {
            val r = cfg.sampleRate
            val rateBytes = byteArrayOf(
                (r and 0xFF).toByte(),
                ((r shr 8) and 0xFF).toByte(),
                ((r shr 16) and 0xFF).toByte()
            )
            val res = conn.controlTransfer(
                0x22, // Host-to-device | Class | Endpoint
                SET_CUR,
                0x0100, // SAMPLING_FREQ_CONTROL
                cfg.endpointAddress,
                rateBytes,
                3,
                500
            )
            Log.d("UAC", "SetSampleRate ${cfg.sampleRate} ep=0x${cfg.endpointAddress.toString(16)} res=$res")
        }
    }

    suspend fun requestPermissionAndInspect(): String {
        return try {
            requestPermissionAndInspectInternal()
        } catch (t: Throwable) {
            val msg = "USB-Fehler: ${t.javaClass.simpleName}: ${t.message}"
            Log.d("UAC", msg)
            msg
        }
    }

    private suspend fun requestPermissionAndInspectInternal(): String {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val device = findUsbAudioDevice() ?: return "Kein USB-Audio-Gerät am USB-Port gefunden."

        if (!usbManager.hasPermission(device)) {
            val granted = requestUsbPermission(usbManager, device)
            if (!granted) {
                return "USB-Berechtigung verweigert."
            }
        }

        val conn = usbManager.openDevice(device)
            ?: return "USB-Gerät konnte nicht geöffnet werden."

        return try {
            val cfg = findLineInStreamConfig(conn, device)
            if (cfg != null) {
                "Line-In Stream erkannt: Interface ${cfg.streamingInterfaceNum} (Alt ${cfg.alternateSetting}), " +
                    "${cfg.sampleRate} Hz, ${cfg.channels}ch, ${cfg.subframeSize * 8}-bit, EP 0x${cfg.endpointAddress.toString(16)}"
            } else {
                "Kein dedizierter Line-In-Stream gefunden (Standard-AudioRecord wird genutzt)."
            }
        } finally {
            conn.close()
        }
    }

    private suspend fun requestUsbPermission(usbManager: UsbManager, device: UsbDevice): Boolean =
        suspendCancellableCoroutine { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (intent?.action == ACTION_USB_PERMISSION) {
                        runCatching { context.unregisterReceiver(this) }
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        if (cont.isActive) cont.resume(granted)
                    }
                }
            }
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, filter)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val piIntent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
            val pi = PendingIntent.getBroadcast(context, 0, piIntent, flags)
            usbManager.requestPermission(device, pi)
            cont.invokeOnCancellation {
                runCatching { context.unregisterReceiver(receiver) }
            }
        }
}
