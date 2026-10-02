package org.sakshi.core.temporal

import java.time.Duration
import java.time.Instant
import org.sakshi.core.model.EventId

/** A contact group with a known interval. */
internal class TimedGroup(val group: ContactGroup, val interval: Interval)

internal fun timedOf(groups: List<ContactGroup>): List<TimedGroup> =
    groups.mapNotNull { group -> group.interval?.let { TimedGroup(group, it) } }

internal fun boundsOf(groups: List<ContactGroup>): CountBounds =
    CountBounds(groups.map { it.clusterKey }.toSet().size, groups.size)

/**
 * Window counting over timed contacts sorted by earliest time. A contact is definitely in a window when its
 * whole interval is inside, and possibly in it when the interval intersects it. Possible duplicates collapse
 * to one in the lower bound.
 */
internal class ContactIndex(private val timed: List<TimedGroup>) {
    private val earliest: List<Instant> = timed.map { it.interval.earliest }
    private val maxSpan: Duration =
        timed.maxOfOrNull { Duration.between(it.interval.earliest, it.interval.latest) } ?: Duration.ZERO
    private val hasClusters: Boolean = timed.any { it.group.clusterSize > 1 }

    private fun firstIndexAtOrAfter(target: Instant): Int {
        var low = 0
        var high = earliest.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (earliest[mid] < target) low = mid + 1 else high = mid
        }
        return low
    }

    /** Bounds for the half-open window [start, end). */
    fun countIn(start: Instant, end: Instant): CountBounds {
        var upper = 0
        var definite = 0
        val clusters = HashSet<EventId>()
        var index = firstIndexAtOrAfter(start.minus(maxSpan))
        while (index < timed.size && earliest[index] < end) {
            val entry = timed[index]
            val interval = entry.interval
            if (interval.latest >= start) {
                upper++
                if (interval.earliest >= start && interval.latest < end) {
                    if (hasClusters) clusters.add(entry.group.clusterKey) else definite++
                }
            }
            index++
        }
        return CountBounds(if (hasClusters) clusters.size else definite, upper)
    }

    /** Contacts whose interval intersects [start, end), in order. */
    fun possiblyIn(start: Instant, end: Instant): List<TimedGroup> {
        val found = mutableListOf<TimedGroup>()
        var index = firstIndexAtOrAfter(start.minus(maxSpan))
        while (index < timed.size && earliest[index] < end) {
            if (timed[index].interval.latest >= start) found += timed[index]
            index++
        }
        return found
    }

    /** Highest lower and highest upper count over windows of [size] anchored at each contact. */
    fun maxWindow(size: Duration): CountBounds {
        var lower = 0
        var upper = 0
        for (anchor in timed) {
            val bounds = countIn(anchor.interval.earliest, anchor.interval.earliest.plus(size))
            lower = maxOf(lower, bounds.lower)
            upper = maxOf(upper, bounds.upper)
        }
        return CountBounds(lower, upper)
    }
}

/** Gap, episode, bin and timeline logic shared by the pattern rules. */
internal class TemporalProjection(private val config: PatternConfig, gaps: List<CoverageGap>) {
    private val gaps: List<CoverageGap> = gaps.sortedBy { it.id.value }

    /** Gaps that apply to [scope]: case-wide gaps and gaps for its confirmed actor. */
    fun gapsFor(scope: ActorScope): List<CoverageGap> =
        gaps.filter { gap ->
            gap.actorId == null || (scope is ActorScope.Confirmed && scope.actorId == gap.actorId)
        }

    /** Gaps of [scope] that intersect the closed interval [from, to]. */
    fun overlapping(scope: ActorScope, from: Instant, to: Instant): List<CoverageGap> =
        gapsFor(scope).filter { it.overlaps(from, to) }

    /** Counts episodes among [timed] groups sorted by earliest time. */
    fun episodes(timed: List<TimedGroup>, scope: ActorScope): Int {
        val scopeGaps = gapsFor(scope)
        var count = 0
        var previous: Interval? = null
        for (entry in timed) {
            val current = entry.interval
            val joins =
                previous?.let { before ->
                    val from = minOf(before.latest, current.earliest)
                    val to = maxOf(before.latest, current.earliest)
                    current.earliest <= before.latest.plus(config.episodeGap) &&
                        scopeGaps.none { it.overlaps(from, to) }
                } ?: false
            if (!joins) count++
            previous = current
        }
        return count
    }

    /** Start of the density bin containing [instant]; bins are aligned to the epoch in the configured zone. */
    fun binStart(instant: Instant): Instant {
        val offset = config.zone.rules.getOffset(instant).totalSeconds.toLong()
        val size = config.densityBin.seconds
        return Instant.ofEpochSecond(Math.floorDiv(instant.epochSecond + offset, size) * size - offset)
    }

    fun timeline(selected: List<Selected>, groups: List<ContactGroup>): List<TimelineEntry> {
        val representativeOf = HashMap<EventId, EventId>()
        for (group in groups) {
            for (member in group.members.drop(1)) representativeOf[member.event.eventId] = group.id
        }
        val ordered = selected.sortedWith(selectedOrder)
        return ordered.mapIndexed { index, entry ->
            val before = ordered.getOrNull(index - 1)?.interval
            val certain = before != null && entry.interval != null && before.definitelyBefore(entry.interval)
            TimelineEntry(
                eventId = entry.event.eventId,
                revision = entry.event.revision,
                earliest = entry.interval?.earliest,
                latest = entry.interval?.latest,
                isContact = entry.event.isContact(),
                orderCertainAfterPrevious = certain,
                canonicalEventId = representativeOf[entry.event.eventId],
            )
        }
    }
}

private fun CoverageGap.overlaps(from: Instant, to: Instant): Boolean {
    val startsInTime = start == null || start <= to
    val endsInTime = end == null || end >= from
    return startsInTime && endsInTime
}
