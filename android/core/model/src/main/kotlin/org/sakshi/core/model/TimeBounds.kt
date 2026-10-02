package org.sakshi.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Earliest/latest bounds with basis and precision; bounds are ambiguity, not a confidence interval. */
@Serializable
public data class TimeBounds(
    val earliest: Timestamp?,
    val latest: Timestamp?,
    val basis: TimeBasis,
    val precision: TimePrecision,
    @SerialName("source_timezone") val sourceTimezone: String?,
    @SerialName("collector_session_id") val collectorSessionId: ScopeId?,
    @SerialName("monotonic_ms") val monotonicMs: Long?,
) {
    init {
        require(sourceTimezone == null || sourceTimezone.length <= 64) { "source_timezone too long" }
        require(monotonicMs == null || monotonicMs >= 0) { "monotonic_ms must be non-negative" }
    }
}
