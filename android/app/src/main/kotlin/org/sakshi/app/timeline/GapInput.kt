package org.sakshi.app.timeline

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import org.sakshi.core.temporal.GapReason

/** Why the two text fields of "Add a period with no records" were not accepted. */
enum class GapProblem { NEITHER_BOUND, START_FORMAT, END_FORMAT, END_BEFORE_START, NOT_SAVED }

sealed interface GapParse {
    /** A null bound is a bound the person left blank, meaning it is not known. */
    data class Valid(val start: Instant?, val end: Instant?) : GapParse

    data class Invalid(val problem: GapProblem) : GapParse
}

/** Reads "YYYY-MM-DD HH:MM" in the device zone. */
object GapInput {
    const val PATTERN_HINT: String = "YYYY-MM-DD HH:MM"

    /** A sample in the same shape, shown in the messages about a field that could not be read. */
    const val EXAMPLE: String = "2026-09-24 21:05"

    private val format: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT)

    fun parse(startText: String, endText: String, zone: ZoneId): GapParse {
        val startBlank = startText.isBlank()
        val endBlank = endText.isBlank()
        if (startBlank && endBlank) return GapParse.Invalid(GapProblem.NEITHER_BOUND)
        val start = if (startBlank) null else read(startText, zone) ?: return GapParse.Invalid(GapProblem.START_FORMAT)
        val end = if (endBlank) null else read(endText, zone) ?: return GapParse.Invalid(GapProblem.END_FORMAT)
        if (start != null && end != null && end.isBefore(start)) return GapParse.Invalid(GapProblem.END_BEFORE_START)
        return GapParse.Valid(start, end)
    }

    private fun read(text: String, zone: ZoneId): Instant? = try {
        LocalDateTime.parse(text.trim(), format).atZone(zone).toInstant()
    } catch (_: DateTimeParseException) {
        null
    }
}

/** The reasons offered for a period with no records, in the order they are shown. */
val GAP_REASONS: List<GapReason> = listOf(
    GapReason.IMPORT_SELECTION,
    GapReason.ACCESS_REVOKED,
    GapReason.LISTENER_DISCONNECTED,
    GapReason.PROCESS_DEATH,
    GapReason.QUEUE_OVERFLOW,
    GapReason.KEY_UNAVAILABLE,
    GapReason.UNKNOWN,
)
