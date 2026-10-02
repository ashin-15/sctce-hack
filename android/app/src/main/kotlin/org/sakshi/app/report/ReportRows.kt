package org.sakshi.app.report

import org.sakshi.app.R
import org.sakshi.app.timeline.BodyView
import org.sakshi.app.timeline.TimelineEventRow
import org.sakshi.app.timeline.TimelineRows
import org.sakshi.app.ui.UiText
import org.sakshi.app.ui.res
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.ConfirmationStatus
import org.sakshi.core.model.Event

/** Why an event cannot go into a report. Only a confirmed event can (megaplan 20.2). */
enum class BlockReason { NOT_CONFIRMED_YET, REJECTED, EXPIRED }

/**
 * One event offered for the report: the same row the timeline shows, the reason it cannot be chosen (or null), and the
 * saved files it comes from.
 */
data class ReportEventRow(
    val row: TimelineEventRow,
    val reason: BlockReason?,
    val evidenceIds: Set<String>,
) {
    val eventId: String get() = row.eventId
    val selectable: Boolean get() = reason == null
}

/** Pure mapping from stored events to the rows of the selection screen. */
object ReportRows {
    /** Longest part of a message shown on a selection row. */
    const val PREVIEW_CODE_POINTS: Int = 120

    fun build(
        events: List<Event>,
        bodies: Map<String, String>,
        actorLabels: Map<ActorId, String>,
        evidenceOf: (Event) -> Set<String>,
    ): List<ReportEventRow> = events.sortedWith(TimelineRows.displayOrder).map { event ->
        ReportEventRow(
            row = TimelineRows.row(event, bodies[event.eventId.value], actorLabels, orderNote = false),
            reason = blockReason(event),
            evidenceIds = evidenceOf(event),
        )
    }

    fun blockReason(event: Event): BlockReason? = when (event.userConfirmation.status) {
        ConfirmationStatus.CONFIRMED -> null
        ConfirmationStatus.PENDING -> BlockReason.NOT_CONFIRMED_YET
        ConfirmationStatus.REJECTED -> BlockReason.REJECTED
        ConfirmationStatus.EXPIRED -> BlockReason.EXPIRED
    }

    fun selectableIds(rows: List<ReportEventRow>): Set<String> = rows.filter { it.selectable }.map { it.eventId }.toSet()

    /** Events the person tagged themselves or agreed with. */
    fun taggedIds(rows: List<ReportEventRow>): Set<String> =
        rows.filter { it.selectable && it.row.tagged }.map { it.eventId }.toSet()

    fun blockText(reason: BlockReason): UiText = res(
        when (reason) {
            BlockReason.NOT_CONFIRMED_YET -> R.string.report_blocked_pending
            BlockReason.REJECTED -> R.string.report_blocked_rejected
            BlockReason.EXPIRED -> R.string.report_blocked_expired
        },
    )

    /** The start of the message, or the reason there is none. */
    fun previewText(body: BodyView): UiText = when (body) {
        is BodyView.Message -> UiText.Raw(TimelineRows.firstCodePoints(body.full, PREVIEW_CODE_POINTS))
        BodyView.MediaOmitted -> res(R.string.body_media_omitted)
        BodyView.NotAvailable -> res(R.string.body_not_available)
    }
}
