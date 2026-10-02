package org.sakshi.export.report

import java.time.ZoneId
import org.sakshi.core.model.Event
import org.sakshi.core.model.OutgoingCoverage
import org.sakshi.core.model.TextStatus
import org.sakshi.core.temporal.GapReason
import org.sakshi.core.vault.StoredCoverageGap
import org.sakshi.export.bundle.OmittedCounts

/** Builds the explicit list of what the selected records do not establish. Counts come from structured data. */
internal object Unknowns {
    fun build(
        events: List<Event>,
        gaps: List<StoredCoverageGap>,
        zone: ZoneId,
        options: ReportOptions,
        omitted: OmittedCounts,
        withheldSuggestions: Int,
    ): List<String> {
        val lines = mutableListOf(ReportText.UNKNOWN_SENDER)
        count(events) { it.coverage.outgoingCoverage != OutgoingCoverage.INCLUDED_FOR_SELECTED_RANGE }
            ?.let { lines += ReportText.fill(ReportText.UNKNOWN_OUTGOING, it) }
        lines += gaps.map { gapLine(it, zone) }
        if (options.unsupportedLanguageItems > 0) {
            lines += ReportText.fill(ReportText.UNKNOWN_LANGUAGE, options.unsupportedLanguageItems)
        }
        count(events) { it.timestamp.earliest == null && it.timestamp.latest == null }
            ?.let { lines += ReportText.fill(ReportText.UNKNOWN_UNTIMED, it) }
        count(events) { it.coverage.textStatus != TextStatus.AVAILABLE }
            ?.let { lines += ReportText.fill(ReportText.UNKNOWN_TEXT, it) }
        if (omitted.eventCount > 0) lines += ReportText.fill(ReportText.UNKNOWN_OMITTED_EVENTS, omitted.eventCount)
        if (omitted.evidenceCount > 0) lines += ReportText.fill(ReportText.UNKNOWN_OMITTED_EVIDENCE, omitted.evidenceCount)
        if (omitted.derivativeCount > 0) {
            lines += ReportText.fill(ReportText.UNKNOWN_OMITTED_DERIVATIVES, omitted.derivativeCount)
        }
        if (withheldSuggestions > 0) lines += ReportText.fill(ReportText.UNKNOWN_WITHHELD, withheldSuggestions)
        return lines
    }

    private fun count(events: List<Event>, test: (Event) -> Boolean): Int? = events.count(test).takeIf { it > 0 }

    private fun gapLine(gap: StoredCoverageGap, zone: ZoneId): String {
        val reason = ReportText.GAP_REASON.getValue(reasonOf(gap.reason))
        val start = gap.startAt?.let { ReportFormat.minute(it, zone) }
        val end = gap.endAt?.let { ReportFormat.minute(it, zone) }
        return when {
            start != null && end != null -> ReportText.fill(ReportText.UNKNOWN_GAP_BOUNDED, start, end, reason)
            start != null -> ReportText.fill(ReportText.UNKNOWN_GAP_START_ONLY, start, reason)
            end != null -> ReportText.fill(ReportText.UNKNOWN_GAP_END_ONLY, end, reason)
            else -> ReportText.fill(ReportText.UNKNOWN_GAP_UNBOUNDED, reason)
        }
    }

    /** Gap reasons are stored as text; only a known reason name is mapped, anything else is "not recorded". */
    fun reasonOf(stored: String): GapReason =
        GapReason.entries.firstOrNull { it.name.equals(stored.trim(), ignoreCase = true) }
            ?: GapReason.UNKNOWN
}
