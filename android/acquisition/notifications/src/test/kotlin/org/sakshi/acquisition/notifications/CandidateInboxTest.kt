package org.sakshi.acquisition.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CandidateInboxTest {
    private fun candidate(n: Int, text: String = "synthetic text $n"): DiffedCandidate {
        val message = NotificationNormalizer.normalize(snap(key = "k$n", shortcut = "s$n", messages = listOf(msg(text)))).messages.single()
        return DiffedCandidate(ObservedCandidate("id$n", message), supersedes = null)
    }

    @Test
    fun `the record bound evicts the oldest and counts it`() {
        val reported = mutableListOf<Int>()
        val inbox = CandidateInbox(onDropped = { reported += it })
        inbox.add((1..NotificationBounds.INBOX_MAX_RECORDS + 5).map(::candidate))
        assertEquals(NotificationBounds.INBOX_MAX_RECORDS, inbox.candidates.value.size)
        assertEquals("id6", inbox.candidates.value.first().id)
        assertEquals(5, inbox.droppedCount.value)
        assertEquals(listOf(5), reported)
    }

    @Test
    fun `the byte bound evicts the oldest and counts it`() {
        val big = "w".repeat(NotificationBounds.MAX_FIELD_CHARS)
        val inbox = CandidateInbox(maxRecords = 1_000, maxBytes = 20_000)
        inbox.add((1..300).map { candidate(it, big) })
        assertTrue(inbox.approximateBytes <= 20_000)
        assertTrue(inbox.droppedCount.value > 0)
        assertEquals(300, inbox.candidates.value.size + inbox.droppedCount.value)
        assertEquals("id300", inbox.candidates.value.last().id)
    }

    @Test
    fun `take hands out the records for an explicit import and removes them`() {
        val inbox = CandidateInbox()
        inbox.add(listOf(candidate(1), candidate(2), candidate(3)))
        val taken = inbox.take(setOf("id2", "missing"))
        assertEquals(listOf("id2"), taken.map { it.id })
        assertEquals(listOf("id1", "id3"), inbox.candidates.value.map { it.id })
        inbox.restore(taken)
        assertEquals(listOf("id1", "id3", "id2"), inbox.candidates.value.map { it.id })
    }

    @Test
    fun `discard removes without handing out`() {
        val inbox = CandidateInbox()
        inbox.add(listOf(candidate(1), candidate(2)))
        inbox.discard(setOf("id1"))
        assertEquals(listOf("id2"), inbox.candidates.value.map { it.id })
    }

    @Test
    fun `emissions are immutable copies`() {
        val inbox = CandidateInbox()
        inbox.add(listOf(candidate(1)))
        val before = inbox.candidates.value
        inbox.add(listOf(candidate(2)))
        assertEquals(1, before.size)
        assertEquals(2, inbox.candidates.value.size)
    }

    @Test
    fun `clear leaves nothing and resets the counter`() {
        val inbox = CandidateInbox(maxRecords = 1)
        inbox.add(listOf(candidate(1), candidate(2)))
        assertEquals(1, inbox.droppedCount.value)
        inbox.clear()
        assertTrue(inbox.candidates.value.isEmpty())
        assertEquals(0, inbox.droppedCount.value)
        assertEquals(0, inbox.approximateBytes)
    }
}
