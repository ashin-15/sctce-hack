package org.sakshi.core.vault

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.BoundaryMarker
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.CategoryReviewStatus
import org.sakshi.core.model.DedupStatus
import org.sakshi.core.model.Direction
import org.sakshi.core.model.EventId
import org.sakshi.core.model.ScopeId
import org.sakshi.core.model.Timestamp
import org.sakshi.core.model.UnwantedContact
import org.sakshi.core.temporal.EvidenceView
import org.sakshi.core.temporal.fixtures.SyntheticTimelines

/** Every vault write that can change what the engine computes marks the case's patterns stale in its own transaction. */
class PatternStalenessTest : PatternTestBase() {
    private val a = SyntheticTimelines.a()
    private val b = SyntheticTimelines.b()
    private val f = SyntheticTimelines.f()
    private val actorA = ActorId("synthetic-actor-a")
    private val alex = SenderSelector("Alex", "synthetic-app-1", ScopeId("synthetic-conversation-1"))

    @Test
    fun savingOneEventMarksStale() = runBlocking<Unit> {
        assertWriteMarksStale(a, "synthetic-actor-a") {
            saveOk(EventFixtures.plain("synthetic-extra", caseId = a.caseId.value))
        }
    }

    @Test
    fun savingANewRevisionMarksStale() = runBlocking<Unit> {
        assertWriteMarksStale(a, "synthetic-actor-a") {
            saveOk(next(latest("synthetic-a2")) { it.copy(direction = Direction.OUTGOING) })
        }
    }

    @Test
    fun savingABatchMarksStale() = runBlocking<Unit> {
        assertWriteMarksStale(a, "synthetic-actor-a") {
            val batch = listOf("synthetic-x1", "synthetic-x2").map { EventFixtures.plain(it, caseId = a.caseId.value) }
            assertEquals(2, (store.saveAll(batch) as BatchSaveResult.Saved).count)
        }
    }

    @Test
    fun addingACoverageGapMarksStale() = runBlocking<Unit> {
        assertWriteMarksStale(a, "synthetic-actor-a") {
            store.addCoverageGap(a.caseId, Timestamp("2026-10-05T00:00:00Z"), Timestamp("2026-10-06T00:00:00Z"), "import_selection")
        }
    }

    @Test
    fun renamingAnActorMarksStale() = runBlocking<Unit> {
        assertWriteMarksStale(a, "synthetic-actor-a") { actors.rename(actorA, "synthetic-renamed") }
    }

    @Test
    fun reviewingACategoryMarksStale() = runBlocking<Unit> {
        assertWriteMarksStale(b, "synthetic-actor-b") {
            assertApplied(review.reviewCategory(EventId("synthetic-b1"), 0, CategoryReviewStatus.UNCERTAIN), "synthetic-b1")
        }
    }

    @Test
    fun addingAUserTagMarksStale() = runBlocking<Unit> {
        assertWriteMarksStale(b, "synthetic-actor-b") {
            val reference = latest("synthetic-b1").evidenceReferences.first().referenceId
            assertApplied(review.addUserTag(EventId("synthetic-b1"), CategoryLabel.INTIMIDATION, listOf(reference)), "synthetic-b1")
        }
    }

    @Test
    fun markingWantednessMarksStale() = runBlocking<Unit> {
        assertWriteMarksStale(a, "synthetic-actor-a") {
            assertApplied(review.markWantedness(listOf(EventId("synthetic-a1")), UnwantedContact.USER_MARKED_WANTED), "synthetic-a1")
        }
    }

    @Test
    fun settingDirectionMarksStale() = runBlocking<Unit> {
        assertWriteMarksStale(a, "synthetic-actor-a") {
            assertApplied(review.setDirection(listOf(EventId("synthetic-a1")), Direction.OUTGOING), "synthetic-a1")
        }
    }

    @Test
    fun markingOwnMessagesMarksStale() = runBlocking<Unit> {
        assertWriteMarksStale(f) {
            assertIs<ReviewResult.Applied>(review.markOwnMessages(f.caseId, alex))
        }
    }

    @Test
    fun assigningASenderMarksStale() = runBlocking<Unit> {
        assertWriteMarksStale(f, "synthetic-actor-f") {
            assertIs<ReviewResult.Applied>(review.assignSender(f.caseId, alex, ActorId("synthetic-actor-f")))
        }
    }

    @Test
    fun assigningToANewPersonMarksStale() = runBlocking<Unit> {
        assertWriteMarksStale(f) {
            assertIs<ReviewResult.Applied>(review.assignSenderToNewPerson(f.caseId, alex, "synthetic-person"))
        }
    }

    @Test
    fun unassigningASenderMarksStale() = runBlocking<Unit> {
        assertWriteMarksStale(a, "synthetic-actor-a") {
            assertApplied(review.unassignSender(listOf(EventId("synthetic-a1"))), "synthetic-a1")
        }
    }

