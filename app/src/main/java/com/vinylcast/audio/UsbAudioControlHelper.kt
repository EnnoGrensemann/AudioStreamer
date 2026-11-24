package com.vinylcast.audio

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Inspects USB Audio Class (UAC1) descriptors via UsbManager and attempts to switch
 * a USB soundcard's Selector Unit to Line-In / unmute Feature Units via Endpoint 0.
 */
class UsbAudioControlHelper(private val context: Context) {

    companion object {
        private const val TAG = "UsbAudioControlHelper"
        private const val ACTION_USB_PERMISSION = "com.vinylcast.USB_PERMISSION"
        private const val CS_INTERFACE = 0x24
        private const val UAC_INPUT_TERMINAL = 0x02
        private const val UAC_SELECTOR_UNIT = 0x05
        private const val UAC_FEATURE_UNIT = 0x06
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

    fun findUsbAudioDevice(): UsbDevice? {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return usbManager.deviceList.values.firstOrNull { device ->
            (0 until device.interfaceCount).any { idx ->
                device.getInterface(idx).interfaceClass == UsbConstants.USB_CLASS_AUDIO
            }
        }
    }

    suspend fun requestPermissionAndInspect(): String {
        return try {
            requestPermissionAndInspectInternal()
        } catch (t: Throwable) {
            val msg = "USB-Fehler: ${t.javaClass.simpleName}: ${t.message}"
            com.vinylcast.util.DebugLog.log("UAC", msg)
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
            val raw = conn.rawDescriptors ?: return "Keine USB-Deskriptoren lesbar."
            val report = StringBuilder()
            report.append("Gerät: ${device.productName ?: device.deviceName}\n")

            val terminals = mutableListOf<UacInputTerminal>()
            var selectorUnitId: Int? = null
            var selectorPins: List<Int> = emptyList()
            var audioControlInterface = 0

            // Walk USB descriptors
            var i = 0
            while (i + 1 < raw.size) {
                val len = raw[i].toInt() and 0xFF
                if (len < 2 || i + len > raw.size) break
                val type = raw[i + 1].toInt() and 0xFF

                // Standard Interface Descriptor (type 4)
                if (type == 4 && len >= 9) {
                    val intfNum = raw[i + 2].toInt() and 0xFF
                    val intfClass = raw[i + 5].toInt() and 0xFF
                    val intfSubClass = raw[i + 6].toInt() and 0xFF
                    if (intfClass == 1 && intfSubClass == 1) {
                        audioControlInterface = intfNum
                    }
                }

                // Class-Specific Interface Descriptor (0x24)
                if (type == CS_INTERFACE && len >= 4) {
                    val subType = raw[i + 2].toInt() and 0xFF
                    when (subType) {
                        UAC_INPUT_TERMINAL -> if (len >= 8) {
                            val unitId = raw[i + 3].toInt() and 0xFF
                            val termType = (raw[i + 4].toInt() and 0xFF) or ((raw[i + 5].toInt() and 0xFF) shl 8)
                            terminals.add(UacInputTerminal(unitId, termType))
                            com.vinylcast.util.DebugLog.log("UAC", "InputTerminal id=$unitId type=0x${termType.toString(16)}")
                        }
                        0x03 -> if (len >= 6) {
                            val unitId = raw[i + 3].toInt() and 0xFF
                            val termType = (raw[i + 4].toInt() and 0xFF) or ((raw[i + 5].toInt() and 0xFF) shl 8)
                            com.vinylcast.util.DebugLog.log("UAC", "OutputTerminal id=$unitId type=0x${termType.toString(16)}")
                        }
                        0x04 -> if (len >= 5) {
                            val unitId = raw[i + 3].toInt() and 0xFF
                            com.vinylcast.util.DebugLog.log("UAC", "MixerUnit id=$unitId")
                        }
                        UAC_SELECTOR_UNIT -> if (len >= 6) {
                            val unitId = raw[i + 3].toInt() and 0xFF
                            val nrPins = raw[i + 4].toInt() and 0xFF
                            val pins = (0 until nrPins).mapNotNull { p ->
                                if (i + 5 + p < raw.size) raw[i + 5 + p].toInt() and 0xFF else null
                            }
                            selectorUnitId = unitId
                            selectorPins = pins
                            com.vinylcast.util.DebugLog.log("UAC", "SelectorUnit id=$unitId pins=$pins")
                        }
                        UAC_FEATURE_UNIT -> if (len >= 6) {
                            val unitId = raw[i + 3].toInt() and 0xFF
                            val srcId = raw[i + 4].toInt() and 0xFF
                            val muteBuf = ByteArray(1)
                            val muteRes = conn.controlTransfer(0xA1, GET_CUR, 0x0100, (unitId shl 8) or audioControlInterface, muteBuf, 1, 500)
                            com.vinylcast.util.DebugLog.log("UAC", "FeatureUnit id=$unitId src=$srcId mute=${if (muteRes >= 1) muteBuf[0].toInt() else muteRes}")
                        }
                    }
                }
                i += len
            }

            if (terminals.isEmpty()) {
                report.append("Keine UAC-Eingänge gefunden.")
            } else {
                report.append("Eingänge: ${terminals.joinToString { "${it.unitId}:${it.label}" }}\n")
            }

            if (selectorUnitId != null && selectorPins.isNotEmpty()) {
                val lineTerminal = terminals.firstOrNull { it.isLineIn }
                val targetPinIndex = if (lineTerminal != null) {
                    val idx = selectorPins.indexOf(lineTerminal.unitId)
                    if (idx >= 0) idx + 1 else 2.coerceAtMost(selectorPins.size)
                } else {
                    2.coerceAtMost(selectorPins.size)
                }

                // Read current selector value
                val curBuf = ByteArray(1)
                val getRes = conn.controlTransfer(
                    0xA1, // Device-to-host | Class | Interface
                    GET_CUR,
                    0,
                    (selectorUnitId shl 8) or audioControlInterface,
                    curBuf,
                    1,
                    1000
                )
                report.append("Selector Unit $selectorUnitId (vorher=${if (getRes >= 1) curBuf[0].toInt() else getRes})")

                // Set selector to targetPinIndex
                val setBuf = byteArrayOf(targetPinIndex.toByte())
                val setRes = conn.controlTransfer(
                    0x21, // Host-to-device | Class | Interface
                    SET_CUR,
                    0,
                    (selectorUnitId shl 8) or audioControlInterface,
                    setBuf,
                    1,
                    1000
                )
                report.append(" -> gesetzt auf Pin $targetPinIndex (res=$setRes)")
            } else {
                report.append("Kein Selector-Unit vorhanden (Hardware-Routing fest).")
            }

            com.vinylcast.util.DebugLog.log("UAC", report.toString().replace("\n", " | "))
            Log.i(TAG, report.toString())
            report.toString()
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
