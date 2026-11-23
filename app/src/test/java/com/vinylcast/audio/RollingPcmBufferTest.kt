package com.vinylcast.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class RollingPcmBufferTest {

    @Test
    fun `buffer wraps around and preserves most recent bytes`() {
        val buf = RollingPcmBuffer(capacityBytes = 6)
        buf.write(byteArrayOf(1, 2, 3, 4))
        buf.write(byteArrayOf(5, 6, 7, 8))

        assertArrayEquals(byteArrayOf(3, 4, 5, 6, 7, 8), buf.snapshot())
    }

    @Test
    fun `buffer handles writes larger than capacity`() {
        val buf = RollingPcmBuffer(capacityBytes = 4)
        buf.write(byteArrayOf(10, 20, 30, 40, 50, 60))

        assertArrayEquals(byteArrayOf(30, 40, 50, 60), buf.snapshot())
    }
}