    @Test
    fun markingAnEventAsBoundaryMarksStale() = runBlocking<Unit> {
        assertWriteMarksStale(a, "synthetic-actor-a") {
            assertApplied(review.markEventAsBoundary(EventId("synthetic-a1"), BoundaryMarker.LIMITED_CONTACT, actorA), "synthetic-a1")
        }
    }

    @Test
    fun clearingABoundaryMarksStale() = runBlocking<Unit> {
        assertWriteMarksStale(a, "synthetic-actor-a") {
            // A note-based boundary event needs its marker, so mark a contact first and clear that.
            assertApplied(review.markEventAsBoundary(EventId("synthetic-a1"), BoundaryMarker.LIMITED_CONTACT, actorA), "synthetic-a1")
            recompute(a.caseId)
            assertEquals(0, staleCount(a.caseId))
            assertApplied(review.clearBoundary(EventId("synthetic-a1")), "synthetic-a1")
        }
    }

    @Test
    fun settingADuplicateMarksStale() = runBlocking<Unit> {
        assertWriteMarksStale(a, "synthetic-actor-a") {
            assertApplied(review.setDuplicate(EventId("synthetic-a2"), EventId("synthetic-a1"), DedupStatus.POSSIBLE_DUPLICATE), "synthetic-a2")
        }
    }

    @Test
    fun addingABoundaryFromANoteMarksStale() = runBlocking<Unit> {
        assertWriteMarksStale(a, "synthetic-actor-a") {
            val note = evidence.import(
                ImportRequest(
                    a.caseId.value, AcquisitionKind.MANUAL_NOTE, AccessClass.USER_MEDIATED, "synthetic-test", "text/plain",
                    "synthetic-origin", "synthetic-note.txt", null, 1_000_000L,
                ),
                ByteArrayInputStream("synthetic note".toByteArray()),
            )
            assertEquals(0, staleCount(a.caseId))
            val time = latest("synthetic-a0").timestamp
            assertIs<ReviewResult.Applied>(
                review.addBoundaryFromNote(a.caseId, note.id, BoundaryMarker.USER_DISENGAGEMENT, actorA, time, communicated = false),
            )
        }
    }

    @Test
    fun deletingEvidenceThatEventsRestOnMarksTheWholeCaseStaleAndLeavesNoOrphanSupport() = runBlocking<Unit> {
        insertCase(a.caseId.value)
        insertActor(a.caseId.value, "synthetic-actor-a")
        val stored = evidence.import(request(a.caseId.value), ByteArrayInputStream(ByteArray(40)))
        val events = a.events.map {
            if (it.eventId.value == "synthetic-a1") it.copy(evidenceReferences = listOf(EventFixtures.ref("r1", artifact = stored.id))) else it
        }
        assertEquals(events.size, (store.saveAll(events) as BatchSaveResult.Saved).count)
        val before = recompute(a.caseId)
        val usingA1 = before.filter { p -> (p.record.supportingEvents + p.record.contextEvents).any { it.eventId.value == "synthetic-a1" } }
        assertTrue(usingA1.isNotEmpty())

        evidence.delete(stored.id)

        assertNull(store.load(EventId("synthetic-a1"), 1))
        val after = patterns.list(a.caseId, EvidenceView.CONFIRMED_ONLY)
        assertEquals(before.map { it.id }.toSet(), after.map { it.id }.toSet())
        assertTrue(after.all { it.stale }, "the whole case is stale, not only the patterns that used the deleted event")
        assertEquals(0, count("pattern_support WHERE event_id = 'synthetic-a1'"))
        assertEquals(0, count("pattern_support WHERE pattern_id NOT IN (SELECT id FROM pattern)"))
        recompute(a.caseId)
        assertEquals(0, staleCount(a.caseId))
        assertEquals(0, count("pattern_support WHERE event_id = 'synthetic-a1'"))
    }

    @Test
    fun deletingEvidenceWithoutEventsLeavesPatternsCurrent() = runBlocking<Unit> {
        load(a, "synthetic-actor-a")
        recompute(a.caseId)
        val unused = evidence.import(request(a.caseId.value), ByteArrayInputStream(ByteArray(40)))
        evidence.delete(unused.id)
        assertEquals(0, staleCount(a.caseId))
    }

    @Test
    fun refusedAndUnchangedWritesLeavePatternsCurrent() = runBlocking<Unit> {
        load(a, "synthetic-actor-a")
        recompute(a.caseId)

        assertIs<SaveResult.RevisionConflict>(store.save(EventFixtures.plain("synthetic-a1", caseId = a.caseId.value, revision = 5)))
        assertIs<BatchSaveResult.Invalid>(store.saveAll(listOf(EventFixtures.plain("synthetic-a1", caseId = a.caseId.value, revision = 5))))
        assertEquals(ReviewResult.NoChange, review.clearBoundary(EventId("synthetic-a1")))
        assertEquals(ReviewResult.NotFound, review.setDirection(listOf(EventId("synthetic-missing")), Direction.INCOMING))
        actors.rename(actorA, "synthetic-label-synthetic-actor-a")
        patterns.review("synthetic-missing", PatternReviewAction.ACCEPT)

        assertEquals(0, staleCount(a.caseId))
    }
}
