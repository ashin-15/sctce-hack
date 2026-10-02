package org.sakshi.core.temporal

import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventId
import org.sakshi.core.temporal.fixtures.EventBuilder
import org.sakshi.core.temporal.fixtures.atOffsetText
import org.sakshi.core.temporal.fixtures.mapTimestamps
import org.sakshi.core.temporal.fixtures.syntheticEvent

internal fun TemporalResult.of(type: PatternType): List<PatternRecord> = patterns.filter { it.type == type }

internal fun TemporalResult.single(type: PatternType): PatternRecord {
    val found = of(type)
    assertEquals(1, found.size, "expected exactly one $type record but found ${found.size}")
    return found.single()
}

internal fun TemporalResult.contactEntries(): List<TimelineEntry> = timeline.filter { it.isContact }

internal fun TemporalInput.analyse(): TemporalResult = TemporalEngine.analyse(this)



internal fun CountBounds.text(): String = if (lower == upper) "$lower" else "$lower to $upper"

internal fun repeated(record: PatternRecord): Measurements.RepeatedContact =
    assertNotNull(record.measurements as? Measurements.RepeatedContact)

internal fun recurrence(record: PatternRecord): Measurements.RecurrenceAfterBoundary =
    assertNotNull(record.measurements as? Measurements.RecurrenceAfterBoundary)

internal fun transition(record: PatternRecord): Measurements.WordingTransition =
    assertNotNull(record.measurements as? Measurements.WordingTransition)

internal fun density(record: PatternRecord): Measurements.DensityChange =
    assertNotNull(record.measurements as? Measurements.DensityChange)

internal fun label(scope: ActorScope): String =
    when (scope) {
        is ActorScope.Confirmed -> "Person ${scope.actorId.value.takeLast(1).uppercase()}"
        is ActorScope.Unresolved -> scope.displayLabel ?: "an unlabelled sender"
    }

internal fun instant(text: String): Instant = Instant.parse(text)

internal fun id(text: String): EventId = EventId(text)

/** Counts, statuses and limitations only; ignores the instants so shifted timelines can be compared. */
internal fun TemporalResult.shape(): List<String> =
    patterns.map { record ->
        val counts =
            when (val m = record.measurements) {
                is Measurements.RepeatedContact -> "${m.total} ${m.uniqueDays} ${m.episodes} ${m.windowMaxima.map { it.bounds }}"
                is Measurements.RecurrenceAfterBoundary -> "${m.afterBoundary} ${m.episodes}"
                is Measurements.WordingTransition -> "${m.earlier} ${m.later} ${m.gap}"
                is Measurements.DensityChange -> "${m.previous} ${m.current}"
            }
        "${record.type} ${record.actorScope.key} ${record.status} ${record.limitations} $counts"
    }

internal const val CASE: String = "synthetic-case-t"
internal val CUTOFF: Instant = Instant.parse("2026-10-03T00:00:00Z")

internal fun caseInput(
    events: List<Event>,
    gaps: List<CoverageGap> = emptyList(),
    view: EvidenceView = EvidenceView.CONFIRMED_ONLY,
    cutoff: Instant = CUTOFF,
    config: PatternConfig = PatternConfig(),
): TemporalInput =
    TemporalInput(CaseId(CASE), events, gaps, cutoff, view, config)

/** A confirmed incoming message from actor "t-a" at the given UTC time on 1 October 2026. */
internal fun msg(
    id: String,
    time: String,
    configure: EventBuilder.() -> Unit = {},
): Event =
    syntheticEvent(id) {
        caseId = CASE
        actor = "synthetic-t-a"
        at("2026-10-01T${time}Z")
        configure()
    }

/** Every timestamp moved by [by] and re-expressed at [offset]; gaps and cutoff move with it. */
internal fun TemporalInput.moved(by: java.time.Duration, offset: java.time.ZoneOffset): TemporalInput =
    copy(
        events = events.map { event -> event.mapTimestamps { it.instant.plus(by).atOffsetText(offset) } },
        gaps = gaps.map { gap -> gap.copy(start = gap.start?.plus(by), end = gap.end?.plus(by)) },
        knowledgeCutoff = knowledgeCutoff.plus(by),
    )
