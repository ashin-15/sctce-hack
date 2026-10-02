package org.sakshi.core.temporal

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.sakshi.core.model.CategoryLabel

private const val HOURS_PER_DAY: Long = 24
private const val MINUTES_PER_HOUR: Long = 60

/** Observed statement, optional bounded interpretation and plain-language limitations. */
public data class Explanation(val observed: String, val interpretation: String?, val limitations: List<String>)

/** Template wording built only from a record's typed measurements, through [PatternFacts]. */
public object PatternExplanation {
    private const val MINUTES_PATTERN: String = "yyyy-MM-dd HH:mm xxx"
    private const val SECONDS_PATTERN: String = "yyyy-MM-dd HH:mm:ss xxx"

    /** The typed facts [render] words, for callers that localise the sentences themselves. */
    public fun facts(record: PatternRecord): PatternFacts = FactsBuilder.of(record)

    /** Times appear as UTC instants such as `2026-09-24T15:35:00Z`. */
    public fun render(record: PatternRecord, labels: (ActorScope) -> String): Explanation =
        render(facts(record), labels(record.actorScope)) { it.toString() }

    /**
     * Times appear in [zone] as `yyyy-MM-dd HH:mm` and the offset, for example `2026-09-24 21:05 +05:30`; seconds
     * are added only when the instant has some. [locale] picks the formatter's locale; digits stay as they are.
     */
    public fun render(
        record: PatternRecord,
        labels: (ActorScope) -> String,
        zone: ZoneId = ZoneOffset.UTC,
        locale: Locale = Locale.ROOT,
    ): Explanation = render(facts(record), labels(record.actorScope), zonedTime(zone, locale))

    /** The wording of [facts] for a sender called [who], with every time written by [formatTime]. */
    public fun render(facts: PatternFacts, who: String, formatTime: (Instant) -> String): Explanation {
        val rendered =
            when (facts) {
                is PatternFacts.RepeatedContact -> repeated(facts, who, formatTime)
                is PatternFacts.RecurrenceAfterBoundary -> recurrence(facts, who, formatTime)
                is PatternFacts.WordingTransition -> transition(facts, who, formatTime)
                is PatternFacts.DensityChange -> density(facts, who, formatTime)
            }
        return Explanation(rendered, interpretation(facts.interpretation), facts.limitations.map { limitationSentence(it) })
    }

    private fun zonedTime(zone: ZoneId, locale: Locale): (Instant) -> String {
        val minutes = DateTimeFormatter.ofPattern(MINUTES_PATTERN, locale)
        val seconds = DateTimeFormatter.ofPattern(SECONDS_PATTERN, locale)
        return { instant ->
            val local = instant.atZone(zone)
            (if (local.second == 0) minutes else seconds).format(local)
        }
    }

    private fun repeated(f: PatternFacts.RepeatedContact, who: String, time: (Instant) -> String): String {
        val lead = "${countText(f.total)} distinct retained incoming ${observations(f.total)} ${linkingVerb(f.total)}"
        return if (f.firstAt != null && f.lastAt != null) {
            "$lead linked to $who between ${time(f.firstAt)} and ${time(f.lastAt)}, " +
                "across ${plural(f.uniqueDays.toLong(), "calendar day")} and ${plural(f.episodes.toLong(), "episode")}."
        } else {
            "$lead linked to $who. Their times are not established."
        }
    }

    private fun recurrence(f: PatternFacts.RecurrenceAfterBoundary, who: String, time: (Instant) -> String): String {
        val phrase = boundaryText(f.boundary)
        val at = f.boundaryAt?.let { " at ${time(it)}" }.orEmpty()
        return if (f.afterBoundary.upper == 0) {
            "No retained incoming observation linked to $who was found after $phrase$at in the selected records."
        } else {
            "${countText(f.afterBoundary)} distinct retained incoming ${observations(f.afterBoundary)} " +
                "${linkingVerb(f.afterBoundary)} linked to $who after $phrase$at."
        }
    }

    private fun transition(f: PatternFacts.WordingTransition, who: String, time: (Instant) -> String): String {
        val adjective = f.tags.name.lowercase()
        return "The selected sequence linked to $who moves from a $adjective ${labelName(f.earlier)} tag " +
            "at ${time(f.earlierAt)} to a $adjective ${labelName(f.later)} tag at ${time(f.laterAt)}. " +
            "The interval between the two records is ${durationText(f.gap)}."
    }

