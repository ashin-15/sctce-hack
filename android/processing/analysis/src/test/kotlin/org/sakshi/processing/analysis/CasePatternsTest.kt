package org.sakshi.processing.analysis

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.AssociationReview
import org.sakshi.core.model.BoundaryMarker
import org.sakshi.core.model.BoundaryReviewStatus
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.CommunicationStatus
import org.sakshi.core.model.Direction
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventId
import org.sakshi.core.model.EventKind
import org.sakshi.core.model.IdentityBasis
import org.sakshi.core.model.Representation
import org.sakshi.core.model.TimeBasis
import org.sakshi.core.model.TimeBounds
import org.sakshi.core.model.Timestamp
import org.sakshi.core.temporal.EvidenceView
import org.sakshi.core.temporal.PatternType
import org.sakshi.processing.text.DateOrder

class CasePatternsTest : AnalysisTestBase() {
    private val kolkata: ZoneId = ZoneId.of("Asia/Kolkata")
    private val patterns: CasePatterns get() = CasePatterns(vault, clock)

    private fun compute(): CasePatternView = runBlocking {
        patterns.compute(CaseId(caseId), EvidenceView.CONFIRMED_ONLY, kolkata)
    }

    @Test
    fun noEventsGivesAnEmptyResult() {
        val view = compute()
        assertTrue(view.result.patterns.isEmpty())
        assertTrue(view.result.timeline.isEmpty())
        assertTrue(view.explanations.isEmpty())
    }

    @Test
    fun gapsArePassedThrough() = runBlocking<Unit> {
        val start = Timestamp("2026-09-01T00:00:00Z")
        val end = Timestamp("2026-09-02T00:00:00Z")
        vault.events.addCoverageGap(CaseId(caseId), start, end, "import_selection")
        val gaps = compute().result.gaps
        assertEquals(1, gaps.size)
        assertEquals(start.instant, gaps.single().start)
        assertEquals(end.instant, gaps.single().end)
    }

    @Test
    fun unconfirmedSenderIsDescribedByItsSourceLabel() = runBlocking<Unit> {
        analysis.analyse(importText(SyntheticExports.EIGHT_MESSAGES), ExportOptions(DateOrder.DAY_MONTH, kolkata, SyntheticExports.OWNER))
        val view = compute()
        val repeated = view.result.patterns.filter { it.type == PatternType.REPEATED_CONTACT }
        assertTrue(repeated.isNotEmpty())
        assertTrue(view.scopeLabels.values.contains(SyntheticExports.OTHER))
        val text = view.explanations.getValue(repeated.first().patternKey).observed
        assertTrue(text.contains(SyntheticExports.OTHER), text)
    }

    @Test
    fun confirmedSenderAndBoundaryGiveRecurrenceWithTheActorLabel() = runBlocking<Unit> {
        analysis.analyse(importText(SyntheticExports.EIGHT_MESSAGES), ExportOptions(DateOrder.DAY_MONTH, kolkata, SyntheticExports.OWNER))
        val actor = vault.actors.create(CaseId(caseId), "synthetic-person", IdentityBasis.USER_ASSERTED, AssociationReview.CONFIRMED)
        val stored = events()
        for (event in stored.filter { it.sender.displayLabel == SyntheticExports.OTHER }) {
            val confirmed = event.copy(
                revision = event.revision + 1,
                sender = event.sender.copy(
                    actorId = actor,
                    identityBasis = IdentityBasis.USER_ASSERTED,
                    associationReview = AssociationReview.CONFIRMED,
                ),
            )
            assertEquals(1, save(confirmed))
        }
        save(boundaryEvent(stored.first(), actor))

        val view = compute()
        val recurrence = view.result.patterns.single { it.type == PatternType.RECURRENCE_AFTER_BOUNDARY }
        val observed = view.explanations.getValue(recurrence.patternKey).observed
        assertTrue(observed.contains("synthetic-person"), observed)
        assertTrue(observed.contains("the stop request you reported"), observed)
        assertEquals("synthetic-person", view.scopeLabels.getValue(recurrence.actorScope.key))
        assertIs<org.sakshi.core.temporal.ActorScope.Confirmed>(recurrence.actorScope)
    }

