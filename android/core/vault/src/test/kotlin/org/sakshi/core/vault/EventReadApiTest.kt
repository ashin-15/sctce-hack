package org.sakshi.core.vault

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.Direction
import org.sakshi.core.model.EventId
import org.sakshi.core.model.ScopeId

class EventReadApiTest : ReviewTestBase() {
    private val eventId = EventId("synthetic-m1")

    @Test
    fun loadLatestAndRevisionsFollowTheStoredRevisions() = runBlocking<Unit> {
        standardCase()
        val first = message("synthetic-m1").also { saveOk(it) }
        assertEquals(first, store.loadLatest(eventId))
        assertEquals(listOf(first), store.revisions(eventId))

        review.setDirection(listOf(eventId), Direction.OUTGOING)
        review.markWantedness(listOf(eventId), org.sakshi.core.model.UnwantedContact.USER_MARKED_UNWANTED)

        assertEquals(3, store.loadLatest(eventId)?.revision)
        assertEquals(listOf(1, 2, 3), store.revisions(eventId).map { it.revision })
        assertEquals(first, store.revisions(eventId).first())
        assertEquals(latest("synthetic-m1"), store.loadLatest(eventId))
        assertNull(store.loadLatest(EventId("synthetic-none")))
        assertEquals(emptyList(), store.revisions(EventId("synthetic-none")))
    }

    @Test
    fun observeEventEmitsNullThenEachNewRevision() = runBlocking<Unit> {
        standardCase()
        val flow = store.observeEvent(eventId)
        assertNull(flow.first())
        message("synthetic-m1").also { saveOk(it) }
        assertEquals(1, flow.first()?.revision)
        review.setDirection(listOf(eventId), Direction.OUTGOING)
        assertEquals(2, flow.first()?.revision)
    }

    @Test
    fun senderClaimsGroupUnconfirmedLabelsInTheDatabase() = runBlocking<Unit> {
        standardCase()
        suspend fun claim(id: String, label: String?, app: String, chat: String, at: String) = message(id) {
            displayLabel = label
            sourceApp = app
            conversation = chat
            at(at)
            observedAt = at
        }.also { saveOk(it) }
        claim("synthetic-a1", "Alex", "synthetic-app-1", "synthetic-chat-1", "2026-10-01T12:00:00+05:30")
        claim("synthetic-a2", "Alex", "synthetic-app-1", "synthetic-chat-1", "2026-10-01T09:00:00Z")
        claim("synthetic-a3", "Alex", "synthetic-app-1", "synthetic-chat-1", "2026-10-02T09:00:00Z")
        claim("synthetic-b1", "Alex", "synthetic-app-2", "synthetic-chat-1", "2026-10-01T09:00:00Z")
        claim("synthetic-c1", "Sam", "synthetic-app-1", "synthetic-chat-9", "2026-10-01T09:00:00Z")
        claim("synthetic-n1", null, "synthetic-app-1", "synthetic-chat-9", "2026-10-01T09:00:00Z")
        val selector = SenderSelector("Sam", "synthetic-app-1", ScopeId("synthetic-chat-9"))
        review.assignSender(caseId, selector, ActorId(EventFixtures.ACTOR))
        review.markOwnMessages(caseId, SenderSelector("Alex", "synthetic-app-2", ScopeId("synthetic-chat-1")))

        val claims = store.senderClaims(caseId)

        assertEquals(
            listOf(
                SenderClaim(SenderSelector("Alex", "synthetic-app-1", ScopeId("synthetic-chat-1")), 3, 0, Instant.parse("2026-10-01T06:30:00Z")),
                SenderClaim(SenderSelector("Alex", "synthetic-app-2", ScopeId("synthetic-chat-1")), 1, 1, Instant.parse("2026-10-01T09:00:00Z")),
            ),
            claims,
        )
        assertEquals(emptyList(), store.senderClaims(CaseId("synthetic-other-case")))
    }

    @Test
    fun observeSenderClaimsFollowsAssignments() = runBlocking<Unit> {
        standardCase()
        message("synthetic-a1") { displayLabel = "Alex" }.also { saveOk(it) }
        val seen = store.observeSenderClaims(caseId).take(1).toList()
        assertEquals(1, seen.single().single().messageCount)
        review.assignSender(caseId, SenderSelector("Alex", "synthetic-app", ScopeId("synthetic-conversation-1")), ActorId(EventFixtures.ACTOR))
        assertEquals(emptyList(), store.observeSenderClaims(caseId).first())
    }
}
