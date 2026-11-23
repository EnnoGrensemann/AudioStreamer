package com.vinylcast.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavStreamHeaderTest {

    @Test
    fun `createHeader produces valid 44-byte RIFF WAVE header`() {
        val header = WavStreamHeader.createHeader(
            dataLengthBytes = 1024L,
            sampleRate = 44100,
            channels = 2,
            bitsPerSample = 16
        )

        assertEquals(44, header.size)
        assertEquals("RIFF", String(header.copyOfRange(0, 4), Charsets.US_ASCII))
        assertEquals("WAVE", String(header.copyOfRange(8, 12), Charsets.US_ASCII))
        assertEquals("fmt ", String(header.copyOfRange(12, 16), Charsets.US_ASCII))
        assertEquals("data", String(header.copyOfRange(36, 40), Charsets.US_ASCII))

        val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1024 + 36, bb.getInt(4))
        assertEquals(1.toShort(), bb.getShort(20)) // PCM
        assertEquals(2.toShort(), bb.getShort(22)) // Stereo
        assertEquals(44100, bb.getInt(24))
        assertEquals(44100 * 2 * 2, bb.getInt(28)) // ByteRate
        assertEquals(1024, bb.getInt(40))
    }

    @Test
    fun `wrapPcmAsWav prepends header to payload`() {
        val pcm = byteArrayOf(1, 2, 3, 4, 5, 6)
        val wav = WavStreamHeader.wrapPcmAsWav(pcm)
        assertEquals(50, wav.size)
        assertTrue(wav.copyOfRange(44, 50).contentEquals(pcm))
    }
}
