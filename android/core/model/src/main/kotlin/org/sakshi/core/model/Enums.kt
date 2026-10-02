package org.sakshi.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public enum class EventKind {
    @SerialName("message_observation")
    MESSAGE_OBSERVATION,
    @SerialName("contact_attempt_observation")
    CONTACT_ATTEMPT_OBSERVATION,
    @SerialName("user_boundary")
    USER_BOUNDARY,
    @SerialName("user_note")
    USER_NOTE,
    @SerialName("reported_external_event")
    REPORTED_EXTERNAL_EVENT,
    @SerialName("notification_lifecycle")
    NOTIFICATION_LIFECYCLE,
}

@Serializable
public enum class IdentityBasis {
    @SerialName("unknown")
    UNKNOWN,
    @SerialName("app_scoped_hint")
    APP_SCOPED_HINT,
    @SerialName("user_asserted")
    USER_ASSERTED,
}

@Serializable
public enum class AssociationReview {
    @SerialName("unreviewed")
    UNREVIEWED,
    @SerialName("confirmed")
    CONFIRMED,
    @SerialName("rejected")
    REJECTED,
    @SerialName("unknown")
    UNKNOWN,
}

@Serializable
public enum class SourceKind {
    @SerialName("notification_excerpt")
    NOTIFICATION_EXCERPT,
    @SerialName("selected_export")
    SELECTED_EXPORT,
    @SerialName("selected_text")
    SELECTED_TEXT,
    @SerialName("selected_image")
    SELECTED_IMAGE,
    @SerialName("selected_audio")
    SELECTED_AUDIO,
    @SerialName("selected_video")
    SELECTED_VIDEO,
    @SerialName("selected_document")
    SELECTED_DOCUMENT,
    @SerialName("manual_entry")
    MANUAL_ENTRY,
}

@Serializable
public enum class Direction {
    @SerialName("incoming")
    INCOMING,
    @SerialName("outgoing")
    OUTGOING,
    @SerialName("system")
    SYSTEM,
    @SerialName("unknown")
    UNKNOWN,
}

@Serializable
public enum class CategoryLabel {
    @SerialName("ordinary")
    ORDINARY,
    @SerialName("verbal_abuse")
    VERBAL_ABUSE,
    @SerialName("explicit_threat")
    EXPLICIT_THREAT,
    @SerialName("implied_threat")
    IMPLIED_THREAT,
    @SerialName("intimidation")
    INTIMIDATION,
    @SerialName("controlling_request")
    CONTROLLING_REQUEST,
    @SerialName("sexual_pressure")
    SEXUAL_PRESSURE,
    @SerialName("privacy_exposure_indicator")
    PRIVACY_EXPOSURE_INDICATOR,
    @SerialName("contact_request")
    CONTACT_REQUEST,
    @SerialName("unknown")
    UNKNOWN,
}

@Serializable
public enum class CategoryBasis {
    @SerialName("rule_suggestion")
    RULE_SUGGESTION,
    @SerialName("classifier_suggestion")
    CLASSIFIER_SUGGESTION,
    @SerialName("llm_suggestion")
    LLM_SUGGESTION,
    @SerialName("user_tag")
    USER_TAG,
}

@Serializable
public enum class CategoryReviewStatus {
    @SerialName("unreviewed")
    UNREVIEWED,
    @SerialName("accepted")
    ACCEPTED,
    @SerialName("rejected")
    REJECTED,
    @SerialName("uncertain")
    UNCERTAIN,
}

@Serializable
public enum class ConfidenceSemantics {
    @SerialName("calibrated_probability")
    CALIBRATED_PROBABILITY,
    @SerialName("uncalibrated_bounded_score")
    UNCALIBRATED_BOUNDED_SCORE,
    @SerialName("not_applicable")
    NOT_APPLICABLE,
    @SerialName("unknown")
    UNKNOWN,
}

@Serializable
public enum class ReviewPriority {
    @SerialName("ordinary")
    ORDINARY,
    @SerialName("review")
    REVIEW,
    @SerialName("urgent_review")
    URGENT_REVIEW,
    @SerialName("unknown")
    UNKNOWN,
}

@Serializable
public enum class SeverityBasis {
    @SerialName("policy_suggestion")
    POLICY_SUGGESTION,
    @SerialName("user_set")
    USER_SET,
    @SerialName("unknown")
    UNKNOWN,
}

@Serializable
public enum class Representation {
    @SerialName("preserved_import")
    PRESERVED_IMPORT,
    @SerialName("notification_excerpt")
    NOTIFICATION_EXCERPT,
    @SerialName("manual_statement")
    MANUAL_STATEMENT,
    @SerialName("ocr_derivative")
    OCR_DERIVATIVE,
    @SerialName("transcript_derivative")
    TRANSCRIPT_DERIVATIVE,
}

@Serializable
public enum class ConfirmationStatus {
    @SerialName("pending")
    PENDING,
    @SerialName("confirmed")
    CONFIRMED,
    @SerialName("rejected")
    REJECTED,
    @SerialName("expired")
    EXPIRED,
}

@Serializable
public enum class ConfirmationScope {
    @SerialName("not_reviewed")
    NOT_REVIEWED,
    @SerialName("preservation_only")
    PRESERVATION_ONLY,
    @SerialName("preservation_and_selected_annotations")
    PRESERVATION_AND_SELECTED_ANNOTATIONS,
}

