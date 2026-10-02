package org.sakshi.core.model

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlinx.serialization.Serializable

private val RFC3339_SHAPE: Regex =
    Regex("""\d{4}-\d{2}-\d{2}[Tt]\d{2}:\d{2}:\d{2}(\.\d+)?([Zz]|[+-]\d{2}:\d{2})""")

/** RFC 3339 date-time with offset. The original text is preserved exactly. */
@Serializable
@JvmInline
public value class Timestamp(public val iso: String) {
    init {
        require(RFC3339_SHAPE.matches(iso)) { "Not an RFC 3339 date-time: $iso" }
        try {
            OffsetDateTime.parse(iso)
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("Not an RFC 3339 date-time: $iso", e)
        }
    }

    public val instant: Instant
        get() = OffsetDateTime.parse(iso).toInstant()
}
