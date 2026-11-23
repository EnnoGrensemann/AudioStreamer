package com.vinylcast.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavStreamHeader {
    const val SAMPLE_RATE = 44100
    const val CHANNELS = 2
    const val BITS_PER_SAMPLE = 16
    const val HEADER_SIZE = 44

    /**
     * Builds a 44-byte RIFF/WAVE header.
     * For continuous live streaming, pass [dataLengthBytes] = 0x7FFF0000 (approx 2 GB).
     */
    fun createHeader(
        dataLengthBytes: Long = 0x7FFF0000L,
        sampleRate: Int = SAMPLE_RATE,
        channels: Int = CHANNELS,
        bitsPerSample: Int = BITS_PER_SAMPLE
    ): ByteArray {
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = channels * (bitsPerSample / 8)
        val riffChunkSize = (dataLengthBytes + 36L).coerceAtMost(0xFFFFFFFFL).toInt()
        val dataChunkSize = dataLengthBytes.coerceAtMost(0xFFFFFFFFL).toInt()

        val buffer = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        // RIFF chunk descriptor
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(riffChunkSize)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        // fmt sub-chunk
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16) // PCM sub-chunk size
        buffer.putShort(1.toShort()) // AudioFormat = 1 (PCM)
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())
        // data sub-chunk
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataChunkSize)
        return buffer.array()
    }

    fun wrapPcmAsWav(
        pcmData: ByteArray,
        sampleRate: Int = SAMPLE_RATE,
        channels: Int = CHANNELS,
        bitsPerSample: Int = BITS_PER_SAMPLE
    ): ByteArray {
        val header = createHeader(
            dataLengthBytes = pcmData.size.toLong(),
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = bitsPerSample
        )
        return header + pcmData
    }
}
