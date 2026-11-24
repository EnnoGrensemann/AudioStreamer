package com.vinylcast.audio

import androidx.annotation.Keep

@Keep
object UsbIsoNative {
    init {
        System.loadLibrary("vinylcast_usb")
    }

    @Keep
    interface PcmCallback {
        @Keep
        fun onPcmData(data: ByteArray, length: Int)
    }

    external fun startIsoCapture(
        fd: Int,
        endpointAddr: Int,
        maxPacketSize: Int,
        subframeSize: Int,
        callback: PcmCallback
    ): Int

    external fun stopIsoCapture()
}
