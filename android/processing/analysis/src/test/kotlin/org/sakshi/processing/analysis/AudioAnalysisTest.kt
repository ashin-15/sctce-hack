package org.sakshi.processing.analysis

import java.io.File
import java.security.MessageDigest
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.sakshi.core.database.SupportState
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.Direction
import org.sakshi.core.model.Locator
import org.sakshi.core.model.Representation
import org.sakshi.core.model.SourceKind
import org.sakshi.core.model.TextStatus
import org.sakshi.core.model.TimeBasis
import org.sakshi.core.vault.AcquisitionKind
import org.sakshi.core.vault.DerivativeKind
import org.sakshi.processing.stt.AudioDecodeResult
import org.sakshi.processing.stt.AudioSource
import org.sakshi.processing.stt.EngineLoad
import org.sakshi.processing.stt.EngineRun
import org.sakshi.processing.stt.MODEL_SAMPLE_RATE_HZ
import org.sakshi.processing.stt.ModelSessionManager
import org.sakshi.processing.stt.ModelSpec
import org.sakshi.processing.stt.PcmAudioSource
import org.sakshi.processing.stt.RandomAccessSource
import org.sakshi.processing.stt.RawSegment
import org.sakshi.processing.stt.Sha256 as ModelSha256
import org.sakshi.processing.stt.SpeechEngine
import org.sakshi.processing.stt.SpeechModel
import org.sakshi.processing.stt.SttProcessor
import org.sakshi.processing.stt.ThermalStatusProvider
import org.sakshi.processing.stt.UnsupportedReason

/** A model that returns what the test scripts. Every word is synthetic. */
private class ScriptedModel(private val run: EngineRun) : SpeechModel {
    var closed = false
        private set

    override fun transcribe(pcm: FloatArray, language: String?, threads: Int): EngineRun = run

    override fun cancel() = Unit

    override fun close() {
        closed = true
    }
}

private class ScriptedEngine(private val run: EngineRun) : SpeechEngine {
    override val version: String = "fake-engine-1"
    val models = mutableListOf<ScriptedModel>()

    override fun load(modelPath: String): EngineLoad = EngineLoad.Loaded(ScriptedModel(run).also { models += it })
}

private fun segment(text: String, start: Long, end: Long, probability: Float? = 0.9f) = RawSegment(text, start, end, 0.01f, probability)

class AudioAnalysisTest : AnalysisTestBase() {
    private val mp3Bytes = "ID3".toByteArray() + ByteArray(64) { 5 }
    private val modelBytes = ByteArray(512) { (it * 3).toByte() }
    private val spec = ModelSpec(
        "fake-model",
        "fake-model.bin",
        ModelSha256.hex(MessageDigest.getInstance("SHA-256").digest(modelBytes)),
        modelBytes.size.toLong(),
    )
    private val speechLike = FloatArray(3 * MODEL_SAMPLE_RATE_HZ) { (0.5 * sin(2.0 * PI * 220.0 * it / MODEL_SAMPLE_RATE_HZ)).toFloat() }
    private var thermalStatus = 0
    private val sources = mutableListOf<RandomAccessSource>()

    private fun modelFile(present: Boolean = true): File {
        val file = File(context.cacheDir, "audio-analysis-model-${System.nanoTime()}.bin")
        if (present) file.writeBytes(modelBytes)
        return file
    }

    private fun processor(engine: SpeechEngine, file: File = modelFile()) =
        SttProcessor(ModelSessionManager(spec, file, engine), ThermalStatusProvider { thermalStatus })

    private fun analyser(
        stt: SttProcessor?,
        audio: AudioSource = PcmAudioSource(speechLike),
        limits: AnalysisLimits = AnalysisLimits(),
        threatClassifier: ThreatLanguageClassifier? = null,
    ) =
        TextAnalysis(
            vault,
            VaultTextDerivatives(vault.derivatives),
            RulesEngineFactory.default(),
            clock,
            ids,
            limits,
            kotlinx.coroutines.Dispatchers.Default,
            null,
            stt,
            threatClassifier = threatClassifier,
        ) { source ->
            sources += source
            audio
        }

    private fun importAudio(): String = importBytes(mp3Bytes, declaredMime = "audio/mpeg", kind = AcquisitionKind.SELECTED_DOCUMENT)

    private fun refusal(analyser: TextAnalysis, id: String) = runBlocking {
        assertIs<AnalysisOutcome.NotAnalysable>(analyser.analyse(id)).reason
    }

    private fun assertNothingWritten(id: String) = runBlocking<Unit> {
        assertTrue(events().isEmpty())
        assertTrue(vault.derivatives.listForEvidence(id).isEmpty())
        assertEquals(SupportState.SAVED, vault.evidence.details(id)?.supportState)
    }

    private fun assertReaderClosed() {
        val source = sources.last()
        assertFailsWith<IllegalStateException> { source.read(0, ByteArray(4), 0, 4) }
    }

