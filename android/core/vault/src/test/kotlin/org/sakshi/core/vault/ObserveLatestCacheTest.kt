package org.sakshi.core.vault

import androidx.room.withTransaction
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.sakshi.core.model.Direction
import org.sakshi.core.model.EventId

class ObserveLatestCacheTest : ReviewTestBase() {
    private val cases2000 = 2_000

    private suspend fun saveMany(count: Int) {
        standardCase()
        val events = (1..count).map { i ->
            message("synthetic-m$i") {
                at("2026-10-01T09:00:00Z")
                earliest = Instant.parse("2026-10-01T09:00:00Z").plusSeconds(i.toLong()).toString()
                latest = earliest
                observedAt = earliest
            }
        }
        events.chunked(500).forEach { assertEquals(BatchSaveResult.Saved(it.size), store.saveAll(it)) }
    }

    @Test
    fun anIncrementalEmissionRebuildsOnlyTheChangedEvent() = runBlocking<Unit> {
        saveMany(cases2000)
        val assembled = mutableListOf<Int>()
        store.assembledObserver = { assembled += it }
        val emissions = Channel<List<org.sakshi.core.model.Event>>(Channel.UNLIMITED)
        val job = launch(Dispatchers.Default) { store.observeLatest(caseId).collect { emissions.send(it) } }
        try {
            withTimeout(TIMEOUT_MS) {
                assertEquals(cases2000, emissions.receive().size)
                assertEquals(listOf(cases2000), assembled)

                review.setDirection(listOf(EventId("synthetic-m7")), Direction.OUTGOING)
                var second = emissions.receive()
                while (second.first { it.eventId.value == "synthetic-m7" }.revision == 1) second = emissions.receive()

                assertEquals(cases2000, second.size)
                assertEquals(listOf(cases2000, 1), assembled)
                assertEquals(store.loadLatest(caseId, Instant.ofEpochMilli(Long.MAX_VALUE)), second)
            }
        } finally {
            job.cancel()
        }
    }

    @Test
    fun aNewCollectionStartsWithAnEmptyCache() = runBlocking<Unit> {
        saveMany(20)
        val assembled = mutableListOf<Int>()
        store.assembledObserver = { assembled += it }
        val first = store.observeLatest(caseId).first()
        val second = store.observeLatest(caseId).first()
        assertEquals(first, second)
        assertEquals(listOf(20, 20), assembled)
    }

    @Test
    fun deletedAnchorsInvalidateTheCachedEvent() = runBlocking<Unit> {
        saveMany(3)
        val flow = store.observeLatest(caseId).map { events -> events.map { it.evidenceReferences.size } }
        val emissions = Channel<List<Int>>(Channel.UNLIMITED)
        val job = launch(Dispatchers.Default) { flow.collect { emissions.send(it) } }
        try {
            withTimeout(TIMEOUT_MS) {
                assertEquals(listOf(1, 1, 1), emissions.receive())
                db.withTransaction {
                    db.openHelper.writableDatabase.execSQL("DELETE FROM evidence_anchor WHERE event_id = 'synthetic-m2'")
                }
                var next = emissions.receive()
                while (next == listOf(1, 1, 1)) next = emissions.receive()
                assertEquals(listOf(1, 0, 1), next)
            }
        } finally {
            job.cancel()
        }
    }

    @Test
    fun observeLatestEqualsLoadLatestOnReviewedEvents() = runBlocking<Unit> {
        saveMany(30)
        review.setDirection(listOf(EventId("synthetic-m3"), EventId("synthetic-m4")), Direction.OUTGOING)
        val observed = store.observeLatest(caseId).take(1).toList().single()
        assertEquals(store.loadLatest(caseId, Instant.ofEpochMilli(Long.MAX_VALUE)), observed)
    }

    private companion object {
        const val TIMEOUT_MS: Long = 30_000
    }
}