    private fun density(f: PatternFacts.DensityChange, who: String, time: (Instant) -> String): String =
        "${countText(f.current)} retained incoming ${observations(f.current)} " +
            "linked to $who in the period starting ${time(f.currentBinStart)}, compared with " +
            "${countText(f.previous)} in the preceding period starting ${time(f.previousBinStart)}."

    private fun interpretation(kind: Interpretation?): String? =
        when (kind) {
            null -> null
            Interpretation.REPEATED_UNWANTED_CONTACT -> "This may indicate repeated unwanted contact."
            Interpretation.REPEATED_UNWANTED_CONTACT_AFTER_BOUNDARY ->
                "This may indicate repeated unwanted contact after that boundary."
            Interpretation.REPEATED_CONTACT_AFTER_BOUNDARY -> "This may indicate repeated contact after that boundary."
            Interpretation.WORDING_CHANGE -> "This may be a change in wording."
        }

    private fun boundaryText(phrase: BoundaryPhrase): String =
        when (phrase) {
            BoundaryPhrase.LIMITED_CONTACT_NOTE -> "your limited-contact note"
            BoundaryPhrase.STOP_CONTACT_MESSAGE -> "your selected stop-contact message"
            BoundaryPhrase.STOP_REQUEST_REPORTED -> "the stop request you reported"
            BoundaryPhrase.DISENGAGEMENT_NOTE -> "your disengagement note"
        }

    private fun observations(bounds: CountBounds): String =
        if (bounds.upper == 1) "observation" else "observations"

    private fun linkingVerb(bounds: CountBounds): String = if (bounds.upper == 1) "is" else "are"

    private fun countText(bounds: CountBounds): String =
        if (bounds.lower == bounds.upper) "${bounds.lower}" else "${bounds.lower} to ${bounds.upper}"

    private fun labelName(label: CategoryLabel): String = label.name.lowercase().replace('_', ' ')

    private fun plural(count: Long, noun: String): String = if (count == 1L) "$count $noun" else "$count ${noun}s"

    private fun durationText(duration: Duration): String =
        when {
            duration.toHours() >= HOURS_PER_DAY -> plural(duration.toDays(), "day")
            duration.toMinutes() >= MINUTES_PER_HOUR -> plural(duration.toHours(), "hour")
            else -> plural(duration.toMinutes(), "minute")
        }

    private fun limitationSentence(limitation: Limitation): String =
        when (limitation) {
            Limitation.SENDER_NOT_AUTHENTICATED ->
                "The sender association is your confirmation, not an authenticated identity."
            Limitation.ACTOR_UNRESOLVED ->
                "The sender is not linked to a confirmed person, so observations are grouped only by " +
                    "source label and conversation."
            Limitation.OUTGOING_COVERAGE_UNKNOWN -> "Your own replies may be missing from the selected records."
            Limitation.COVERAGE_GAP ->
                "Observation coverage is unknown for part of this period, so absence of records there " +
                    "does not show absence of contact."
            Limitation.DUPLICATE_UNCERTAINTY ->
                "Some observations may repeat the same message, so the count is shown as a range."
            Limitation.TIME_UNCERTAIN ->
                "Some times are uncertain or missing, so their order or period is not established."
            Limitation.SELECTION_PARTIAL -> "The selected records may cover only part of the conversation."
            Limitation.NOTIFICATION_PARTIAL -> "Notification excerpts may be incomplete."
            Limitation.UNREVIEWED_TAGS -> "Some tags have not been reviewed by you."
            Limitation.UNCONFIRMED_EVIDENCE -> "Some records are not yet confirmed by you."
            Limitation.BOUNDARY_NOT_COMMUNICATED ->
                "The selected records do not show that this boundary was communicated to the sender."
            Limitation.BOUNDARY_USER_REPORTED ->
                "The boundary is your own report, not an original message in the selected records."
            Limitation.DELIVERY_UNKNOWN ->
                "A selected outgoing message does not show whether it was delivered or read."
            Limitation.DEMO_THRESHOLD ->
                "The threshold used here is a demonstration setting, not a validated criterion."
        }
}

