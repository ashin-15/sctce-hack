package org.sakshi.processing.stt

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResamplerTest {
    private fun rms(samples: FloatArray, from: Int, to: Int): Double {
        var sum = 0.0
        for (i in from until to) sum += samples[i].toDouble() * samples[i]
        return sqrt(sum / (to - from))
    }

    @Test
    fun lengthFollowsTheRatioForCommonRates() {
        listOf(48_000, 44_100, 32_000, 8_000, 22_050).forEach { rate ->
            val out = Resampler.resample(sine(440.0, 2.0, rate), rate, 16_000)
            assertEquals(32_000, out.size, "rate $rate")
        }
    }

    @Test
    fun speechBandSineKeepsItsAmplitude() {
        listOf(48_000, 44_100, 8_000).forEach { rate ->
            val out = Resampler.resample(sine(440.0, 1.0, rate, amplitude = 0.5), rate, 16_000)
            // RMS of a 0.5 amplitude sine is 0.3536; edges are skipped.
            assertTrue(abs(rms(out, 1_000, 15_000) - 0.3536) < 0.01, "rate $rate rms ${rms(out, 1_000, 15_000)}")
            val peak = out.slice(1_000 until 15_000).maxOf { abs(it) }
            assertTrue(abs(peak - 0.5f) < 0.02f, "rate $rate peak $peak")
        }
    }

    @Test
    fun energyAboveTheNewNyquistFrequencyIsRemovedNotFolded() {
        // 20 kHz at 48 kHz would alias to 4 kHz at 16 kHz without a low-pass.
        val out = Resampler.resample(sine(20_000.0, 1.0, 48_000, amplitude = 0.5), 48_000, 16_000)
        assertTrue(rms(out, 1_000, 15_000) < 0.02, "aliased rms ${rms(out, 1_000, 15_000)}")
    }

    @Test
    fun sameRateReturnsAnIndependentCopy() {
        val input = sine(440.0, 0.1, 16_000)
        val out = Resampler.resample(input, 16_000, 16_000)
        assertTrue(input.contentEquals(out))
        out[0] = 9f
        assertTrue(input[0] != 9f)
    }

    @Test
    fun emptyInputGivesEmptyOutput() {
        assertEquals(0, Resampler.resample(FloatArray(0), 44_100, 16_000).size)
    }

    @Test
    fun downmixAveragesChannels() {
        val mono = PcmConversion.downmix(floatArrayOf(1f, 0f, 0.5f, 0.5f, -1f, 1f), 2)
        assertTrue(mono.contentEquals(floatArrayOf(0.5f, 0.5f, 0f)))
    }
}
