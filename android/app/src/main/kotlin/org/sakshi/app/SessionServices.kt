package org.sakshi.app

import android.content.ContentResolver
import android.content.Context
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.sakshi.app.analysis.AnalysisQueue
import org.sakshi.app.analysis.Analyser
import org.sakshi.app.analysis.unanalysedText
import org.sakshi.acquisition.importer.EvidenceImporter
import org.sakshi.acquisition.importer.enableNoteSearch
import org.sakshi.core.temporal.PatternConfig
import org.sakshi.core.vault.Vault
import org.sakshi.export.bundle.GeneratorInfo
import org.sakshi.export.bundle.ManifestSigner
import org.sakshi.export.report.ExportService
import org.sakshi.export.report.KeystoreManifestSigner
import org.sakshi.export.report.ReportBuilder
import org.sakshi.export.report.ReportPdfRenderer
import org.sakshi.export.report.ReportRenderer
import org.sakshi.export.report.VaultQuoteSource
import org.sakshi.app.review.EvidenceImageLoader
import org.sakshi.app.review.VaultImageLoader
import org.sakshi.processing.analysis.CasePatterns
import org.sakshi.processing.analysis.RulesEngineFactory
import org.sakshi.processing.analysis.TextAnalysis
import org.sakshi.processing.llm.engine.InferenceLock
import org.sakshi.processing.llm.analysis.QwenThreatLanguageClassifier
import org.sakshi.processing.llm.model.LlmSessionManager
import org.sakshi.processing.llm.model.ModelManager
import org.sakshi.processing.ocr.MlKitOcrProcessor
import org.sakshi.processing.stt.HeavyModelLock
import org.sakshi.processing.stt.ModelProvisioner
import org.sakshi.processing.stt.ModelSessionManager
import org.sakshi.processing.stt.ModelSpec
import org.sakshi.processing.stt.SpeechEngine
import org.sakshi.processing.stt.SttProcessor
import org.sakshi.processing.stt.ThermalStatusProvider
import org.sakshi.processing.stt.WhisperEngine
import org.sakshi.processing.text.BenchCueList

/**
 * Objects that exist only while one vault is open. [context] must be the application context. The signer and the
 * renderer can be replaced because the Keystore and the PDF writer do not run on the JVM.
 *
 * Creating the services makes the text of the person's own notes searchable in [vault]; the services are made once per
 * opened vault, so that happens once per session.
 *
 * Creating the services wipes the export folder, so a file left behind by an earlier run never outlives the next unlock.
 * [close] stops the automatic analysis and releases the text recognition engine and the speech model when the session ends.
 *
 * Speech recognition runs the whisper.cpp base model from [speechProvisioner]'s file. The model is never bundled or
 * downloaded; it is in memory only while a recording is being read. It shares one lock with the local language model,
 * so the two heavy models are never loaded at the same time.
 */
class SessionServices(
    val vault: Vault,
    context: Context,
    val io: CoroutineDispatcher,
    clock: () -> Instant = Instant::now,
    ids: () -> String = { UUID.randomUUID().toString() },
    signer: ManifestSigner = KeystoreManifestSigner(),
    renderer: ReportRenderer = ReportPdfRenderer(),
    appVersion: String = BuildConfig.VERSION_NAME,
    speechEngine: SpeechEngine = WhisperEngine(),
    val speechProvisioner: ModelProvisioner = ModelProvisioner.forContext(context),
) : AutoCloseable {
    val appContext: Context = context.applicationContext
    init {
        vault.enableNoteSearch()
    }

    val resolver: ContentResolver = context.contentResolver

    val importer: EvidenceImporter = EvidenceImporter(vault.evidence, resolver)

    /** Bundled Latin-script text recognition; the engine is loaded on first use, on the phone, with no download. */
    private val ocr = MlKitOcrProcessor()

    private val speechSessions = ModelSessionManager(
        ModelSpec.WHISPER_BASE_Q5_1,
        speechProvisioner.modelFile,
        speechEngine,
        lock = LanguageModelLock,
    )

    private val speech = SttProcessor(speechSessions, ThermalStatusProvider.system(context))

    private val languageSessions = LlmSessionManager(ModelManager(context), io)
    private val threatLanguage = QwenThreatLanguageClassifier(languageSessions)

    /**
     * Reads saved text, text recognised in screenshots and photos, and speech in recordings into events. Uses the
     * demonstration word list, so every match is only a suggestion.
     */
    val textAnalysis: TextAnalysis = TextAnalysis(
        vault,
        RulesEngineFactory.default(),
        clock,
        ids,
        ocr = ocr,
        stt = speech,
        threatClassifier = threatLanguage,
    )

    /**
     * Analyses saved text on its own while this vault is open, so the person does not start each run. It starts with
     * the session and [close] stops it at the lock. A run the lock interrupts writes nothing and starts again later.
     */
    val analysisQueue: AnalysisQueue = AnalysisQueue(
        vault.unanalysedText(),
        { evidenceId -> textAnalysis.analyse(evidenceId, null, ids(), discardCancelledRun = true) },
        CoroutineScope(SupervisorJob() + io),
    ).also { it.start() }

    /** The run the person starts from the analysis screen. It waits for the automatic run of the moment to finish. */
    val analyse: Analyser = { evidenceId, options -> analysisQueue.exclusive { textAnalysis.analyse(evidenceId, options) } }

    /** Decodes a saved picture in memory for the review screen; nothing is written to storage. */
    val images: EvidenceImageLoader = VaultImageLoader(vault.evidence, io)

    val casePatterns: CasePatterns = CasePatterns(vault, clock)

    /**
     * What the report says about the software that made it. The cue list version is read from the list that
     * [RulesEngineFactory.default] uses; the factory does not expose it, so the two must be kept in step.
     */
    private val generator = GeneratorInfo(
        appVersion,
        listOf(PatternConfig().ruleVersion, BenchCueList.V1.version),
        listOf("${ocr.engine.id}-${ocr.engine.version}"),
    )

    val reportBuilder: ReportBuilder = ReportBuilder(vault, VaultQuoteSource(vault), clock, generator)

    val exports: ExportService = ExportService(context, vault, reportBuilder, renderer, signer, clock, ids, io).also {
        it.clearExports()
    }

    /** Frees the speech model when the phone is short of memory. Forward `onTrimMemory` here. */
    fun onTrimMemory(level: Int) = speechSessions.onTrimMemory(level)

    override fun close() {
        analysisQueue.close()
        speech.cancelCurrent()
        speechSessions.release()
        ocr.close()
    }
}

/** The speech model waits for the local language model, and the other way round, so only one is loaded at a time. */
private object LanguageModelLock : HeavyModelLock {
    override suspend fun <T> withExclusive(block: suspend () -> T): T = InferenceLock.withLock(block)
}
