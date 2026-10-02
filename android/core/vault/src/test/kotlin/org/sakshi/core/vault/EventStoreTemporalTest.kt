package org.sakshi.core.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.sakshi.core.database.CaseEntity
import org.sakshi.core.database.CaseStatus
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.AssociationReview
import org.sakshi.core.model.Event
import org.sakshi.core.model.IdentityBasis
import org.sakshi.core.temporal.TemporalEngine
import org.sakshi.core.temporal.TemporalInput
import org.sakshi.core.temporal.fixtures.SyntheticTimelines

class EventStoreTemporalTest : EventStoreTestBase() {
    private suspend fun storeAll(input: TemporalInput) {
        val caseId = input.caseId.value
        db.caseDao().insert(CaseEntity(caseId, "synthetic-title", "2026-10-02T10:00:00Z", CaseStatus.ACTIVE))
        val actorIds = input.events.flatMap { listOfNotNull(it.sender.actorId, it.boundary.actorId) }.toSet()
        actorIds.forEach { actors.create(input.caseId, "synthetic-label", IdentityBasis.USER_ASSERTED, AssociationReview.CONFIRMED, it) }
        var pending: List<Event> = input.events.sortedBy { it.revision }
        var rounds = 0
        while (pending.isNotEmpty()) {
            check(rounds++ < pending.size + 1) { "Unresolvable references in fixture" }
            val stillPending = mutableListOf<Event>()
            for (event in pending) {
                when (val result = store.save(event)) {
                    is SaveResult.Saved -> Unit
                    is SaveResult.Invalid, is SaveResult.RevisionConflict -> stillPending += event
                    else -> error("Unexpected $result")
                }
            }
            pending = stillPending
        }
    }

    @Test
    fun storedTimelinesGiveTheSamePatternsAsTheOriginalEvents() = runBlocking<Unit> {
        val timelines = listOf(
            SyntheticTimelines.a(), SyntheticTimelines.b(), SyntheticTimelines.c(),
            SyntheticTimelines.d(), SyntheticTimelines.e(), SyntheticTimelines.f(),
        )
        var checked = 0
        for (input in timelines) {
            storeAll(input)
            val everything = store.loadAll(input.caseId)
            assertEquals(input.events.sortedWith(compareBy({ it.eventId.value }, { it.revision })), everything)
            val original = TemporalEngine.analyse(input)
            assertEquals(original, TemporalEngine.analyse(input.copy(events = everything)))
            val latest = store.loadLatest(input.caseId, input.knowledgeCutoff)
            assertEquals(original, TemporalEngine.analyse(input.copy(events = latest)))
            checked += input.events.size
        }
        assertTrue(checked > 0)
    }
}
