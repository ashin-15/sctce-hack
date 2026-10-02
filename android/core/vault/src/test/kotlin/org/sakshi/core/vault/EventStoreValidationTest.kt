package org.sakshi.core.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.ArtifactId
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventId
import org.sakshi.core.model.EventRelationship
import org.sakshi.core.model.Locator
import org.sakshi.core.model.RelationshipBasis
import org.sakshi.core.model.RelationshipReviewStatus
import org.sakshi.core.model.RelationshipType
import org.sakshi.core.model.ViolationCode

class EventStoreValidationTest : EventStoreTestBase() {
    private suspend fun assertInvalid(event: Event, code: ViolationCode, lengths: (ArtifactId) -> Int? = { null }) {
        val before = rowCounts()
        val callsBefore = recording.calls.size
        val result = assertIs<SaveResult.Invalid>(store.save(event, lengths))
        assertEquals(true, result.violations.any { it.code == code }, "expected $code in ${result.violations}")
        assertEquals(before, rowCounts())
        assertEquals(callsBefore, recording.calls.size)
    }

    @Test
    fun unknownCaseIsRefusedAndWritesNothing() = runBlocking<Unit> {
        standardCase()
        val before = rowCounts()
        assertEquals(SaveResult.UnknownCase, store.save(EventFixtures.plain("synthetic-x", caseId = "synthetic-missing")))
        assertEquals(before, rowCounts())
    }

    @Test
    fun unknownActorIsInvalid() = runBlocking<Unit> {
        standardCase()
        val event = EventFixtures.plain("synthetic-x")
        assertInvalid(event.copy(sender = event.sender.copy(actorId = ActorId("synthetic-ghost"))), ViolationCode.ACTOR_UNKNOWN)
    }

    @Test
    fun actorOfAnotherCaseIsInvalid() = runBlocking<Unit> {
        standardCase()
        insertCase("synthetic-case-2")
        insertActor("synthetic-case-2", "synthetic-actor-foreign")
        val event = EventFixtures.plain("synthetic-x")
        assertInvalid(event.copy(sender = event.sender.copy(actorId = ActorId("synthetic-actor-foreign"))), ViolationCode.ACTOR_CROSS_CASE)
    }

    @Test
    fun relationshipToAnUnknownEventIsInvalid() = runBlocking<Unit> {
        standardCase()
        val link = EventRelationship(
            EventId("synthetic-ghost"), RelationshipType.REPLY_TO, RelationshipBasis.RULE, EventFixtures.none(),
            RelationshipReviewStatus.UNREVIEWED,
        )
        assertInvalid(
            EventFixtures.plain("synthetic-x").copy(relationshipToPreviousEvents = listOf(link)),
            ViolationCode.RELATIONSHIP_TARGET_UNKNOWN,
        )
    }

    @Test
    fun textLocatorBeyondTheArtifactLengthIsInvalid() = runBlocking<Unit> {
        standardCase()
        val event = EventFixtures.plain("synthetic-x").copy(
            evidenceReferences = listOf(EventFixtures.ref("r1", artifact = "synthetic-text", locator = Locator.Text(0, 50))),
        )
        assertInvalid(event, ViolationCode.TEXT_LOCATOR_OUT_OF_RANGE) { if (it.value == "synthetic-text") 10 else null }
        saveOk(event.copy(eventId = EventId("synthetic-y")))
    }

    @Test
    fun unknownCanonicalEventIsInvalid() = runBlocking<Unit> {
        standardCase()
        val event = EventFixtures.sparse("synthetic-x", "synthetic-ghost")
        assertInvalid(event, ViolationCode.CANONICAL_UNKNOWN)
    }

    @Test
    fun canonicalCycleThroughStoredEventsIsInvalid() = runBlocking<Unit> {
        standardCase()
        saveOk(EventFixtures.plain("synthetic-a"))
        saveOk(EventFixtures.sparse("synthetic-b", "synthetic-a"))
        // Revision 2 of a now points at b, which points at a.
        assertInvalid(EventFixtures.sparse("synthetic-a", "synthetic-b").copy(revision = 2), ViolationCode.CANONICAL_CYCLE)
    }
}
