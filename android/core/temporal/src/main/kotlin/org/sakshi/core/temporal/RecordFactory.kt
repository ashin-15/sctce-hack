package org.sakshi.core.temporal

import org.sakshi.core.model.AssociationReview
import org.sakshi.core.model.CoverageContext
import org.sakshi.core.model.Direction
import org.sakshi.core.model.Event
import org.sakshi.core.model.IdentityBasis
import org.sakshi.core.model.OutgoingCoverage

/** An event revision with the role it plays in one record. */
internal data class Part(val selected: Selected, val role: SupportRole)

/** Assembles a [PatternRecord] and derives the limitations that follow from its events and gaps. */
internal class RecordFactory(private val input: TemporalInput, private val projection: TemporalProjection) {
    private val partOrder: Comparator<Part> =
        Comparator<Part> { a, b -> selectedOrder.compare(a.selected, b.selected) }
            .thenComparing { part -> part.role.ordinal }

    fun build(
        type: PatternType,
        keyTail: String,
        scope: ActorScope,
        window: Interval?,
        gapWindow: Interval?,
        supporting: List<Part>,
        context: List<Part>,
        measurements: Measurements,
        status: AssessmentStatus,
        extraLimitations: Set<Limitation>,
    ): PatternRecord {
        val supportingParts = supporting.distinct().sortedWith(partOrder)
        val contextParts = context.distinct().sortedWith(partOrder)
        val events = (supportingParts + contextParts).map { it.selected }.distinctBy { it.event.eventId }
        val gapIds =
            gapWindow
                ?.let { projection.overlapping(scope, it.earliest, it.latest) }
                .orEmpty()
                .map { it.id }
                .distinct()
                .sortedBy { it.value }
        val limitations = sortedSetOf<Limitation>()
        limitations += extraLimitations
        if (gapIds.isNotEmpty()) limitations += Limitation.COVERAGE_GAP
        if (events.any { it.pending }) limitations += Limitation.UNCONFIRMED_EVIDENCE
        for (entry in events) limitations += entry.event.coverageLimitations()
        when (scope) {
            is ActorScope.Unresolved -> limitations += Limitation.ACTOR_UNRESOLVED
            is ActorScope.Confirmed ->
                if (events.any { it.event.isUserAssertedIncoming() }) {
                    limitations += Limitation.SENDER_NOT_AUTHENTICATED
                }
        }
        val cappedStatus =
            if (status == AssessmentStatus.SUPPORTED_DESCRIPTION && events.any { it.pending }) {
                AssessmentStatus.CANDIDATE
            } else {
                status
            }
        return PatternRecord(
            patternKey = "${type.name}|${scope.key}|$keyTail|${input.config.ruleVersion}",
            type = type,
            ruleVersion = input.config.ruleVersion,
            caseId = input.caseId,
            actorScope = scope,
            view = input.view,
            knowledgeCutoff = input.knowledgeCutoff,
            windowStart = window?.earliest,
            windowEnd = window?.latest,
            clockBases = events.map { it.event.timestamp.basis }.toSortedSet(),
            supportingEvents = supportingParts.map { it.toRef() },
            contextEvents = contextParts.map { it.toRef() },
            measurements = measurements,
            limitations = limitations,
            gapIds = gapIds,
            status = cappedStatus,
        )
    }

    private fun Part.toRef(): EventRef = EventRef(selected.event.eventId, selected.event.revision, role)
}

private fun Event.coverageLimitations(): Set<Limitation> {
    val found = sortedSetOf<Limitation>()
    if (coverage.outgoingCoverage != OutgoingCoverage.INCLUDED_FOR_SELECTED_RANGE) {
        found += Limitation.OUTGOING_COVERAGE_UNKNOWN
    }
    when (coverage.context) {
        CoverageContext.SELECTION_PARTIAL -> found += Limitation.SELECTION_PARTIAL
        CoverageContext.NOTIFICATION_PARTIAL -> found += Limitation.NOTIFICATION_PARTIAL
        CoverageContext.COMPLETE_FOR_SELECTED_RANGE, CoverageContext.UNKNOWN -> Unit
    }
    return found
}

private fun Event.isUserAssertedIncoming(): Boolean =
    direction == Direction.INCOMING &&
        sender.identityBasis == IdentityBasis.USER_ASSERTED &&
        sender.associationReview == AssociationReview.CONFIRMED
