package org.sakshi.core.vault

import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import org.sakshi.core.database.ReviewAction
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.AssociationReview
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.Boundary
import org.sakshi.core.model.BoundaryMarker
import org.sakshi.core.model.BoundaryReviewStatus
import org.sakshi.core.model.CommunicationStatus
import org.sakshi.core.model.ConfirmationScope
import org.sakshi.core.model.ConfirmationStatus
import org.sakshi.core.model.Direction
import org.sakshi.core.model.EventId
import org.sakshi.core.model.EventKind
import org.sakshi.core.model.Locator
import org.sakshi.core.model.Representation
import org.sakshi.core.model.RetentionMode
import org.sakshi.core.model.SourceKind
import org.sakshi.core.model.TimeBasis
import org.sakshi.core.model.TimeBounds
import org.sakshi.core.model.TimePrecision
import org.sakshi.core.model.Timestamp
import org.sakshi.core.model.UnwantedContact
import org.sakshi.core.model.ViolationCode
import org.sakshi.core.temporal.EvidenceView
import org.sakshi.core.temporal.Limitation
import org.sakshi.core.temporal.PatternType
import org.sakshi.core.temporal.TemporalEngine
import org.sakshi.core.temporal.TemporalInput
import org.sakshi.core.temporal.PatternConfig

class ReviewBoundaryTest : ReviewTestBase() {
    private val actor = ActorId(EventFixtures.ACTOR)
    private val time = TimeBounds(
        Timestamp("2026-10-01T09:00:00+05:30"), Timestamp("2026-10-01T09:00:00+05:30"),
        TimeBasis.USER_REPORTED, TimePrecision.MINUTE, null, null, null,
    )

    private suspend fun importNote(acquisitionKind: String): String =
        evidence.import(
            ImportRequest(
                EventFixtures.CASE, acquisitionKind, AccessClass.USER_MEDIATED, "synthetic-test", "text/plain",
                null, null, null, 10_000L,
            ),
            ByteArrayInputStream("synthetic note".toByteArray()),
        ).id

    @Test
    fun markEventAsBoundaryUsesSupportedEvidenceOnlyForOutgoingNonManualEvents() = runBlocking<Unit> {
        standardCase()
        val outgoing = message("synthetic-o1") { direction = Direction.OUTGOING; unwanted() }.also { saveOk(it) }
        val incoming = message("synthetic-i1").also { saveOk(it) }
        val manual = message("synthetic-m1") { direction = Direction.OUTGOING; representation = Representation.MANUAL_STATEMENT }.also { saveOk(it) }

        assertApplied(review.markEventAsBoundary(outgoing.eventId, BoundaryMarker.DO_NOT_CONTACT, actor), "synthetic-o1")
        assertApplied(review.markEventAsBoundary(incoming.eventId, BoundaryMarker.LIMITED_CONTACT, actor), "synthetic-i1")
        assertApplied(review.markEventAsBoundary(manual.eventId, BoundaryMarker.USER_DISENGAGEMENT, actor), "synthetic-m1")

        fun boundary(marker: BoundaryMarker, status: CommunicationStatus, unwanted: UnwantedContact) =
            Boundary(marker, actor, BoundaryReviewStatus.CONFIRMED, status, unwanted)
        assertEquals(
            next(outgoing) { it.copy(boundary = boundary(BoundaryMarker.DO_NOT_CONTACT, CommunicationStatus.SUPPORTED_BY_SELECTED_EVIDENCE, UnwantedContact.USER_MARKED_UNWANTED)) },
            latest("synthetic-o1"),
        )
        assertEquals(CommunicationStatus.USER_REPORTED, latest("synthetic-i1").boundary.communicationStatus)
        assertEquals(CommunicationStatus.USER_REPORTED, latest("synthetic-m1").boundary.communicationStatus)
        assertEquals(EventKind.MESSAGE_OBSERVATION, latest("synthetic-o1").eventKind)
        assertEquals(outgoing, store.load(outgoing.eventId, 1))
        assertEquals(ReviewAction.ACCEPT, review.decisions("synthetic-o1").single().action)
        assertEquals(ReviewResult.NoChange, review.markEventAsBoundary(outgoing.eventId, BoundaryMarker.DO_NOT_CONTACT, actor))
        val audit = lastAudit("review.boundary")
        assertEquals(true, audit.contains("\"marker\":\"user_disengagement\""))
    }

    @Test
    fun markEventAsBoundaryRefusesBadInputs() = runBlocking<Unit> {
        standardCase()
        val event = message("synthetic-o1") { direction = Direction.OUTGOING }.also { saveOk(it) }

        listOf(BoundaryMarker.NONE, BoundaryMarker.UNKNOWN).forEach {
            val result = assertWritesNothing { review.markEventAsBoundary(event.eventId, it, actor) }
            assertEquals(ReviewProblem.VALUE_NOT_ALLOWED, assertIs<ReviewResult.Invalid>(result).problem)
        }
        assertEquals(ReviewResult.NotFound, assertWritesNothing { review.markEventAsBoundary(EventId("synthetic-none"), BoundaryMarker.DO_NOT_CONTACT, actor) })
        val ghost = assertWritesNothing { review.markEventAsBoundary(event.eventId, BoundaryMarker.DO_NOT_CONTACT, ActorId("synthetic-ghost")) }
        assertEquals(ViolationCode.ACTOR_UNKNOWN, assertIs<ReviewResult.Invalid>(ghost).violations.single().code)
    }

