package com.vinylcast.audio

/**
 * Thread-safe circular buffer holding the most recent [capacityBytes] of PCM audio.
 * Default capacity is ~8 seconds of 44.1 kHz, 16-bit stereo audio (1,411,200 bytes).
 */
class RollingPcmBuffer(
    private val capacityBytes: Int = WavStreamHeader.SAMPLE_RATE * WavStreamHeader.CHANNELS * 2 * 8
) {
    private val buffer = ByteArray(capacityBytes)
    private var writePos = 0
    private var totalWritten = 0L

    @Synchronized
    fun write(data: ByteArray, length: Int = data.size) {
        if (length <= 0) return
        val toWrite = length.coerceAtMost(data.size)
        if (toWrite >= capacityBytes) {
            System.arraycopy(data, toWrite - capacityBytes, buffer, 0, capacityBytes)
            writePos = 0
            totalWritten += toWrite
            return
        }

        val firstChunk = (capacityBytes - writePos).coerceAtMost(toWrite)
        System.arraycopy(data, 0, buffer, writePos, firstChunk)
        val remaining = toWrite - firstChunk
        if (remaining > 0) {
            System.arraycopy(data, firstChunk, buffer, 0, remaining)
        }
        writePos = (writePos + toWrite) % capacityBytes
        totalWritten += toWrite
    }

    @Synchronized
    fun snapshot(): ByteArray {
        val available = totalWritten.coerceAtMost(capacityBytes.toLong()).toInt()
        if (available == 0) return ByteArray(0)
        val result = ByteArray(available)
        if (totalWritten < capacityBytes) {
            System.arraycopy(buffer, 0, result, 0, available)
        } else {
            val tailLen = capacityBytes - writePos
            System.arraycopy(buffer, writePos, result, 0, tailLen)
            if (writePos > 0) {
                System.arraycopy(buffer, 0, result, tailLen, writePos)
            }
        }
        return result
    }

    @Synchronized
    fun clear() {
        writePos = 0
        totalWritten = 0L
    }
}
