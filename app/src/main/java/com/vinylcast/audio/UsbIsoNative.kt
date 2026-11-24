package com.vinylcast.audio

object UsbIsoNative {
    init {
        System.loadLibrary("vinylcast_usb")
    }

    interface PcmCallback {
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
