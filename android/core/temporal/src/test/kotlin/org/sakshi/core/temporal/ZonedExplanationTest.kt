package org.sakshi.core.temporal

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.sakshi.core.temporal.fixtures.SyntheticTimelines

class ZonedExplanationTest {
    private val kolkata = ZoneId.of("Asia/Kolkata")
    private val newYork = ZoneId.of("America/New_York")

    private fun texts(explanation: Explanation): List<String> =
        listOf(explanation.observed) + listOfNotNull(explanation.interpretation) + explanation.limitations

    private fun repeatedRecord(first: Instant, last: Instant): PatternRecord =
        SyntheticTimelines.a().analyse().patterns.first { it.type == PatternType.REPEATED_CONTACT }
            .let { it.copy(measurements = repeated(it).copy(firstAt = first, lastAt = last)) }

    @Test
    fun twoArgumentRenderKeepsUtcInstantText() {
        val record = repeatedRecord(instant("2026-09-24T15:35:00Z"), instant("2026-09-25T10:00:30Z"))
        val observed = PatternExplanation.render(record, ::label).observed
        assertTrue("between 2026-09-24T15:35:00Z and 2026-09-25T10:00:30Z," in observed, observed)
    }

    @Test
    fun zonedRenderWritesLocalTimeWithOffsetAndSecondsOnlyWhenNeeded() {
        val record = repeatedRecord(instant("2026-09-24T15:35:00Z"), instant("2026-09-25T10:00:30Z"))
        val observed = PatternExplanation.render(record, ::label, kolkata, Locale.ROOT).observed
        assertTrue("between 2026-09-24 21:05 +05:30 and 2026-09-25 15:30:30 +05:30," in observed, observed)
        val utc = PatternExplanation.render(record, ::label, ZoneOffset.UTC).observed
        assertTrue("between 2026-09-24 15:35 +00:00 and 2026-09-25 10:00:30 +00:00," in utc, utc)
        assertEquals(utc, PatternExplanation.render(record, ::label, locale = Locale.ROOT).observed)
    }

    @Test
    fun daylightSavingChangesTheOffsetButNotTheInstant() {
        val summer = repeatedRecord(instant("2026-07-01T12:00:00Z"), instant("2026-12-01T12:00:00Z"))
        val observed = PatternExplanation.render(summer, ::label, newYork).observed
        assertTrue("between 2026-07-01 08:00 -04:00 and 2026-12-01 07:00 -05:00," in observed, observed)
    }

    @Test
    fun zonedOutputKeepsWordSafetyAndShownCounts() {
        var rendered = 0
        for (view in EvidenceView.entries) {
            for ((name, input) in SyntheticTimelines.all(view)) {
                for (record in input.analyse().patterns) {
                    val explanation = PatternExplanation.render(record, ::label, kolkata)
                    texts(explanation).forEach {
                        assertFalse(FORBIDDEN.containsMatchIn(it), "$name: $it")
                        assertFalse("null" in it, "$name: $it")
                    }
                    when (val m = record.measurements) {
                        is Measurements.RepeatedContact -> assertTrue(explanation.observed.startsWith(m.total.text()))
                        is Measurements.RecurrenceAfterBoundary ->
                            if (m.afterBoundary.upper > 0) assertTrue(explanation.observed.startsWith(m.afterBoundary.text()))
                        is Measurements.DensityChange -> {
                            assertTrue(explanation.observed.startsWith(m.current.text()))
                            assertTrue("compared with ${m.previous.text()} in the preceding" in explanation.observed)
                        }
                        is Measurements.WordingTransition -> assertTrue("+05:30" in explanation.observed)
                    }
                    assertEquals(PatternExplanation.render(record, ::label).limitations, explanation.limitations)
                    rendered++
                }
            }
        }
        assertTrue(rendered > 20)
    }

    @Test
    fun renderIsAPureFunctionOfFacts() {
        for ((name, input) in SyntheticTimelines.all()) {
            for (record in input.analyse().patterns) {
                val facts = PatternExplanation.facts(record)
                val viaFacts = PatternExplanation.render(facts, label(record.actorScope)) { it.toString() }
                assertEquals(PatternExplanation.render(record, ::label), viaFacts, name)
                val rebuilt = PatternExplanation.render(PatternExplanation.facts(record), "X") { "T" }
                val again = PatternExplanation.render(facts, "X") { "T" }
                assertEquals(rebuilt, again, name)
            }
        }
    }

    @Test
    fun factsCarryTypedValues() {
        val record = repeatedRecord(instant("2026-09-24T15:35:00Z"), instant("2026-09-25T10:00:30Z"))
        val facts = PatternExplanation.facts(record) as PatternFacts.RepeatedContact
        assertEquals(repeated(record).total, facts.total)
        assertEquals(instant("2026-09-24T15:35:00Z"), facts.firstAt)
        assertEquals(record.limitations.sorted(), facts.limitations)
        assertEquals(record.actorScope, facts.scope)
        val transition = SyntheticTimelines.all().flatMap { it.value.analyse().patterns }
            .map { PatternExplanation.facts(it) }.filterIsInstance<PatternFacts.WordingTransition>()
        assertTrue(transition.all { it.interpretation == Interpretation.WORDING_CHANGE && it.gap >= Duration.ZERO })
    }
}
