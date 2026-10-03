package org.sakshi.processing.stt

import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Turns one audio clip into a transcript with time ranges, or says plainly why it did not.
 *
 * Order of work: refuse on a hot phone, refuse over-long clips from the container duration before decoding, decode to
 * mono 16 kHz PCM in memory, declare silence from the signal level without loading the model, then load the verified
 * model, transcribe, and release it. Silence, unsupported audio and refusals are results, never empty transcripts.
 *
 * A confidence on a segment is the engine's own, uncalibrated signal. Nothing here translates: the model writes down
 * what it hears in the language it hears.
 */
public class SttProcessor(
    private val sessions: ModelSessionManager,
    private val thermal: ThermalStatusProvider,
    private val cancelDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    /**
     * Transcribes [source]. Cancelling the calling coroutine aborts the native run and throws as usual;
     * [cancelCurrent] aborts it and returns [SttResult.Cancelled].
     */
    public suspend fun transcribe(source: AudioSource, options: SttOptions): SttResult {
        val started = System.nanoTime()
        if (thermal.status() >= ThermalStatusProvider.SEVERE) return SttResult.Failed(SttFailure.THERMAL)

        val declared = source.containerDurationMs()
        if (declared != null && declared > options.maxDurationMs + AudioSource.DURATION_TOLERANCE_MS) {
            return SttResult.TooLong(declared, options.maxDurationMs)
        }

        val decoded = when (val decode = source.decode(options.maxDurationMs)) {
            is AudioDecodeResult.Decoded -> decode.audio
            is AudioDecodeResult.TooLong -> return SttResult.TooLong(decode.durationMs, options.maxDurationMs)
            is AudioDecodeResult.Unsupported -> return SttResult.Unsupported(decode.reason)
            AudioDecodeResult.OutOfMemory -> return SttResult.Failed(SttFailure.OUT_OF_MEMORY)
            AudioDecodeResult.Failed -> return SttResult.Failed(SttFailure.DECODE_FAILED)
        }
        val decodeMs = elapsedMs(started)
        val durationMs = decoded.durationMs
        if (!SignalGate.hasSignal(decoded.samples)) return SttResult.NoSpeech(durationMs, NoSpeechBasis.SIGNAL_LEVEL)
        if (thermal.status() >= ThermalStatusProvider.SEVERE) return SttResult.Failed(SttFailure.THERMAL)

        val pcm = SignalGate.padToMinimum(decoded.samples)
        val session = try {
            sessions.withModel { loaded -> runModel(loaded, pcm, options) }
        } catch (e: OutOfMemoryError) {
            return SttResult.Failed(SttFailure.OUT_OF_MEMORY)
        }
        return when (session) {
            is SessionResult.Unavailable -> SttResult.ModelUnavailable(session.reason)
            SessionResult.OutOfMemory -> SttResult.Failed(SttFailure.OUT_OF_MEMORY)
            is SessionResult.Ran -> finish(session.value, durationMs, decodeMs, started)
        }
    }

    /** Asks the transcription that is running now, if any, to stop; its [transcribe] then returns [SttResult.Cancelled]. */
    public fun cancelCurrent() {
        sessions.cancelActive()
    }

    private suspend fun runModel(loaded: LoadedModel, pcm: FloatArray, options: SttOptions): Timed =
        coroutineScope {
            // Undispatched, so the watcher is already waiting when the blocking native call starts.
            val watcher = launch(cancelDispatcher, start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    if (!this@coroutineScope.isActive) loaded.model.cancel()
                }
            }
            val inferenceStart = System.nanoTime()
            val run = try {
                loaded.model.transcribe(pcm, options.language, options.threads)
            } catch (e: OutOfMemoryError) {
                EngineRun.OutOfMemory
            } finally {
                watcher.cancel()
            }
            Timed(run, loaded.loadMs, elapsedMs(inferenceStart))
        }

    private fun finish(timed: Timed, durationMs: Long, decodeMs: Long, started: Long): SttResult =
        when (val run = timed.run) {
            EngineRun.Cancelled -> SttResult.Cancelled
            EngineRun.OutOfMemory -> SttResult.Failed(SttFailure.OUT_OF_MEMORY)
            EngineRun.Failed -> SttResult.Failed(SttFailure.ENGINE_FAILED)
            is EngineRun.Done -> {
                val segments = TranscriptFilter.keep(run.segments, durationMs)
                if (segments.isEmpty()) {
                    SttResult.NoSpeech(durationMs, NoSpeechBasis.MODEL)
                } else {
                    SttResult.Success(
                        segments = segments,
                        language = run.language,
                        audioDurationMs = durationMs,
                        modelId = sessions.spec.id,
                        modelSha256 = sessions.spec.sha256,
                        engineVersion = sessions.engineVersion,
                        timings = SttTimings(decodeMs, timed.loadMs, timed.inferenceMs, elapsedMs(started)),
                    )
                }
            }
        }

    private class Timed(val run: EngineRun, val loadMs: Long, val inferenceMs: Long)

    private fun elapsedMs(since: Long): Long = (System.nanoTime() - since) / NANOS_PER_MILLI

    private companion object {
        const val NANOS_PER_MILLI: Long = 1_000_000L
    }
}

/**
 * Cheap checks around the model. The thresholds are demonstration settings, not calibrated: quiet real speech below
 * the signal level is called silence, and the model's own no-speech estimate is used as it is.
 */
internal object SignalGate {
    /** Block RMS of 0.00316 is about -50 dBFS. */
    private const val SILENCE_RMS: Double = 0.00316
    private const val BLOCK_SAMPLES: Int = MODEL_SAMPLE_RATE_HZ / 10
    private const val MIN_MODEL_INPUT_MS: Int = 1100

    fun hasSignal(samples: FloatArray): Boolean {
        var start = 0
        while (start < samples.size) {
            val end = min(samples.size, start + BLOCK_SAMPLES)
            var energy = 0.0
            for (i in start until end) energy += samples[i].toDouble() * samples[i]
            if (sqrt(energy / (end - start)) >= SILENCE_RMS) return true
            start = end
        }
        return false
    }

    /** The model refuses input under one second; short clips are padded with silence, which adds no time to the clip. */
    fun padToMinimum(samples: FloatArray): FloatArray {
        val minimum = MIN_MODEL_INPUT_MS * MODEL_SAMPLE_RATE_HZ / 1000
        return if (samples.size >= minimum) samples else samples.copyOf(minimum)
    }
}

/** Drops what is not speech: segments the model itself thinks are silence, bracketed sound labels, and anything past the clip. */
internal object TranscriptFilter {
    /** A demonstration setting, not calibrated. */
    const val NO_SPEECH_LIMIT: Float = 0.6f

    private val SOUND_LABEL: Regex = Regex("""^[\[(*].*[\])*]$""")

    fun keep(raw: List<RawSegment>, audioDurationMs: Long): List<TranscriptSegment> =
        raw.mapNotNull { segment ->
            val text = segment.text.trim()
            val speech = text.isNotEmpty() &&
                !SOUND_LABEL.matches(text) &&
                segment.noSpeechProbability < NO_SPEECH_LIMIT &&
                segment.startMs < audioDurationMs
            if (!speech) {
                null
            } else {
                val start = segment.startMs.coerceAtLeast(0)
                TranscriptSegment(text, start, segment.endMs.coerceIn(start, audioDurationMs), segment.meanTokenProbability)
            }
        }
}