    @Test
    fun boundaryFromNoteIsANewManualEventAndNeverSupportedByEvidence() = runBlocking<Unit> {
        standardCase()
        val note = importNote(AcquisitionKind.MANUAL_NOTE)
        val sha = db.evidenceDao().get(note)!!.sha256

        val reported = review.addBoundaryFromNote(caseId, note, BoundaryMarker.USER_DISENGAGEMENT, actor, time, communicated = true)
        val silent = review.addBoundaryFromNote(caseId, note, BoundaryMarker.DO_NOT_CONTACT, actor, time, communicated = false)

        val reportedId = assertIs<ReviewResult.Applied>(reported).changedEventIds.single().value
        val silentId = assertIs<ReviewResult.Applied>(silent).changedEventIds.single().value
        val event = latest(reportedId)
        assertEquals(1, event.revision)
        assertEquals(EventKind.USER_BOUNDARY, event.eventKind)
        assertEquals(Direction.UNKNOWN, event.direction)
        assertEquals(SourceKind.MANUAL_ENTRY, event.source.kind)
        val reference = event.evidenceReferences.single()
        assertEquals(note, reference.artifactId.value)
        assertEquals(Representation.MANUAL_STATEMENT, reference.representation)
        assertEquals(Locator.WholeArtifact, reference.locator)
        assertEquals(sha, reference.sha256)
        assertEquals(ConfirmationStatus.CONFIRMED, event.userConfirmation.status)
        assertEquals(ConfirmationScope.PRESERVATION_AND_SELECTED_ANNOTATIONS, event.userConfirmation.scope)
        assertEquals(RetentionMode.CONFIRMED_VAULT, event.retention.mode)
        assertEquals(time, event.timestamp)
        assertEquals(null, event.sender.actorId)
        assertEquals(Boundary(BoundaryMarker.USER_DISENGAGEMENT, actor, BoundaryReviewStatus.CONFIRMED, CommunicationStatus.USER_REPORTED, UnwantedContact.NOT_APPLICABLE), event.boundary)
        assertEquals(CommunicationStatus.NOT_COMMUNICATED, latest(silentId).boundary.communicationStatus)
        assertEquals(ReviewAction.ACCEPT, review.decisions(reportedId).single().action)
        assertEquals(false, lastAudit("review.boundary").contains("synthetic note"))
    }

    private suspend fun limitationsAfterNote(case: String, communicated: Boolean): Set<Limitation> {
        insertCase(case)
        val own = "synthetic-actor-$case"
        insertActor(case, own)
        val note = evidence.import(
            ImportRequest(case, AcquisitionKind.MANUAL_NOTE, AccessClass.USER_MEDIATED, "synthetic-test", "text/plain", null, null, null, 10_000L),
            ByteArrayInputStream("synthetic note".toByteArray()),
        ).id
        val contacts = (1..4).map { index ->
            message("synthetic-$case-c$index") {
                caseId = case
                at("2026-10-01T${10 + index}:00:00+05:30")
                actor = own
                associationReview = AssociationReview.CONFIRMED
                unwanted()
            }
        }
        store.saveAll(contacts)
        val owner = CaseId(case)
        assertIs<ReviewResult.Applied>(review.addBoundaryFromNote(owner, note, BoundaryMarker.DO_NOT_CONTACT, ActorId(own), time, communicated))
        val cutoff = Instant.parse("2026-10-03T00:00:00Z")
        val input = TemporalInput(
            owner, store.loadLatest(owner, cutoff), emptyList(), cutoff, EvidenceView.CONFIRMED_ONLY,
            PatternConfig(zone = ZoneOffset.ofHoursMinutes(5, 30)),
        )
        return TemporalEngine.analyse(input).patterns.single { it.type == PatternType.RECURRENCE_AFTER_BOUNDARY }.limitations
    }

    @Test
    fun noteBoundaryGivesTheMatchingPatternLimitation() = runBlocking<Unit> {
        val reported = limitationsAfterNote("synthetic-case-r", communicated = true)
        val silent = limitationsAfterNote("synthetic-case-s", communicated = false)

        assertEquals(true, Limitation.BOUNDARY_USER_REPORTED in reported)
        assertEquals(false, Limitation.BOUNDARY_NOT_COMMUNICATED in reported)
        assertEquals(true, Limitation.BOUNDARY_NOT_COMMUNICATED in silent)
        assertEquals(false, Limitation.BOUNDARY_USER_REPORTED in silent)
    }

    @Test
    fun noteBoundaryRefusesBadEvidenceAndMarkers() = runBlocking<Unit> {
        standardCase()
        val stream = importNote(AcquisitionKind.SHARED_STREAM)
        val note = importNote(AcquisitionKind.MANUAL_NOTE)

        val wrong = assertWritesNothing { review.addBoundaryFromNote(caseId, stream, BoundaryMarker.DO_NOT_CONTACT, actor, time, true) }
        assertEquals(ReviewProblem.WRONG_EVIDENCE_KIND, assertIs<ReviewResult.Invalid>(wrong).problem)
        assertEquals(ReviewResult.NotFound, assertWritesNothing { review.addBoundaryFromNote(caseId, "synthetic-none", BoundaryMarker.DO_NOT_CONTACT, actor, time, true) })
        insertCase("synthetic-case-2")
        assertEquals(ReviewResult.NotFound, assertWritesNothing { review.addBoundaryFromNote(CaseId2, note, BoundaryMarker.DO_NOT_CONTACT, actor, time, true) })
        assertIs<ReviewResult.Invalid>(assertWritesNothing { review.addBoundaryFromNote(caseId, note, BoundaryMarker.NONE, actor, time, true) })
        assertIs<ReviewResult.Invalid>(assertWritesNothing { review.addBoundaryFromNote(caseId, note, BoundaryMarker.DO_NOT_CONTACT, ActorId("synthetic-ghost"), time, true) })
    }

    private companion object {
        val CaseId2: org.sakshi.core.model.CaseId = CaseId("synthetic-case-2")
    }
}
