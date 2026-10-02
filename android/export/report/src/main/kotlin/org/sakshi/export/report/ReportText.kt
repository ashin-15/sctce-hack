package org.sakshi.export.report

import org.sakshi.core.model.BoundaryMarker
import org.sakshi.core.model.CategoryBasis
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.CategoryReviewStatus
import org.sakshi.core.model.ConfidenceSemantics
import org.sakshi.core.model.Direction
import org.sakshi.core.model.EventKind
import org.sakshi.core.model.Representation
import org.sakshi.core.model.SourceKind
import org.sakshi.core.model.TimeBasis
import org.sakshi.core.model.TimePrecision
import org.sakshi.core.temporal.AssessmentStatus
import org.sakshi.core.temporal.GapReason
import org.sakshi.core.temporal.PatternType

/**
 * Every sentence, heading and word the report templates print, in one place so that [ForbiddenPhraseGuard] can
 * enumerate them. Placeholders are written `{0}`, `{1}` and filled with [fill]. Counts, dates and names come
 * from structured data at the call site, never from this file.
 */
public object ReportText {
    public const val TITLE_SUFFIX: String = "Report of selected records"
    public const val NOT_EVIDENCE: String =
        "This report is a derivative document made from the records you selected. It is not the evidence itself."

    // Part and section headings.
    public const val SECTION_SCOPE: String = "Scope of this report"
    public const val SECTION_TIMELINE: String = "Timeline"
    public const val SECTION_RECORDS: String = "Records"
    public const val SECTION_PATTERNS: String = "Patterns across the selected records"
    public const val SECTION_UNKNOWN: String = "What is not known"
    public const val SECTION_INTEGRITY: String = "Integrity appendix"
    public const val UNREVIEWED_HEADING: String = "Unreviewed suggestions (not confirmed by the user)"
    public const val ACCEPTED_TAGS_HEADING: String = "Tags you accepted"

    // Status words printed in capitals above each part.
    public const val WORD_OBSERVED: String = "OBSERVED"
    public const val WORD_USER: String = "YOUR STATEMENT"
    public const val WORD_INFERRED: String = "INFERRED"
    public const val WORD_PATTERN: String = "PATTERN"
    public const val WORD_UNKNOWN: String = "UNKNOWN"

    // Cover and scope.
    public const val GENERATED: String = "Generated {0} by the clock of this device. Device clocks are not trusted time."
    public const val VERSIONS: String = "Report version {0}. App version {1}. Rule versions: {2}. Model versions: {3}."
    public const val NONE_LISTED: String = "none"
    public const val SCOPE_SELECTED: String = "{0} of {1} records in this case are included in this report."
    public const val SCOPE_EXCLUDED: String = "{0} records of this case were left out by your selection."
    public const val SCOPE_ORIGINALS: String = "{0} original files are included in the bundle; {1} are not."
    public const val SCOPE_VIEW_CONFIRMED: String = "Only records you confirmed were used."
    public const val SCOPE_VIEW_CANDIDATE: String = "Records you have not yet confirmed may also have been considered."
    public const val SCOPE_ZONE: String = "Times in the timeline and records are shown in the time zone {0}."
    public const val SCOPE_UTC_NOTE: String = "Times inside pattern descriptions are written in UTC."
    public const val SCOPE_METHODS: String = "How the records reached the app: {0}."
    public const val SCOPE_SOURCES: String = "Kinds of source: {0}."
    public const val SCOPE_GAPS: String = "{0} known gaps in coverage are listed under what is not known."

    // Timeline, footer and lists.
    public const val TIMELINE_ROW: String = "{0} ({1}). Sender: {2} ({3}). {4}. Source: {5}."
    public const val PAGE_FOOTER: String = "Page {0} of {1} - Report version {2}"
    public const val BULLET: String = "- {0}"

