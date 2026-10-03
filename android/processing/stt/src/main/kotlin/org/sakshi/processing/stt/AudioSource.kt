package org.sakshi.processing.stt

/** PCM ready for the speech model: mono, 16 kHz, floats in -1 to 1. [sourceSampleRate] and [sourceChannels] describe the original. */
public class DecodedAudio(
    public val samples: FloatArray,
    public val sourceSampleRate: Int,
    public val sourceChannels: Int,
) {
    public val durationMs: Long
        get() = samples.size * MS_PER_SECOND / MODEL_SAMPLE_RATE_HZ

    private companion object {
        const val MS_PER_SECOND: Long = 1000L
    }
}

public sealed interface AudioDecodeResult {
    public class Decoded(public val audio: DecodedAudio) : AudioDecodeResult

    /** The audio is longer than the cap; decoding stopped as soon as that was known. */
    public data class TooLong(public val durationMs: Long?) : AudioDecodeResult

    public data class Unsupported(public val reason: UnsupportedReason) : AudioDecodeResult

    public data object OutOfMemory : AudioDecodeResult

    public data object Failed : AudioDecodeResult
}

/**
 * Where the audio of one clip comes from. Implementations must keep decrypted bytes in memory only: no temporary file
 * of plaintext evidence may be written for a decoder (megaplan D-13).
 */
public interface AudioSource {
    /** The duration the container states, in milliseconds, or null when it does not say or cannot be read. */
    public suspend fun containerDurationMs(): Long?

    /**
     * Decodes to mono 16 kHz PCM. Gives up with [AudioDecodeResult.TooLong] as soon as the audio is known to exceed
     * [maxDurationMs] plus [DURATION_TOLERANCE_MS], without decoding the rest.
     */
    public suspend fun decode(maxDurationMs: Long): AudioDecodeResult

    public companion object {
        /** Containers and codec padding report durations a little off; refusals only start beyond this much over the cap. */
        public const val DURATION_TOLERANCE_MS: Long = 500L
    }
}

/** Audio that is already PCM in memory, resampled when it is not 16 kHz mono. Used by tests and by callers that decode themselves. */
public class PcmAudioSource(
    private val samples: FloatArray,
    private val sampleRate: Int = MODEL_SAMPLE_RATE_HZ,
    private val channels: Int = 1,
) : AudioSource {
    init {
        require(sampleRate > 0) { "sampleRate must be positive" }
        require(channels > 0) { "channels must be positive" }
        require(samples.size % channels == 0) { "samples must hold whole frames" }
    }

    private val frames: Int = samples.size / channels

    override suspend fun containerDurationMs(): Long = frames * MS_PER_SECOND / sampleRate

    override suspend fun decode(maxDurationMs: Long): AudioDecodeResult {
        val durationMs = containerDurationMs()
        if (durationMs > maxDurationMs + AudioSource.DURATION_TOLERANCE_MS) return AudioDecodeResult.TooLong(durationMs)
        val mono = PcmConversion.downmix(samples, channels)
        return AudioDecodeResult.Decoded(
            DecodedAudio(Resampler.resample(mono, sampleRate, MODEL_SAMPLE_RATE_HZ), sampleRate, channels),
        )
    }

    private companion object {
        const val MS_PER_SECOND: Long = 1000L
    }
}
