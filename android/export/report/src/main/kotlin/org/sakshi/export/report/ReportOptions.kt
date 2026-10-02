package org.sakshi.export.report

import java.time.ZoneId
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.EventId
import org.sakshi.core.temporal.EvidenceView

/** What the user chose to put into one report. */
public data class ReportSelection(
    val caseId: CaseId,
    /** Events the user chose to include. */
    val eventIds: Set<EventId>,
    /** Evidence ids whose original bytes go into the bundle. */
    val includeOriginalsFor: Set<String>,
    val view: EvidenceView = EvidenceView.CONFIRMED_ONLY,
    /** Zone for the displayed times and for calendar-day counts. */
    val zone: ZoneId,
)

/** Choices that change what the report prints. */
public data class ReportOptions(
    /** Lists suggestions the user has not accepted, under a separate heading. Rejected ones are never listed. */
    val includeUnreviewedSuggestions: Boolean = false,
    val reportVersion: Int = 1,
    /**
     * Number of selected items in a language the app does not support. The event model has no language field
     * yet, so the caller supplies the count and the report lists it as unknown.
     */
    val unsupportedLanguageItems: Int = 0,
) {
    init {
        require(reportVersion >= 1) { "reportVersion must be at least 1" }
        require(unsupportedLanguageItems >= 0) { "unsupportedLanguageItems must not be negative" }
    }
}

/** Why a report cannot be built (megaplan 20.2). */
public enum class RefusalReason {
    EMPTY_SELECTION,
    UNCONFIRMED_EVENT,
    UNKNOWN_EVENT,
    CASE_MISSING,

    /** An original was requested that is not evidence of this case. */
    UNKNOWN_EVIDENCE,
}
