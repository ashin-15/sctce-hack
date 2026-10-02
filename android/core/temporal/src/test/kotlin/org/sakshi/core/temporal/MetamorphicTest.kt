package org.sakshi.core.temporal

import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.sakshi.core.model.BoundaryMarker
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.CategoryReviewStatus
import org.sakshi.core.model.CommunicationStatus
import org.sakshi.core.model.ConfirmationStatus
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.DedupStatus
import org.sakshi.core.model.Direction
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventKind
import org.sakshi.core.model.OutgoingCoverage
import org.sakshi.core.model.ReferenceId
import org.sakshi.core.model.RelationshipReviewStatus
import org.sakshi.core.model.Representation
import org.sakshi.core.model.TextStatus
import org.sakshi.core.model.Timestamp
import org.sakshi.core.temporal.fixtures.SyntheticTimelines
import org.sakshi.core.temporal.fixtures.syntheticEvent

/** The sixteen functional and metamorphic tests of report section 13.4, adapted to this engine. */
class MetamorphicTest {
    private fun contactA(id: String, time: String, configure: org.sakshi.core.temporal.fixtures.EventBuilder.() -> Unit = {}) =
        syntheticEvent(id) {
            caseId = "synthetic-case-a"
            actor = "synthetic-actor-a"
            at(SyntheticTimelines.local(time))
            unwanted()
            configure()
        }

    private fun Event.movedTo(iso: String): Event =
        copy(timestamp = timestamp.copy(earliest = Timestamp(iso), latest = Timestamp(iso)))

    private fun bounds(input: TemporalInput): CountBounds =
        recurrence(input.analyse().single(PatternType.RECURRENCE_AFTER_BOUNDARY)).afterBoundary

    @Test
    fun test01_repostingTheSamePayloadDoesNotAddContactCount() {
        val base = SyntheticTimelines.a()
        val repost = contactA("synthetic-a1-repost", "09:05") { dedup(DedupStatus.SAME_REPRESENTATION, "synthetic-a1") }
        val result = base.copy(events = base.events + repost).analyse()
        assertEquals(CountBounds(6, 6), recurrence(result.single(PatternType.RECURRENCE_AFTER_BOUNDARY)).afterBoundary)
        assertEquals(CountBounds(6, 6), repeated(result.single(PatternType.REPEATED_CONTACT)).total)
    }

    @Test
    fun test02_twoDistinctIdenticalMessagesRemainTwoContacts() {
        val hash = "b".repeat(64)
        val events = listOf(msg("synthetic-m1", "10:00:00") { sha256 = hash }, msg("synthetic-m2", "10:00:00") { sha256 = hash })
        assertEquals(CountBounds(2, 2), repeated(caseInput(events).analyse().single(PatternType.REPEATED_CONTACT)).total)
    }

    @Test
    fun test03_independentScreenshotOfReviewedMessageDoesNotDoubleCount() {
        val base = SyntheticTimelines.a()
        fun withScreenshot(review: RelationshipReviewStatus) =
            base.copy(
                events = base.events + contactA("synthetic-a1-shot", "09:06") {
                    representation = Representation.OCR_DERIVATIVE
                    relate("synthetic-a1", review = review)
                },
            )
        assertEquals(CountBounds(6, 6), bounds(withScreenshot(RelationshipReviewStatus.CONFIRMED)))
        val unreviewed = withScreenshot(RelationshipReviewStatus.UNREVIEWED)
        assertEquals(CountBounds(6, 7), bounds(unreviewed))
        assertTrue(Limitation.DUPLICATE_UNCERTAINTY in unreviewed.analyse().single(PatternType.RECURRENCE_AFTER_BOUNDARY).limitations)
    }

    @Test
    fun test04_renamingDisplayLabelDoesNotChangeConfirmedActorIdentity() {
        val base = SyntheticTimelines.a()
        val renamed = base.copy(events = base.events.map { it.copy(sender = it.sender.copy(displayLabel = "Someone Else")) })
        assertEquals(base.analyse(), renamed.analyse())

        val alex = SyntheticTimelines.fTwoAlex()
        val keys = alex.analyse().patterns.map { it.actorScope.key }.toSet()
        val relabelled =
            alex.copy(events = alex.events.map { it.copy(sender = it.sender.copy(displayLabel = "Not Alex")) })
        assertEquals(keys.size, relabelled.analyse().patterns.map { it.actorScope.key }.toSet().size)
        assertTrue(keys.intersect(relabelled.analyse().patterns.map { it.actorScope.key }.toSet()).isEmpty())
    }

