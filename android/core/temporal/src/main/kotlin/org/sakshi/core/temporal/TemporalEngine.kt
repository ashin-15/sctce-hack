package org.sakshi.core.temporal

/** Deterministic projection of case events into timeline entries and pattern records. */
public object TemporalEngine {
    /** Same input in any order gives an identical result. Reads no clock and has no side effects. */
    public fun analyse(input: TemporalInput): TemporalResult {
        val selected = EventSelection.select(input)
        val groups = ContactCanonicalizer.group(selected)
        val caseGaps = input.gaps.filter { it.caseId == input.caseId }
        val projection = TemporalProjection(input.config, caseGaps)
        return TemporalResult(
            timeline = projection.timeline(selected, groups),
            patterns = PatternReducer(input, selected, groups, projection).reduce(),
            gaps = caseGaps.sortedBy { it.id.value },
        )
    }
}
