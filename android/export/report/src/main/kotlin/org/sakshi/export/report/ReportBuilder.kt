package org.sakshi.export.report

import java.time.Instant
import kotlinx.coroutines.flow.first
import org.sakshi.core.integrity.Sha256
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.ConfirmationStatus
import org.sakshi.core.model.Event
import org.sakshi.core.model.Locator
import org.sakshi.core.model.Representation
import org.sakshi.core.temporal.CoverageGap
import org.sakshi.core.temporal.EvidenceView
import org.sakshi.core.temporal.PatternConfig
import org.sakshi.core.temporal.TemporalEngine
import org.sakshi.core.temporal.TemporalInput
import org.sakshi.core.temporal.TemporalResult
import org.sakshi.core.vault.DecisionTargets
import org.sakshi.core.vault.StoredCoverageGap
import org.sakshi.core.vault.Vault
import org.sakshi.export.bundle.Correction
import org.sakshi.export.bundle.GeneratorInfo
import org.sakshi.export.bundle.OmittedCounts
import org.sakshi.export.bundle.RedactionSummary

/**
 * Builds the report model and the bundle inputs for a selection. Patterns are computed over the selected
 * events only, so the report never cites a record that is not in it.
 *
 * Corrections are the user's stored review decisions about the selected events and their tags. Their target
 * ids are the vault's, so a decision about a tag resolves to the finding of the same id when that tag is listed.
 */
