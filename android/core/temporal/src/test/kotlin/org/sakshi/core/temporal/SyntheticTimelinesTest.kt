package org.sakshi.core.temporal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.sakshi.core.model.BoundaryMarker
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.temporal.fixtures.SyntheticTimelines

class SyntheticTimelinesTest {
    private val forbidden = Regex("""\b(stalker|stalking|guilty|proves|proof|admissible|danger|safe|will|ignored)\b""", RegexOption.IGNORE_CASE)

    @Test
    fun fixtureA_recurrenceAfterSelectedStopMessage() {
        val result = SyntheticTimelines.a().analyse()
        val record = result.single(PatternType.RECURRENCE_AFTER_BOUNDARY)
        val m = recurrence(record)
        assertEquals(CountBounds(6, 6), m.afterBoundary)
        assertEquals(1, m.episodes)
        assertEquals(BoundaryMarker.DO_NOT_CONTACT, m.marker)
        assertEquals(AssessmentStatus.SUPPORTED_DESCRIPTION, record.status)
        assertTrue(Limitation.DELIVERY_UNKNOWN in record.limitations)
        assertFalse(Limitation.BOUNDARY_NOT_COMMUNICATED in record.limitations)
        assertEquals(instant("2026-10-01T03:35:00Z"), record.windowStart)
        assertEquals(instant("2026-10-01T04:10:00Z"), record.windowEnd)
        val text = PatternExplanation.render(record, ::label)
        assertTrue("after your selected stop-contact message" in text.observed)
        assertEquals("This may indicate repeated unwanted contact after that boundary.", text.interpretation)

        val repeated = result.single(PatternType.REPEATED_CONTACT)
        assertEquals(CountBounds(6, 6), repeated(repeated).total)
        assertEquals("This may indicate repeated unwanted contact.", PatternExplanation.render(repeated, ::label).interpretation)
        assertEquals(1, repeated(repeated).uniqueDays)
    }

    @Test
    fun fixtureB_wordingTransitionBetweenB1AndB3() {
        val result = SyntheticTimelines.b().analyse()
        val record = result.single(PatternType.WORDING_TRANSITION)
        val m = transition(record)
        assertEquals(CategoryLabel.VERBAL_ABUSE, m.earlier)
        assertEquals(CategoryLabel.EXPLICIT_THREAT, m.later)
        assertEquals(14L, m.gap.toHours())
        assertEquals(AssessmentStatus.SUPPORTED_DESCRIPTION, record.status)
        assertEquals(
            listOf("synthetic-b1" to SupportRole.EARLIER_CATEGORY, "synthetic-b3" to SupportRole.LATER_CATEGORY),
            record.supportingEvents.map { it.eventId.value to it.role },
        )
        assertEquals("This may be a change in wording.", PatternExplanation.render(record, ::label).interpretation)
    }

    @Test
    fun fixtureC_densityChangeIsOnlyADescription() {
        val result = SyntheticTimelines.c().analyse()
        val record = result.single(PatternType.DENSITY_CHANGE)
        val m = density(record)
        assertEquals(CountBounds(3, 3), m.previous)
        assertEquals(CountBounds(12, 12), m.current)
        assertEquals(AssessmentStatus.SUPPORTED_DESCRIPTION, record.status)
        assertNull(PatternExplanation.render(record, ::label).interpretation)
        val repeated = result.single(PatternType.REPEATED_CONTACT)
        assertEquals(CountBounds(15, 15), repeated(repeated).total)
        assertNull(PatternExplanation.render(repeated, ::label).interpretation)
        assertTrue(result.of(PatternType.RECURRENCE_AFTER_BOUNDARY).isEmpty())
    }

    @Test
    fun fixtureD_disengagementWithCoverageGap() {
        val result = SyntheticTimelines.d().analyse()
        val record = result.single(PatternType.RECURRENCE_AFTER_BOUNDARY)
        val m = recurrence(record)
        assertEquals(BoundaryMarker.USER_DISENGAGEMENT, m.marker)
        assertEquals(CountBounds(5, 5), m.afterBoundary)
        assertEquals(2, m.episodes)
        assertTrue(Limitation.COVERAGE_GAP in record.limitations)
        assertTrue(Limitation.BOUNDARY_NOT_COMMUNICATED in record.limitations)
        assertEquals(listOf("synthetic-g1"), record.gapIds.map { it.value })
        assertEquals(listOf("synthetic-g1"), result.gaps.map { it.id.value })
        val text = PatternExplanation.render(record, ::label)
        assertTrue("your disengagement note" in text.observed)
        val everything = listOf(text.observed, text.interpretation.orEmpty()) + text.limitations
        everything.forEach { assertFalse(forbidden.containsMatchIn(it), it) }
    }

    @Test
    fun fixtureE_outgoingReplyIsNotAContact() {
        val result = SyntheticTimelines.e().analyse()
        assertEquals(4, result.timeline.size)
        assertEquals(listOf("synthetic-e1", "synthetic-e3", "synthetic-e4"), result.contactEntries().map { it.eventId.value })
        assertFalse(result.timeline.single { it.eventId.value == "synthetic-e2" }.isContact)
        val record = result.single(PatternType.REPEATED_CONTACT)
        assertEquals(CountBounds(3, 3), repeated(record).total)
        assertTrue(result.of(PatternType.WORDING_TRANSITION).isEmpty())
    }

    @Test
    fun fixtureF_fiveObservationsThreeContacts() {
        val result = SyntheticTimelines.f().analyse()
        assertEquals(5, result.timeline.size)
        assertEquals(5, result.contactEntries().size)
        val record = result.single(PatternType.REPEATED_CONTACT)
        assertEquals(CountBounds(3, 3), repeated(record).total)
        assertEquals(3, record.supportingEvents.size)
        assertEquals(2, record.contextEvents.size)
        assertTrue(Limitation.ACTOR_UNRESOLVED in record.limitations)
        val merged = result.timeline.filter { it.canonicalEventId != null }
        assertEquals(listOf("synthetic-f1-repost", "synthetic-f2-repost"), merged.map { it.eventId.value }.sorted())
    }

    @Test
    fun fixtureF_twoAlexScopesStaySeparate() {
        val result = SyntheticTimelines.fTwoAlex().analyse()
        val records = result.of(PatternType.REPEATED_CONTACT)
        assertEquals(2, records.size)
        assertEquals(2, records.map { it.actorScope }.toSet().size)
        records.forEach { record ->
            val scope = record.actorScope
            assertTrue(scope is ActorScope.Unresolved)
            assertEquals("Alex", scope.displayLabel)
            assertEquals(CountBounds(2, 2), repeated(record).total)
        }
        assertEquals(2, records.map { it.actorScope.key }.toSet().size)
    }
}
