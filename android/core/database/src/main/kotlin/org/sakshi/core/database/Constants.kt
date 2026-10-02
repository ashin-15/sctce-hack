package org.sakshi.core.database

/** Values of `case_file.status`. */
public object CaseStatus {
    public const val ACTIVE: String = "active"
    public const val ARCHIVED: String = "archived"
}

/** Values of `evidence_state.support_state`. */
public object SupportState {
    public const val SAVED: String = "saved"
    public const val ANALYSIS_PENDING: String = "analysis_pending"
    public const val ANALYZED: String = "analyzed"
    public const val PARTIAL: String = "partial"
    public const val UNSUPPORTED: String = "unsupported"
    public const val UNAVAILABLE: String = "unavailable"
    public const val FAILED: String = "failed"
}

/** Values of `capture_metadata.provider_transform`. */
public object ProviderTransform {
    public const val UNKNOWN: String = "unknown"
    public const val NONE: String = "none"
    public const val TRANSCODED: String = "transcoded"
}

/** Values of `derivative.kind`. */
public object DerivativeKind {
    public const val PARSED_TEXT: String = "parsed_text"
    public const val OCR: String = "ocr"
    public const val TRANSCRIPT: String = "transcript"
    public const val NORMALISED_VIEW: String = "normalised_view"
    public const val USER_EDIT: String = "user_edit"
}

/** Values of `finding.epistemic_status`. */
public object EpistemicStatus {
    public const val INFERRED: String = "inferred"
    public const val USER_REPORTED: String = "user_reported"
}

/** Values of `finding.review_status_at_import`. */
public object FindingReviewStatus {
    public const val UNREVIEWED: String = "unreviewed"
    public const val ACCEPTED: String = "accepted"
    public const val REJECTED: String = "rejected"
    public const val UNCERTAIN: String = "uncertain"
}

/** Values of `review_decision.target_type`. */
public object ReviewTargetType {
    public const val ASSOCIATION: String = "association"
    public const val FINDING: String = "finding"
    public const val PATTERN: String = "pattern"
    public const val EXPLANATION: String = "explanation"
}

/** Values of `review_decision.action`. */
public object ReviewAction {
    public const val ACCEPT: String = "accept"
    public const val REJECT: String = "reject"
    public const val EDIT: String = "edit"
    public const val ADD_CONTEXT: String = "add_context"
    public const val MARK_UNKNOWN: String = "mark_unknown"
}

/** Values of `pattern.evidence_view`. */
public object EvidenceView {
    public const val CONFIRMED_ONLY: String = "confirmed_only"
    public const val CANDIDATE_PREVIEW: String = "candidate_preview"
}

/** Values of `pattern.assessment_status`. */
public object AssessmentStatus {
    public const val CANDIDATE: String = "candidate"
    public const val SUPPORTED_DESCRIPTION: String = "supported_description"
    public const val INSUFFICIENT_CONTEXT: String = "insufficient_context"
    public const val NOT_OBSERVED: String = "not_observed"
    public const val STALE: String = "stale"
}

/** Values of `pattern_support.role`. */
public object SupportRole {
    public const val SUPPORTING: String = "supporting"
    public const val CONTEXT: String = "context"
}

/** Values of `processing_job.status`. */
public object JobStatus {
    public const val PENDING: String = "pending"
    public const val RUNNING: String = "running"
    public const val COMPLETE: String = "complete"
    public const val FAILED: String = "failed"
    public const val CANCELLED: String = "cancelled"
}
