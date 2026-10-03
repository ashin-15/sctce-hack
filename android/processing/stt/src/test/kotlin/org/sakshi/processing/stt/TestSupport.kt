package org.sakshi.processing.stt

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.sin

/** Mono sine wave of [seconds] at [rate], amplitude [amplitude]. */
internal fun sine(frequencyHz: Double, seconds: Double, rate: Int, amplitude: Double = 0.5): FloatArray =
    FloatArray((seconds * rate).toInt()) { (amplitude * sin(2.0 * PI * frequencyHz * it / rate)).toFloat() }

internal fun testSpec(content: ByteArray): ModelSpec = ModelSpec(
    id = "test-model",
    fileName = "test-model.bin",
    sha256 = Sha256.hex(java.security.MessageDigest.getInstance("SHA-256").digest(content)),
    sizeBytes = content.size.toLong(),
)

internal val TEST_MODEL_BYTES: ByteArray = ByteArray(2048) { (it * 7).toByte() }

internal fun writeModel(directory: File, content: ByteArray = TEST_MODEL_BYTES): File =
    File(directory, testSpec(content).fileName).also { it.writeBytes(content) }

internal fun raw(text: String, start: Long, end: Long, noSpeech: Float = 0.01f, probability: Float? = 0.9f): RawSegment =
    RawSegment(text, start, end, noSpeech, probability)

/** A model whose behaviour the test scripts. */
internal class FakeModel(private val script: (FloatArray) -> EngineRun) : SpeechModel {
    val started = CountDownLatch(1)
    val cancelRequested = CountDownLatch(1)
    var closed = false
        private set
    var lastLanguage: String? = "unset"
        private set
    var calls = 0
        private set

    override fun transcribe(pcm: FloatArray, language: String?, threads: Int): EngineRun {
        calls++
        lastLanguage = language
        started.countDown()
        return script(pcm)
    }

    override fun cancel() {
        cancelRequested.countDown()
    }

    override fun close() {
        closed = true
    }

    /** Blocks until [cancel] is called, then reports cancellation. */
    fun blockUntilCancelled(): EngineRun {
        check(cancelRequested.await(10, TimeUnit.SECONDS)) { "cancel was never requested" }
        return EngineRun.Cancelled
    }
}

internal class FakeEngine(private val outcome: () -> EngineLoad) : SpeechEngine {
    override val version: String = "fake-engine-1"
    val loads = AtomicInteger()
    val models = mutableListOf<FakeModel>()

    constructor(model: FakeModel) : this({ EngineLoad.Loaded(model) }) {
        models += model
    }

    override fun load(modelPath: String): EngineLoad {
        loads.incrementAndGet()
        return outcome()
    }
}

internal val SPEECH_LIKE: FloatArray = sine(220.0, 3.0, MODEL_SAMPLE_RATE_HZ)

internal fun done(vararg segments: RawSegment, language: String? = "en"): EngineRun =
    EngineRun.Done(segments.toList(), language)
