package org.sakshi.processing.stt

/** What an engine returns for one run. Segment times are in milliseconds from the start of the PCM. */
public class RawSegment(
    public val text: String,
    public val startMs: Long,
    public val endMs: Long,
    public val noSpeechProbability: Float,
    public val meanTokenProbability: Float?,
)

public sealed interface EngineRun {
    public class Done(public val segments: List<RawSegment>, public val language: String?) : EngineRun

    public data object Cancelled : EngineRun

    public data object OutOfMemory : EngineRun

    public data object Failed : EngineRun
}

/** A model held in memory. Not thread-safe except for [cancel], which may be called from any thread while [transcribe] runs. */
public interface SpeechModel : AutoCloseable {
    /** Runs the model over [pcm] (mono, 16 kHz). [language] null means detect. Never translates. */
    public fun transcribe(pcm: FloatArray, language: String?, threads: Int): EngineRun

    /** Asks a running [transcribe] to stop. Does nothing once the model is closed. */
    public fun cancel()

    /** Frees the model's memory. */
    override fun close()
}

public sealed interface EngineLoad {
    public class Loaded(public val model: SpeechModel) : EngineLoad

    public data object LibraryMissing : EngineLoad

    public data object OutOfMemory : EngineLoad

    public data object Failed : EngineLoad
}

/** Creates [SpeechModel]s from a model file whose hash has already been verified. */
public interface SpeechEngine {
    /** Name and version of the engine, recorded with every transcript. */
    public val version: String

    public fun load(modelPath: String): EngineLoad
}
