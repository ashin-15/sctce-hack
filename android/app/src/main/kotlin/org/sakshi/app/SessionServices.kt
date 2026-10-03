package org.sakshi.app

import android.content.ContentResolver
import android.content.Context
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
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
import org.sakshi.processing.ocr.MlKitOcrProcessor
import org.sakshi.processing.text.BenchCueList

/**
 * Objects that exist only while one vault is open. [context] must be the application context. The signer and the
 * renderer can be replaced because the Keystore and the PDF writer do not run on the JVM.
 *
 * Creating the services makes the text of the person's own notes searchable in [vault]; the services are made once per
 * opened vault, so that happens once per session.
 *
 * Creating the services wipes the export folder, so a file left behind by an earlier run never outlives the next unlock.
 * [close] releases the text recognition engine when the session ends.
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
) : AutoCloseable {
    init {
        vault.enableNoteSearch()
    }

    val resolver: ContentResolver = context.contentResolver

    val importer: EvidenceImporter = EvidenceImporter(vault.evidence, resolver)

    /** Bundled Latin-script text recognition; the engine is loaded on first use, on the phone, with no download. */
    private val ocr = MlKitOcrProcessor()

    /**
     * Reads saved text, and text recognised in screenshots and photos, into events. Uses the demonstration word list,
     * so every match is only a suggestion.
     */
    val textAnalysis: TextAnalysis = TextAnalysis(vault, RulesEngineFactory.default(), clock, ids, ocr = ocr)

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

    override fun close() = ocr.close()
}
