package org.sakshi.core.temporal

import java.time.Duration
import java.time.Instant
import org.sakshi.core.model.BoundaryMarker
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.CommunicationStatus

/** Which boundary the sender's later contact is counted against. */
public enum class BoundaryPhrase {
    LIMITED_CONTACT_NOTE,
    STOP_CONTACT_MESSAGE,
    STOP_REQUEST_REPORTED,
    DISENGAGEMENT_NOTE,
}

/** Whether the tags of a wording transition were reviewed by the person. */
public enum class TagWording { SUGGESTED, REVIEWED }

/** The bounded sentence a pattern may carry beside what was observed. */
public enum class Interpretation {
    REPEATED_UNWANTED_CONTACT,
    REPEATED_UNWANTED_CONTACT_AFTER_BOUNDARY,
    REPEATED_CONTACT_AFTER_BOUNDARY,
    WORDING_CHANGE,
}

/**
 * Everything the wording of one pattern depends on, as typed values. [PatternExplanation.render] is a pure function
 * of these facts, so a UI can build its own localised sentences from them without parsing English.
 */
public sealed interface PatternFacts {
    public val scope: ActorScope
    public val status: AssessmentStatus
    public val limitations: List<Limitation>
    public val interpretation: Interpretation?

    /** Distinct retained incoming contacts of one sender. Times are null when not established. */
    public data class RepeatedContact(
        override val scope: ActorScope,
        override val status: AssessmentStatus,
        override val limitations: List<Limitation>,
        override val interpretation: Interpretation?,
        val total: CountBounds,
        val firstAt: Instant?,
        val lastAt: Instant?,
        val uniqueDays: Int,
        val episodes: Int,
    ) : PatternFacts

    public data class RecurrenceAfterBoundary(
        override val scope: ActorScope,
        override val status: AssessmentStatus,
        override val limitations: List<Limitation>,
        override val interpretation: Interpretation?,
        val afterBoundary: CountBounds,
        val boundary: BoundaryPhrase,
        val boundaryAt: Instant?,
    ) : PatternFacts

    public data class WordingTransition(
        override val scope: ActorScope,
        override val status: AssessmentStatus,
        override val limitations: List<Limitation>,
        override val interpretation: Interpretation?,
        val tags: TagWording,
        val earlier: CategoryLabel,
        val later: CategoryLabel,
        val earlierAt: Instant,
        val laterAt: Instant,
        val gap: Duration,
    ) : PatternFacts

    public data class DensityChange(
        override val scope: ActorScope,
        override val status: AssessmentStatus,
        override val limitations: List<Limitation>,
        override val interpretation: Interpretation?,
        val previous: CountBounds,
        val current: CountBounds,
        val previousBinStart: Instant,
        val currentBinStart: Instant,
    ) : PatternFacts
}

internal object FactsBuilder {
    fun of(record: PatternRecord): PatternFacts {
        val limitations = record.limitations.sorted()
        return when (val m = record.measurements) {
            is Measurements.RepeatedContact -> PatternFacts.RepeatedContact(
                record.actorScope, record.status, limitations,
                Interpretation.REPEATED_UNWANTED_CONTACT.takeIf { m.allMarkedUnwanted },
                m.total, m.firstAt, m.lastAt, m.uniqueDays, m.episodes,
            )
            is Measurements.RecurrenceAfterBoundary -> PatternFacts.RecurrenceAfterBoundary(
                record.actorScope, record.status, limitations, recurrenceInterpretation(m, record.status),
                m.afterBoundary, boundaryPhrase(m.marker, m.communication), m.boundaryAt,
            )
            is Measurements.WordingTransition -> PatternFacts.WordingTransition(
                record.actorScope, record.status, limitations, Interpretation.WORDING_CHANGE,
                if (Limitation.UNREVIEWED_TAGS in record.limitations) TagWording.SUGGESTED else TagWording.REVIEWED,
                m.earlier, m.later, m.earlierAt, m.laterAt, m.gap,
            )
            is Measurements.DensityChange -> PatternFacts.DensityChange(
                record.actorScope, record.status, limitations, null,
                m.previous, m.current, m.previousBinStart, m.currentBinStart,
            )
        }
    }

    private fun recurrenceInterpretation(m: Measurements.RecurrenceAfterBoundary, status: AssessmentStatus): Interpretation? {
        val reportable =
            (status == AssessmentStatus.SUPPORTED_DESCRIPTION || status == AssessmentStatus.CANDIDATE) &&
                m.afterBoundary.lower >= 1
        return when {
            !reportable -> null
            m.allMarkedUnwanted -> Interpretation.REPEATED_UNWANTED_CONTACT_AFTER_BOUNDARY
            else -> Interpretation.REPEATED_CONTACT_AFTER_BOUNDARY
        }
    }

    private fun boundaryPhrase(marker: BoundaryMarker, communication: CommunicationStatus): BoundaryPhrase =
        when {
            marker == BoundaryMarker.LIMITED_CONTACT -> BoundaryPhrase.LIMITED_CONTACT_NOTE
            marker == BoundaryMarker.DO_NOT_CONTACT &&
                communication == CommunicationStatus.SUPPORTED_BY_SELECTED_EVIDENCE -> BoundaryPhrase.STOP_CONTACT_MESSAGE
            marker == BoundaryMarker.DO_NOT_CONTACT && communication == CommunicationStatus.USER_REPORTED ->
                BoundaryPhrase.STOP_REQUEST_REPORTED
            else -> BoundaryPhrase.DISENGAGEMENT_NOTE
        }
}
