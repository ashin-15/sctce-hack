package org.sakshi.export.report

import java.time.ZoneId
import org.sakshi.core.integrity.Sha256
import org.sakshi.core.temporal.ActorScope
import org.sakshi.core.temporal.EventRef
import org.sakshi.core.temporal.Explanation
import org.sakshi.core.temporal.PatternExplanation
import org.sakshi.core.temporal.PatternRecord
import org.sakshi.export.bundle.Pattern
import org.sakshi.export.bundle.PatternReference

private const val ID_HEX_LENGTH: Int = 16

/** Maps engine records to report blocks and bundle patterns. Wording comes from [PatternExplanation]. */
internal object PatternBlocks {
    /** Times are written in [zone], the same zone as the timeline. */
    fun explain(record: PatternRecord, actorLabels: Map<String, String>, zone: ZoneId): Explanation =
        PatternExplanation.render(record, { scope -> actorText(scope, actorLabels) }, zone)

    fun idOf(record: PatternRecord): String =
        "pattern-" + Sha256.hex(Sha256.digest(record.patternKey.toByteArray(Charsets.UTF_8))).take(ID_HEX_LENGTH)

    fun block(record: PatternRecord, actorLabels: Map<String, String>, zone: ZoneId): PatternBlock {
        val explanation = explain(record, actorLabels, zone)
        return PatternBlock(
            id = idOf(record),
            heading = ReportText.PATTERN_TYPE.getValue(record.type),
            assessment = ReportText.PATTERN_STATUS_WORDS.getValue(record.status),
            observed = explanation.observed,
            interpretation = explanation.interpretation,
            limitations = explanation.limitations,
            supportingEventIds = record.supportingEvents.map { it.eventId.value }.distinct(),
            ruleVersion = record.ruleVersion,
        )
    }

    /** Null when the record has no supporting event, which the bundle format does not allow. */
    fun bundlePattern(record: PatternRecord, actorLabels: Map<String, String>, zone: ZoneId): Pattern? {
        if (record.supportingEvents.isEmpty()) return null
        val explanation = explain(record, actorLabels, zone)
        return Pattern(
            id = idOf(record),
            type = record.type.name.lowercase(),
            ruleVersion = record.ruleVersion,
            status = record.status.name.lowercase(),
            evidenceView = record.view.name.lowercase(),
            knowledgeCutoff = record.knowledgeCutoff.toString(),
            supporting = record.supportingEvents.map(::reference),
            context = record.contextEvents.map(::reference),
            limitations = explanation.limitations,
            observedText = explanation.observed,
            interpretationText = explanation.interpretation,
        )
    }

    private fun reference(ref: EventRef): PatternReference =
        PatternReference(ref.eventId.value, ref.revision, ref.role.name.lowercase())

    private fun actorText(scope: ActorScope, labels: Map<String, String>): String = when (scope) {
        is ActorScope.Confirmed -> labels[scope.actorId.value] ?: ReportText.ACTOR_UNNAMED
        is ActorScope.Unresolved ->
            scope.displayLabel?.let { ReportText.fill(ReportText.ACTOR_UNRESOLVED, it) } ?: ReportText.ACTOR_UNNAMED
    }
}
