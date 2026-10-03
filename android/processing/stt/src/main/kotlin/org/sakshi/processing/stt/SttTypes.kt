package org.sakshi.processing.stt

/** The longest clip the speech lane accepts (megaplan: clips of at most 60 s, one at a time). */
public const val MAX_CLIP_DURATION_MS: Long = 60_000L

/** Sample rate of the PCM handed to the speech model. */
public const val MODEL_SAMPLE_RATE_HZ: Int = 16_000

/**
 * Bounds for one transcription.
 *
 * [language] is an ISO 639-1 code the user chose, or null to let the model detect it. Whatever the language, the model
 * transcribes in that language: translation is never requested.
 */
public data class SttOptions(
    public val language: String? = null,
    public val maxDurationMs: Long = MAX_CLIP_DURATION_MS,
    public val threads: Int = defaultThreadCount(),
) {
    init {
        require(maxDurationMs in 1..MAX_CLIP_DURATION_MS) { "maxDurationMs must be within 1 to $MAX_CLIP_DURATION_MS" }
        require(threads in 1..MAX_THREADS) { "threads must be within 1 to $MAX_THREADS" }
        require(language == null || language.matches(LANGUAGE_CODE)) { "language must be a two or three letter code" }
    }

    public companion object {
        private const val MAX_THREADS: Int = 16
        private const val PREFERRED_THREADS: Int = 4
        private val LANGUAGE_CODE: Regex = Regex("[a-z]{2,3}")

        /** Four threads at most: the big cores of a phone, not all of them, to keep heat and contention down. */
        public fun defaultThreadCount(): Int = Runtime.getRuntime().availableProcessors().coerceIn(1, PREFERRED_THREADS)
    }
}

/**
 * One stretch of transcribed speech. [startMs] and [endMs] are positions in the clip, in milliseconds, as the engine
 * reported them. [uncalibratedConfidence] is the mean probability the model gave to the tokens of [text], from 0 to 1,
 * or null when the engine gave none. It is an extraction-quality signal of the engine, not a probability that the words
 * are right, not calibrated by Sakshi, and not a measure of what was meant.
 */
public data class TranscriptSegment(
    public val text: String,
    public val startMs: Long,
    public val endMs: Long,
    public val uncalibratedConfidence: Float?,
)

/** Wall-clock milliseconds spent in each stage of one transcription. */
public data class SttTimings(
    public val decodeMs: Long,
    public val modelLoadMs: Long,
    public val inferenceMs: Long,
    public val totalMs: Long,
)

/** Why a model cannot be used. */
public enum class ModelUnavailableReason {
    /** No model file has been provisioned. */
    MISSING,

    /** The file does not hash to the pinned SHA-256; it was not loaded. */
    HASH_MISMATCH,

    /** The file passed the hash check but the engine could not load it. */
    LOAD_FAILED,

    /** The native engine library is not present in this build or for this CPU architecture. */
    NATIVE_LIBRARY_MISSING,
}

/** Why audio could not be turned into PCM. */
public enum class UnsupportedReason {
    /** The container could not be opened or read. */
    UNREADABLE,

    /** The container has no audio track. */
    NO_AUDIO_TRACK,

    /** No decoder on this phone handles the audio format. */
    CODEC_UNAVAILABLE,

    /** The PCM layout is one the decoder here does not convert. */
    PCM_FORMAT,
}

/** Why a transcription stopped without a result. */
public enum class SttFailure {
    OUT_OF_MEMORY,

    /** The phone reported a thermal status of SEVERE or worse; nothing was started. */
    THERMAL,

    /** The decoder failed part way through the audio. */
    DECODE_FAILED,

    /** The speech engine reported an error. */
    ENGINE_FAILED,
}

/** What decided that a clip holds no speech. */
public enum class NoSpeechBasis {
    /** The signal never rose above the silence level; the model was not run. */
    SIGNAL_LEVEL,

    /** The model ran and produced no segment it could stand behind. */
    MODEL,
}

public sealed interface SttResult {
    /**
     * Speech was transcribed. [language] is the language the model detected or was told, or null. [modelSha256] is the
     * hash the model file was verified against before it was loaded.
     */
    public data class Success(
        public val segments: List<TranscriptSegment>,
        public val language: String?,
        public val audioDurationMs: Long,
        public val modelId: String,
        public val modelSha256: String,
        public val engineVersion: String,
        public val timings: SttTimings,
    ) : SttResult

    /** Explicit silence: the clip holds no speech the model can transcribe. Nothing is written. */
    public data class NoSpeech(
        public val audioDurationMs: Long,
        public val basis: NoSpeechBasis,
    ) : SttResult

    /** The clip is longer than the cap. [durationMs] is what was learnt of its length; it was refused, not cut. */
    public data class TooLong(public val durationMs: Long?, public val maxDurationMs: Long) : SttResult

    public data class Unsupported(public val reason: UnsupportedReason) : SttResult

    public data class ModelUnavailable(public val reason: ModelUnavailableReason) : SttResult

    public data class Failed(public val reason: SttFailure) : SttResult

    /** [SttProcessor.cancelCurrent] stopped the work. Coroutine cancellation is thrown, not returned. */
    public data object Cancelled : SttResult
}
