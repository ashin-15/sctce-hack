package org.sakshi.core.vault

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.AssociationReview
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.Direction
import org.sakshi.core.model.Event
import org.sakshi.core.model.IdentityBasis

/** Events stored in the real SQLCipher vault under a Keystore-wrapped passphrase. All data is synthetic. */
@RunWith(AndroidJUnit4::class)
class EventStoreDeviceTest : DeviceTestBase() {
    private val everything = Instant.parse("2030-01-01T00:00:00Z")

    private suspend fun registerActor(vault: Vault, caseId: CaseId, label: String): ActorId =
        vault.actors.create(caseId, label, IdentityBasis.USER_ASSERTED, AssociationReview.CONFIRMED)

    @Test
    fun fixtureAndMaximalEventsSurviveClosingAndReopeningTheVault() = runBlocking<Unit> {
        val wrapper = newWrapper()
        val first = openVault(wrapper)
        first.startUp()
        val caseId = CaseId(first.cases.create("synthetic-case-events").id)
        val actorA = registerActor(first, caseId, "synthetic-actor-a")
        val actorB = registerActor(first, caseId, "synthetic-actor-b")

        val demo = EventFixtures.demoEvent()
        val fixture = demo.copy(caseId = caseId, sender = demo.sender.copy(actorId = actorA))
        val targetA = EventFixtures.plain("synthetic-target-a", caseId.value)
        val targetB = EventFixtures.plain("synthetic-target-b", caseId.value)
        val maximal = EventFixtures.maximal("synthetic-maximal", "synthetic-target-a", "synthetic-target-b").copy(
            caseId = caseId,
            sender = EventFixtures.maximal("x", "y", "y").sender.copy(actorId = actorA),
        ).let { it.copy(boundary = it.boundary.copy(actorId = actorB)) }
        val sparse = EventFixtures.sparse("synthetic-sparse", "synthetic-target-a").copy(caseId = caseId)
        val saved = listOf(fixture, targetA, targetB, maximal, sparse)
        saved.forEach { assertEquals(SaveResult.Saved(it.eventId, it.revision), first.events.save(it)) }
        assertIs<AuditVerification.Valid>(first.audit.verify())
        closeVault(first)

        val second = openVault(wrapper)
        saved.forEach { assertEquals(it, second.events.load(it.eventId, it.revision)) }
        assertEquals(saved.sortedBy { it.eventId.value }, second.events.loadAll(caseId))
        assertEquals(
            saved.size,
            second.audit.records().count { it.action == AuditActions.EVENT_SAVED },
        )
        assertIs<AuditVerification.Valid>(second.audit.verify())
        assertEquals(saved.size, second.events.loadLatest(caseId, everything).size)
    }

    @Test
    fun savingAndLoadingAThousandEventsTiming() = runBlocking<Unit> {
        val vault = openVault(newWrapper())
        vault.startUp()
        val caseId = CaseId(vault.cases.create("synthetic-case-bench").id)
        val actors = listOf(registerActor(vault, caseId, "synthetic-a"), registerActor(vault, caseId, "synthetic-b"))
        val generator = RandomEvents(seed = 20_261_002L, caseId = caseId.value, actorIds = actors.map { it.value })
        val events: List<Event> = List(EVENT_COUNT) { generator.next(it) }

        val saveStart = System.nanoTime()
        for (event in events) {
            assertEquals(SaveResult.Saved(event.eventId, event.revision), vault.events.save(event))
        }
        val saveMillis = (System.nanoTime() - saveStart) / NANOS_PER_MILLI

        val latestStart = System.nanoTime()
        val latest = vault.events.loadLatest(caseId, everything)
        val latestMillis = (System.nanoTime() - latestStart) / NANOS_PER_MILLI

        val allStart = System.nanoTime()
        val all = vault.events.loadAll(caseId)
        val allMillis = (System.nanoTime() - allStart) / NANOS_PER_MILLI

        assertEquals(EVENT_COUNT, latest.size)
        assertEquals(events.sortedBy { it.eventId.value }, all)
        assertEquals(events.toSet(), latest.toSet())
        record(
            "event_store_timings",
            *deviceState(context),
            "events" to EVENT_COUNT,
            "save_total_ms" to saveMillis,
            "save_ms_per_event" to saveMillis / EVENT_COUNT,
            "load_latest_ms" to latestMillis,
            "load_all_ms" to allMillis,
        )
    }

    @Test
    fun batchSavingTimings() = runBlocking<Unit> {
        val vault = openVault(newWrapper())
        vault.startUp()
        var offset = 0
        for ((label, count) in listOf("1000" to 1_000, "10000" to 10_000)) {
            val caseId = CaseId(vault.cases.create("synthetic-case-batch-$label").id)
            val actors = listOf(registerActor(vault, caseId, "synthetic-a"), registerActor(vault, caseId, "synthetic-b"))
            val generator = RandomEvents(seed = 31L + count, caseId = caseId.value, actorIds = actors.map { it.value })
            val events: List<Event> = List(count) { generator.next(offset + it) }
            offset += count

            val saveStart = System.nanoTime()
            assertEquals(BatchSaveResult.Saved(count), vault.events.saveAll(events))
            val saveMillis = (System.nanoTime() - saveStart) / NANOS_PER_MILLI

            val latestStart = System.nanoTime()
            val latest = vault.events.loadLatest(caseId, everything)
            val latestMillis = (System.nanoTime() - latestStart) / NANOS_PER_MILLI

            assertEquals(events.toSet(), latest.toSet())
            record(
                "event_store_batch_timings",
                *deviceState(context),
                "events" to count,
                "save_all_total_ms" to saveMillis,
                "save_all_ms_per_event" to saveMillis / count,
                "load_latest_ms" to latestMillis,
            )
        }
    }

    @Test
    fun readApisAgreeWithTheFullLoadOnTheRealDatabase() = runBlocking<Unit> {
        val vault = openVault(newWrapper())
        vault.startUp()
        val caseId = CaseId(vault.cases.create("synthetic-case-read-api").id)
        val actors = listOf(registerActor(vault, caseId, "synthetic-a"), registerActor(vault, caseId, "synthetic-b"))
        val generator = RandomEvents(seed = 77L, caseId = caseId.value, actorIds = actors.map { it.value })
        val events: List<Event> = List(READ_API_EVENTS) { generator.next(it) }
        assertEquals(BatchSaveResult.Saved(events.size), vault.events.saveAll(events))

        val latest = vault.events.loadLatest(caseId, everything)
        assertEquals(latest, vault.events.observeLatest(caseId).first())

        val claimed = latest.filter { it.sender.displayLabel != null && !(it.sender.actorId != null && it.sender.associationReview == AssociationReview.CONFIRMED) }
        assertEquals(claimed.size, vault.events.senderClaims(caseId).sumOf { it.messageCount })

        val target = latest.first()
        val changed = if (target.direction == Direction.OUTGOING) Direction.INCOMING else Direction.OUTGOING
        assertIs<ReviewResult.Applied>(vault.review.setDirection(listOf(target.eventId), changed))
        assertEquals(changed, vault.events.loadLatest(target.eventId)?.direction)
        assertEquals(listOf(target.revision, target.revision + 1), vault.events.revisions(target.eventId).map { it.revision })
        assertEquals(DecisionChange.Direction(changed), vault.review.decisionsForEvent(target.eventId).single().change)
        assertEquals(latest.size, vault.events.observeLatest(caseId).first().size)
    }

    private companion object {
        const val READ_API_EVENTS = 200
        const val EVENT_COUNT = 1000
        const val NANOS_PER_MILLI = 1e6
    }
}
