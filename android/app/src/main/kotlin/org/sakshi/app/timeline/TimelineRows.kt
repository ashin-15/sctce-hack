package org.sakshi.app.timeline

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.AssociationReview
import org.sakshi.core.model.CategoryAssessment
import org.sakshi.core.model.CategoryBasis
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.CategoryReviewStatus
import org.sakshi.core.model.Direction
import org.sakshi.core.model.Event
import org.sakshi.core.model.TextStatus
import org.sakshi.core.vault.StoredCoverageGap

enum class TimelineFilter { ALL, NEEDS_REVIEW, TAGGED }

/** How a tag is shown. Only [SUGGESTION] is an unreviewed suggestion; the others record what the person did. */
enum class TagKind { SUGGESTION, ACCEPTED, OWN, DISAGREED, NOT_SURE }

data class TagLine(val kind: TagKind, val label: CategoryLabel)

sealed interface BodyView {
    /** [preview] is at most [PREVIEW_CODE_POINTS] characters of [full]. */
    data class Message(val preview: String, val full: String) : BodyView {
        val truncated: Boolean get() = preview.length < full.length
    }

    /** The export says media was omitted, so there is no text. */
    data object MediaOmitted : BodyView

    /** The text could not be read from the saved derivative. */
    data object NotAvailable : BodyView

    companion object {
        const val PREVIEW_CODE_POINTS: Int = 300
    }
}

enum class SenderStatus { NOT_CONFIRMED, CONFIRMED }

/** [label] is the name as the source gave it; [personLabel] is the name the person gave when they confirmed it. */
data class SenderView(val label: String?, val status: SenderStatus, val personLabel: String?)

data class TimelineEventRow(
    val eventId: String,
    val time: TimeReading,
    /** True on the first event of a run of events whose times overlap, so their order is not known. */
    val orderNote: Boolean,
    val sender: SenderView,
    val direction: Direction,
    val body: BodyView,
    val tags: List<TagLine>,
    val needsReview: Boolean,
    val tagged: Boolean,
)

sealed interface TimelineItem {
    val key: String

    /** [date] is null for the events whose time is not known. */
    data class DayHeader(val date: LocalDate?) : TimelineItem {
        override val key: String get() = "day-${date ?: "unknown"}"
    }

    data class EventItem(val row: TimelineEventRow) : TimelineItem {
        override val key: String get() = "event-${row.eventId}"
    }

    data class GapItem(val id: String, val start: Instant?, val end: Instant?) : TimelineItem {
        override val key: String get() = "gap-$id"
    }
}

/** [total] and [needsReview] count all events of the case, whatever the filter. */
data class TimelineView(val items: List<TimelineItem>, val total: Int, val needsReview: Int)

/** Pure mapping from stored events to the rows of the timeline. */
object TimelineRows {
    fun build(
        events: List<Event>,
        gaps: List<StoredCoverageGap>,
        bodies: Map<String, String>,
        actorLabels: Map<ActorId, String>,
        zone: ZoneId,
        filter: TimelineFilter,
    ): TimelineView {
        val sorted = events.sortedWith(displayOrder)
        val kept = sorted.filter { passes(it, filter) }
        val noteStarts = orderNoteStarts(kept)
        val items = ArrayList<TimelineItem>(kept.size + gaps.size + DAY_HEADER_ESTIMATE)
        val pendingGaps = if (filter == TimelineFilter.ALL) gaps.sortedWith(gapOrder).toMutableList() else mutableListOf()
        var lastDay: LocalDate? = null
        var undatedStarted = false

        fun flushGaps(before: Instant?) {
            while (pendingGaps.isNotEmpty()) {
                val key = gapKey(pendingGaps.first())
                if (before != null && (key == null || !key.isBefore(before))) break
                items += gapItem(pendingGaps.removeAt(0))
            }
        }

        for (event in kept) {
            val at = event.timestamp.earliest?.instant
            if (at != null) {
                flushGaps(at)
                val day = at.atZone(zone).toLocalDate()
                if (day != lastDay) items += TimelineItem.DayHeader(day).also { lastDay = day }
            } else if (!undatedStarted) {
                flushGaps(null)
                items += TimelineItem.DayHeader(null)
                undatedStarted = true
            }
            items += TimelineItem.EventItem(row(event, bodies[event.eventId.value], actorLabels, event.eventId.value in noteStarts))
        }
        flushGaps(null)
        return TimelineView(items, events.size, events.count { needsReview(it) })
    }

    fun row(event: Event, body: String?, actorLabels: Map<ActorId, String>, orderNote: Boolean): TimelineEventRow =
        TimelineEventRow(
            eventId = event.eventId.value,
            time = readTime(event.timestamp),
            orderNote = orderNote,
            sender = senderOf(event, actorLabels),
            direction = event.direction,
            body = bodyOf(event, body),
            tags = event.categories.map { tagLine(it) },
            needsReview = needsReview(event),
            tagged = isTagged(event),
        )