    // Event records.
    public const val RECORD_HEADING: String = "Record {0}: {1}"
    public const val RECORD_ID: String = "Record id {0}, revision {1}."
    public const val RECORD_TIME: String = "Time: {0} ({1})."
    public const val RECORD_SENDER: String = "Sender: {0} ({1})."
    public const val QUOTE_UNAVAILABLE: String = "The text could not be read from the saved records."
    public const val ARTIFACT_LINE: String = "{0}. Artefact {1}. SHA-256 {2}. Location: {3}."
    public const val HASH_NOT_RECORDED: String = "not recorded"
    public const val SCORE_NOT_RECORDED: String = "not recorded"
    public const val STATEMENT_LINE: String = "Written {0}. Artefact {1}. Location: {2}."
    public const val TAG_LINE: String = "{0} ({1}). Basis: {2}. Producer version {3}. Confidence: {4}."
    public const val TAG_NOTE_SUGGESTION: String = "A suggestion, not a finding."
    public const val TAG_NOTE_USER: String = "A tag you made yourself."

    // Locators.
    public const val LOCATOR_WHOLE: String = "the whole saved item"
    public const val LOCATOR_TEXT: String = "characters {0} to {1} of the saved text"
    public const val LOCATOR_AUDIO: String = "{0} to {1} of the audio"
    public const val LOCATOR_REGION_PAGE: String = "region {0} on page {1}"
    public const val LOCATOR_REGION: String = "region {0}"

    // Patterns.
    public const val PATTERN_STATUS: String = "Status: {0}."
    public const val PATTERN_RULES: String = "Rule version {0}."
    public const val PATTERN_EVENTS: String = "Supporting records: {0}."
    public const val PATTERN_LIMITS_HEADING: String = "Limits of this description:"
    public const val PATTERNS_NONE: String = "No pattern was described for the selected records."
    public const val ACTOR_UNNAMED: String = "an unnamed sender"
    public const val ACTOR_UNRESOLVED: String = "the sender labelled {0} (not confirmed)"

    // Unknowns.
    public const val UNKNOWN_SENDER: String =
        "Sender identities are not authenticated. Names are labels from the export or from you."
    public const val UNKNOWN_OUTGOING: String =
        "{0} records come from a selection that may not include your own replies."
    public const val UNKNOWN_GAP_BOUNDED: String = "No records cover the period from {0} to {1} ({2})."
    public const val UNKNOWN_GAP_START_ONLY: String = "No records cover the period from {0} to an unknown end ({1})."
    public const val UNKNOWN_GAP_END_ONLY: String = "No records cover the period up to {0} from an unknown start ({1})."
    public const val UNKNOWN_GAP_UNBOUNDED: String = "No records cover an unknown period ({0})."
    public const val UNKNOWN_LANGUAGE: String = "{0} items are in a language this app does not support."
    public const val UNKNOWN_UNTIMED: String = "{0} records have no established time."
    public const val UNKNOWN_TEXT: String = "{0} records have text that is cut short, removed, missing or uncertain."
    public const val UNKNOWN_OMITTED_EVENTS: String = "{0} other records of this case are not part of this report."
    public const val UNKNOWN_OMITTED_EVIDENCE: String = "{0} saved files are not included in the bundle."
    public const val UNKNOWN_OMITTED_DERIVATIVES: String = "{0} extracted texts are not included in the bundle."
    public const val UNKNOWN_WITHHELD: String =
        "{0} suggestions you have not accepted were left out of this report."

    // Integrity appendix.
    public const val INTEGRITY_MANIFEST: String =
        "The manifest hash and Merkle root are recorded in manifest.json of this bundle. They cannot be printed " +
            "here because this file is listed in the manifest."
    public const val INTEGRITY_SIGNER: String = "Signer key id (SHA-256 of the public key): {0}."
    public const val INTEGRITY_SIGNER_UNKNOWN: String = "Signer key id: shown by the app and in signer.json."
    public const val INTEGRITY_AUDIT: String = "Audit chain head at the time of export: {0}."
    public const val INTEGRITY_VERIFY: String =
        "To check this bundle, follow verification/README.txt. The check compares the files with the manifest and " +
            "the signature with the key inside the bundle."
    public const val LIMITS_HEADING: String = "What a successful check does not show:"

