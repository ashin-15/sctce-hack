package org.sakshi.processing.stt

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class SttProcessorTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val spec = testSpec(TEST_MODEL_BYTES)
    private var thermalStatus = 0

    private fun manager(engine: SpeechEngine, file: File = writeModel(folder.root), keepLoaded: Boolean = false) =
        ModelSessionManager(spec, file, engine, keepLoaded = keepLoaded)

    private fun processor(manager: ModelSessionManager) = SttProcessor(manager, ThermalStatusProvider { thermalStatus })

    private class CountingSource(
        private val declaredMs: Long?,
        private val samples: FloatArray = SPEECH_LIKE,
    ) : AudioSource {
        var decodes = 0

        override suspend fun containerDurationMs(): Long? = declaredMs

        override suspend fun decode(maxDurationMs: Long): AudioDecodeResult {
            decodes++
            return AudioDecodeResult.Decoded(DecodedAudio(samples, MODEL_SAMPLE_RATE_HZ, 1))
        }
    }

    private val options = SttOptions()

    @Test
    fun successCarriesSegmentsLanguageAndPinnedModelIdentity() = runBlocking<Unit> {
        val model = FakeModel { done(raw(" Hello there. ", 0, 2_000), raw("Second one", 2_000, 2_900, probability = null)) }
        val result = processor(manager(FakeEngine(model))).transcribe(CountingSource(null), options)
        val success = assertIs<SttResult.Success>(result)
        assertEquals(listOf("Hello there.", "Second one"), success.segments.map { it.text })
        assertEquals(0.9f, success.segments[0].uncalibratedConfidence)
        assertNull(success.segments[1].uncalibratedConfidence)
        assertEquals("en", success.language)
        assertEquals(3_000L, success.audioDurationMs)
        assertEquals(spec.id, success.modelId)
        assertEquals(spec.sha256, success.modelSha256)
        assertEquals("fake-engine-1", success.engineVersion)
        assertEquals(2_900L, success.segments[1].endMs)
    }

    @Test
    fun theModelIsToldTheLanguageOrNullAndNeverATranslationTarget() = runBlocking<Unit> {
        val model = FakeModel { done(raw("x", 0, 1_000)) }
        val processor = processor(manager(FakeEngine { EngineLoad.Loaded(model) }))
        processor.transcribe(CountingSource(null), SttOptions(language = "ml"))
        assertEquals("ml", model.lastLanguage)
        processor.transcribe(CountingSource(null), SttOptions(language = null))
        assertNull(model.lastLanguage)
    }

    @Test
    fun silenceIsExplicitAndTheModelIsNeverLoaded() = runBlocking<Unit> {
        val engine = FakeEngine(FakeModel { error("must not run") })
        val result = processor(manager(engine)).transcribe(CountingSource(null, FloatArray(160_000)), options)
        assertEquals(SttResult.NoSpeech(10_000, NoSpeechBasis.SIGNAL_LEVEL), result)
        assertEquals(0, engine.loads.get())
    }

    @Test
    fun modelThatFindsNoSpeechGivesNoSpeechNotAnEmptyTranscript() = runBlocking<Unit> {
        val model = FakeModel {
            done(raw("[BLANK_AUDIO]", 0, 3_000), raw("Thank you.", 0, 1_000, noSpeech = 0.9f), raw("  ", 0, 500), raw("(music)", 0, 900))
        }
        val result = processor(manager(FakeEngine(model))).transcribe(CountingSource(null), options)
        assertEquals(SttResult.NoSpeech(3_000, NoSpeechBasis.MODEL), result)
    }

    @Test
    fun segmentsBeyondTheClipAreDroppedAndEndsAreClamped() = runBlocking<Unit> {
        val model = FakeModel { done(raw("inside", 2_000, 3_400), raw("hallucinated tail", 3_000, 3_500), raw("past", 3_100, 3_900)) }
        val success = assertIs<SttResult.Success>(processor(manager(FakeEngine(model))).transcribe(CountingSource(null), options))
        assertEquals(listOf("inside"), success.segments.map { it.text }.take(1))
        assertTrue(success.segments.all { it.endMs <= 3_000 && it.startMs < 3_000 })
    }

    @Test
    fun overLongClipIsRefusedFromTheContainerDurationWithoutDecoding() = runBlocking<Unit> {
        val engine = FakeEngine(FakeModel { error("must not run") })
        val source = CountingSource(declaredMs = 70_000)
        val result = processor(manager(engine)).transcribe(source, options)
        assertEquals(SttResult.TooLong(70_000, MAX_CLIP_DURATION_MS), result)
        assertEquals(0, source.decodes)
        assertEquals(0, engine.loads.get())
    }

    @Test
    fun clipJustOverTheCapWithinToleranceIsAccepted() = runBlocking<Unit> {
        val model = FakeModel { done(raw("ok", 0, 1_000)) }
        val source = CountingSource(declaredMs = 60_300)
        assertIs<SttResult.Success>(processor(manager(FakeEngine(model))).transcribe(source, options))
    }

    @Test
    fun pcmSourceOverTheCapIsTooLongBeforeResampling() = runBlocking<Unit> {
        val source = PcmAudioSource(FloatArray(48_000 * 70), sampleRate = 48_000)
        assertEquals(70_000L, source.containerDurationMs())
        val result = processor(manager(FakeEngine(FakeModel { error("must not run") }))).transcribe(source, options)
        assertEquals(SttResult.TooLong(70_000, MAX_CLIP_DURATION_MS), result)
    }

    @Test
    fun decodeOutcomesMapToResults() = runBlocking<Unit> {
        fun source(outcome: AudioDecodeResult) = object : AudioSource {
            override suspend fun containerDurationMs(): Long? = null

            override suspend fun decode(maxDurationMs: Long): AudioDecodeResult = outcome
        }
        val processor = processor(manager(FakeEngine(FakeModel { error("must not run") })))
        assertEquals(
            SttResult.Unsupported(UnsupportedReason.CODEC_UNAVAILABLE),
            processor.transcribe(source(AudioDecodeResult.Unsupported(UnsupportedReason.CODEC_UNAVAILABLE)), options),
        )
        assertEquals(SttResult.Failed(SttFailure.OUT_OF_MEMORY), processor.transcribe(source(AudioDecodeResult.OutOfMemory), options))
        assertEquals(SttResult.Failed(SttFailure.DECODE_FAILED), processor.transcribe(source(AudioDecodeResult.Failed), options))
        assertEquals(SttResult.TooLong(65_000, MAX_CLIP_DURATION_MS), processor.transcribe(source(AudioDecodeResult.TooLong(65_000)), options))
    }

    @Test
    fun severeThermalStatusRefusesBeforeAnythingElseHappens() = runBlocking<Unit> {
        val engine = FakeEngine(FakeModel { error("must not run") })
        val source = CountingSource(null)
        listOf(3, 4, 5, 6).forEach { status ->
            thermalStatus = status
            assertEquals(SttResult.Failed(SttFailure.THERMAL), processor(manager(engine)).transcribe(source, options), "status $status")
        }
        assertEquals(0, source.decodes)
        assertEquals(0, engine.loads.get())
    }

    @Test
    fun moderateThermalStatusStillRuns() = runBlocking<Unit> {
        thermalStatus = 2
        val model = FakeModel { done(raw("ok", 0, 1_000)) }
        assertIs<SttResult.Success>(processor(manager(FakeEngine(model))).transcribe(CountingSource(null), options))
    }

    @Test
    fun wrongHashRefusesToLoadAndNeverReachesTheEngine() = runBlocking<Unit> {
        val tampered = writeModel(folder.root, TEST_MODEL_BYTES.copyOf().also { it[5] = (it[5] + 1).toByte() })
        val engine = FakeEngine(FakeModel { error("must not run") })
        val result = processor(manager(engine, tampered)).transcribe(CountingSource(null), options)
        assertEquals(SttResult.ModelUnavailable(ModelUnavailableReason.HASH_MISMATCH), result)
        assertEquals(0, engine.loads.get())
    }

    @Test
    fun wrongSizeIsRefusedWithoutHashing() = runBlocking<Unit> {
        val short = File(folder.root, "short.bin").also { it.writeBytes(TEST_MODEL_BYTES.copyOf(10)) }
        var hashed = false
        val manager = ModelSessionManager(spec, short, FakeEngine { EngineLoad.Failed }, hasher = { hashed = true; "" })
        val result = processor(manager).transcribe(CountingSource(null), options)
        assertEquals(SttResult.ModelUnavailable(ModelUnavailableReason.HASH_MISMATCH), result)
        assertFalse(hashed)
    }

    @Test
    fun missingFileAndEngineProblemsAreModelUnavailableOrOutOfMemory() = runBlocking<Unit> {
        val missing = ModelSessionManager(spec, File(folder.root, "absent.bin"), FakeEngine { EngineLoad.Failed })
        assertEquals(SttResult.ModelUnavailable(ModelUnavailableReason.MISSING), processor(missing).transcribe(CountingSource(null), options))
        val file = writeModel(folder.root)
        assertEquals(
            SttResult.ModelUnavailable(ModelUnavailableReason.LOAD_FAILED),
            processor(manager(FakeEngine { EngineLoad.Failed }, file)).transcribe(CountingSource(null), options),
        )
        assertEquals(
            SttResult.ModelUnavailable(ModelUnavailableReason.NATIVE_LIBRARY_MISSING),
            processor(manager(FakeEngine { EngineLoad.LibraryMissing }, file)).transcribe(CountingSource(null), options),
        )
        assertEquals(
            SttResult.Failed(SttFailure.OUT_OF_MEMORY),
            processor(manager(FakeEngine { EngineLoad.OutOfMemory }, file)).transcribe(CountingSource(null), options),
        )
    }

    @Test
    fun engineRunOutcomesMapToResults() = runBlocking<Unit> {
        val oom = FakeModel { EngineRun.OutOfMemory }
        assertEquals(SttResult.Failed(SttFailure.OUT_OF_MEMORY), processor(manager(FakeEngine(oom))).transcribe(CountingSource(null), options))
        val failed = FakeModel { EngineRun.Failed }
        assertEquals(SttResult.Failed(SttFailure.ENGINE_FAILED), processor(manager(FakeEngine(failed))).transcribe(CountingSource(null), options))
        val thrown = FakeModel { throw OutOfMemoryError("test") }
        assertEquals(SttResult.Failed(SttFailure.OUT_OF_MEMORY), processor(manager(FakeEngine(thrown))).transcribe(CountingSource(null), options))
    }

    @Test
    fun modelIsReleasedAfterUseAndAfterFailure() = runBlocking<Unit> {
        val model = FakeModel { done(raw("ok", 0, 1_000)) }
        val manager = manager(FakeEngine(model))
        processor(manager).transcribe(CountingSource(null), options)
        assertTrue(model.closed)
        assertFalse(manager.isLoaded)
        val broken = FakeModel { error("engine blew up") }
        val brokenManager = manager(FakeEngine(broken))
        assertFailsWith<IllegalStateException> { processor(brokenManager).transcribe(CountingSource(null), options) }
        assertTrue(broken.closed)
        assertFalse(brokenManager.isLoaded)
    }

    @Test
    fun trimMemoryReleasesAHeldModelOnlyAtCriticalLevels() = runBlocking<Unit> {
        val model = FakeModel { done(raw("ok", 0, 1_000)) }
        val manager = manager(FakeEngine(model), keepLoaded = true)
        processor(manager).transcribe(CountingSource(null), options)
        assertTrue(manager.isLoaded)
        manager.onTrimMemory(ModelSessionManager.TRIM_MEMORY_RUNNING_CRITICAL - 5)
        assertTrue(manager.isLoaded)
        manager.onTrimMemory(ModelSessionManager.TRIM_MEMORY_RUNNING_CRITICAL)
        assertFalse(manager.isLoaded)
        assertTrue(model.closed)
    }

    @Test
    fun trimMemoryDuringUseReleasesWhenTheWorkEnds() = runBlocking<Unit> {
        val gate = java.util.concurrent.CountDownLatch(1)
        val model = FakeModel {
            gate.await(10, TimeUnit.SECONDS)
            done(raw("ok", 0, 1_000))
        }
        val manager = manager(FakeEngine(model), keepLoaded = true)
        val job = async(Dispatchers.Default) { processor(manager).transcribe(CountingSource(null), options) }
        assertTrue(model.started.await(10, TimeUnit.SECONDS))
        manager.onTrimMemory(80)
        assertFalse(model.closed, "a model in use is not freed under the work")
        gate.countDown()
        assertIs<SttResult.Success>(job.await())
        assertTrue(model.closed)
        assertFalse(manager.isLoaded)
    }

    @Test
    fun keptModelIsLoadedOnceAcrossRuns() = runBlocking<Unit> {
        val model = FakeModel { done(raw("ok", 0, 1_000)) }
        val engine = FakeEngine(model)
        val manager = manager(engine, keepLoaded = true)
        repeat(3) { processor(manager).transcribe(CountingSource(null), options) }
        assertEquals(1, engine.loads.get())
        manager.release()
        assertTrue(model.closed)
    }

    @Test
    fun cancelCurrentAbortsTheRunAndReturnsCancelled() = runBlocking<Unit> {
        lateinit var model: FakeModel
        model = FakeModel { model.blockUntilCancelled() }
        val processor = processor(manager(FakeEngine(model)))
        val job = async(Dispatchers.Default) { processor.transcribe(CountingSource(null), options) }
        assertTrue(model.started.await(10, TimeUnit.SECONDS))
        processor.cancelCurrent()
        assertEquals(SttResult.Cancelled, job.await())
        assertTrue(model.closed)
    }

    @Test
    fun coroutineCancellationAbortsTheNativeRunAndThrows() = runBlocking<Unit> {
        lateinit var model: FakeModel
        model = FakeModel { model.blockUntilCancelled() }
        val processor = processor(manager(FakeEngine(model)))
        val job = async(Dispatchers.Default) { processor.transcribe(CountingSource(null), options) }
        assertTrue(model.started.await(10, TimeUnit.SECONDS))
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertTrue(model.cancelRequested.await(10, TimeUnit.SECONDS))
        assertFailsWith<CancellationException> { job.await() }
        assertTrue(model.closed)
    }

    @Test
    fun onlyOneModelIsResidentAtATimeAcrossManagers() = runBlocking<Unit> {
        val resident = java.util.concurrent.atomic.AtomicInteger()
        val peak = java.util.concurrent.atomic.AtomicInteger()
        fun busyModel() = FakeModel {
            peak.accumulateAndGet(resident.incrementAndGet(), ::maxOf)
            Thread.sleep(50)
            resident.decrementAndGet()
            done(raw("ok", 0, 1_000))
        }
        val first = processor(manager(FakeEngine(busyModel()), writeModel(folder.newFolder())))
        val second = processor(manager(FakeEngine(busyModel()), writeModel(folder.newFolder())))
        val results = listOf(
            async(Dispatchers.Default) { first.transcribe(CountingSource(null), options) },
            async(Dispatchers.Default) { second.transcribe(CountingSource(null), options) },
            async(Dispatchers.Default) { first.transcribe(CountingSource(null), options) },
        ).map { it.await() }
        assertTrue(results.all { it is SttResult.Success })
        assertEquals(1, peak.get())
    }

    @Test
    fun aSharedLockCanBeInjectedSoOtherHeavyModelsExcludeThisOne() = runBlocking<Unit> {
        var acquisitions = 0
        val shared = object : HeavyModelLock {
            override suspend fun <T> withExclusive(block: suspend () -> T): T {
                acquisitions++
                return HeavyModelLock.ProcessWide.withExclusive(block)
            }
        }
        val model = FakeModel { done(raw("ok", 0, 1_000)) }
        val manager = ModelSessionManager(spec, writeModel(folder.root), FakeEngine(model), lock = shared)
        processor(manager).transcribe(CountingSource(null), options)
        assertEquals(1, acquisitions)
    }

    @Test
    fun heavyWorkRunsOffTheCallingThread() = runBlocking<Unit> {
        val caller = Thread.currentThread()
        var workThread: Thread? = null
        val model = FakeModel {
            workThread = Thread.currentThread()
            done(raw("ok", 0, 1_000))
        }
        withContext(Dispatchers.Default) { processor(manager(FakeEngine(model))).transcribe(CountingSource(null), options) }
        assertTrue(workThread != null && workThread !== caller)
    }

    @Test
    fun shortClipIsPaddedForTheModelButKeepsItsOwnDuration() = runBlocking<Unit> {
        var seen = 0
        val model = FakeModel { pcm ->
            seen = pcm.size
            done(raw("hi", 0, 400))
        }
        val short = sine(220.0, 0.4, MODEL_SAMPLE_RATE_HZ)
        val success = assertIs<SttResult.Success>(processor(manager(FakeEngine(model))).transcribe(CountingSource(null, short), options))
        assertEquals(17_600, seen)
        assertEquals(400L, success.audioDurationMs)
    }

    @Test
    fun optionsRejectValuesOutsideTheLimits() {
        assertFailsWith<IllegalArgumentException> { SttOptions(maxDurationMs = 61_000) }
        assertFailsWith<IllegalArgumentException> { SttOptions(threads = 0) }
        assertFailsWith<IllegalArgumentException> { SttOptions(language = "English") }
    }
}
