package org.sakshi.processing.analysis

import java.time.Instant
import java.time.ZoneId
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventId
import org.sakshi.core.temporal.ActorScope
import org.sakshi.core.temporal.CoverageGap
import org.sakshi.core.temporal.EvidenceView
import org.sakshi.core.temporal.Explanation
import org.sakshi.core.temporal.GapReason
import org.sakshi.core.temporal.PatternConfig
import org.sakshi.core.temporal.PatternExplanation
import org.sakshi.core.temporal.TemporalEngine
import org.sakshi.core.temporal.TemporalInput
import org.sakshi.core.temporal.TemporalResult
import org.sakshi.core.vault.StoredCoverageGap
import org.sakshi.core.vault.Vault

/**
 * Temporal result for a case with wording for each pattern. [explanations] is keyed by pattern key and
 * [scopeLabels] by scope key; labels are the user's own aliases or source claims, never verified identities.
 */
public data class CasePatternView(
    val result: TemporalResult,
    val explanations: Map<String, Explanation>,
    val scopeLabels: Map<String, String>,
    val supportingEvents: Map<EventId, SupportingEventView> = emptyMap(),
)

/** An event a pattern rests on, as a pattern card shows it. [bodyPreview] is at most 120 code points of its text. */
public data class SupportingEventView(
    val eventId: EventId,
    val revision: Int,
    val earliest: Instant?,
    val latest: Instant?,
    val bodyPreview: String?,
)

/** Computes patterns on demand from the stored events. Nothing is written. */
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
        val gaps = vault.events.coverageGaps(caseId).map { it.toGap() }
        val result = TemporalEngine.analyse(
            TemporalInput(caseId, events, gaps, now, view, config.copy(zone = zone)),
        )
        val actorLabels = vault.actors.list(caseId).associate { it.id to it.displayLabel }
        val scopes = result.patterns.map { it.actorScope }.distinctBy { it.key }
        val labels = scopes.associate { scope ->
            scope.key to when (scope) {
                is ActorScope.Confirmed -> actorLabels[scope.actorId] ?: UNLABELLED
                is ActorScope.Unresolved -> scope.displayLabel ?: UNLABELLED
            }
        }
        val explanations = result.patterns.associate { record ->
            record.patternKey to PatternExplanation.render(record, { labels[it.key] ?: UNLABELLED }, zone)
        }
        val supporting = if (withSupportingEvents) supportingViews(result, events) else emptyMap()
        return CasePatternView(result, explanations, labels, supporting)
    }

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

    private fun StoredCoverageGap.toGap(): CoverageGap = CoverageGap(
        id = id,
        caseId = caseId,
        actorId = null,
        start = startAt?.instant,
        end = endAt?.instant,
        reason = GapReason.entries.firstOrNull { it.name.equals(reason, ignoreCase = true) } ?: GapReason.UNKNOWN,
    )

    private companion object {
        const val UNLABELLED: String = "unlabelled sender"
        const val PREVIEW_CODE_POINTS: Int = 120
    }
}