@Serializable
public enum class DedupStatus {
    @SerialName("distinct_observation")
    DISTINCT_OBSERVATION,
    @SerialName("same_representation")
    SAME_REPRESENTATION,
    @SerialName("possible_duplicate")
    POSSIBLE_DUPLICATE,
    @SerialName("lifecycle_only")
    LIFECYCLE_ONLY,
}

@Serializable
public enum class CoverageContext {
    @SerialName("complete_for_selected_range")
    COMPLETE_FOR_SELECTED_RANGE,
    @SerialName("selection_partial")
    SELECTION_PARTIAL,
    @SerialName("notification_partial")
    NOTIFICATION_PARTIAL,
    @SerialName("unknown")
    UNKNOWN,
}

@Serializable
public enum class TextStatus {
    @SerialName("available")
    AVAILABLE,
    @SerialName("truncated")
    TRUNCATED,
    @SerialName("redacted")
    REDACTED,
    @SerialName("absent")
    ABSENT,
    @SerialName("extraction_uncertain")
    EXTRACTION_UNCERTAIN,
}

@Serializable
public enum class OutgoingCoverage {
    @SerialName("included_for_selected_range")
    INCLUDED_FOR_SELECTED_RANGE,
    @SerialName("partial")
    PARTIAL,
    @SerialName("unknown")
    UNKNOWN,
}

@Serializable
public enum class BoundaryMarker {
    @SerialName("none")
    NONE,
    @SerialName("do_not_contact")
    DO_NOT_CONTACT,
    @SerialName("user_disengagement")
    USER_DISENGAGEMENT,
    @SerialName("limited_contact")
    LIMITED_CONTACT,
    @SerialName("user_resumption")
    USER_RESUMPTION,
    @SerialName("unknown")
    UNKNOWN,
}

@Serializable
public enum class BoundaryReviewStatus {
    @SerialName("not_applicable")
    NOT_APPLICABLE,
    @SerialName("unreviewed")
    UNREVIEWED,
    @SerialName("confirmed")
    CONFIRMED,
    @SerialName("rejected")
    REJECTED,
    @SerialName("unknown")
    UNKNOWN,
}

@Serializable
public enum class CommunicationStatus {
    @SerialName("supported_by_selected_evidence")
    SUPPORTED_BY_SELECTED_EVIDENCE,
    @SerialName("user_reported")
    USER_REPORTED,
    @SerialName("not_communicated")
    NOT_COMMUNICATED,
    @SerialName("unknown")
    UNKNOWN,
    @SerialName("not_applicable")
    NOT_APPLICABLE,
}

@Serializable
public enum class UnwantedContact {
    @SerialName("user_marked_unwanted")
    USER_MARKED_UNWANTED,
    @SerialName("user_marked_wanted")
    USER_MARKED_WANTED,
    @SerialName("unknown")
    UNKNOWN,
    @SerialName("not_applicable")
    NOT_APPLICABLE,
}

@Serializable
public enum class RelationshipType {
    @SerialName("reply_to")
    REPLY_TO,
    @SerialName("quotes")
    QUOTES,
    @SerialName("possible_same_occurrence")
    POSSIBLE_SAME_OCCURRENCE,
    @SerialName("after_boundary")
    AFTER_BOUNDARY,
    @SerialName("same_topic_candidate")
    SAME_TOPIC_CANDIDATE,
    @SerialName("recurrence_candidate")
    RECURRENCE_CANDIDATE,
    @SerialName("category_transition_candidate")
    CATEGORY_TRANSITION_CANDIDATE,
    @SerialName("user_linked")
    USER_LINKED,
}

@Serializable
public enum class RelationshipBasis {
    @SerialName("source_explicit")
    SOURCE_EXPLICIT,
    @SerialName("rule")
    RULE,
    @SerialName("model")
    MODEL,
    @SerialName("user_asserted")
    USER_ASSERTED,
}

@Serializable
public enum class RelationshipReviewStatus {
    @SerialName("unreviewed")
    UNREVIEWED,
    @SerialName("confirmed")
    CONFIRMED,
    @SerialName("rejected")
    REJECTED,
    @SerialName("unknown")
    UNKNOWN,
}

@Serializable
public enum class RetentionMode {
    @SerialName("session_only")
    SESSION_ONLY,
    @SerialName("encrypted_candidate")
    ENCRYPTED_CANDIDATE,
    @SerialName("confirmed_vault")
    CONFIRMED_VAULT,
}

@Serializable
public enum class TimeBasis {
    @SerialName("source_claim")
    SOURCE_CLAIM,
    @SerialName("collector_wall_clock")
    COLLECTOR_WALL_CLOCK,
    @SerialName("user_reported")
    USER_REPORTED,
    @SerialName("unknown")
    UNKNOWN,
}

@Serializable
public enum class TimePrecision {
    @SerialName("millisecond")
    MILLISECOND,
    @SerialName("second")
    SECOND,
    @SerialName("minute")
    MINUTE,
    @SerialName("day")
    DAY,
    @SerialName("range")
    RANGE,
    @SerialName("unknown")
    UNKNOWN,
}