    /**
     * The five limits lines of the bundle format, verbatim. They contain words such as "authenticity" and
     * "admissibility" as part of the mandated statement; the guard matches whole words only.
     */
    public val LIMITS: List<String> = listOf(
        "hash != authenticity",
        "signature != identity",
        "integrity != truth",
        "integrity != legal admissibility",
        "device clock != trusted time",
    )

    public val EVENT_KIND: Map<EventKind, String> = mapOf(
        EventKind.MESSAGE_OBSERVATION to "Message",
        EventKind.CONTACT_ATTEMPT_OBSERVATION to "Contact attempt",
        EventKind.USER_BOUNDARY to "Your boundary note",
        EventKind.USER_NOTE to "Your note",
        EventKind.REPORTED_EXTERNAL_EVENT to "Event you reported",
        EventKind.NOTIFICATION_LIFECYCLE to "Notification record",
    )

    public val DIRECTION: Map<Direction, String> = mapOf(
        Direction.INCOMING to "incoming, received by you",
        Direction.OUTGOING to "outgoing, sent by you",
        Direction.SYSTEM to "system",
        Direction.UNKNOWN to "direction not established",
    )

    public val SOURCE_KIND: Map<SourceKind, String> = mapOf(
        SourceKind.NOTIFICATION_EXCERPT to "notification excerpt",
        SourceKind.SELECTED_EXPORT to "export you selected",
        SourceKind.SELECTED_TEXT to "text you selected",
        SourceKind.SELECTED_IMAGE to "image you selected",
        SourceKind.SELECTED_AUDIO to "audio you selected",
        SourceKind.SELECTED_VIDEO to "video you selected",
        SourceKind.SELECTED_DOCUMENT to "document you selected",
        SourceKind.MANUAL_ENTRY to "entry you typed",
    )

    public val ACQUISITION: Map<String, String> = mapOf(
        "shared_text" to "text shared to the app",
        "shared_stream" to "file shared to the app",
        "selected_document" to "document chosen by you",
        "selected_visual_media" to "picture or video chosen by you",
        "pasted_text" to "text pasted by you",
        "manual_note" to "note typed by you",
    )
    public const val ACQUISITION_OTHER: String = "another way you chose"

    public val REPRESENTATION: Map<Representation, String> = mapOf(
        Representation.PRESERVED_IMPORT to "saved copy of what you imported",
        Representation.NOTIFICATION_EXCERPT to "notification excerpt",
        Representation.MANUAL_STATEMENT to "statement you wrote",
        Representation.OCR_DERIVATIVE to "text read from an image by software, not the image itself",
        Representation.TRANSCRIPT_DERIVATIVE to "text made from audio by software, not the audio itself",
    )

    public val TIME_BASIS: Map<TimeBasis, String> = mapOf(
        TimeBasis.SOURCE_CLAIM to "as written in the export",
        TimeBasis.COLLECTOR_WALL_CLOCK to "recorded by this device when it was captured",
        TimeBasis.USER_REPORTED to "as reported by you",
        TimeBasis.UNKNOWN to "basis of the time not established",
    )

    public val TIME_PRECISION: Map<TimePrecision, String> = mapOf(
        TimePrecision.MILLISECOND to "to the millisecond",
        TimePrecision.SECOND to "to the second",
        TimePrecision.MINUTE to "to the minute",
        TimePrecision.DAY to "to the day",
        TimePrecision.RANGE to "within a range",
        TimePrecision.UNKNOWN to "precision not established",
    )
    public const val TIME_NOT_ESTABLISHED: String = "time not established"
    public const val TIME_BETWEEN: String = "between {0} and {1}"

    public const val IDENTITY_CONFIRMED: String = "confirmed by you, not an authenticated identity"
    public const val IDENTITY_USER: String = "stated by you, not an authenticated identity"
    public const val IDENTITY_EXPORT: String = "name as it appears in the export, not confirmed"
    public const val SENDER_UNNAMED: String = "unnamed sender"