    @Test
    fun test05_shiftedTimestampsAndOffsetChangesPreserveRelativePatternsAndInstants() {
        for (input in listOf(SyntheticTimelines.b(), SyntheticTimelines.d())) {
            val shifted = input.moved(Duration.ofDays(14), ZoneOffset.ofHoursMinutes(-3, -30))
            assertEquals(input.analyse().shape(), shifted.analyse().shape())
            assertEquals(input.analyse(), input.moved(Duration.ZERO, ZoneOffset.ofHours(9)).analyse())
        }
    }

    @Test
    fun test06_insertingACoverageGapBlocksNoContactAndRateClaimsButKeepsRecords() {
        val c = SyntheticTimelines.c()
        val gap =
            CoverageGap(
                ReferenceId("synthetic-gap"), c.caseId, null,
                Instant.parse("2026-10-01T02:40:00Z"), Instant.parse("2026-10-01T03:00:00Z"), GapReason.QUEUE_OVERFLOW,
            )
        val result = c.copy(gaps = listOf(gap)).analyse()
        val density = result.single(PatternType.DENSITY_CHANGE)
        assertEquals(AssessmentStatus.INSUFFICIENT_CONTEXT, density.status)
        assertTrue(Limitation.COVERAGE_GAP in density.limitations)
        assertEquals(15, result.single(PatternType.REPEATED_CONTACT).supportingEvents.size)

        val a = SyntheticTimelines.a()
        val moved = a.events.map { if (it.eventId.value == "synthetic-a0") it.movedTo(SyntheticTimelines.local("10:00")) else it }
        val gapA = gap.copy(caseId = a.caseId, start = Instant.parse("2026-10-01T04:20:00Z"), end = Instant.parse("2026-10-01T04:40:00Z"))
        val noContact = a.copy(events = moved, gaps = listOf(gapA)).analyse().single(PatternType.RECURRENCE_AFTER_BOUNDARY)
        assertEquals(AssessmentStatus.INSUFFICIENT_CONTEXT, noContact.status)
        assertTrue(Limitation.COVERAGE_GAP in noContact.limitations)
    }

    @Test
    fun test07_movingTheStopMarkerAfterTheContactsRemovesTheAfterBoundaryClaim() {
        val a = SyntheticTimelines.a()
        val moved = a.events.map { if (it.eventId.value == "synthetic-a0") it.movedTo(SyntheticTimelines.local("09:50")) else it }
        val record = a.copy(events = moved).analyse().single(PatternType.RECURRENCE_AFTER_BOUNDARY)
        assertEquals(AssessmentStatus.NOT_OBSERVED, record.status)
        assertEquals(CountBounds(0, 0), recurrence(record).afterBoundary)
    }

    @Test
    fun test08_removingCommunicationEvidenceChangesTheBoundaryWording() {
        val a = SyntheticTimelines.a()
        fun with(status: CommunicationStatus): PatternRecord =
            a.copy(
                events = a.events.map {
                    if (it.eventId.value == "synthetic-a0") it.copy(boundary = it.boundary.copy(communicationStatus = status)) else it
                },
            ).analyse().single(PatternType.RECURRENCE_AFTER_BOUNDARY)
        val reported = with(CommunicationStatus.USER_REPORTED)
        assertTrue(Limitation.BOUNDARY_USER_REPORTED in reported.limitations)
        assertFalse(Limitation.DELIVERY_UNKNOWN in reported.limitations)
        assertTrue("the stop request you reported" in PatternExplanation.render(reported, ::label).observed)
        val none = with(CommunicationStatus.NOT_COMMUNICATED)
        assertTrue(Limitation.BOUNDARY_NOT_COMMUNICATED in none.limitations)
        assertTrue("your disengagement note" in PatternExplanation.render(none, ::label).observed)
    }

