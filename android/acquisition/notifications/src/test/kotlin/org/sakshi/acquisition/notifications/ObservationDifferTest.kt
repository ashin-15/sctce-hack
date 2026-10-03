package org.sakshi.acquisition.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The section 7 acceptance fixtures of the notification report that run on the JVM, one test per fixture. */
class ObservationDifferTest {
    private val a = msg("synthetic message A", time = T0)
    private val b = msg("synthetic message B", time = T0 + 60_000)

    @Test
    fun `fixture - A then A with a ranking update yields one analysed representation`() {
        val d = differ()
        val first = d.process(snap(messages = listOf(a)))
        val second = d.process(snap(messages = listOf(a), elapsed = 2_000, wall = T0 + 9_000))
        assertEquals(listOf("synthetic message A"), first.texts())
        assertTrue(second.newCandidates.isEmpty())
        assertEquals(1, second.suppressed[SuppressionLayer.UPDATE_EQUIVALENCE])
    }

    @Test
    fun `fixture - A then A and B yields only B as newly included`() {
        val d = differ()
        d.process(snap(messages = listOf(a)))
        val result = d.process(snap(messages = listOf(a, b)))
        assertEquals(listOf("synthetic message B"), result.texts())
        assertEquals(1, result.suppressed[SuppressionLayer.ALREADY_SEEN_IN_CONVERSATION])
    }

    @Test
    fun `fixture - two identical entries in one array keep their multiplicity`() {
        val d = differ()
        val result = d.process(snap(messages = listOf(a, a)))
        assertEquals(listOf("synthetic message A", "synthetic message A"), result.texts())
        // A later update that still holds both entries adds nothing, and one that adds a third adds exactly one.
        assertTrue(d.process(snap(messages = listOf(a, a))).newCandidates.isEmpty())
        assertEquals(1, d.process(snap(messages = listOf(a, a, a))).newCandidates.size)
    }

    @Test
    fun `fixture - equal text at a different time stays eligible`() {
        val d = differ()
        d.process(snap(messages = listOf(msg("same words", time = T0))))
        val later = d.process(snap(messages = listOf(msg("same words", time = T0), msg("same words", time = T0 + 3_600_000))))
        assertEquals(listOf("same words"), later.texts())
        assertEquals(T0 + 3_600_000, later.newCandidates.single().candidate.message.sourceClaimTime.epochMs)
    }

    @Test
    fun `fixture - equal text with equal time in two different conversations is never merged`() {
        val d = differ()
        d.process(snap(key = "k1", shortcut = "chat-1", messages = listOf(a)))
        val other = d.process(snap(key = "k2", shortcut = "chat-2", messages = listOf(a)))
        assertEquals(1, other.newCandidates.size)
    }

    @Test
    fun `fixture - summary then child does not double a finding`() {
        val d = differ()
        val summary = d.process(snap(key = "sum", groupKey = "g", summary = true, shortcut = null, title = "Synthetic Sender One", text = "synthetic message A"))
        assertEquals(ObservedTextStatus.SUMMARY_ONLY, summary.newCandidates.single().candidate.message.textStatus)
        val child = d.process(snap(key = "child", groupKey = "g", messages = listOf(a)))
        assertEquals(1, child.newCandidates.size)
        assertEquals(summary.newCandidates.single().candidate.id, child.newCandidates.single().supersedes)
        val inbox = CandidateInbox()
        inbox.add(summary.newCandidates)
        inbox.add(child.newCandidates)
        assertEquals(1, inbox.candidates.value.size)
        assertEquals(ObservedTextStatus.COMPLETE, inbox.candidates.value.single().message.textStatus)
        assertEquals(1, inbox.candidates.value.single().corroboratingObservations)
    }

    @Test
    fun `fixture - child then summary adds no candidate for the summary`() {
        val d = differ()
        d.process(snap(key = "child", groupKey = "g", messages = listOf(a)))
        val summary = d.process(snap(key = "sum", groupKey = "g", summary = true, shortcut = null, title = "x", text = "synthetic message A"))
        assertTrue(summary.newCandidates.isEmpty())
        assertEquals(1, summary.suppressed[SuppressionLayer.GROUP_SUMMARY_OVERLAP])
    }

    @Test
    fun `fixture - a summary alone is a lower confidence summary-only excerpt`() {
        val result = differ().process(snap(key = "sum", groupKey = "g", summary = true, shortcut = null, title = "x", text = "only surface"))
        assertEquals(ObservedTextStatus.SUMMARY_ONLY, result.newCandidates.single().candidate.message.textStatus)
    }

    @Test
    fun `fixture - removal then repost is not automatically the same message`() {
        val d = differ()
        val untimed = msg("no time claimed", time = null)
        assertEquals(1, d.process(snap(messages = listOf(untimed))).newCandidates.size)
        val removed = d.process(snap(origin = SnapshotOrigin.REMOVAL, removalReason = 8))
        assertNotNull(removed.lifecycle)
        val repost = d.process(snap(messages = listOf(untimed)))
        assertEquals(1, repost.newCandidates.size)
    }