    public val CATEGORY: Map<CategoryLabel, String> = mapOf(
        CategoryLabel.ORDINARY to "Ordinary",
        CategoryLabel.VERBAL_ABUSE to "Verbal abuse",
        CategoryLabel.EXPLICIT_THREAT to "Explicit threat",
        CategoryLabel.IMPLIED_THREAT to "Implied threat",
        CategoryLabel.INTIMIDATION to "Intimidation",
        CategoryLabel.CONTROLLING_REQUEST to "Controlling request",
        CategoryLabel.SEXUAL_PRESSURE to "Sexual pressure",
        CategoryLabel.PRIVACY_EXPOSURE_INDICATOR to "Privacy exposure indicator",
        CategoryLabel.CONTACT_REQUEST to "Contact request",
        CategoryLabel.UNKNOWN to "Unknown",
    )

    public val CATEGORY_BASIS: Map<CategoryBasis, String> = mapOf(
        CategoryBasis.RULE_SUGGESTION to "suggested by a rule",
        CategoryBasis.CLASSIFIER_SUGGESTION to "suggested by a classifier",
        CategoryBasis.LLM_SUGGESTION to "suggested by a language model",
        CategoryBasis.USER_TAG to "tagged by you",
    )

    public val REVIEW_STATUS: Map<CategoryReviewStatus, String> = mapOf(
        CategoryReviewStatus.UNREVIEWED to "not reviewed by you",
        CategoryReviewStatus.ACCEPTED to "accepted by you",
        CategoryReviewStatus.REJECTED to "rejected by you",
        CategoryReviewStatus.UNCERTAIN to "marked uncertain by you",
    )

    public val CONFIDENCE: Map<ConfidenceSemantics, String> = mapOf(
        ConfidenceSemantics.CALIBRATED_PROBABILITY to "score {0}, calibrated probability",
        ConfidenceSemantics.UNCALIBRATED_BOUNDED_SCORE to "score {0}, not a probability",
        ConfidenceSemantics.NOT_APPLICABLE to "no score applies",
        ConfidenceSemantics.UNKNOWN to "meaning of the score not established",
    )

    public val BOUNDARY: Map<BoundaryMarker, String> = mapOf(
        BoundaryMarker.NONE to "none",
        BoundaryMarker.DO_NOT_CONTACT to "stop-contact request",
        BoundaryMarker.USER_DISENGAGEMENT to "disengagement note",
        BoundaryMarker.LIMITED_CONTACT to "limited-contact note",
        BoundaryMarker.USER_RESUMPTION to "resumption note",
        BoundaryMarker.UNKNOWN to "not established",
    )

    public val PATTERN_TYPE: Map<PatternType, String> = mapOf(
        PatternType.REPEATED_CONTACT to "Repeated contact",
        PatternType.RECURRENCE_AFTER_BOUNDARY to "Contact after a boundary",
        PatternType.WORDING_TRANSITION to "Change in wording",
        PatternType.DENSITY_CHANGE to "Change in density",
    )

    public val PATTERN_STATUS_WORDS: Map<AssessmentStatus, String> = mapOf(
        AssessmentStatus.SUPPORTED_DESCRIPTION to "description supported by the selected records",
        AssessmentStatus.CANDIDATE to "candidate, needs review",
        AssessmentStatus.INSUFFICIENT_CONTEXT to "not enough context",
        AssessmentStatus.NOT_OBSERVED to "not observed in the selected records",
    )

    public val GAP_REASON: Map<GapReason, String> = mapOf(
        GapReason.LISTENER_DISCONNECTED to "the notification listener was disconnected",
        GapReason.ACCESS_REVOKED to "access was withdrawn",
        GapReason.PROCESS_DEATH to "the app was stopped by the system",
        GapReason.QUEUE_OVERFLOW to "too many notifications arrived at once",
        GapReason.KEY_UNAVAILABLE to "the storage key was unavailable",
        GapReason.IMPORT_SELECTION to "the import covered only part of the period",
        GapReason.UNKNOWN to "reason not recorded",
    )

    /** Replaces `{0}`, `{1}` and so on with [args]. */
    public fun fill(template: String, vararg args: Any): String =
        args.foldIndexed(template) { index, text, value -> text.replace("{$index}", value.toString()) }
}
