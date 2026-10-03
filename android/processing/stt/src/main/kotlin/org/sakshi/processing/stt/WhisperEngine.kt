package org.sakshi.processing.stt

import java.nio.charset.StandardCharsets

/** JNI surface of `libsakshi_stt.so`. Handles are opaque; `0` means none. */
internal object WhisperNative {
    const val STATUS_OK: Int = 0
    const val STATUS_CANCELLED: Int = 2
    const val STATUS_OUT_OF_MEMORY: Int = 3

    external fun nativeVersion(): String

    external fun nativeLoad(path: String): Long

    external fun nativeFree(handle: Long)

    external fun nativeCancel(handle: Long)

    external fun nativeTranscribe(handle: Long, samples: FloatArray, language: String?, threads: Int): Int

    external fun nativeSegmentCount(handle: Long): Int

    external fun nativeSegmentText(handle: Long, index: Int): ByteArray

    external fun nativeSegmentStartMs(handle: Long, index: Int): Long

    external fun nativeSegmentEndMs(handle: Long, index: Int): Long

    external fun nativeSegmentNoSpeechProbability(handle: Long, index: Int): Float

    external fun nativeSegmentMeanTokenProbability(handle: Long, index: Int): Float

    external fun nativeLanguage(handle: Long): String?
}

/** The whisper.cpp engine, compiled for the CPU only and linked into this module's native library. */
public class WhisperEngine : SpeechEngine {
    override val version: String
        get() = "whisper.cpp-" + (if (libraryLoaded) WhisperNative.nativeVersion() else "unavailable")

    override fun load(modelPath: String): EngineLoad {
        if (!libraryLoaded) return EngineLoad.LibraryMissing
        val handle = try {
            WhisperNative.nativeLoad(modelPath)
        } catch (e: OutOfMemoryError) {
            return EngineLoad.OutOfMemory
        }
        return if (handle == 0L) EngineLoad.Failed else EngineLoad.Loaded(WhisperModel(handle))
    }

    private class WhisperModel(private var handle: Long) : SpeechModel {
        private val monitor = Any()

        override fun transcribe(pcm: FloatArray, language: String?, threads: Int): EngineRun {
            val live = synchronized(monitor) { handle }
            check(live != 0L) { "Model is closed" }
            return when (WhisperNative.nativeTranscribe(live, pcm, language, threads)) {
                WhisperNative.STATUS_OK -> EngineRun.Done(readSegments(live), WhisperNative.nativeLanguage(live))
                WhisperNative.STATUS_CANCELLED -> EngineRun.Cancelled
                WhisperNative.STATUS_OUT_OF_MEMORY -> EngineRun.OutOfMemory
                else -> EngineRun.Failed
            }
        }

        override fun cancel() {
            synchronized(monitor) {
                if (handle != 0L) WhisperNative.nativeCancel(handle)
            }
        }

        override fun close() {
            synchronized(monitor) {
                if (handle != 0L) WhisperNative.nativeFree(handle)
                handle = 0L
            }
        }

        private fun readSegments(live: Long): List<RawSegment> =
            (0 until WhisperNative.nativeSegmentCount(live)).map { index ->
                val probability = WhisperNative.nativeSegmentMeanTokenProbability(live, index)
                RawSegment(
                    text = String(WhisperNative.nativeSegmentText(live, index), StandardCharsets.UTF_8),
                    startMs = WhisperNative.nativeSegmentStartMs(live, index),
                    endMs = WhisperNative.nativeSegmentEndMs(live, index),
                    noSpeechProbability = WhisperNative.nativeSegmentNoSpeechProbability(live, index),
                    meanTokenProbability = probability.takeIf { it >= 0f },
                )
            }
    }

    private companion object {
        val libraryLoaded: Boolean = try {
            System.loadLibrary("sakshi_stt")
            true
        } catch (e: UnsatisfiedLinkError) {
            false
        }
    }
}
