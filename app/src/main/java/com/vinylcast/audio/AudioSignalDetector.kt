package com.vinylcast.audio

import kotlin.math.max

/**
 * Detects whether meaningful audio is playing on Line-In using RMS threshold and hold hysteresis.
 */
class AudioSignalDetector(
    private val thresholdRms: Float = 0.004f,
    private val attackHoldMs: Long = 150L,
    private val silenceHoldMs: Long = 2000L
) {
    private var isPlaying = false
    private var aboveThresholdSinceMs: Long? = null
    private var belowThresholdSinceMs: Long? = null

    fun update(leftRms: Float, rightRms: Float, nowMs: Long = System.currentTimeMillis()): Boolean {
        val peak = max(leftRms, rightRms)
        if (peak >= thresholdRms) {
            belowThresholdSinceMs = null
            if (!isPlaying) {
                val since = aboveThresholdSinceMs ?: nowMs.also { aboveThresholdSinceMs = it }
                if (nowMs - since >= attackHoldMs) {
                    isPlaying = true
                }
            }
        } else {
            aboveThresholdSinceMs = null
            if (isPlaying) {
                val since = belowThresholdSinceMs ?: nowMs.also { belowThresholdSinceMs = it }
                if (nowMs - since >= silenceHoldMs) {
                    isPlaying = false
                }
            }
        }
        return isPlaying
    }

    fun reset() {
        isPlaying = false
        aboveThresholdSinceMs = null
        belowThresholdSinceMs = null
    }
}