public class ReportBuilder(
    private val vault: Vault,
    private val quotes: QuoteSource,
    private val clock: () -> Instant,
    private val generator: GeneratorInfo,
) {
    /** [signerKeyId] is printed in the integrity appendix; pass the key id of the signer that will sign the bundle. */
    public suspend fun build(
        selection: ReportSelection,
        options: ReportOptions = ReportOptions(),
        signerKeyId: String? = null,
    ): ReportBuildResult {
        if (selection.eventIds.isEmpty()) return refused(RefusalReason.EMPTY_SELECTION)
        val title = caseTitle(selection.caseId) ?: return refused(RefusalReason.CASE_MISSING)
        val now = clock()
        val latest = vault.events.loadLatest(selection.caseId, now)
        val byId = latest.associateBy { it.eventId }
        if (selection.eventIds.any { it !in byId }) return refused(RefusalReason.UNKNOWN_EVENT)
        val chosen = selection.eventIds.map { byId.getValue(it) }
        if (chosen.any { it.userConfirmation.status != ConfirmationStatus.CONFIRMED }) {
            return refused(RefusalReason.UNCONFIRMED_EVENT)
        }
        val evidence = vault.evidence.observeForCase(selection.caseId.value).first().map { it.id }.toSet()
        if (!evidence.containsAll(selection.includeOriginalsFor)) return refused(RefusalReason.UNKNOWN_EVIDENCE)
        val plan = when (val planned = RedactionPlanner.plan(selection, chosen, quotes)) {
            is PlanResult.Refused -> return refused(planned.reason)
            is PlanResult.Ready -> planned.plan
        }
        val request = Request(selection, options, signerKeyId, title, now, latest, chosen, evidence.size, plan)
        return assemble(request)
    }

    private class Request(
        val selection: ReportSelection,
        val options: ReportOptions,
        val signerKeyId: String?,
        val title: String,
        val now: Instant,
        val caseEvents: List<Event>,
        val chosen: List<Event>,
        val evidenceCount: Int,
        val redaction: RedactionPlan,
    )

    private suspend fun assemble(r: Request): ReportBuildResult {
        val caseId = r.selection.caseId
        val storedGaps = vault.events.coverageGaps(caseId)
        val analysis = analyse(r, storedGaps)
        val actorLabels = vault.actors.list(caseId).associate { it.id.value to it.displayLabel }
        val ordered = displayOrder(r.chosen, analysis)
        val details = DetailCache(vault, caseId)

        val tags = ordered.associateWith { EventBlocks.split(it, r.options) }
        val blocks = ordered.mapIndexed { index, event ->
            EventBlocks.block(index + 1, event, r.selection.zone, tags.getValue(event), quotes, r.redaction.of(event)) {
                details.sha256(it)
            }
        }
        val omitted = omittedCounts(r)
        val redactions = r.redaction.summary(originalHoldsRemovedText(r))
        val scope = scope(r, storedGaps.size, details.acquisitionKinds(ordered), redactions)
        val model = ReportModel(
            title = r.title,
            reportVersion = r.options.reportVersion,
            generatedAt = r.now,
            generator = generator,
            scope = scope,
            timeline = ordered.map { EventBlocks.timelineRow(it, r.selection.zone) },
            events = blocks,
            patterns = analysis.patterns.map { PatternBlocks.block(it, actorLabels, r.selection.zone) },
            unknowns = Unknowns.build(
                ordered, storedGaps, r.selection.zone, r.options, omitted, tags.values.sumOf { it.withheld },
            ),
            integrity = IntegrityAppendix(
                manifestHash = null,
                merkleRoot = null,
                signerKeyId = r.signerKeyId,
                auditChainHead = Sha256.hex(vault.audit.head()),
                limits = ReportText.LIMITS,
                derivativeNote = ReportText.NOT_EVIDENCE,
            ),
        )
        val inputs = BundleInputs(
            caseId = caseId.value,
            events = ordered.map { withheld(it, r.redaction) },
            findings = ordered.flatMap { EventBlocks.findings(it, tags.getValue(it)) },
            corrections = corrections(r.selection.caseId, ordered),
            patterns = analysis.patterns.mapNotNull { PatternBlocks.bundlePattern(it, actorLabels, r.selection.zone) },
            omitted = omitted,
            auditChainHead = model.integrity.auditChainHead,
            includeOriginalsFor = r.selection.includeOriginalsFor,
            redactions = redactions,
            redactedCopies = r.redaction.references.map { RedactedCopy(it.copyId, it.artifactId, it.text) },
        )
        return ReportBuildResult.Built(model, inputs)
    }

    private fun analyse(r: Request, gaps: List<StoredCoverageGap>): TemporalResult {
        val coverage = gaps.map {
            CoverageGap(it.id, it.caseId, null, it.startAt?.instant, it.endAt?.instant, Unknowns.reasonOf(it.reason))
        }
        return TemporalEngine.analyse(
            TemporalInput(
                caseId = r.selection.caseId,
                events = r.chosen,
                gaps = coverage,
                knowledgeCutoff = r.now,
                view = r.selection.view,
                config = PatternConfig(zone = r.selection.zone),
            ),
        )
    }

    private fun displayOrder(chosen: List<Event>, analysis: TemporalResult): List<Event> {
        val byId = chosen.associateBy { it.eventId }
        val timed = analysis.timeline.mapNotNull { byId[it.eventId] }
        val rest = chosen.filter { it !in timed }.sortedBy { it.eventId.value }
        return (timed + rest).distinct()
    }

    private suspend fun corrections(caseId: CaseId, events: List<Event>): List<Correction> =
        events.flatMap { event ->
            val tagTargets = (1..event.revision).flatMap { revision ->
                event.categories.indices.map { DecisionTargets.category(event.eventId, revision, it) }
            }
            (listOf(event.eventId.value) + tagTargets).flatMap { vault.review.decisions(it) }
        }
            .filter { it.caseId == caseId.value }
            .distinctBy { it.id }
            .sortedBy { it.seq }
            .map {
                Correction(it.id, it.targetType, it.targetId, it.targetRevision, it.action, it.reasonCode, it.decidedAt)
            }

    /**
     * The copy of [event] that goes into the bundle. The reference a person removed text from loses its hash and its
     * exact range, which would otherwise let a reader test guesses at the removed part or work out its length.
     */
    private fun withheld(event: Event, plan: RedactionPlan): Event {
        val redacted = plan.of(event) ?: return event
        return event.copy(
            evidenceReferences = event.evidenceReferences.map {
                if (it.referenceId.value == redacted.referenceId) it.copy(sha256 = null, locator = Locator.WholeArtifact) else it
            },
        )
    }

    /** True when a redacted quote was read from a file that is also included as an original. */
    private suspend fun originalHoldsRemovedText(r: Request): Boolean = r.redaction.references.any { reference ->
        val evidenceId = vault.derivatives.get(reference.artifactId)?.evidenceId ?: reference.artifactId
        evidenceId in r.selection.includeOriginalsFor
    }

    private fun omittedCounts(r: Request): OmittedCounts {
        val derivatives = r.caseEvents.flatMap { it.evidenceReferences }
            .filter { it.representation == Representation.OCR_DERIVATIVE || it.representation == Representation.TRANSCRIPT_DERIVATIVE }
            .map { it.artifactId.value }
            .toSet()
        return OmittedCounts(
            evidenceCount = (r.evidenceCount - r.selection.includeOriginalsFor.size).coerceAtLeast(0),
            derivativeCount = derivatives.size,
            eventCount = r.caseEvents.size - r.chosen.size,
        )
    }

    private fun scope(r: Request, gapCount: Int, methods: List<String>, redactions: RedactionSummary): ScopeStatement {
        val lines = mutableListOf(
            ReportText.fill(ReportText.SCOPE_SELECTED, r.chosen.size, r.caseEvents.size),
            ReportText.fill(ReportText.SCOPE_EXCLUDED, r.caseEvents.size - r.chosen.size),
            ReportText.fill(
                ReportText.SCOPE_ORIGINALS,
                r.selection.includeOriginalsFor.size,
                (r.evidenceCount - r.selection.includeOriginalsFor.size).coerceAtLeast(0),
            ),
            if (r.selection.view == EvidenceView.CONFIRMED_ONLY) {
                ReportText.SCOPE_VIEW_CONFIRMED
            } else {
                ReportText.SCOPE_VIEW_CANDIDATE
            },
            ReportText.fill(ReportText.SCOPE_ZONE, r.selection.zone.id),
            ReportText.SCOPE_UTC_NOTE,
        )
        if (r.selection.includeOriginalsFor.isNotEmpty()) lines += ReportText.SCOPE_ORIGINALS_NOTE
        if (!redactions.isEmpty) {
            lines += ReportText.fill(ReportText.SCOPE_REDACTED, redactions.passageCount, redactions.eventCount)
            lines += ReportText.SCOPE_REDACTED_LIMIT
            if (redactions.originalMayHoldRemovedContent) lines += ReportText.SCOPE_REDACTED_ORIGINAL
        }
        if (methods.isNotEmpty()) lines += ReportText.fill(ReportText.SCOPE_METHODS, methods.joinToString(", "))
        val sources = r.chosen.map { ReportText.SOURCE_KIND.getValue(it.source.kind) }.distinct().sorted()
        if (sources.isNotEmpty()) lines += ReportText.fill(ReportText.SCOPE_SOURCES, sources.joinToString(", "))
        if (gapCount > 0) lines += ReportText.fill(ReportText.SCOPE_GAPS, gapCount)
        return ScopeStatement(lines)
    }

    private fun refused(reason: RefusalReason): ReportBuildResult = ReportBuildResult.Refused(reason)

    private suspend fun caseTitle(caseId: CaseId): String? =
        vault.cases.observe().first().firstOrNull { it.id == caseId.value }?.title
}
