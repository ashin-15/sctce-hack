package org.sakshi.processing.text

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import org.sakshi.core.model.TimePrecision

private const val TWO_DIGIT_YEAR_BASE = 2000
private const val NOON_HOUR = 12
private const val HOURS_MAX = 23
private const val MINUTES_MAX = 59

/**
 * Earliest and latest instants consistent with a stated export time. Both bounds are inclusive; the
 * latest is the last millisecond of the stated minute (or second). [zone] is the zone the caller supplied.
 */
public data class TimeBoundsCandidate(
    val earliest: Instant,
    val latest: Instant,
    val precision: TimePrecision,
    val zone: ZoneId,
)

/** Turns raw export date and time strings into time bounds once the caller has confirmed order and zone. */
public object ExportTime {
    /**
     * Resolves [record] using the confirmed [order] and [zone]; two-digit years mean 2000-2099.
     * Returns null for an invalid date or time, or a local time that does not exist in [zone] (a DST gap).
     * For a local time that occurs twice (a DST overlap) the bounds span both occurrences.
     */
    public fun resolve(record: ParsedRecord, order: DateOrder, zone: ZoneId): TimeBoundsCandidate? {
        require(order != DateOrder.AMBIGUOUS) { "Date order must be confirmed before resolving a time" }
        val date = date(record.dateRaw, order) ?: return null
        val clock = clock(record.timeRaw) ?: return null
        val start = LocalDateTime.of(date, clock.time)
        val unit = if (clock.hasSeconds) 1_000L else 60_000L
        val offsets = zone.rules.getValidOffsets(start)
        if (offsets.isEmpty()) return null
        val earliest = start.toInstant(offsets.first())
        val latest = start.toInstant(offsets.last()).plusMillis(unit - 1)
        val precision = if (clock.hasSeconds) TimePrecision.SECOND else TimePrecision.MINUTE
        return TimeBoundsCandidate(earliest, latest, precision, zone)
    }

    private class Clock(val time: LocalTime, val hasSeconds: Boolean)

    private fun date(raw: String, order: DateOrder): LocalDate? {
        val parts = raw.split('/', '.', '-')
        if (parts.size != 3 || parts.any { !isDigits(it) }) return null
        val (a, b, year) = parts.map { it.toInt() }
        val (day, month) = if (order == DateOrder.DAY_MONTH) a to b else b to a
        val fullYear = if (parts[2].length <= 2) TWO_DIGIT_YEAR_BASE + year else year
        return try {
            LocalDate.of(fullYear, month, day)
        } catch (_: DateTimeException) {
            null
        }
    }

    private fun clock(raw: String): Clock? {
        val upper = raw.trim().uppercase(Locale.ROOT)
        val meridiem = upper.takeIf { it.endsWith("AM") || it.endsWith("PM") }?.takeLast(2)
        val core = (if (meridiem == null) upper else upper.dropLast(2)).trim { it == ' ' || it == ' ' || it == ' ' }
        val parts = core.split(':')
        if (parts.size !in 2..3 || parts.any { !isDigits(it) }) return null
        val numbers = parts.map { it.toInt() }
        val seconds = numbers.getOrElse(2) { 0 }
        var hour = numbers[0]
        if (meridiem != null) {
            if (hour !in 1..NOON_HOUR) return null
            hour = hour % NOON_HOUR + if (meridiem == "PM") NOON_HOUR else 0
        }
        if (hour > HOURS_MAX || numbers[1] > MINUTES_MAX || seconds > MINUTES_MAX) return null
        return Clock(LocalTime.of(hour, numbers[1], seconds), parts.size == 3)
    }

    private fun isDigits(s: String): Boolean = s.isNotEmpty() && s.length <= 4 && s.all { it in '0'..'9' }
}
