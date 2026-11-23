package com.vinylcast.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSignalDetectorTest {

    @Test
    fun `detects playback after attack hold and drops after silence hold`() {
        val detector = AudioSignalDetector(
            thresholdRms = 0.01f,
            attackHoldMs = 150L,
            silenceHoldMs = 1000L
        )

        assertFalse(detector.update(0.02f, 0.02f, nowMs = 0L))
        assertFalse(detector.update(0.02f, 0.02f, nowMs = 100L))
        assertTrue(detector.update(0.02f, 0.02f, nowMs = 200L))

        // Brief silence does not immediately drop playback state
        assertTrue(detector.update(0.001f, 0.001f, nowMs = 500L))
        assertTrue(detector.update(0.001f, 0.001f, nowMs = 1200L))
        assertFalse(detector.update(0.001f, 0.001f, nowMs = 1600L))
    }
}
