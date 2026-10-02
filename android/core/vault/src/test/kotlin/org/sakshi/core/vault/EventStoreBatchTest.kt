package org.sakshi.core.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.sakshi.core.database.CaseEntity
import org.sakshi.core.database.CaseStatus
import org.sakshi.core.database.SakshiDatabaseFactory
import org.sakshi.core.model.AssociationReview
import org.sakshi.core.model.IdentityBasis
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventId
import org.sakshi.core.model.ViolationCode

class EventStoreBatchTest : EventStoreTestBase() {
    private val caseId = CaseId(EventFixtures.CASE)
    private val actorIds = listOf(EventFixtures.ACTOR, EventFixtures.OTHER_ACTOR)

    private fun generated(count: Int): List<Event> {
        val generator = RandomEvents(seed = 77L, caseId = EventFixtures.CASE, actorIds = actorIds)
        return List(count) { generator.next(it) }
    }

    @Test
    fun fiveHundredEventsAreSavedAtomicallyWithOneAuditRow() = runBlocking<Unit> {
        standardCase()
        val events = generated(500)
        val auditBefore = audit.count()

        assertEquals(BatchSaveResult.Saved(500), store.saveAll(events))

        assertEquals(events.sortedBy { it.eventId.value }, store.loadAll(caseId))
        assertEquals(auditBefore + 1, audit.count())
        val row = recording.calls.single { it.startsWith("events.saved|") }
        assertEquals(
            "events.saved|event|synthetic-random-0|{\"case_id\":\"${EventFixtures.CASE}\",\"count\":500," +
                "\"first_event_id\":\"synthetic-random-0\",\"last_event_id\":\"synthetic-random-499\"}",
            row,
        )
        assertEquals(0, recording.calls.count { it.startsWith("event.saved|") })
        assertIs<AuditVerification.Valid>(audit.verify())
    }

    @Test
    fun oneInvalidEventInTheMiddleWritesNothing() = runBlocking<Unit> {
        standardCase()
        val events = generated(20).toMutableList()
        events[18] = events[18].copy(sender = events[18].sender.copy(actorId = ActorId("synthetic-ghost")))
        events[19] = events[19].copy(revision = 3)
        val before = rowCounts()

        val result = assertIs<BatchSaveResult.Invalid>(store.saveAll(events))

        assertEquals(listOf(18, 19), result.failures.map { it.index })
        assertEquals(ViolationCode.ACTOR_UNKNOWN, result.failures[0].violations.single().code)
        assertEquals(BatchProblem.REVISION_CONFLICT, result.failures[1].problem)
        assertEquals(1, result.failures[1].expectedRevision)
        assertEquals(before, rowCounts())
    }

    @Test
    fun referencesInsideTheBatchValidateAndLaterOnesDoNot() = runBlocking<Unit> {
        standardCase()
        val first = EventFixtures.plain("synthetic-first")
        val second = EventFixtures.sparse("synthetic-second", "synthetic-first")
        val third = EventFixtures.maximal("synthetic-third", "synthetic-first", "synthetic-second")
        assertEquals(BatchSaveResult.Saved(3), store.saveAll(listOf(first, second, third)))
        assertEquals(third, store.load(third.eventId, 1))

        val forward = assertIs<BatchSaveResult.Invalid>(
            store.saveAll(listOf(EventFixtures.sparse("synthetic-b", "synthetic-a"), EventFixtures.plain("synthetic-a"))),
        )
        assertEquals(listOf(0), forward.failures.map { it.index })
        assertEquals(ViolationCode.CANONICAL_UNKNOWN, forward.failures.single().violations.single().code)
    }

    @Test
    fun revisionsOfOneEventAreSequencedInsideAndAcrossBatches() = runBlocking<Unit> {
        standardCase()
        val r1 = EventFixtures.plain("synthetic-e", at = "2026-10-01T09:00:00Z")
        val r2 = r1.copy(revision = 2)
        val r3 = r1.copy(revision = 3)
        assertEquals(BatchSaveResult.Saved(2), store.saveAll(listOf(r1, r2)))
        assertEquals(BatchSaveResult.Saved(1), store.saveAll(listOf(r3)))
        assertEquals(listOf(r1, r2, r3), store.loadAll(caseId).filter { it.eventId == EventId("synthetic-e") })

        val before = rowCounts()
        val outOfOrder = assertIs<BatchSaveResult.Invalid>(
            store.saveAll(listOf(r1.copy(eventId = EventId("synthetic-f"), revision = 2), r1.copy(eventId = EventId("synthetic-f")))),
        )
        assertEquals(listOf(0), outOfOrder.failures.map { it.index })
        assertEquals(before, rowCounts())
    }

    @Test
    fun mixedCasesAndUnknownCaseAreRefused() = runBlocking<Unit> {
        standardCase()
        insertCase("synthetic-case-2")
        val before = rowCounts()
        val mixed = assertIs<BatchSaveResult.Invalid>(
            store.saveAll(listOf(EventFixtures.plain("synthetic-a"), EventFixtures.plain("synthetic-b", caseId = "synthetic-case-2"))),
        )
        assertEquals(listOf(BatchProblem.MIXED_CASE), mixed.failures.map { it.problem })
        assertEquals(1, mixed.failures.single().index)
        assertEquals(BatchSaveResult.UnknownCase, store.saveAll(listOf(EventFixtures.plain("synthetic-c", caseId = "synthetic-missing"))))
        assertEquals(before, rowCounts())
        assertEquals(BatchSaveResult.Saved(0), store.saveAll(emptyList()))
    }

    @Test
    fun savingAsABatchGivesTheSameStoredEventsAsSavingOneByOne() = runBlocking<Unit> {
        standardCase()
        val events = generated(120)
        assertEquals(BatchSaveResult.Saved(120), store.saveAll(events))

        val otherDb = SakshiDatabaseFactory.openInMemoryForTests(context)
        try {
            val otherStore = EventStore(otherDb, AuditLog(otherDb, clock), clock, ids, Dispatchers.IO)
            val otherActors = ActorRegistry(otherDb, AuditLog(otherDb, clock), ids, Dispatchers.IO)
            otherDb.caseDao().insert(CaseEntity(EventFixtures.CASE, "synthetic-title", "2026-10-02T10:00:00Z", CaseStatus.ACTIVE))
            actorIds.forEach {
                otherActors.create(caseId, "synthetic-label", IdentityBasis.USER_ASSERTED, AssociationReview.CONFIRMED, ActorId(it))
            }
            events.forEach { assertEquals(SaveResult.Saved(it.eventId, 1), otherStore.save(it)) }

            assertEquals(otherStore.loadAll(caseId), store.loadAll(caseId))
            assertEquals(events.sortedBy { it.eventId.value }, store.loadAll(caseId))
        } finally {
            otherDb.close()
        }
    }
}
