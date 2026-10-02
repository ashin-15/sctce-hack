package org.sakshi.core.temporal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.sakshi.core.model.BoundaryMarker
import org.sakshi.core.model.CommunicationStatus
import org.sakshi.core.temporal.fixtures.SyntheticTimelines

internal val FORBIDDEN: Regex =
    Regex(
        """\b(stalker|stalking|guilty|proves|proof|admissible|danger|risk score|will|harassment occurred|safe|all clear|ignored)\b""",
        RegexOption.IGNORE_CASE,
    )

class ExplanationTest {
    private fun texts(explanation: Explanation): List<String> =
        listOf(explanation.observed) + listOfNotNull(explanation.interpretation) + explanation.limitations

    @Test
    fun noRecordInAnyFixtureOrViewUsesForbiddenWordsOrNull() {
        var rendered = 0
        for (view in EvidenceView.entries) {
            for ((name, input) in SyntheticTimelines.all(view)) {
                for (record in input.analyse().patterns) {
                    val explanation = PatternExplanation.render(record, ::label)
                    texts(explanation).forEach {
                        assertFalse(FORBIDDEN.containsMatchIn(it), "$name: $it")
                        assertFalse("null" in it, "$name: $it")
                    }
                    rendered++
                }
            }
        }
        assertTrue(rendered > 20)
    }

    @Test
    fun everyLimitationHasAFixedSentenceInEnumOrder() {
        val sentences =
            Limitation.entries.map { limitation ->
                val record = SyntheticTimelines.a().analyse().patterns.first().copy(limitations = setOf(limitation))
                PatternExplanation.render(record, ::label).limitations.single()
            }
        assertEquals(Limitation.entries.size, sentences.toSet().size)
        sentences.forEach { assertFalse(FORBIDDEN.containsMatchIn(it), it) }
        val all = SyntheticTimelines.a().analyse().patterns.first().copy(limitations = Limitation.entries.toSet())
        assertEquals(sentences, PatternExplanation.render(all, ::label).limitations)
    }

    @Test
    fun shownCountsMatchMeasurements() {
        for ((name, input) in SyntheticTimelines.all()) {
            for (record in input.analyse().patterns) {
                val observed = PatternExplanation.render(record, ::label).observed
                when (val m = record.measurements) {
                    is Measurements.RepeatedContact -> assertTrue(observed.startsWith(m.total.text()), "$name: $observed")
                    is Measurements.RecurrenceAfterBoundary ->
                        if (m.afterBoundary.upper > 0) assertTrue(observed.startsWith(m.afterBoundary.text()), observed)
                    is Measurements.DensityChange -> {
                        assertTrue(observed.startsWith(m.current.text()), observed)
                        assertTrue("compared with ${m.previous.text()} in the preceding" in observed, observed)
                    }
                    is Measurements.WordingTransition -> assertTrue("${m.earlierAt}" in observed && "${m.laterAt}" in observed)
                }
            }
        }
    }

    @Test
    fun notObservedSaysInTheSelectedRecords() {
        val events =
            listOf(
                msg("synthetic-stop", "10:00:00") {
                    boundary(BoundaryMarker.LIMITED_CONTACT, "synthetic-t-a", CommunicationStatus.USER_REPORTED)
                },
                msg("synthetic-m1", "09:00:00"),
            )
        val record = caseInput(events).analyse().single(PatternType.RECURRENCE_AFTER_BOUNDARY)
        assertEquals(AssessmentStatus.NOT_OBSERVED, record.status)
        val text = PatternExplanation.render(record, ::label)
        assertTrue("in the selected records." in text.observed)
        assertTrue("your limited-contact note" in text.observed)
        assertEquals(null, text.interpretation)
    }

    @Test
    fun boundaryPhrasesFollowMarkerAndCommunication() {
        fun phrase(marker: BoundaryMarker, status: CommunicationStatus): String {
            val events =
                listOf(
                    msg("synthetic-stop", "10:00:00") { boundary(marker, "synthetic-t-a", status) },
                    msg("synthetic-m1", "10:05:00"),
                )
            val record = caseInput(events).analyse().single(PatternType.RECURRENCE_AFTER_BOUNDARY)
            return PatternExplanation.render(record, ::label).observed
        }
        assertTrue("after the stop request you reported" in phrase(BoundaryMarker.DO_NOT_CONTACT, CommunicationStatus.USER_REPORTED))
        assertTrue("after your disengagement note" in phrase(BoundaryMarker.DO_NOT_CONTACT, CommunicationStatus.NOT_COMMUNICATED))
        assertTrue("after your disengagement note" in phrase(BoundaryMarker.USER_DISENGAGEMENT, CommunicationStatus.USER_REPORTED))
    }

    @Test
    fun denseBenignConversationNeverGetsTheUnwantedSentence() {
        val result = SyntheticTimelines.c().analyse()
        result.patterns.forEach {
            assertEquals(null, PatternExplanation.render(it, ::label).interpretation)
        }
    }
}
