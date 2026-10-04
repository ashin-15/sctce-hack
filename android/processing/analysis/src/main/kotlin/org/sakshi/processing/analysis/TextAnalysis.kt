package org.sakshi.processing.analysis

import java.io.IOException
import java.security.GeneralSecurityException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.sakshi.core.database.SupportState
import org.sakshi.core.integrity.Sha256
import org.sakshi.core.model.ArtifactId
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.Event
import org.sakshi.core.model.Timestamp
import org.sakshi.core.model.CategoryAssessment
import org.sakshi.core.model.CategoryBasis
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.CategoryReviewStatus
import org.sakshi.core.model.Confidence
import org.sakshi.core.model.ConfidenceSemantics
import org.sakshi.core.model.Direction
import org.sakshi.core.model.Locator
import org.sakshi.core.model.ReferenceId
import org.sakshi.core.model.ScopeId
import org.sakshi.core.model.SourceKind
import org.sakshi.core.vault.AcquisitionKind
import org.sakshi.core.vault.BatchSaveResult
import org.sakshi.core.vault.EvidenceDetails
import org.sakshi.core.vault.NotificationClaims
import org.sakshi.core.vault.Vault
import org.sakshi.core.vault.ThreatAnalysisRunDraft
import org.sakshi.core.vault.ThreatAnalysisRunStatus
import org.sakshi.processing.ocr.OcrFailure
import org.sakshi.processing.ocr.OcrOutcome
import org.sakshi.processing.ocr.OcrProcessor
import org.sakshi.processing.stt.AudioEventPlan
import org.sakshi.processing.stt.AudioSource
import org.sakshi.processing.stt.MediaAudioSource
import org.sakshi.processing.stt.RandomAccessSource
import org.sakshi.processing.stt.SttOptions
import org.sakshi.processing.stt.SttProcessor
import org.sakshi.processing.stt.SttResult
import org.sakshi.processing.stt.asRandomAccessSource
import org.sakshi.processing.text.RulesEngine

/**
 * Turns one imported text evidence item into a parsed-text derivative, events and rule suggestions. With an [ocr]
 * engine, a JPEG, PNG or WebP image becomes an OCR derivative with one region per recognised line, and one event
 * whose suggestions point at both the recognised text and the image regions. Without one, images are kept as
 * received and not analysed.
 *
 * With an [stt] processor, a recording (an audio type by its bytes or, where the bytes cannot say, by the provider's
 * claim; not a note) is transcribed on this phone from the encrypted original, read in memory only. The words become
 * one transcript derivative and one event; its suggestions point at both the transcribed words and the time range of
 * the recording. Silence, a recording that is too long or
 * cannot be decoded, a missing model and a failed run are refusals that write nothing, and a refusal for silence
 * never says nothing was said. Video stays kept as received. Without [stt], recordings are not analysed.
 *
 * A notification excerpt is always one plain-text event built from its recorded claims; a summary-only excerpt is
 * refused as [NotAnalysableReason.PRESERVE_ONLY_TYPE] because it is not a message.
 *
 * Input kind rule: the text is read as a WhatsApp-style export when the parser finds at least two sender
 * messages and any text before the first record is at most 256 code points; otherwise it is plain text and
 * becomes one event. Evidence text is data: it is decoded, split and matched, never interpreted as instructions.
 * Originals are only read. Every cue match is an unreviewed suggestion for the user to confirm or reject.
 */
