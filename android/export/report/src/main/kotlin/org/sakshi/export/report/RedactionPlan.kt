package org.sakshi.export.report

import org.sakshi.core.integrity.Sha256
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventId
import org.sakshi.export.bundle.RedactionSummary
import org.sakshi.export.bundle.Redactor

/** One quote whose removed parts have been replaced. [text] never holds the removed text. */
internal class RedactedReference(
    val eventId: String,
    val referenceId: String,
    val artifactId: String,
    val text: String,
    val passages: Int,
) {
    /** File name of the copy in `derivatives/`. Derived from ids only, never from text. */
    val copyId: String =
        "redacted-" + Sha256.hex(Sha256.digest("sakshi-redacted:$eventId:$referenceId".toByteArray(Charsets.UTF_8))).take(COPY_ID_LENGTH)

    private companion object {
        const val COPY_ID_LENGTH = 32
    }
}

/** The quotes of a selection after removal, keyed by event id. */
internal class RedactionPlan(private val byEvent: Map<String, RedactedReference>) {
    val isEmpty: Boolean get() = byEvent.isEmpty()
    val references: List<RedactedReference> get() = byEvent.values.sortedBy { it.eventId }

    fun of(event: Event): RedactedReference? = byEvent[event.eventId.value]

    fun summary(originalMayHoldRemovedContent: Boolean): RedactionSummary =
        if (isEmpty) {
            RedactionSummary.NONE
        } else {
            RedactionSummary(byEvent.size, byEvent.values.sumOf { it.passages }, originalMayHoldRemovedContent)
        }

    companion object {
        val NONE: RedactionPlan = RedactionPlan(emptyMap())
    }
}

/** Outcome of [RedactionPlanner.plan]. */
internal sealed interface PlanResult {
    class Ready(val plan: RedactionPlan) : PlanResult

    class Refused(val reason: RefusalReason) : PlanResult
}

/** Applies the requested removals to the first quoted text of each event. */
internal object RedactionPlanner {
    suspend fun plan(selection: ReportSelection, chosen: List<Event>, quotes: QuoteSource): PlanResult {
        val requested = selection.redactions.filterValues { it.isNotEmpty() }
        if (requested.isEmpty()) return PlanResult.Ready(RedactionPlan.NONE)
        val events = chosen.associateBy { it.eventId }
        val done = HashMap<String, RedactedReference>()
        for ((eventId, spans) in requested.toSortedMap(compareBy(EventId::value))) {
            val event = events[eventId] ?: return PlanResult.Refused(RefusalReason.UNKNOWN_EVENT)
            val quoted = firstQuote(event, quotes) ?: return PlanResult.Refused(RefusalReason.CHANGED_SINCE_PREVIEW)
            val result = try {
                Redactor.apply(quoted.second, spans)
            } catch (_: IllegalArgumentException) {
                return PlanResult.Refused(RefusalReason.CHANGED_SINCE_PREVIEW)
            }
            done[eventId.value] = RedactedReference(eventId.value, quoted.first.referenceId.value, quoted.first.artifactId.value, result.text, result.passageCount)
        }
        return PlanResult.Ready(RedactionPlan(done))
    }

    private suspend fun firstQuote(event: Event, quotes: QuoteSource) =
        event.evidenceReferences.firstNotNullOfOrNull { reference -> quotes.quote(event, reference)?.let { reference to it } }
}
