package org.sakshi.app.timeline

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.sakshi.app.R
import org.sakshi.app.ui.UiText
import org.sakshi.app.ui.res
import org.sakshi.core.model.TimeBasis
import org.sakshi.core.model.TimeBounds
import org.sakshi.core.model.TimePrecision

/** What is known about when something happened, before it is turned into words for a locale and a zone. */
sealed interface TimeLabel {
    /** A time of day, to the minute or better. */
    data class At(val instant: Instant) : TimeLabel

    /** Only the day is known. */
    data class DayOnly(val instant: Instant) : TimeLabel

    data class Between(val from: Instant, val to: Instant) : TimeLabel

    data object Unknown : TimeLabel
}

/** The reading of a time-bounds value: its label and the words that say where the time came from. */
data class TimeReading(val label: TimeLabel, val basis: TimeBasis)

fun readTime(bounds: TimeBounds): TimeReading {
    val earliest = bounds.earliest?.instant
    val latest = bounds.latest?.instant
    val label = when {
        earliest == null || latest == null || bounds.basis == TimeBasis.UNKNOWN || bounds.precision == TimePrecision.UNKNOWN -> TimeLabel.Unknown
        bounds.precision == TimePrecision.DAY -> TimeLabel.DayOnly(earliest)
        bounds.precision == TimePrecision.RANGE -> TimeLabel.Between(earliest, latest)
        else -> TimeLabel.At(earliest)
    }
    return TimeReading(label, bounds.basis)
}

fun formatClock(instant: Instant, zone: ZoneId, locale: Locale): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone).format(instant)

fun formatDay(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(date)

fun formatDayTime(instant: Instant, zone: ZoneId, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale).withZone(zone).format(instant)

/** The time itself in words, or "time not known". */
fun timeText(label: TimeLabel, zone: ZoneId, locale: Locale): UiText = when (label) {
    is TimeLabel.At -> UiText.Raw(formatClock(label.instant, zone, locale))
    is TimeLabel.DayOnly -> res(R.string.time_day_only, formatDay(label.instant.atZone(zone).toLocalDate(), locale))
    is TimeLabel.Between -> res(R.string.time_between, formatDayTime(label.from, zone, locale), formatDayTime(label.to, zone, locale))
    TimeLabel.Unknown -> res(R.string.time_unknown)
}

/** Full date and time, for lists that are not grouped by day. */
fun dateTimeText(label: TimeLabel, zone: ZoneId, locale: Locale): UiText = when (label) {
    is TimeLabel.At -> UiText.Raw(formatDayTime(label.instant, zone, locale))
    else -> timeText(label, zone, locale)
}

/** Where the time came from, in small words. */
fun basisText(basis: TimeBasis): UiText = res(
    when (basis) {
        TimeBasis.SOURCE_CLAIM -> R.string.time_basis_source
        TimeBasis.COLLECTOR_WALL_CLOCK -> R.string.time_basis_collector
        TimeBasis.USER_REPORTED -> R.string.time_basis_user
        TimeBasis.UNKNOWN -> R.string.time_basis_unknown
    },
)

/** The period of a coverage gap; a missing end or start is "not known" and a missing pair is "period not known". */
fun gapPeriodText(start: Instant?, end: Instant?, zone: ZoneId, locale: Locale): UiText = when {
    start != null && end != null -> res(R.string.gap_period_both, formatDayTime(start, zone, locale), formatDayTime(end, zone, locale))
    start != null -> res(R.string.gap_period_from, formatDayTime(start, zone, locale))
    end != null -> res(R.string.gap_period_until, formatDayTime(end, zone, locale))
    else -> res(R.string.gap_period_unknown)
}