    private val speech = EngineRun.Done(
        listOf(segment("please stop messaging me", 0, 1_400), segment("you are an idiot", 1_400, 2_900)),
        "en",
    )

    @Test
    fun speechBecomesOneEventAnchoredToWordsAndToTheMomentTheyWereHeard() = runBlocking<Unit> {
        val id = importAudio()
        val engine = ScriptedEngine(speech)
        val outcome = assertIs<AnalysisOutcome.Analysed>(analyser(processor(engine)).analyse(id))
        assertEquals(InputKind.AUDIO_TRANSCRIPT, outcome.kind)
        assertEquals(1, outcome.eventCount)
        assertEquals(setOf(AnalysisWarning.AUDIO_TRANSCRIPT_MAY_CONTAIN_ERRORS), outcome.warnings)

        val derivatives = vault.derivatives.listForEvidence(id)
        assertEquals(1, derivatives.size, "exactly one derivative")
        val derivative = vault.derivatives.latest(id, DerivativeKind.TRANSCRIPT)!!
        assertEquals("please stop messaging me\nyou are an idiot", derivative.text)
        assertEquals("fake-engine-1", derivative.toolId)
        assertEquals(spec.sha256, derivative.toolVersion)
        assertTrue(derivative.sourceMapJson!!.contains("\"start_ms\":1400"))
        assertTrue(derivative.qualityJson!!.contains("\"translated\":false"))

        val event = events().single()
        assertSchemaValid(listOf(event))
        assertEquals(SourceKind.SELECTED_AUDIO, event.source.kind)
        assertEquals(Direction.UNKNOWN, event.direction, "speech to text does not say who spoke")
        assertEquals(TimeBasis.UNKNOWN, event.timestamp.basis, "a recording does not say when")
        assertNull(event.sender.displayLabel)
        assertEquals(TextStatus.AVAILABLE, event.coverage.textStatus)
        assertTrue(event.evidenceReferences.all { it.representation == Representation.TRANSCRIPT_DERIVATIVE })
        assertEquals(derivative.text, EventText(vault).bodyOf(event))

        val category = event.categories.single()
        assertEquals(CategoryLabel.VERBAL_ABUSE, category.label)
        val referenced = event.evidenceReferences.filter { it.referenceId in category.evidenceReferenceIds }
        val text = referenced.single { it.locator is Locator.Text }
        assertEquals("idiot", EventText(vault).quote(event, text)?.lowercase())
        val audio = referenced.single { it.locator is Locator.AudioTime }.locator as Locator.AudioTime
        assertEquals(1_400L, audio.startMs)
        assertEquals(2_900L, audio.endMs)
        assertEquals(SupportState.ANALYZED, vault.evidence.details(id)?.supportState)
        assertReaderClosed()
        assertTrue(engine.models.single().closed, "the model is released")
    }

    @Test
    fun transcriptTextIsNotSentToThreatClassifier() = runBlocking<Unit> {
        val id = importAudio()
        var classifierCalls = 0
        val classifier = ThreatLanguageClassifier { _, inputs ->
            classifierCalls += 1
            inputs.map { ThreatLanguageResult(ThreatLanguageResultStatus.POSSIBLE_THREAT_LANGUAGE, "idiot") }
        }

        val outcome = assertIs<AnalysisOutcome.Analysed>(
            analyser(processor(ScriptedEngine(speech)), threatClassifier = classifier).analyse(id),
        )

        assertEquals(InputKind.AUDIO_TRANSCRIPT, outcome.kind)
        assertEquals(0, classifierCalls)
        assertTrue(vault.threatAnalysisRuns.forEvent(events().single().eventId.value).isEmpty())
    }

    @Test
    fun lowOrMissingScoresMarkTheTextUncertainButKeepEverySegment() = runBlocking<Unit> {
        val id = importAudio()
        val shaky = EngineRun.Done(listOf(segment("you are an idiot", 0, 1_500, 0.2f), segment("synthetic", 1_500, 2_500, null), segment("fine", 2_500, 2_900)), "en")
        val outcome = assertIs<AnalysisOutcome.Analysed>(analyser(processor(ScriptedEngine(shaky))).analyse(id))
        assertEquals(2, outcome.warningCounts[AnalysisWarning.AUDIO_LOW_CONFIDENCE_SEGMENTS])
        assertTrue(AnalysisWarning.AUDIO_TRANSCRIPT_MAY_CONTAIN_ERRORS in outcome.warnings)
        val event = events().single()
        assertSchemaValid(listOf(event))
        assertEquals(TextStatus.EXTRACTION_UNCERTAIN, event.coverage.textStatus)
        assertEquals("you are an idiot\nsynthetic\nfine", EventText(vault).bodyOf(event))
        assertEquals(listOf(CategoryLabel.VERBAL_ABUSE), event.categories.map { it.label })
        assertEquals(SupportState.PARTIAL, vault.evidence.details(id)?.supportState)
    }

