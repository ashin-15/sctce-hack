package org.sakshi.core.temporal

import java.time.Duration
import java.time.Instant
import org.sakshi.core.model.BoundaryMarker
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.CommunicationStatus

/** Typed numbers behind a pattern. Explanations are rendered from these, never from free text. */
public sealed interface Measurements {
    /** Highest bounds found in any window of [size] anchored at a contact. */
    public data class WindowMaximum(val size: Duration, val bounds: CountBounds)

    public data class RepeatedContact(
        val total: CountBounds,
        val timedContacts: Int,
        val firstAt: Instant?,
        val lastAt: Instant?,
        val span: Duration?,
        val uniqueDays: Int,
        val episodes: Int,
        val windowMaxima: List<WindowMaximum>,
        val markedUnwanted: Int,
        val markedWanted: Int,
        /** True when there is at least one contact and every representative contact is marked unwanted. */
        val allMarkedUnwanted: Boolean,
    ) : Measurements

    public data class RecurrenceAfterBoundary(
        val afterBoundary: CountBounds,
        val episodes: Int,
        val marker: BoundaryMarker,
        val communication: CommunicationStatus,
        val boundaryAt: Instant?,
        val firstCountedAt: Instant?,
        val lastCountedAt: Instant?,
        val endedByResumption: Boolean,
        /** True when at least one contact was counted and every counted contact is marked unwanted. */
        val allMarkedUnwanted: Boolean,
    ) : Measurements

    public data class WordingTransition(
        val earlier: CategoryLabel,
        val later: CategoryLabel,
        val earlierAt: Instant,
        val laterAt: Instant,
        val gap: Duration,
    ) : Measurements

    public data class DensityChange(
        val previous: CountBounds,
        val current: CountBounds,
        val previousBinStart: Instant,
        val currentBinStart: Instant,
        val binSize: Duration,
    ) : Measurements
}
