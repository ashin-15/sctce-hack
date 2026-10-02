package org.sakshi.core.temporal

import java.time.Duration
import org.sakshi.core.model.BoundaryMarker
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.CommunicationStatus

private const val HOURS_PER_DAY: Long = 24
private const val MINUTES_PER_HOUR: Long = 60

/** Observed statement, optional bounded interpretation and plain-language limitations. */
public data class Explanation(val observed: String, val interpretation: String?, val limitations: List<String>)

/** Template wording built only from a record's typed measurements. */
public object PatternExplanation {
    public fun render(record: PatternRecord, labels: (ActorScope) -> String): Explanation {
        val who = labels(record.actorScope)
        val rendered =
            when (val m = record.measurements) {
                is Measurements.RepeatedContact -> repeated(m, who)
                is Measurements.RecurrenceAfterBoundary -> recurrence(m, record.status, who)
                is Measurements.WordingTransition -> transition(m, record, who)
                is Measurements.DensityChange -> density(m, who)
            }
        return Explanation(
            observed = rendered.first,
            interpretation = rendered.second,
            limitations = record.limitations.sorted().map { limitationSentence(it) },
        )
    }

    private fun repeated(m: Measurements.RepeatedContact, who: String): Pair<String, String?> {
        val lead = "${countText(m.total)} distinct retained incoming ${observations(m.total)} ${linkingVerb(m.total)}"
        val observed =
            if (m.firstAt != null && m.lastAt != null) {
                "$lead linked to $who between ${m.firstAt} and ${m.lastAt}, " +
                    "across ${plural(m.uniqueDays.toLong(), "calendar day")} and ${plural(m.episodes.toLong(), "episode")}."
            } else {
                "$lead linked to $who. Their times are not established."
            }
        val interpretation = "This may indicate repeated unwanted contact.".takeIf { m.allMarkedUnwanted }
        return observed to interpretation
    }

    private fun recurrence(
        m: Measurements.RecurrenceAfterBoundary,
        status: AssessmentStatus,
        who: String,
    ): Pair<String, String?> {
        val phrase = boundaryPhrase(m.marker, m.communication)
        val at = m.boundaryAt?.let { " at $it" }.orEmpty()
        val observed =
            if (m.afterBoundary.upper == 0) {
                "No retained incoming observation linked to $who was found after $phrase$at in the selected records."
            } else {
                "${countText(m.afterBoundary)} distinct retained incoming ${observations(m.afterBoundary)} " +
                    "${linkingVerb(m.afterBoundary)} linked to $who after $phrase$at."
            }
        val reportable =
            (status == AssessmentStatus.SUPPORTED_DESCRIPTION || status == AssessmentStatus.CANDIDATE) &&
                m.afterBoundary.lower >= 1
        val interpretation =
            when {
                !reportable -> null
                m.allMarkedUnwanted -> "This may indicate repeated unwanted contact after that boundary."
                else -> "This may indicate repeated contact after that boundary."
            }
        return observed to interpretation
    }

    private fun transition(
        m: Measurements.WordingTransition,
        record: PatternRecord,
        who: String,
    ): Pair<String, String?> {
        val adjective = if (Limitation.UNREVIEWED_TAGS in record.limitations) "suggested" else "reviewed"
        val observed =
            "The selected sequence linked to $who moves from a $adjective ${labelName(m.earlier)} tag " +
                "at ${m.earlierAt} to a $adjective ${labelName(m.later)} tag at ${m.laterAt}. " +
                "The interval between the two records is ${durationText(m.gap)}."
        return observed to "This may be a change in wording."
    }

    private fun density(m: Measurements.DensityChange, who: String): Pair<String, String?> =
        "${countText(m.current)} retained incoming ${observations(m.current)} " +
            "linked to $who in the period starting ${m.currentBinStart}, compared with " +
            "${countText(m.previous)} in the preceding period starting ${m.previousBinStart}." to null

    private fun boundaryPhrase(marker: BoundaryMarker, communication: CommunicationStatus): String =
        when {
            marker == BoundaryMarker.LIMITED_CONTACT -> "your limited-contact note"
            marker == BoundaryMarker.DO_NOT_CONTACT &&
                communication == CommunicationStatus.SUPPORTED_BY_SELECTED_EVIDENCE ->
                "your selected stop-contact message"
            marker == BoundaryMarker.DO_NOT_CONTACT && communication == CommunicationStatus.USER_REPORTED ->
                "the stop request you reported"
            else -> "your disengagement note"
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

