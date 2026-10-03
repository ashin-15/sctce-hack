package org.sakshi.processing.analysis

import java.time.Instant
import java.time.ZoneId
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventId
import org.sakshi.core.temporal.ActorScope
import org.sakshi.core.temporal.EvidenceView
import org.sakshi.core.temporal.Explanation
import org.sakshi.core.temporal.PatternConfig
import org.sakshi.core.temporal.PatternExplanation
import org.sakshi.core.temporal.PatternRecord
import org.sakshi.core.temporal.TemporalEngine
import org.sakshi.core.temporal.TemporalInput
import org.sakshi.core.temporal.TemporalResult
import org.sakshi.core.vault.ComputedPattern
import org.sakshi.core.vault.PatternInputs
import org.sakshi.core.vault.PatternReview
import org.sakshi.core.vault.StoredPattern
import org.sakshi.core.vault.Vault
import org.sakshi.core.vault.toCoverageGap

/**
 * Temporal result for a case with wording for each pattern. [explanations] is keyed by pattern key and
 * [scopeLabels] by scope key; labels are the user's own aliases or source claims, never verified identities.
 * [storedIds] and [reviews] are keyed by pattern key and are filled only by [CasePatterns.refresh]: the id the
 * description is stored under and what the person has said about exactly that description. [reviewReasons] holds, for
 * rejected descriptions only, the `ReviewReason` constant the person gave (absent when none was given).
 */
public data class CasePatternView(
    val result: TemporalResult,
    val explanations: Map<String, Explanation>,
    val scopeLabels: Map<String, String>,
    val supportingEvents: Map<EventId, SupportingEventView> = emptyMap(),
    val storedIds: Map<String, String> = emptyMap(),
    val reviews: Map<String, PatternReview> = emptyMap(),
    val reviewReasons: Map<String, String> = emptyMap(),
)

/**
 * What [CasePatterns.stored] reads without recomputing. [patterns] are the stored descriptions, each with its own
 * [StoredPattern.stale] flag and review; [explanations] are keyed by stored id. [anyStale] is true when at least one
 * description is out of date, so a screen can offer a refresh.
 */
public data class StoredCasePatterns(
    val patterns: List<StoredPattern>,
    val explanations: Map<String, Explanation>,
    val scopeLabels: Map<String, String>,
) {
    val anyStale: Boolean get() = patterns.any { it.stale }
}

/** An event a pattern rests on, as a pattern card shows it. [bodyPreview] is at most 120 code points of its text. */
public data class SupportingEventView(
    val eventId: EventId,
    val revision: Int,
    val earliest: Instant?,
    val latest: Instant?,
    val bodyPreview: String?,
)

/**
 * Computes patterns from the stored events. [compute] writes nothing. [refresh] runs the same engine and stores the
 * result in the vault (`vault.patterns`), so later corrections can mark it out of date and the person can review it.
 * To observe stored descriptions as they change, collect `vault.patterns.observe` directly.
 */
