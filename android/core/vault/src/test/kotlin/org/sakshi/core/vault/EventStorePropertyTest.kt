package org.sakshi.core.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.EventSchemaAdapter

class EventStorePropertyTest : EventStoreTestBase() {
    @Test
    fun twoHundredGeneratedEventsRoundTripExactlyAndKeepTheirSchemaJson() = runBlocking<Unit> {
        standardCase()
        val generator = RandomEvents(seed = 20_261_002L, caseId = EventFixtures.CASE, actorIds = listOf(EventFixtures.ACTOR, EventFixtures.OTHER_ACTOR))
        val events = List(200) { generator.next(it) }

        for (event in events) assertRoundTrip(event)

        val all = store.loadAll(CaseId(EventFixtures.CASE))
        assertEquals(events.sortedBy { it.eventId.value }, all)
        assertEquals(
            events.map { EventSchemaAdapter.toJsonElement(it) }.toSet(),
            all.map { EventSchemaAdapter.toJsonElement(it) }.toSet(),
        )
    }

    @Test
    fun generatorCoversEveryBoundaryMarkerRetentionModeAndLocatorKind() {
        val generator = RandomEvents(seed = 20_261_002L, caseId = EventFixtures.CASE, actorIds = listOf(EventFixtures.ACTOR))
        val events = List(200) { generator.next(it) }
        assertEquals(org.sakshi.core.model.BoundaryMarker.entries.toSet(), events.map { it.boundary.marker }.toSet())
        assertEquals(org.sakshi.core.model.RetentionMode.entries.toSet(), events.map { it.retention.mode }.toSet())
        assertEquals(4, events.flatMap { e -> e.evidenceReferences.map { it.locator::class } }.toSet().size)
        assertEquals(org.sakshi.core.model.EventKind.entries.toSet(), events.map { it.eventKind }.toSet())
    }
}
