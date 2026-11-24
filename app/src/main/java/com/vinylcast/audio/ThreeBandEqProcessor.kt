package com.vinylcast.audio

import com.vinylcast.model.EqSettings
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Real-time 3-band stereo biquad equalizer (Low-Shelf 120Hz, Peaking 1kHz, High-Shelf 6.5kHz).
 */
class ThreeBandEqProcessor(private var sampleRate: Int = 48000) {

    private class Biquad {
        var b0 = 1.0
        var b1 = 0.0
        var b2 = 0.0
        var a1 = 0.0
        var a2 = 0.0

        private var z1L = 0.0
        private var z2L = 0.0
        private var z1R = 0.0
        private var z2R = 0.0

        fun processLeft(x: Double): Double {
            val y = b0 * x + z1L
            z1L = b1 * x - a1 * y + z2L
            z2L = b2 * x - a2 * y
            return y
        }

        fun processRight(x: Double): Double {
            val y = b0 * x + z1R
            z1R = b1 * x - a1 * y + z2R
            z2R = b2 * x - a2 * y
            return y
        }

        fun setCoefficients(nb0: Double, nb1: Double, nb2: Double, na0: Double, na1: Double, na2: Double) {
            b0 = nb0 / na0
            b1 = nb1 / na0
            b2 = nb2 / na0
            a1 = na1 / na0
            a2 = na2 / na0
        }

        fun reset() {
            z1L = 0.0; z2L = 0.0
            z1R = 0.0; z2R = 0.0
        }
    }

    private val lowShelf = Biquad()
    private val midPeak = Biquad()
    private val highShelf = Biquad()

    @Volatile
    private var currentSettings = EqSettings()

    @Volatile
    private var bypass = true

    fun updateSettings(settings: EqSettings, newSampleRate: Int = sampleRate) {
        if (settings == currentSettings && newSampleRate == sampleRate) return
        sampleRate = newSampleRate
        currentSettings = settings

        val bass = settings.bassDb.coerceIn(-12f, 12f)
        val mid = settings.midDb.coerceIn(-12f, 12f)
        val treble = settings.trebleDb.coerceIn(-12f, 12f)

        bypass = abs(bass) < 0.05f && abs(mid) < 0.05f && abs(treble) < 0.05f
        if (bypass) {
            lowShelf.reset()
            midPeak.reset()
            highShelf.reset()
            return
        }

        computeLowShelf(120.0, bass.toDouble())
        computePeaking(1000.0, 0.9, mid.toDouble())
        computeHighShelf(6500.0, treble.toDouble())
    }

    fun processStereo16BitInPlace(pcm: ByteArray, length: Int = pcm.size) {
        if (bypass || length < 4) return
        var idx = 0
        val end = length - 3
        while (idx < end) {
            val lRaw = (pcm[idx].toInt() and 0xFF) or (pcm[idx + 1].toInt() shl 8)
            val rRaw = (pcm[idx + 2].toInt() and 0xFF) or (pcm[idx + 3].toInt() shl 8)

            val lOut = softLimit(highShelf.processLeft(midPeak.processLeft(lowShelf.processLeft(lRaw.toDouble()))))
            val rOut = softLimit(highShelf.processRight(midPeak.processRight(lowShelf.processRight(rRaw.toDouble()))))

            pcm[idx] = (lOut and 0xFF).toByte()
            pcm[idx + 1] = ((lOut shr 8) and 0xFF).toByte()
            pcm[idx + 2] = (rOut and 0xFF).toByte()
            pcm[idx + 3] = ((rOut shr 8) and 0xFF).toByte()
            idx += 4
        }
    }

    private fun softLimit(sample: Double): Int {
        val threshold = 28000.0
        val absVal = abs(sample)
        if (absVal <= threshold) return sample.toInt()
        val excess = absVal - threshold
        val compressed = threshold + (excess / (1.0 + excess / 4767.0))
        val limited = if (sample >= 0.0) compressed else -compressed
        return limited.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
    }

    fun processStereo16Bit(pcm: ByteArray, length: Int = pcm.size): ByteArray {
        if (bypass || length < 4) return pcm
        val out = pcm.copyOf(length)
        processStereo16BitInPlace(out, length)
        return out
    }

    private fun computeLowShelf(freq: Double, gainDb: Double) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freq / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / 2.0 * sqrt(2.0) // Q = 0.707
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

        lowShelf.setCoefficients(
            nb0 = a * ((a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha),
            nb1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0),
            nb2 = a * ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha),
            na0 = (a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha,
            na1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW0),
            na2 = (a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha
        )
    }

    private fun computePeaking(freq: Double, q: Double, gainDb: Double) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freq / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / (2.0 * q)

        midPeak.setCoefficients(
            nb0 = 1.0 + alpha * a,
            nb1 = -2.0 * cosW0,
            nb2 = 1.0 - alpha * a,
            na0 = 1.0 + alpha / a,
            na1 = -2.0 * cosW0,
            na2 = 1.0 - alpha / a
        )
    }

    private fun computeHighShelf(freq: Double, gainDb: Double) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freq / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / 2.0 * sqrt(2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

        highShelf.setCoefficients(
            nb0 = a * ((a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha),
            nb1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0),
            nb2 = a * ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha),
            na0 = (a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha,
            na1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosW0),
            na2 = (a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha
        )
    }
}
