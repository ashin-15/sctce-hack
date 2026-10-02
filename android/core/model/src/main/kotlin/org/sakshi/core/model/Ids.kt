package org.sakshi.core.model

import kotlinx.serialization.Serializable

private const val MAX_ID_LENGTH: Int = 128

private fun requireValidId(value: String, kind: String) {
    val length = value.codePointCount(0, value.length)
    require(length in 1..MAX_ID_LENGTH) { "$kind must be 1..$MAX_ID_LENGTH characters, was $length" }
}

/** Opaque local event identifier (schema `$defs.id`). */
@Serializable
@JvmInline
public value class EventId(public val value: String) {
    init {
        requireValidId(value, "EventId")
    }
}

/** Opaque local case identifier. */
@Serializable
@JvmInline
public value class CaseId(public val value: String) {
    init {
        requireValidId(value, "CaseId")
    }
}

/** Case-scoped actor alias; never an authenticated identity. */
@Serializable
@JvmInline
public value class ActorId(public val value: String) {
    init {
        requireValidId(value, "ActorId")
    }
}

/** Identifier of a preserved artifact or derivative. */
@Serializable
@JvmInline
public value class ArtifactId(public val value: String) {
    init {
        requireValidId(value, "ArtifactId")
    }
}

/** Identifier of an evidence reference within one event. */
@Serializable
@JvmInline
public value class ReferenceId(public val value: String) {
    init {
        requireValidId(value, "ReferenceId")
    }
}

/**
 * Generic identifier for profile, conversation, source-record, collector-session,
 * region, parser/producer/method/calibration version scopes.
 */
@Serializable
@JvmInline
public value class ScopeId(public val value: String) {
    init {
        requireValidId(value, "ScopeId")
    }
}