    @Test
    fun malayalamAndDevanagariTextSpansCountCodePointsNotUnits() = runBlocking<Unit> {
        val id = importAudio()
        val mixed = EngineRun.Done(
            listOf(segment("നീ മോശം", 0, 1_000), segment("तुम बेवकूफ हो \uD83D\uDE00 idiot", 1_000, 2_800)),
            "ml",
        )
        assertIs<AnalysisOutcome.Analysed>(analyser(processor(ScriptedEngine(mixed))).analyse(id))
        val event = events().single()
        assertSchemaValid(listOf(event))
        val text = EventText(vault).bodyOf(event)!!
        val body = event.evidenceReferences.first().locator as Locator.Text
        assertEquals(text.codePointCount(0, text.length), body.end)
        assertTrue(text.length > body.end, "an emoji takes two UTF-16 units but one code point")
        val cue = event.evidenceReferences.single { it.locator is Locator.Text && it.referenceId.value.startsWith("cue-") }
        assertEquals("idiot", EventText(vault).quote(event, cue))
    }

    @Test
    fun everyFailureMapsToItsReasonAndWritesNothing() {
        val id = importAudio()
        val noModel = analyser(processor(ScriptedEngine(speech), modelFile(present = false)))
        assertEquals(NotAnalysableReason.SPEECH_MODEL_UNAVAILABLE, refusal(noModel, id))

        val silent = analyser(processor(ScriptedEngine(speech)), PcmAudioSource(FloatArray(2 * MODEL_SAMPLE_RATE_HZ)))
        assertEquals(NotAnalysableReason.NO_SPEECH, refusal(silent, id))

        val noSegments = analyser(processor(ScriptedEngine(EngineRun.Done(emptyList(), "en"))))
        assertEquals(NotAnalysableReason.NO_SPEECH, refusal(noSegments, id))

        val tooLong = analyser(processor(ScriptedEngine(speech)), PcmAudioSource(FloatArray(70 * MODEL_SAMPLE_RATE_HZ)))
        assertEquals(NotAnalysableReason.AUDIO_TOO_LONG, refusal(tooLong, id))

        val undecodable = object : AudioSource {
            override suspend fun containerDurationMs(): Long? = null

            override suspend fun decode(maxDurationMs: Long): AudioDecodeResult = AudioDecodeResult.Unsupported(UnsupportedReason.CODEC_UNAVAILABLE)
        }
        assertEquals(NotAnalysableReason.AUDIO_NOT_DECODABLE, refusal(analyser(processor(ScriptedEngine(speech)), undecodable), id))

        assertEquals(NotAnalysableReason.TRANSCRIPTION_FAILED, refusal(analyser(processor(ScriptedEngine(EngineRun.Failed))), id))
        assertEquals(NotAnalysableReason.TRANSCRIPTION_FAILED, refusal(analyser(processor(ScriptedEngine(EngineRun.Cancelled))), id))
        thermalStatus = ThermalStatusProvider.SEVERE
        assertEquals(NotAnalysableReason.TRANSCRIPTION_FAILED, refusal(analyser(processor(ScriptedEngine(speech))), id))
        assertNothingWritten(id)
        assertReaderClosed()
    }

    @Test
    fun withoutAnSttProcessorAudioStaysPreserveOnly() {
        val id = importAudio()
        assertEquals(NotAnalysableReason.PRESERVE_ONLY_TYPE, refusal(analyser(null), id))
        assertTrue(sources.isEmpty(), "the original was never opened")
        assertNothingWritten(id)
    }

    @Test
    fun anAudioNoteIsNeverTranscribed() {
        val id = importBytes(mp3Bytes, declaredMime = "audio/mpeg", kind = AcquisitionKind.MANUAL_NOTE)
        assertEquals(NotAnalysableReason.MANUAL_NOTE, refusal(analyser(processor(ScriptedEngine(speech))), id))
        assertTrue(sources.isEmpty())
    }

    @Test
    fun videoStaysPreserveOnlyEvenWithAProcessor() {
        val video = byteArrayOf(0, 0, 0, 24) + "ftypisom".toByteArray() + ByteArray(32)
        val id = importBytes(video, declaredMime = "video/mp4", kind = AcquisitionKind.SELECTED_DOCUMENT)
        assertEquals(NotAnalysableReason.PRESERVE_ONLY_TYPE, refusal(analyser(processor(ScriptedEngine(speech))), id))
        assertTrue(sources.isEmpty())
    }

    @Test
    fun aSecondRunIsRefusedWithoutTranscribingAgain() = runBlocking<Unit> {
        val id = importAudio()
        val engine = ScriptedEngine(speech)
        val analyser = analyser(processor(engine))
        assertIs<AnalysisOutcome.Analysed>(analyser.analyse(id))
        assertEquals(NotAnalysableReason.ALREADY_ANALYSED, refusal(analyser, id))
        assertEquals(1, engine.models.size, "the model was loaded once")
        assertEquals(1, events().size)
        assertEquals(1, vault.derivatives.listForEvidence(id).size)
    }
}
