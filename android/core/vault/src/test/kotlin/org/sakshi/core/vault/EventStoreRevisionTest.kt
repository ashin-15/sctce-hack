package org.sakshi.core.vault

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.sakshi.core.model.BoundaryMarker
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.Direction
import org.sakshi.core.model.EventId
import org.sakshi.core.model.EventRelationship
import org.sakshi.core.model.RelationshipBasis
import org.sakshi.core.model.RelationshipReviewStatus
import org.sakshi.core.model.RelationshipType

class EventStoreRevisionTest : EventStoreTestBase() {
    private val caseId = CaseId(EventFixtures.CASE)

    private fun revisions(id: String) = listOf(
        EventFixtures.plain(id, at = "2026-10-01T09:00:00Z"),
        EventFixtures.maximal(id, "synthetic-target", "synthetic-target").copy(
            revision = 2,
            availableAt = EventFixtures.ts("2026-10-01T10:00:00Z"),
        ),
        EventFixtures.plain(id, revision = 3, at = "2026-10-01T09:00:00Z").copy(
            availableAt = EventFixtures.ts("2026-10-01T11:00:00Z"),
            direction = Direction.OUTGOING,
        ),
    )

    @Test
    fun everyRevisionLoadsBackAsItsOwnContent() = runBlocking<Unit> {
        standardCase()
        saveOk(EventFixtures.plain("synthetic-target"))
        val all = revisions("synthetic-event")
        all.forEach { saveOk(it) }

        all.forEach { assertEquals(it, store.load(it.eventId, it.revision)) }
        assertNull(store.load(EventId("synthetic-event"), 4))
        assertEquals(all.sortedBy { it.revision }, store.loadAll(caseId).filter { it.eventId.value == "synthetic-event" })
        // Revision 2 had a boundary, categories and links; revision 3 must not inherit them.
        val third = store.load(EventId("synthetic-event"), 3)!!
        assertEquals(BoundaryMarker.NONE, third.boundary.marker)
        assertEquals(emptyList(), third.categories)
        assertEquals(emptyList(), third.relationshipToPreviousEvents)
    }

    @Test
    fun loadLatestAppliesTheAvailabilityCutoff() = runBlocking<Unit> {
        standardCase()
        saveOk(EventFixtures.plain("synthetic-target", at = "2026-10-01T09:00:00Z"))
        revisions("synthetic-event").forEach { saveOk(it) }

        fun latest(cutoff: String) =
            runBlocking { store.loadLatest(caseId, Instant.parse(cutoff)).associate { it.eventId.value to it.revision } }

        // The target is available at 09:00; the event's revisions at 09:00, 10:00 and 11:00.
        assertEquals(emptyMap(), latest("2026-10-01T05:00:00Z"))
        assertEquals(mapOf("synthetic-event" to 1, "synthetic-target" to 1), latest("2026-10-01T09:00:00Z"))
        assertEquals(1, latest("2026-10-01T09:59:59Z").getValue("synthetic-event"))
        assertEquals(2, latest("2026-10-01T10:00:00Z").getValue("synthetic-event"))
        assertEquals(2, latest("2026-10-01T10:59:59.999Z").getValue("synthetic-event"))
        assertEquals(3, latest("2026-10-01T11:00:00Z").getValue("synthetic-event"))
        assertEquals(3, latest("2030-01-01T00:00:00Z").getValue("synthetic-event"))
    }

    @Test
    fun outOfOrderOrRepeatedRevisionsAreRefused() = runBlocking<Unit> {
        standardCase()
        saveOk(EventFixtures.plain("synthetic-target"))
        val (first, second, third) = revisions("synthetic-event")
        saveOk(first)
        val before = rowCounts()

        assertEquals(SaveResult.RevisionConflict(expected = 2), store.save(third))
        assertEquals(SaveResult.RevisionConflict(expected = 2), store.save(first))
        assertEquals(before, rowCounts())

        saveOk(second)
        saveOk(third)
        assertEquals(SaveResult.RevisionConflict(expected = 4), store.save(EventFixtures.plain("synthetic-event", revision = 5)))
        assertEquals(SaveResult.RevisionConflict(expected = 1), store.save(EventFixtures.plain("synthetic-new", revision = 2)))
    }

    @Test
    fun anEventIdOfAnotherCaseIsAConflict() = runBlocking<Unit> {
        standardCase()
        insertCase("synthetic-case-2")
        saveOk(EventFixtures.plain("synthetic-shared-id"))
        val before = rowCounts()
        assertEquals(
            SaveResult.RevisionConflict(expected = 2),
            store.save(EventFixtures.plain("synthetic-shared-id", caseId = "synthetic-case-2", revision = 2)),
        )
        assertEquals(before, rowCounts())
    }

    @Test
    fun relationshipsOfDifferentRevisionsStaySeparate() = runBlocking<Unit> {
        standardCase()
        saveOk(EventFixtures.plain("synthetic-a"))
        saveOk(EventFixtures.plain("synthetic-b"))
        fun link(target: String) = EventRelationship(
            EventId(target), RelationshipType.REPLY_TO, RelationshipBasis.RULE, EventFixtures.none(),
            RelationshipReviewStatus.UNREVIEWED,
        )
        val one = EventFixtures.plain("synthetic-c").copy(relationshipToPreviousEvents = listOf(link("synthetic-a"), link("synthetic-b")))
        val two = EventFixtures.plain("synthetic-c", revision = 2).copy(relationshipToPreviousEvents = listOf(link("synthetic-b")))
        saveOk(one)
        saveOk(two)
        assertEquals(one, store.load(EventId("synthetic-c"), 1))
        assertEquals(two, store.load(EventId("synthetic-c"), 2))
    }
}
