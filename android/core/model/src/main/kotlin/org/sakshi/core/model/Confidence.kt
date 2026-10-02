package org.sakshi.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Score with explicit semantics; the schema ties `value` and `calibration_version` to `semantics`. */
@Serializable
public data class Confidence(
    val value: Double?,
    val semantics: ConfidenceSemantics,
    @SerialName("calibration_version") val calibrationVersion: ScopeId?,
) {
    init {
        require(value == null || value in 0.0..1.0) { "value must be within 0..1" }
    }
}