    @Test
    fun explanationTimesAreWrittenInTheGivenZone() = runBlocking<Unit> {
        analysis.analyse(importText(SyntheticExports.EIGHT_MESSAGES), ExportOptions(DateOrder.DAY_MONTH, kolkata, SyntheticExports.OWNER))
        val observed = compute().explanations.values.map { it.observed }.first { "between" in it }
        assertTrue("2026-09-25 08:15 +05:30" in observed || "+05:30" in observed, observed)
        assertTrue(!observed.contains("T0") && !observed.contains("Z "), observed)
        val utc = runBlocking { patterns.compute(CaseId(caseId), EvidenceView.CONFIRMED_ONLY, java.time.ZoneOffset.UTC) }
        assertTrue(utc.explanations.values.any { "+00:00" in it.observed })
    }

    @Test
    fun supportingEventsCarryTimesAndAShortPreview() = runBlocking<Unit> {
        val long = "synthetic ".repeat(40) + "\uD83D\uDE00"
        val text = SyntheticExports.EIGHT_MESSAGES + "27/09/2026, 10:00 - ${SyntheticExports.OTHER}: $long\n"
        analysis.analyse(importText(text), ExportOptions(DateOrder.DAY_MONTH, kolkata, SyntheticExports.OWNER))
        val plain = compute()
        assertTrue(plain.supportingEvents.isEmpty())

        val view = patterns.compute(CaseId(caseId), EvidenceView.CONFIRMED_ONLY, kolkata, withSupportingEvents = true)

        assertEquals(plain.explanations, view.explanations)
        val wanted = view.result.patterns.flatMap { r -> r.supportingEvents.map { it.eventId } }.toSet()
        assertEquals(wanted, view.supportingEvents.keys)
        assertTrue(wanted.isNotEmpty())
        val byId = events().associateBy { it.eventId }
        val texts = EventText(vault)
        for ((id, support) in view.supportingEvents) {
            val event = byId.getValue(id)
            assertEquals(event.revision, support.revision)
            assertEquals(event.timestamp.earliest?.instant, support.earliest)
            assertEquals(event.timestamp.latest?.instant, support.latest)
            val body = texts.bodyOf(event)
            val expected = body?.let { if (it.codePointCount(0, it.length) > 120) it.substring(0, it.offsetByCodePoints(0, 120)) else it }
            assertEquals(expected, support.bodyPreview)
        }
        assertTrue(view.supportingEvents.values.any { (it.bodyPreview?.codePointCount(0, it.bodyPreview.length) ?: 0) == 120 })
    }

    @Test
    fun resultIsDeterministic() = runBlocking<Unit> {
        analysis.analyse(importText(SyntheticExports.EIGHT_MESSAGES), ExportOptions(DateOrder.DAY_MONTH, kolkata, SyntheticExports.OWNER))
        assertEquals(compute(), compute())
    }

    private fun save(event: Event): Int = runBlocking {
        val result = vault.events.save(event) { id -> if (id.value == event.evidenceReferences.first().artifactId.value) LENGTH else null }
        assertIs<org.sakshi.core.vault.SaveResult.Saved>(result)
        1
    }

    private fun boundaryEvent(base: Event, actor: ActorId): Event = base.copy(
        eventId = EventId("synthetic-boundary"),
        revision = 1,
        eventKind = EventKind.USER_BOUNDARY,
        direction = Direction.UNKNOWN,
        timestamp = TimeBounds(
            Timestamp("2026-09-24T22:00:00Z"),
            Timestamp("2026-09-24T22:00:00Z"),
            TimeBasis.USER_REPORTED,
            org.sakshi.core.model.TimePrecision.MINUTE,
            null,
            null,
            null,
        ),
        sender = base.sender.copy(actorId = null, displayLabel = "You", identityBasis = IdentityBasis.UNKNOWN, associationReview = AssociationReview.UNKNOWN),
        categories = emptyList(),
        severity = CueReferences.severity(emptyList()),
        evidenceReferences = base.evidenceReferences.take(1).map { it.copy(representation = Representation.MANUAL_STATEMENT) },
        boundary = base.boundary.copy(
            marker = BoundaryMarker.DO_NOT_CONTACT,
            actorId = actor,
            reviewStatus = BoundaryReviewStatus.CONFIRMED,
            communicationStatus = CommunicationStatus.USER_REPORTED,
        ),
    )

    private companion object {
        const val LENGTH: Int = 100_000
    }
}