public class CasePatterns(
    private val vault: Vault,
    private val clock: () -> Instant,
    private val config: PatternConfig = PatternConfig(),
) {
    /**
     * Deterministic for the same stored events, cutoff, [view] and [zone]. Times in [CasePatternView.explanations]
     * are written in [zone]. With [withSupportingEvents] the view also lists the events the patterns rest on, their
     * times and a text preview, from the one event load this call already makes and one batched read of the texts.
     */
    public suspend fun compute(
        caseId: CaseId,
        view: EvidenceView,
        zone: ZoneId,
        withSupportingEvents: Boolean = false,
    ): CasePatternView {
        val now = clock()
        val events = vault.events.loadLatest(caseId, now)
        val gaps = vault.events.coverageGaps(caseId).map { it.toCoverageGap() }
        val result = TemporalEngine.analyse(
            TemporalInput(caseId, events, gaps, now, view, config.copy(zone = zone)),
        )
        val actorLabels = vault.actors.list(caseId).associate { it.id to it.displayLabel }
        val labels = scopeLabels(result.patterns, actorLabels)
        val explanations = explain(result.patterns, labels, zone)
        val supporting = if (withSupportingEvents) supportingViews(result, events) else emptyMap()
        return CasePatternView(result, explanations, labels, supporting)
    }

    /**
     * Like [compute], but also stores the descriptions through `vault.patterns.recompute` in one transaction with the
     * read of their inputs: unchanged descriptions are kept (and are no longer stale), vanished ones are removed.
     * The returned view has the same [CasePatternView.result], wording and labels as [compute] would give for the
     * same stored events, plus [CasePatternView.storedIds] and [CasePatternView.reviews]. The stored interpretation
     * sentence is the one rendered here, in [zone]. Descriptions are zone-dependent (calendar days follow [zone]), so
     * a different zone can give different descriptions with different ids.
     */
    public suspend fun refresh(
        caseId: CaseId,
        view: EvidenceView,
        zone: ZoneId,
        withSupportingEvents: Boolean = false,
    ): CasePatternView {
        var read: PatternInputs? = null
        var analysed: TemporalResult? = null
        val stored = vault.patterns.recompute(caseId, view) { inputs ->
            val result = TemporalEngine.analyse(
                TemporalInput(caseId, inputs.events, inputs.gaps, inputs.now, view, config.copy(zone = zone)),
            )
            read = inputs
            analysed = result
            val labels = scopeLabels(result.patterns, inputs.actorLabels)
            result.patterns.map { ComputedPattern(it, render(it, labels, zone).interpretation) }
        }
        val inputs = checkNotNull(read) { "recompute did not run the engine" }
        val result = checkNotNull(analysed) { "recompute did not run the engine" }
        val labels = scopeLabels(result.patterns, inputs.actorLabels)
        val supporting = if (withSupportingEvents) supportingViews(result, inputs.events) else emptyMap()
        val byKey = stored.associateBy { it.record.patternKey }
        return CasePatternView(
            result = result,
            explanations = explain(result.patterns, labels, zone),
            scopeLabels = labels,
            supportingEvents = supporting,
            storedIds = byKey.mapValues { it.value.id },
            reviews = byKey.mapValues { it.value.review },
            reviewReasons = byKey.mapNotNull { (key, pattern) -> pattern.reviewReason?.let { key to it } }.toMap(),
        )
    }

    /**
     * The stored descriptions of the case for [view] as they are, with wording rendered in [zone] from the stored
     * records. Nothing is computed or written, so out-of-date descriptions are shown as such ([StoredPattern.stale]).
     */
    public suspend fun stored(caseId: CaseId, view: EvidenceView, zone: ZoneId): StoredCasePatterns {
        val patterns = vault.patterns.list(caseId, view)
        val actorLabels = vault.actors.list(caseId).associate { it.id to it.displayLabel }
        val records = patterns.map { it.record }
        val labels = scopeLabels(records, actorLabels)
        val explanations = patterns.associate { it.id to render(it.record, labels, zone) }
        return StoredCasePatterns(patterns, explanations, labels)
    }

    private fun scopeLabels(records: List<PatternRecord>, actorLabels: Map<ActorId, String>): Map<String, String> =
        records.map { it.actorScope }.distinctBy { it.key }.associate { scope ->
            scope.key to when (scope) {
                is ActorScope.Confirmed -> actorLabels[scope.actorId] ?: UNLABELLED
                is ActorScope.Unresolved -> scope.displayLabel ?: UNLABELLED
            }
        }

    private fun render(record: PatternRecord, labels: Map<String, String>, zone: ZoneId): Explanation =
        PatternExplanation.render(record, { labels[it.key] ?: UNLABELLED }, zone)

    private fun explain(records: List<PatternRecord>, labels: Map<String, String>, zone: ZoneId): Map<String, Explanation> =
        records.associate { it.patternKey to render(it, labels, zone) }

    private suspend fun supportingViews(result: TemporalResult, events: List<Event>): Map<EventId, SupportingEventView> {
        val wanted = result.patterns.flatMap { record -> record.supportingEvents.map { it.eventId } }.toSet()
        val used = events.filter { it.eventId in wanted }
        val bodies = EventText(vault).bodiesOf(used)
        return used.associate { event ->
            event.eventId to SupportingEventView(
                event.eventId,
                event.revision,
                event.timestamp.earliest?.instant,
                event.timestamp.latest?.instant,
                bodies[event.eventId]?.let(::preview),
            )
        }
    }

    private fun preview(body: String): String =
        if (body.codePointCount(0, body.length) <= PREVIEW_CODE_POINTS) body else body.substring(0, body.offsetByCodePoints(0, PREVIEW_CODE_POINTS))

    private companion object {
        const val UNLABELLED: String = "unlabelled sender"
        const val PREVIEW_CODE_POINTS: Int = 120
    }
}