public class TextAnalysis internal constructor(
    private val vault: Vault,
    private val derivatives: TextDerivatives,
    private val rules: RulesEngine,
    private val clock: () -> Instant,
    private val ids: () -> String,
    private val limits: AnalysisLimits,
    private val dispatcher: CoroutineDispatcher,
    private val ocr: OcrProcessor? = null,
    private val stt: SttProcessor? = null,
    private val threatClassifier: ThreatLanguageClassifier? = null,
    private val audioSources: (RandomAccessSource) -> AudioSource = ::MediaAudioSource,
) : TextAnalyser {
    public constructor(
        vault: Vault,
        rules: RulesEngine,
        clock: () -> Instant,
        ids: () -> String,
        limits: AnalysisLimits = AnalysisLimits(),
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
        ocr: OcrProcessor? = null,
        stt: SttProcessor? = null,
        threatClassifier: ThreatLanguageClassifier? = null,
    ) : this(vault, VaultTextDerivatives(vault.derivatives), rules, clock, ids, limits, dispatcher, ocr, stt, threatClassifier, ::MediaAudioSource)

    /**
     * Analyses the evidence. For an export, [exportOptions] is required before events can be written; without
     * it only the derivative is stored and [AnalysisOutcome.NeedsExportOptions] asks the user the open questions.
     *
     * A cancelled run may leave the stored parsed-text derivative without events. That is a normal state: the next
     * call reuses the derivative and behaves like a first run.
     *
     * @throws IllegalStateException if the produced events break the event model (a programming error).
     */
    override suspend fun analyse(evidenceId: String, exportOptions: ExportOptions?): AnalysisOutcome =
        analyse(evidenceId, exportOptions, UUID.randomUUID().toString())

    override suspend fun analyse(evidenceId: String, exportOptions: ExportOptions?, requestId: String): AnalysisOutcome =
        analyse(evidenceId, exportOptions, requestId, discardCancelledRun = false)

    /**
     * With [discardCancelledRun], a run cancelled during local model inference writes no events and no run record, so
     * the next call starts again from the stored derivative. Automatic analysis uses this: a lock in the middle of a
     * run must not leave a message whose threat-language check was never made.
     */
    public suspend fun analyse(
        evidenceId: String,
        exportOptions: ExportOptions?,
        requestId: String,
        discardCancelledRun: Boolean,
    ): AnalysisOutcome {
        val details = vault.evidence.details(evidenceId)
            ?: return AnalysisOutcome.NotAnalysable(NotAnalysableReason.EVIDENCE_MISSING)
        val recogniser = ocr?.takeIf { details.detectedMime in OCR_IMAGE_TYPES && details.acquisitionKind != AcquisitionKind.MANUAL_NOTE }
        if (recogniser != null) return analyseImage(details, recogniser)
        val transcriber = stt?.takeIf { isRecording(details) && details.acquisitionKind != AcquisitionKind.MANUAL_NOTE }
        if (transcriber != null) return analyseAudio(details, transcriber)
        eligibility(details)?.let { return AnalysisOutcome.NotAnalysable(it) }
        val claims = if (details.acquisitionKind == AcquisitionKind.NOTIFICATION_EXCERPT) {
            NotificationClaims.decode(details.captureClaimsJson)
                ?: return AnalysisOutcome.NotAnalysable(NotAnalysableReason.UNREADABLE)
        } else {
            null
        }
        if (claims?.summaryOnly == true) return AnalysisOutcome.NotAnalysable(NotAnalysableReason.PRESERVE_ONLY_TYPE)
        val derivative = when (val prepared = prepare(details)) {
            is Prepared.Refused -> return AnalysisOutcome.NotAnalysable(prepared.reason)
            is Prepared.Ready -> prepared.derivative
        }
        if (alreadyAnalysed(CaseId(details.caseId), derivative.id)) {
            return AnalysisOutcome.NotAnalysable(NotAnalysableReason.ALREADY_ANALYSED)
        }
        val built = withContext(dispatcher) {
            val builder = EventBuilder(rules, ids, contextOf(details, derivative), details.id, limits)
            when {
                details.acquisitionKind == AcquisitionKind.VISIBLE_TEXT_SNAPSHOT -> builder.buildVisibleSnapshot(derivative.text)
                claims != null -> builder.buildNotification(derivative.text, claims)
                else -> builder.build(derivative.text, exportOptions)
            }
        }
        return when (built) {
            is BuildResult.NeedsOptions -> AnalysisOutcome.NeedsExportOptions(
                derivative.id,
                built.senders,
                built.detectedDateOrder,
                built.recordCount,
                built.sampleDates,
            )
            is BuildResult.Built -> save(details, derivative, built, requestId, discardCancelledRun)
        }
    }

    /** The image path: recognise once, keep the result as a derivative, then build the single image event. */
    private suspend fun analyseImage(details: EvidenceDetails, recogniser: OcrProcessor): AnalysisOutcome {
        if (details.byteSize > limits.maxImageBytes) return AnalysisOutcome.NotAnalysable(NotAnalysableReason.TOO_LARGE)
        val image = when (val prepared = prepareImage(details, recogniser)) {
            is PreparedImage.Refused -> return AnalysisOutcome.NotAnalysable(prepared.reason)
            is PreparedImage.Ready -> prepared.image
        }
        if (alreadyAnalysed(CaseId(details.caseId), image.derivative.id)) {
            return AnalysisOutcome.NotAnalysable(NotAnalysableReason.ALREADY_ANALYSED)
        }
        val built = withContext(dispatcher) {
            EventBuilder(rules, ids, contextOf(details, image.derivative), details.id, limits).buildImage(image, recogniser.engine)
        }
        return save(details, image.derivative, built)
    }

    /**
     * The audio path: transcribe the original once, keep the words as a derivative, then build the single event.
     * A transcript that already has events is not made again, because that would run the speech model for nothing.
     */
    private suspend fun analyseAudio(details: EvidenceDetails, transcriber: SttProcessor): AnalysisOutcome {
        val caseId = CaseId(details.caseId)
        derivatives.latestTranscript(details.id)?.let { stored ->
            if (alreadyAnalysed(caseId, stored.id)) return AnalysisOutcome.NotAnalysable(NotAnalysableReason.ALREADY_ANALYSED)
        }
        val success = when (val result = transcribe(details, transcriber)) {
            is Transcribed.Refused -> return AnalysisOutcome.NotAnalysable(result.reason)
            is Transcribed.Done -> result.success
        }
        val plan = AudioEventPlan.of(success, limits.minSpeechSegmentConfidence)
        val saved = derivatives.saveTranscript(
            details.id,
            TranscriptDraft(
                text = plan.text,
                toolId = success.engineVersion,
                toolVersion = success.modelSha256,
                sourceMapJson = plan.document.sourceMapJson(),
                qualityJson = plan.document.qualityJson(limits.minSpeechSegmentConfidence),
            ),
        )
        val parser = "${success.engineVersion}-${success.modelId}"
        val built = withContext(dispatcher) {
            EventBuilder(rules, ids, contextOf(details, saved), details.id, limits).buildAudio(plan, parser)
        }
        return save(details, saved, built)
    }

    /** Opens the encrypted original for the decoder to read in memory, and always closes it again. */
    private suspend fun transcribe(details: EvidenceDetails, transcriber: SttProcessor): Transcribed {
        val reader = try {
            vault.evidence.openOriginal(details.id)
        } catch (_: IOException) {
            return Transcribed.Refused(NotAnalysableReason.UNREADABLE)
        } catch (_: GeneralSecurityException) {
            return Transcribed.Refused(NotAnalysableReason.UNREADABLE)
        }
        try {
            val intact = withContext(Dispatchers.IO) {
                try {
                    Sha256.hex(reader.verifyAll()) == details.sha256
                } catch (_: IOException) {
                    false
                } catch (_: GeneralSecurityException) {
                    false
                }
            }
            if (!intact) return Transcribed.Refused(NotAnalysableReason.UNREADABLE)
            val source = audioSources(reader.asRandomAccessSource())
            return when (val result = transcriber.transcribe(source, SttOptions())) {
                is SttResult.Success -> Transcribed.Done(result)
                is SttResult.NoSpeech -> Transcribed.Refused(NotAnalysableReason.NO_SPEECH)
                is SttResult.TooLong -> Transcribed.Refused(NotAnalysableReason.AUDIO_TOO_LONG)
                is SttResult.Unsupported -> Transcribed.Refused(NotAnalysableReason.AUDIO_NOT_DECODABLE)
                is SttResult.ModelUnavailable -> Transcribed.Refused(NotAnalysableReason.SPEECH_MODEL_UNAVAILABLE)
                is SttResult.Failed, SttResult.Cancelled -> Transcribed.Refused(NotAnalysableReason.TRANSCRIPTION_FAILED)
            }
        } finally {
            reader.close()
        }
    }

    /** Reuses the newest OCR derivative when it can be read back; otherwise runs recognition on the original. */
    private suspend fun prepareImage(details: EvidenceDetails, recogniser: OcrProcessor): PreparedImage {
        derivatives.latestOcr(details.id)?.let(OcrRecord::read)?.let { return PreparedImage.Ready(it) }
        val bytes = readOriginal(details) ?: return PreparedImage.Refused(NotAnalysableReason.UNREADABLE)
        val outcome = try {
            recogniser.process(bytes)
        } finally {
            bytes.fill(0)
        }
        return when (outcome) {
            is OcrOutcome.NoText -> PreparedImage.Refused(NotAnalysableReason.NO_TEXT_RECOGNISED)
            is OcrOutcome.Failed -> PreparedImage.Refused(
                if (outcome.failure == OcrFailure.UNDECODABLE) NotAnalysableReason.IMAGE_NOT_DECODABLE else NotAnalysableReason.RECOGNITION_FAILED,
            )
            is OcrOutcome.Success -> {
                val draft = OcrRecord.draft(outcome.result, recogniser.engine, ids, limits.minOcrLineConfidence)
                val stored = derivatives.saveOcr(details.id, draft)
                PreparedImage.Ready(checkNotNull(OcrRecord.read(stored)) { "Stored OCR derivative cannot be read back" })
            }
        }
    }

    private fun contextOf(details: EvidenceDetails, derivative: DerivativeText) = EventContext(
        caseId = CaseId(details.caseId),
        derivative = ArtifactId(derivative.id),
        evidenceSha256 = details.sha256,
        receivedAt = Timestamp(details.receivedAt),
        availableAt = Timestamp(clock().toString()),
    )

    private suspend fun save(
        details: EvidenceDetails,
        derivative: DerivativeText,
        built: BuildResult.Built,
        threatRequestId: String? = null,
        discardCancelledRun: Boolean = false,
    ): AnalysisOutcome {
        val lengthOfDerivative = CodePointIndex(derivative.text).length
        val threatEligible = built.kind in THREAT_ELIGIBLE_INPUT_KINDS &&
            built.events.all { it.source.kind in THREAT_ELIGIBLE_SOURCE_KINDS }
        val threat = if (threatRequestId != null && threatClassifier != null && threatEligible) {
            analyzeThreatLanguage(details, derivative, built.events, threatRequestId)
        } else {
            null
        }
        suspend fun persist(): BatchSaveResult {
            val result = if (threat == null) {
                vault.events.saveAll(built.events) { if (it.value == derivative.id) lengthOfDerivative else null }
            } else {
                vault.events.saveAllWithThreatAnalysis(threat.events, threat.runs) {
                    if (it.value == derivative.id) lengthOfDerivative else null
                }
            }
            check(result is BatchSaveResult.Saved) { "Analysis produced events the vault refused: ${describe(result)}" }
            vault.evidence.setSupportState(details.id, if (PARTIAL_WARNINGS.any { it in built.warnings }) SupportState.PARTIAL else SupportState.ANALYZED)
            return result
        }
        if (threat?.inferenceCancelled == true) {
            if (discardCancelledRun) throw kotlinx.coroutines.CancellationException("Automatic analysis was cancelled")
            withContext(NonCancellable) { persist() }
        } else {
            persist()
        }
        return AnalysisOutcome.Analysed(
            derivativeId = derivative.id,
            eventCount = built.events.size,
            suggestionCount = (threat?.events ?: built.events).sumOf { it.categories.size },
            kind = built.kind,
            warnings = built.warnings.keys,
            warningCounts = built.warnings,
        )
    }

    private suspend fun analyzeThreatLanguage(
        details: EvidenceDetails,
        derivative: DerivativeText,
        events: List<Event>,
        requestId: String,
    ): ThreatWrite {
        val eventText = EventText(derivatives)
        val inputs = mutableListOf<ThreatLanguageInput>()
        val initial = mutableMapOf<String, ThreatLanguageResult>()
        for (event in events) {
            val bodyReference = event.evidenceReferences.firstOrNull()
            when {
                event.direction == Direction.OUTGOING -> initial[event.eventId.value] =
                    ThreatLanguageResult(ThreatLanguageResultStatus.NOT_RUN, reasonCode = "known_outgoing")
                bodyReference == null || bodyReference.locator !is Locator.Text -> initial[event.eventId.value] =
                    ThreatLanguageResult(ThreatLanguageResultStatus.NEEDS_REVIEW, reasonCode = "source_anchor_missing")
                else -> {
                    val body = eventText.bodyOf(event)
                    if (body == null) {
                        initial[event.eventId.value] = ThreatLanguageResult(
                            ThreatLanguageResultStatus.NEEDS_REVIEW,
                            reasonCode = "source_text_unavailable",
                        )
                    } else {
                        inputs += ThreatLanguageInput(event, bodyReference, body)
                    }
                }
            }
        }
        var inferenceCancelled = false
        val classified = try {
            threatClassifier?.classify(requestId, inputs).orEmpty()
        } catch (_: kotlinx.coroutines.CancellationException) {
            inferenceCancelled = true
            List(inputs.size) { ThreatLanguageResult(ThreatLanguageResultStatus.CANCELLED, reasonCode = "cancelled") }
        }
        inputs.forEachIndexed { index, input ->
            initial[input.event.eventId.value] = classified.getOrNull(index)
                ?: ThreatLanguageResult(ThreatLanguageResultStatus.NEEDS_REVIEW, reasonCode = "classifier_contract")
        }
        val byEvent = initial
        events.forEach { event ->
            val result = byEvent[event.eventId.value] ?: return@forEach
            if (result.status == ThreatLanguageResultStatus.POSSIBLE_THREAT_LANGUAGE) {
                val body = eventText.bodyOf(event)
                val quote = result.quote
                if (body == null || quote.isNullOrEmpty() || body.indexOf(quote) < 0 || body.indexOf(quote) != body.lastIndexOf(quote)) {
                    byEvent[event.eventId.value] = ThreatLanguageResult(
                        ThreatLanguageResultStatus.NEEDS_REVIEW,
                        reasonCode = "unmatched_or_ambiguous_quote",
                        modelPreset = result.modelPreset,
                        weightSha256 = result.weightSha256,
                        runtimeCommit = result.runtimeCommit,
                        runtimeVersion = result.runtimeVersion,
                    )
                }
            }
        }
        val outputEvents = events.map { event ->
            val result = byEvent[event.eventId.value]
                ?: ThreatLanguageResult(ThreatLanguageResultStatus.NOT_RUN, reasonCode = "not_scheduled")
            if (result.status != ThreatLanguageResultStatus.POSSIBLE_THREAT_LANGUAGE) return@map event
            val bodyReference = event.evidenceReferences.firstOrNull() ?: return@map event
            val bodyLocator = bodyReference.locator as? Locator.Text ?: return@map event
            val body = eventText.bodyOf(event) ?: return@map event
            val quote = result.quote?.takeIf { it.isNotEmpty() } ?: return@map event
            val startUtf16 = body.indexOf(quote).takeIf { it >= 0 && body.indexOf(quote, it + 1) < 0 } ?: return@map event
            val startCp = body.codePointCount(0, startUtf16)
            val endCp = startCp + quote.codePointCount(0, quote.length)
            val quoteReference = bodyReference.copy(
                referenceId = ReferenceId("threat_quote_${ids()}"),
                locator = Locator.Text(bodyLocator.start + startCp, bodyLocator.start + endCp),
            )
            event.copy(
                categories = event.categories + CategoryAssessment(
                    label = CategoryLabel.EXPLICIT_THREAT,
                    basis = CategoryBasis.CLASSIFIER_SUGGESTION,
                    confidence = Confidence(null, ConfidenceSemantics.UNKNOWN, null),
                    producerVersion = ScopeId(THREAT_TASK_VERSION),
                    evidenceReferenceIds = listOf(quoteReference.referenceId),
                    reviewStatus = CategoryReviewStatus.UNREVIEWED,
                ),
                evidenceReferences = event.evidenceReferences + quoteReference,
            )
        }
        val runs = events.map { event ->
            val result = byEvent[event.eventId.value]
                ?: ThreatLanguageResult(ThreatLanguageResultStatus.NOT_RUN, reasonCode = "not_scheduled")
            ThreatAnalysisRunDraft(
                id = ids(),
                caseId = details.caseId,
                eventId = event.eventId.value,
                eventRevision = event.revision,
                derivativeId = event.evidenceReferences.firstOrNull()?.artifactId?.value ?: derivative.id,
                requestId = requestId,
                status = result.status.toRunStatus(),
                reasonCode = result.reasonCode?.takeIf { it in THREAT_REASON_CODES },
                modelPreset = result.modelPreset,
                weightSha256 = result.weightSha256,
                runtimeCommit = result.runtimeCommit,
                runtimeVersion = result.runtimeVersion,
                taskVersion = THREAT_TASK_VERSION,
                createdAt = clock().toString(),
                findingId = null,
            )
        }
        return ThreatWrite(outputEvents, runs, inferenceCancelled)
    }

    private data class ThreatWrite(
        val events: List<Event>,
        val runs: List<ThreatAnalysisRunDraft>,
        val inferenceCancelled: Boolean,
    )

    private fun ThreatLanguageResultStatus.toRunStatus(): ThreatAnalysisRunStatus = when (this) {
        ThreatLanguageResultStatus.POSSIBLE_THREAT_LANGUAGE -> ThreatAnalysisRunStatus.POSSIBLE_THREAT_LANGUAGE
        ThreatLanguageResultStatus.NO_SIGNAL_UNCALIBRATED -> ThreatAnalysisRunStatus.NO_SIGNAL_UNCALIBRATED
        ThreatLanguageResultStatus.NEEDS_REVIEW -> ThreatAnalysisRunStatus.NEEDS_REVIEW
        ThreatLanguageResultStatus.UNSUPPORTED_LANGUAGE -> ThreatAnalysisRunStatus.UNSUPPORTED_LANGUAGE
        ThreatLanguageResultStatus.MODEL_UNAVAILABLE -> ThreatAnalysisRunStatus.MODEL_UNAVAILABLE
        ThreatLanguageResultStatus.INFERENCE_FAILED -> ThreatAnalysisRunStatus.INFERENCE_FAILED
        ThreatLanguageResultStatus.CANCELLED -> ThreatAnalysisRunStatus.CANCELLED
        ThreatLanguageResultStatus.TRUNCATED -> ThreatAnalysisRunStatus.TRUNCATED
        ThreatLanguageResultStatus.NOT_RUN -> ThreatAnalysisRunStatus.NOT_RUN
    }

    private fun describe(result: BatchSaveResult): String = when (result) {
        is BatchSaveResult.Saved -> "saved"
        BatchSaveResult.UnknownCase -> "unknown case"
        is BatchSaveResult.Invalid -> result.failures.joinToString { failure ->
            "event ${failure.index}: ${failure.violations.joinToString { it.code.name }} ${failure.problem ?: ""}".trim()
        }
    }

    /**
     * A recording is a file whose leading bytes are an audio type, or one the provider called audio whose leading bytes
     * cannot say: an unrecognised audio format, or an MP4 container, which looks the same with sound only or with
     * video. The claim only opens the speech lane; the decoder still refuses a file that holds no audio it can read.
     */
    private fun isRecording(details: EvidenceDetails): Boolean {
        val detected = details.detectedMime
        if (detected?.startsWith(AUDIO_PREFIX) == true) return true
        val declaredAudio = details.declaredMime?.trim()?.lowercase()?.startsWith(AUDIO_PREFIX) == true
        return declaredAudio && (detected == null || detected == MP4_CONTAINER)
    }

    private fun eligibility(details: EvidenceDetails): NotAnalysableReason? {
        val declaredText = details.declaredMime?.trim()?.lowercase()?.startsWith(TEXT_PREFIX) == true
        val sharedText = details.acquisitionKind == AcquisitionKind.SHARED_TEXT ||
            details.acquisitionKind == AcquisitionKind.PASTED_TEXT ||
            details.acquisitionKind == AcquisitionKind.NOTIFICATION_EXCERPT ||
            details.acquisitionKind == AcquisitionKind.VISIBLE_TEXT_SNAPSHOT
        return when {
            details.acquisitionKind == AcquisitionKind.MANUAL_NOTE -> NotAnalysableReason.MANUAL_NOTE
            details.detectedMime != null -> NotAnalysableReason.PRESERVE_ONLY_TYPE
            !declaredText && !sharedText -> NotAnalysableReason.PRESERVE_ONLY_TYPE
            details.byteSize > limits.maxTextBytes -> NotAnalysableReason.TOO_LARGE
            else -> null
        }
    }

    private suspend fun prepare(details: EvidenceDetails): Prepared {
        derivatives.latestParsedText(details.id)?.let { return Prepared.Ready(it) }
        val bytes = readOriginal(details) ?: return Prepared.Refused(NotAnalysableReason.UNREADABLE)
        val decoded = TextDecoder.decode(bytes)
        if (decoded !is DecodedText.Text || decoded.text.isEmpty()) return Prepared.Refused(NotAnalysableReason.NOT_TEXT)
        return Prepared.Ready(derivatives.saveParsedText(details.id, decoded.text))
    }

    /** The authenticated original, or null if it cannot be read or does not match its stored digest. */
    private suspend fun readOriginal(details: EvidenceDetails): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val bytes = vault.evidence.openOriginal(details.id).use { it.inputStream().readBytes() }
            bytes.takeIf { Sha256.hex(Sha256.digest(it)) == details.sha256 }
        } catch (_: IOException) {
            null
        } catch (_: GeneralSecurityException) {
            null
        }
    }

    private suspend fun alreadyAnalysed(caseId: CaseId, derivativeId: String): Boolean =
        vault.events.loadLatest(caseId, Instant.ofEpochMilli(Long.MAX_VALUE)).any { event ->
            event.evidenceReferences.any { it.artifactId.value == derivativeId }
        }

    private sealed interface Prepared {
        class Ready(val derivative: DerivativeText) : Prepared

        class Refused(val reason: NotAnalysableReason) : Prepared
    }

    private sealed interface PreparedImage {
        class Ready(val image: ImageText) : PreparedImage

        class Refused(val reason: NotAnalysableReason) : PreparedImage
    }

    private sealed interface Transcribed {
        class Done(val success: SttResult.Success) : Transcribed

        class Refused(val reason: NotAnalysableReason) : Transcribed
    }

    private companion object {
        const val THREAT_TASK_VERSION: String = "qwen-threat-language-v1"
        const val QWEN_PRESET_ID: String = "qwen2.5-1.5b-instruct-q4_k_m"
        const val LLAMA_COMMIT: String = "a7a98e0fffed794396b3fbad4dcdbbc184963645"
        const val LLAMA_VERSION: String = "llama.cpp-b6500"
        val THREAT_ELIGIBLE_INPUT_KINDS = setOf(InputKind.PLAIN_TEXT, InputKind.WHATSAPP_EXPORT)
        val THREAT_ELIGIBLE_SOURCE_KINDS = setOf(SourceKind.NOTIFICATION_EXCERPT, SourceKind.SELECTED_EXPORT, SourceKind.SELECTED_TEXT)
        val THREAT_REASON_CODES = setOf(
            "known_outgoing", "source_anchor_missing", "source_text_unavailable", "classifier_contract",
            "not_scheduled", "empty_body", "language_not_qualified", "event_budget", "malformed_output",
            "unmatched_or_ambiguous_quote", "ambiguous_context", "unknown_result_enum", "output_or_context_budget",
            "model_not_ready", "runtime_error", "resource_limit", "cancelled", "local_qwen_not_ready",
        )
        const val TEXT_PREFIX: String = "text/"
        const val AUDIO_PREFIX: String = "audio/"
        const val MP4_CONTAINER: String = "video/mp4"

        /** Detected types the image path reads; other images stay preserve-only. */
        val OCR_IMAGE_TYPES: Set<String> = setOf("image/jpeg", "image/png", "image/webp")

        /** Warnings after which the evidence counts as analysed in part. */
        val PARTIAL_WARNINGS: Set<AnalysisWarning> = setOf(
            AnalysisWarning.UNRESOLVED_TIMES,
            AnalysisWarning.RECORD_LIMIT_REACHED,
            AnalysisWarning.OCR_LOW_CONFIDENCE_LINES,
            AnalysisWarning.AUDIO_LOW_CONFIDENCE_SEGMENTS,
        )
    }
}
