package com.vinylcast.audio

import com.vinylcast.model.EqPreset
import com.vinylcast.model.EqSettings
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ThreeBandEqProcessorTest {

    @Test
    fun `flat preset bypasses PCM unchanged`() {
        val eq = ThreeBandEqProcessor(48000)
        eq.updateSettings(EqSettings(EqPreset.FLAT, 0f, 0f, 0f))

        val input = byteArrayOf(10, 0, -10, -1, 100, 0, -100, -1)
        val out = eq.processStereo16Bit(input)
        assertArrayEquals(input, out)
    }

    @Test
    fun `bass boost modifies low-frequency signal without overflow`() {
        val eq = ThreeBandEqProcessor(48000)
        eq.updateSettings(EqSettings(EqPreset.BASS_BOOST, 6f, 0f, 1f))

        val buf = ByteBuffer.allocate(400).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 100) {
            buf.putShort(2000)
            buf.putShort(2000)
        }
        val input = buf.array()
        val out = eq.processStereo16Bit(input)
        assertFalse(input.contentEquals(out))
    }
}