    @Test
    fun test09_aReportedQuoteOfAThreatIsNotAContactAndCreatesNoTransition() {
        val b = SyntheticTimelines.b()
        val quote =
            syntheticEvent("synthetic-b3") {
                caseId = "synthetic-case-b"
                actor = "synthetic-actor-b"
                kind = EventKind.REPORTED_EXTERNAL_EVENT
                at(SyntheticTimelines.local("08:00", "2026-10-02"))
                category(CategoryLabel.EXPLICIT_THREAT)
            }
        val result = b.copy(events = b.events.filter { it.eventId.value != "synthetic-b3" } + quote).analyse()
        assertTrue(result.of(PatternType.WORDING_TRANSITION).isEmpty())
        assertFalse(result.timeline.single { it.eventId == quote.eventId }.isContact)
        assertEquals(CountBounds(2, 2), repeated(result.single(PatternType.REPEATED_CONTACT)).total)
    }

    @Test
    fun test10_anIncomingOnlyViewNeverProvesAbsenceOfReplies() {
        val a = SyntheticTimelines.a()
        assertFalse(a.analyse().patterns.any { Limitation.OUTGOING_COVERAGE_UNKNOWN in it.limitations })
        val partial = a.copy(events = a.events.map { it.copy(coverage = it.coverage.copy(outgoingCoverage = OutgoingCoverage.UNKNOWN)) })
        val records = partial.analyse().patterns
        assertTrue(records.isNotEmpty())
        records.forEach {
            assertTrue(Limitation.OUTGOING_COVERAGE_UNKNOWN in it.limitations)
            val text = PatternExplanation.render(it, ::label)
            assertTrue("Your own replies may be missing from the selected records." in text.limitations)
            assertFalse(Regex("no repl", RegexOption.IGNORE_CASE).containsMatchIn(text.observed))
        }
    }

    @Test
    fun test11_rejectingOrCorrectingATagInvalidatesDependentPatterns() {
        val b = SyntheticTimelines.b(EvidenceView.CANDIDATE_PREVIEW)
        fun b3(configure: org.sakshi.core.temporal.fixtures.EventBuilder.() -> Unit) =
            syntheticEvent("synthetic-b3") {
                caseId = "synthetic-case-b"
                actor = "synthetic-actor-b"
                at(SyntheticTimelines.local("08:00", "2026-10-02"))
                configure()
            }
        val others = b.events.filter { it.eventId.value != "synthetic-b3" }
        val pending = b3 { category(CategoryLabel.EXPLICIT_THREAT); pending() }
        val candidate = b.copy(events = others + pending).analyse().single(PatternType.WORDING_TRANSITION)
        assertEquals(AssessmentStatus.CANDIDATE, candidate.status)
        assertTrue(candidate.dependsOn(pending.eventId))

        val rejected = b3 { revision = 2; category(CategoryLabel.EXPLICIT_THREAT); confirmation = ConfirmationStatus.REJECTED }
        val afterReject = b.copy(events = others + pending + rejected).analyse()
        assertFalse(afterReject.patterns.any { it.dependsOn(pending.eventId) })

        val corrected = b3 { revision = 2; category(CategoryLabel.EXPLICIT_THREAT, CategoryReviewStatus.REJECTED) }
        assertTrue(b.copy(events = others + pending + corrected).analyse().of(PatternType.WORDING_TRANSITION).isEmpty())
    }

    @Test
    fun test12_reorderedHistoricalImportsGiveTheSameProjectionAndKnowledgeTimeIsNotRewritten() {
        val a = SyntheticTimelines.a()
        assertEquals(a.analyse(), a.copy(events = a.events.reversed()).analyse())

        val early = Instant.parse("2026-10-01T05:00:00Z")
        val late = contactA("synthetic-a7-late", "09:45") { availableAt = "2026-10-02T00:00:00Z"; observedAt = "2026-10-01T09:45:00+05:30" }
        val withLate = a.copy(events = a.events + late)
        assertEquals(a.copy(knowledgeCutoff = early).analyse(), withLate.copy(knowledgeCutoff = early).analyse())
        assertEquals(CountBounds(6, 6), bounds(withLate.copy(knowledgeCutoff = Instant.parse("2026-10-01T12:00:00Z"))))
        assertEquals(CountBounds(7, 7), bounds(withLate))
    }