    @Test
    fun `fixture - a reconnect snapshot is not a fresh arrival claim`() {
        val d = differ()
        d.process(snap(messages = listOf(a)))
        val redelivery = d.process(snap(origin = SnapshotOrigin.ACTIVE_SNAPSHOT, messages = listOf(a)))
        assertTrue(redelivery.newCandidates.isEmpty())
        assertEquals(1, redelivery.suppressed[SuppressionLayer.ACTIVE_SNAPSHOT_REDELIVERY])
    }

    @Test
    fun `an active snapshot only seeds state by default and reports nothing`() {
        val d = differ()
        val seeded = d.process(snap(origin = SnapshotOrigin.ACTIVE_SNAPSHOT, messages = listOf(a)))
        assertTrue(seeded.newCandidates.isEmpty())
        assertEquals(1, seeded.suppressed[SuppressionLayer.ACTIVE_SNAPSHOT_SEEDED])
        // A live repeat of what was seeded is the same notification content, not a new arrival.
        assertTrue(d.process(snap(messages = listOf(a))).newCandidates.isEmpty())
    }

    @Test
    fun `an active snapshot can be reported when the person opted in and is tagged as such`() {
        val result = differ(ActiveSnapshotPolicy.REPORT_AS_ACTIVE_SNAPSHOT)
            .process(snap(origin = SnapshotOrigin.ACTIVE_SNAPSHOT, messages = listOf(a)))
        assertEquals(SnapshotOrigin.ACTIVE_SNAPSHOT, result.newCandidates.single().candidate.message.origin)
    }

    @Test
    fun `fixture - historic and current user context is never blamed on the other party`() {
        val result = differ().process(snap(messages = listOf(msg("my own words", sender = null)), historic = listOf(msg("old words"))))
        assertTrue(result.newCandidates.isEmpty())
        assertEquals(WithheldReason.NO_TEXT, result.withheld)
    }

    @Test
    fun `removal is lifecycle metadata and never a deletion`() {
        val d = differ()
        d.process(snap(messages = listOf(a)))
        val removed = d.process(snap(origin = SnapshotOrigin.REMOVAL, removalReason = 10))
        val lifecycle = checkNotNull(removed.lifecycle)
        assertEquals("key-1", lifecycle.notificationKey)
        assertEquals(10, lifecycle.removal.platformReasonCode)
        assertEquals(WithheldReason.REMOVAL_ONLY, removed.withheld)
        assertTrue(removed.newCandidates.isEmpty())

        val inbox = CandidateInbox()
        inbox.add(d.process(snap(key = "key-2", shortcut = "other", messages = listOf(b))).newCandidates)
        inbox.applyLifecycle(LifecycleUpdate("key-2", RemovalLifecycle(10, T0)))
        val kept = inbox.candidates.value.single()
        assertEquals("synthetic message B", kept.message.text)
        assertEquals(10, kept.removal?.platformReasonCode)
    }

    @Test
    fun `the lifecycle record carries no deletion field`() {
        val names = RemovalLifecycle::class.java.declaredFields.map { it.name.lowercase() }
        assertTrue(names.none { "delet" in it })
    }

    @Test
    fun `the same input sequence gives the same output`() {
        fun run(): List<List<String>> {
            val d = differ()
            return listOf(
                d.process(snap(messages = listOf(a))),
                d.process(snap(messages = listOf(a, b))),
                d.process(snap(messages = listOf(a, b, a))),
            ).map { it.newCandidates.map { c -> c.candidate.id + c.candidate.message.text } }
        }
        assertEquals(run(), run())
    }

    @Test
    fun `scopes are bounded and forgetting a scope only repeats never loses`() {
        val d = differ()
        repeat(NotificationBounds.DEDUP_MAX_SCOPES + 20) { i ->
            d.process(snap(key = "k$i", shortcut = "chat-$i", messages = listOf(a)))
        }
        assertEquals(NotificationBounds.DEDUP_MAX_SCOPES, d.trackedScopeCount)
        val forgotten = d.process(snap(key = "k0", shortcut = "chat-0", messages = listOf(a)))
        assertEquals(1, forgotten.newCandidates.size)
    }

    @Test
    fun `idle scopes expire after the time to live`() {
        val d = differ()
        d.process(snap(messages = listOf(a), elapsed = 1_000))
        val later = d.process(snap(key = "key-1", messages = listOf(a), elapsed = 1_000 + NotificationBounds.DEDUP_TTL_MS + 1))
        assertEquals(1, later.newCandidates.size)
    }

    @Test
    fun `reset forgets everything`() {
        val d = differ()
        d.process(snap(messages = listOf(a)))
        d.reset()
        assertEquals(0, d.trackedScopeCount)
        assertEquals(1, d.process(snap(messages = listOf(a))).newCandidates.size)
    }

    @Test
    fun `withheld snapshots report their reason and no supersede`() {
        val result = differ().process(snap(title = "App", text = "3 new messages"))
        assertEquals(WithheldReason.SUMMARY_COUNT_NOTICE, result.withheld)
        assertNull(result.lifecycle)
    }
}
