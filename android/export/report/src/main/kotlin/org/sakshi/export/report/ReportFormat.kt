package org.sakshi.export.report

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.sakshi.core.model.AssociationReview
import org.sakshi.core.model.Event
import org.sakshi.core.model.IdentityBasis
import org.sakshi.core.model.Locator
import org.sakshi.core.model.Timestamp
import org.sakshi.core.model.TimePrecision

private const val MILLIS_PER_SECOND: Long = 1000
private const val SECONDS_PER_MINUTE: Long = 60

/** A time shown to the reader, with the basis and precision in words. */
internal data class TimeText(val text: String, val basis: String)

/** Turns structured event data into the words the templates need. */
internal object ReportFormat {
    private val MINUTE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)
    private val SECOND: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss", Locale.ENGLISH)
    private val MILLI: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss.SSS", Locale.ENGLISH)
    private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    fun time(event: Event, zone: ZoneId): TimeText {
        val bounds = event.timestamp
        val earliest = bounds.earliest
        val latest = bounds.latest
        if (earliest == null && latest == null) return TimeText(ReportText.TIME_NOT_ESTABLISHED, basisWords(event))
        val first = earliest ?: latest
        val last = latest ?: earliest
        val formatter = formatterFor(bounds.precision)
        val from = first?.let { render(it, zone, formatter) }.orEmpty()
        val to = last?.let { render(it, zone, formatter) }.orEmpty()
        val text = if (from == to) from else ReportText.fill(ReportText.TIME_BETWEEN, from, to)
        return TimeText(text, basisWords(event))
    }

    fun sender(event: Event): Pair<String, String> {
        val sender = event.sender
        val label = sender.displayLabel?.takeIf { it.isNotBlank() } ?: ReportText.SENDER_UNNAMED
        val basis = when {
            sender.associationReview == AssociationReview.CONFIRMED && sender.actorId != null ->
                ReportText.IDENTITY_CONFIRMED
            sender.identityBasis == IdentityBasis.USER_ASSERTED -> ReportText.IDENTITY_USER
            else -> ReportText.IDENTITY_EXPORT
        }
        return label to basis
    }

    fun locator(locator: Locator): String = when (locator) {
        Locator.WholeArtifact -> ReportText.LOCATOR_WHOLE
        is Locator.Text -> ReportText.fill(ReportText.LOCATOR_TEXT, locator.start, locator.end)
        is Locator.AudioTime ->
            ReportText.fill(ReportText.LOCATOR_AUDIO, clock(locator.startMs), clock(locator.endMs))
        is Locator.ImageOrPageRegion -> locator.pageIndex?.let {
            ReportText.fill(ReportText.LOCATOR_REGION_PAGE, locator.regionId.value, it + 1)
        } ?: ReportText.fill(ReportText.LOCATOR_REGION, locator.regionId.value)
    }

    private fun basisWords(event: Event): String {
        val bounds = event.timestamp
        val basis = ReportText.TIME_BASIS.getValue(bounds.basis)
        return if (bounds.earliest == null && bounds.latest == null) {
            basis
        } else {
            "$basis, ${ReportText.TIME_PRECISION.getValue(bounds.precision)}"
        }
    }

    private fun formatterFor(precision: TimePrecision): DateTimeFormatter = when (precision) {
        TimePrecision.MILLISECOND -> MILLI
        TimePrecision.SECOND -> SECOND
        TimePrecision.DAY -> DAY
        TimePrecision.MINUTE, TimePrecision.RANGE, TimePrecision.UNKNOWN -> MINUTE
    }

    /** A moment written to the minute in [zone]. */
    fun minute(timestamp: Timestamp, zone: ZoneId): String = render(timestamp, zone, MINUTE)

    private fun render(timestamp: Timestamp, zone: ZoneId, formatter: DateTimeFormatter): String =
        formatter.format(timestamp.instant.atZone(zone))

    private fun clock(ms: Long): String {
        val seconds = ms / MILLIS_PER_SECOND
        return "%d:%02d.%03d".format(Locale.ENGLISH, seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE, ms % MILLIS_PER_SECOND)
    }
}
