package org.sakshi.processing.analysis

import org.sakshi.core.model.Event
import org.sakshi.core.model.EvidenceReference

/** One message body, already sliced from its immutable text derivative. */
public data class ThreatLanguageInput(
    public val event: Event,
    public val bodyReference: EvidenceReference,
    public val bodyText: String,
)

public enum class ThreatLanguageResultStatus {
    POSSIBLE_THREAT_LANGUAGE,
    NO_SIGNAL_UNCALIBRATED,
    NEEDS_REVIEW,
    UNSUPPORTED_LANGUAGE,
    MODEL_UNAVAILABLE,
    INFERENCE_FAILED,
    CANCELLED,
    TRUNCATED,
    NOT_RUN,
}

/** Raw quote is validated against [ThreatLanguageInput.bodyText] before any event or finding is written. */
public data class ThreatLanguageResult(
    public val status: ThreatLanguageResultStatus,
    public val quote: String? = null,
    public val reasonCode: String? = null,
    public val modelPreset: String? = null,
    public val weightSha256: String? = null,
    public val runtimeCommit: String? = null,
    public val runtimeVersion: String? = null,
)

/** Batch boundary lets an implementation hold a single lazy model lease for all eligible event bodies. */
public fun interface ThreatLanguageClassifier {
    public suspend fun classify(requestId: String, inputs: List<ThreatLanguageInput>): List<ThreatLanguageResult>
}
