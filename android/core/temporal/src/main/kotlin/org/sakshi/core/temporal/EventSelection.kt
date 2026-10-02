package org.sakshi.core.temporal

import java.time.Instant
import org.sakshi.core.model.ConfirmationStatus
import org.sakshi.core.model.DedupStatus
import org.sakshi.core.model.Direction
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventKind
import org.sakshi.core.model.RetentionMode

/** Absolute-time bounds of an event; both bounds are known and ordered. */
internal data class Interval(val earliest: Instant, val latest: Instant) {
    fun definitelyBefore(other: Interval): Boolean = latest < other.earliest
}

/** An event revision that may be used at the knowledge cutoff. [pending] means not confirmed at the cutoff. */
internal class Selected(val event: Event, val pending: Boolean, val interval: Interval?)

internal fun Event.isContact(): Boolean =
    (eventKind == EventKind.MESSAGE_OBSERVATION || eventKind == EventKind.CONTACT_ATTEMPT_OBSERVATION) &&
        direction == Direction.INCOMING &&
        deduplication.status != DedupStatus.LIFECYCLE_ONLY

private fun Event.interval(): Interval? {
    val earliest = timestamp.earliest?.instant ?: return null
    val latest = timestamp.latest?.instant ?: return null
    return if (earliest <= latest) Interval(earliest, latest) else null
}

private fun <T : Comparable<T>> compareNullsLast(a: T?, b: T?): Int =
    when {
        a == null && b == null -> 0
        a == null -> 1
        b == null -> -1
        else -> a.compareTo(b)
    }

/** Display order: earliest, latest, event id, with untimed events last. */
internal val selectedOrder: Comparator<Selected> =
    Comparator { a, b ->
        val byEarliest = compareNullsLast(a.interval?.earliest, b.interval?.earliest)
        if (byEarliest != 0) return@Comparator byEarliest
        val byLatest = compareNullsLast(a.interval?.latest, b.interval?.latest)
        if (byLatest != 0) return@Comparator byLatest
        a.event.eventId.value.compareTo(b.event.eventId.value)
    }

/** Chooses, per event id, the revision and review state visible at the knowledge cutoff. */
internal object EventSelection {
    private val revisionOrder: Comparator<Event> =
        compareBy<Event>({ it.revision }, { it.availableAt.instant }, { it.toString() })

    fun select(input: TemporalInput): List<Selected> {
        val cutoff = input.knowledgeCutoff
        return input.events
            .filter { it.caseId == input.caseId && it.availableAt.instant <= cutoff }
            .groupBy { it.eventId }
            .values
            .mapNotNull { revisions -> classify(revisions.maxWith(revisionOrder), cutoff, input.view) }
            .sortedBy { it.event.eventId.value }
    }

    private fun classify(event: Event, cutoff: Instant, view: EvidenceView): Selected? {
        val retention = event.retention
        val candidateExpired =
            retention.mode == RetentionMode.ENCRYPTED_CANDIDATE &&
                retention.expiresAt?.instant?.let { it <= cutoff } == true
        if (candidateExpired) return null
        val confirmation = event.userConfirmation
        val reviewKnown = confirmation.reviewedAt?.instant?.let { it <= cutoff } == true
        val pending =
            when (confirmation.status) {
                ConfirmationStatus.EXPIRED -> return null
                // A rejection without a review time cannot be placed after the cutoff, so it stays excluded.
                ConfirmationStatus.REJECTED -> if (reviewKnown || confirmation.reviewedAt == null) return null else true
                ConfirmationStatus.CONFIRMED -> !reviewKnown
                ConfirmationStatus.PENDING -> true
            }
        if (pending && view == EvidenceView.CONFIRMED_ONLY) return null
        return Selected(event, pending, event.interval())
    }
}
