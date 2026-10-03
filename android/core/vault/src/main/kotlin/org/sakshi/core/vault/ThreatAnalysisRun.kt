package org.sakshi.core.vault

/** Stable outcomes recorded for one explicit, user-started threat-language attempt. */
public enum class ThreatAnalysisRunStatus {
    NOT_RUN,
    POSSIBLE_THREAT_LANGUAGE,
    NO_SIGNAL_UNCALIBRATED,
    NEEDS_REVIEW,
    UNSUPPORTED_LANGUAGE,
    MODEL_UNAVAILABLE,
    INFERENCE_FAILED,
    CANCELLED,
    TRUNCATED,
}

/** Provenance and status only; evidence text and prompt material are intentionally excluded. */
public data class ThreatAnalysisRunDraft(
    val id: String,
    val caseId: String,
    val eventId: String,
    val eventRevision: Int,
    val derivativeId: String?,
    val requestId: String,
    val status: ThreatAnalysisRunStatus,
    val reasonCode: String?,
    val modelPreset: String?,
    val weightSha256: String?,
    val runtimeCommit: String?,
    val runtimeVersion: String?,
    val taskVersion: String,
    val createdAt: String,
    val findingId: String?,
)