    fun needsReview(event: Event): Boolean =
        event.categories.any { it.basis != CategoryBasis.USER_TAG && it.reviewStatus == CategoryReviewStatus.UNREVIEWED }

    fun isTagged(event: Event): Boolean =
        event.categories.any { it.basis == CategoryBasis.USER_TAG || it.reviewStatus == CategoryReviewStatus.ACCEPTED }

    private fun passes(event: Event, filter: TimelineFilter): Boolean = when (filter) {
        TimelineFilter.ALL -> true
        TimelineFilter.NEEDS_REVIEW -> needsReview(event)
        TimelineFilter.TAGGED -> isTagged(event)
    }

    private fun tagLine(category: CategoryAssessment): TagLine {
        val kind = when {
            category.basis == CategoryBasis.USER_TAG -> TagKind.OWN
            else -> when (category.reviewStatus) {
                CategoryReviewStatus.UNREVIEWED -> TagKind.SUGGESTION
                CategoryReviewStatus.ACCEPTED -> TagKind.ACCEPTED
                CategoryReviewStatus.REJECTED -> TagKind.DISAGREED
                CategoryReviewStatus.UNCERTAIN -> TagKind.NOT_SURE
            }
        }
        return TagLine(kind, category.label)
    }

    fun senderOf(event: Event, actorLabels: Map<ActorId, String>): SenderView {
        val sender = event.sender
        val actor = sender.actorId
        val confirmed = actor != null && sender.associationReview == AssociationReview.CONFIRMED
        return SenderView(
            label = sender.displayLabel,
            status = if (confirmed) SenderStatus.CONFIRMED else SenderStatus.NOT_CONFIRMED,
            personLabel = if (confirmed) actorLabels[actor] else null,
        )
    }

    private fun bodyOf(event: Event, body: String?): BodyView = when {
        event.coverage.textStatus == TextStatus.ABSENT -> BodyView.MediaOmitted
        body.isNullOrEmpty() -> BodyView.NotAvailable
        else -> BodyView.Message(firstCodePoints(body, BodyView.PREVIEW_CODE_POINTS), body)
    }

    /** The first [count] characters (code points) of [text], never cutting a surrogate pair. */
    fun firstCodePoints(text: String, count: Int): String =
        if (text.codePointCount(0, text.length) <= count) text else text.substring(0, text.offsetByCodePoints(0, count))

    /** Event ids that start a run of two or more events with overlapping times. */
    private fun orderNoteStarts(events: List<Event>): Set<String> {
        val starts = HashSet<String>()
        var inRun = false
        for (index in 0 until events.size - 1) {
            val overlaps = overlap(events[index], events[index + 1])
            if (overlaps && !inRun) starts += events[index].eventId.value
            inRun = overlaps
        }
        return starts
    }

    private fun overlap(a: Event, b: Event): Boolean {
        val aStart = a.timestamp.earliest?.instant
        val aEnd = a.timestamp.latest?.instant
        val bStart = b.timestamp.earliest?.instant
        val bEnd = b.timestamp.latest?.instant
        if (aStart == null || aEnd == null || bStart == null || bEnd == null) return false
        return !aStart.isAfter(bEnd) && !bStart.isAfter(aEnd)
    }

    private fun gapItem(gap: StoredCoverageGap) =
        TimelineItem.GapItem(gap.id.value, gap.startAt?.instant, gap.endAt?.instant)

    private fun gapKey(gap: StoredCoverageGap): Instant? = (gap.startAt ?: gap.endAt)?.instant

    private val gapOrder: Comparator<StoredCoverageGap> = Comparator { a, b ->
        val ka = gapKey(a)
        val kb = gapKey(b)
        when {
            ka == null && kb == null -> a.id.value.compareTo(b.id.value)
            ka == null -> 1
            kb == null -> -1
            else -> ka.compareTo(kb).takeIf { it != 0 } ?: a.id.value.compareTo(b.id.value)
        }
    }

    /** Earliest known time first, then latest, then id; events without a time come last. */
    val displayOrder: Comparator<Event> = Comparator { a, b ->
        val ea = a.timestamp.earliest?.instant
        val eb = b.timestamp.earliest?.instant
        when {
            ea == null && eb == null -> a.eventId.value.compareTo(b.eventId.value)
            ea == null -> 1
            eb == null -> -1
            else -> ea.compareTo(eb).takeIf { it != 0 }
                ?: compareLatest(a, b).takeIf { it != 0 }
                ?: a.eventId.value.compareTo(b.eventId.value)
        }
    }

    private fun compareLatest(a: Event, b: Event): Int {
        val la = a.timestamp.latest?.instant
        val lb = b.timestamp.latest?.instant
        return when {
            la == null && lb == null -> 0
            la == null -> 1
            lb == null -> -1
            else -> la.compareTo(lb)
        }
    }

    private const val DAY_HEADER_ESTIMATE: Int = 16
}
