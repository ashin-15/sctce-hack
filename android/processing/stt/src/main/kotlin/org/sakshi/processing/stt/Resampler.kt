package org.sakshi.processing.stt

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Sample-format helpers that work in memory on whole clips. */
internal object PcmConversion {
    /** Averages the channels of interleaved [samples] into one. */
    fun downmix(samples: FloatArray, channels: Int): FloatArray {
        if (channels == 1) return samples
        val frames = samples.size / channels
        val mono = FloatArray(frames)
        for (frame in 0 until frames) {
            var sum = 0f
            for (channel in 0 until channels) sum += samples[frame * channels + channel]
            mono[frame] = sum / channels
        }
        return mono
    }
}

/**
 * Band-limited resampling with a Hann-windowed sinc kernel, so that down-sampling does not fold energy above the new
 * Nyquist frequency back into the speech band. The kernel is tabulated once per call.
 */
internal object Resampler {
    private const val ZERO_CROSSINGS: Int = 8
    private const val TABLE_STEPS_PER_SAMPLE: Int = 512

    fun resample(input: FloatArray, inputRate: Int, outputRate: Int): FloatArray {
        require(inputRate > 0 && outputRate > 0) { "Sample rates must be positive" }
        if (inputRate == outputRate) return input.copyOf()
        val outputSize = (input.size.toLong() * outputRate / inputRate).toInt()
        val output = FloatArray(outputSize)
        if (outputSize == 0) return output

        val step = inputRate.toDouble() / outputRate
        val cutoff = min(1.0, outputRate.toDouble() / inputRate)
        val halfWidth = ZERO_CROSSINGS / cutoff
        val table = kernelTable(cutoff, halfWidth)
        for (index in output.indices) {
            val centre = index * step
            val first = maxOf(0, floor(centre - halfWidth).toInt() + 1)
            val last = min(input.size - 1, floor(centre + halfWidth).toInt())
            var sum = 0.0
            var weights = 0.0
            for (position in first..last) {
                val weight = table[(abs(position - centre) * TABLE_STEPS_PER_SAMPLE).roundToInt()]
                sum += weight * input[position]
                weights += weight
            }
            output[index] = if (weights == 0.0) 0f else (sum / weights).toFloat()
        }
        return output
    }

    private fun kernelTable(cutoff: Double, halfWidth: Double): DoubleArray {
        val size = (halfWidth * TABLE_STEPS_PER_SAMPLE).toInt() + 2
        return DoubleArray(size) { step ->
            val distance = step.toDouble() / TABLE_STEPS_PER_SAMPLE
            if (distance >= halfWidth) {
                0.0
            } else {
                val x = PI * cutoff * distance
                val sinc = if (x == 0.0) 1.0 else sin(x) / x
                val window = 0.5 * (1.0 + cos(PI * distance / halfWidth))
                sinc * window
            }
        }
    }
}
