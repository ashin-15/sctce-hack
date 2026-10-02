package org.sakshi.core.model

import kotlinx.serialization.Serializable

/** How a statement relates to the preserved evidence. */
@Serializable
public enum class EpistemicStatus {
    /** Directly present in preserved evidence. */
    OBSERVED,

    /** The user's own statement. */
    USER_REPORTED,

    /** Rule or model interpretation. */
    INFERRED,

    /** Supported by multiple events. */
    PATTERN,

    /** Not established. */
    UNKNOWN,
}