    @Test
    fun test13_aBenignHighDensityConversationIsNotRepeatedUnwantedContact() {
        val result = SyntheticTimelines.c().analyse()
        assertTrue(result.of(PatternType.RECURRENCE_AFTER_BOUNDARY).isEmpty())
        assertFalse(repeated(result.single(PatternType.REPEATED_CONTACT)).allMarkedUnwanted)
        result.patterns.forEach { assertEquals(null, PatternExplanation.render(it, ::label).interpretation) }
    }

    @Test
    fun test14_userResumptionEndsTheBoundaryScopeButDoesNotSuppressOtherPatterns() {
        val events =
            listOf(
                msg("synthetic-stop", "10:00:00") { boundary(BoundaryMarker.DO_NOT_CONTACT, "synthetic-t-a", CommunicationStatus.SUPPORTED_BY_SELECTED_EVIDENCE) },
                msg("synthetic-m1", "10:05:00"),
                msg("synthetic-m2", "10:06:00"),
                msg("synthetic-resume", "10:10:00") { boundary(BoundaryMarker.USER_RESUMPTION, "synthetic-t-a", CommunicationStatus.NOT_APPLICABLE) },
                msg("synthetic-m3", "11:00:00") { category(CategoryLabel.VERBAL_ABUSE) },
                msg("synthetic-m4", "11:30:00") { category(CategoryLabel.EXPLICIT_THREAT) },
            )
        val result = caseInput(events).analyse()
        val recurrence = recurrence(result.single(PatternType.RECURRENCE_AFTER_BOUNDARY))
        assertEquals(CountBounds(2, 2), recurrence.afterBoundary)
        assertTrue(recurrence.endedByResumption)
        assertEquals(1, result.of(PatternType.WORDING_TRANSITION).size)
    }

    @Test
    fun test15_aContactWithOnlyAnUnknownCategoryAndNoTextIsCountedButStartsNoTransition() {
        val events =
            listOf(
                msg("synthetic-m1", "10:00:00") { category(CategoryLabel.VERBAL_ABUSE) },
                msg("synthetic-m2", "10:30:00") {
                    category(CategoryLabel.UNKNOWN, CategoryReviewStatus.UNREVIEWED)
                    textStatus = TextStatus.ABSENT
                },
            )
        val result = caseInput(events, view = EvidenceView.CANDIDATE_PREVIEW).analyse()
        assertEquals(CountBounds(2, 2), repeated(result.single(PatternType.REPEATED_CONTACT)).total)
        assertTrue(result.of(PatternType.WORDING_TRANSITION).isEmpty())
    }

    @Test
    fun test16_everyEventRefPointsToAnInputEventAtTheSelectedRevision() {
        for (view in EvidenceView.entries) {
            for ((name, base) in SyntheticTimelines.all(view)) {
                val first = base.events.first { it.eventKind == EventKind.MESSAGE_OBSERVATION && it.direction == Direction.INCOMING }
                val revised = first.copy(revision = 2)
                val invisible = first.copy(revision = 3, availableAt = Timestamp("2026-10-09T00:00:00Z"))
                val input = base.copy(events = base.events + revised + invisible)
                val expected =
                    input.events
                        .filter { it.availableAt.instant <= input.knowledgeCutoff }
                        .groupBy { it.eventId }
                        .mapValues { (_, revisions) -> revisions.maxOf { it.revision } }
                val refs = input.analyse().patterns.flatMap { it.supportingEvents + it.contextEvents }
                assertTrue(refs.isNotEmpty(), name)
                refs.forEach { assertEquals(expected[it.eventId], it.revision, "$name ${it.eventId.value}") }
                assertTrue(refs.any { it.eventId == first.eventId && it.revision == 2 }, name)
            }
        }
    }

    @Test
    fun differentUnresolvedScopesRemainDifferent() {
        val f = SyntheticTimelines.fTwoAlex()
        val scopes = f.analyse().patterns.map { it.actorScope }.toSet()
        assertEquals(2, scopes.size)
        assertNotEquals(scopes.first().key, scopes.last().key)
        assertEquals(CaseId("synthetic-case-f"), f.caseId)
    }
}
