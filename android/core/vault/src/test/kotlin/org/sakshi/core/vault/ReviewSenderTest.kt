package org.sakshi.core.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import org.sakshi.core.database.ReviewAction
import org.sakshi.core.database.ReviewTargetType
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.AssociationReview
import org.sakshi.core.model.Direction
import org.sakshi.core.model.EventId
import org.sakshi.core.model.IdentityBasis
import org.sakshi.core.model.ScopeId
import org.sakshi.core.model.ViolationCode

class ReviewSenderTest : ReviewTestBase() {
    private val actor = ActorId(EventFixtures.ACTOR)
    private val alexOne = SenderSelector("Alex", "synthetic-app-1", ScopeId("synthetic-chat-1"))

    private suspend fun alex(id: String, app: String, chat: String, label: String = "Alex") = message(id) {
        displayLabel = label
        sourceApp = app
        conversation = chat
    }.also { saveOk(it) }

    @Test
    fun assignSenderTouchesOnlyTheExactSelector() = runBlocking<Unit> {
        standardCase()
        val a1 = alex("synthetic-a1", "synthetic-app-1", "synthetic-chat-1")
        val a2 = alex("synthetic-a2", "synthetic-app-1", "synthetic-chat-1")
        val otherChat = alex("synthetic-a3", "synthetic-app-1", "synthetic-chat-2")
        val otherApp = alex("synthetic-a4", "synthetic-app-2", "synthetic-chat-1")
        val otherLabel = alex("synthetic-a5", "synthetic-app-1", "synthetic-chat-1", label = "Alexa")

        val result = review.assignSender(caseId, alexOne, actor)

        assertApplied(result, "synthetic-a1", "synthetic-a2")
        val assign = { e: org.sakshi.core.model.Event ->
            e.copy(sender = e.sender.copy(actorId = actor, identityBasis = IdentityBasis.USER_ASSERTED, associationReview = AssociationReview.CONFIRMED))
        }
        assertEquals(next(a1, assign), latest("synthetic-a1"))
        assertEquals(next(a2, assign), latest("synthetic-a2"))
        assertEquals("Alex", latest("synthetic-a1").sender.displayLabel)
        listOf(otherChat, otherApp, otherLabel).forEach { assertEquals(it, latest(it.eventId.value)) }
        assertEquals(a1, store.load(a1.eventId, 1))
        val decision = review.decisions("synthetic-a1").single()
        assertEquals(ReviewTargetType.ASSOCIATION, decision.targetType)
        assertEquals(ReviewAction.ACCEPT, decision.action)
        assertEquals(2, decision.targetRevision)
        val audit = lastAudit("review.sender")
        assertEquals("review.sender|case|${EventFixtures.CASE}|{\"case_id\":\"${EventFixtures.CASE}\",\"operation\":\"assign\",\"actor_id\":\"${EventFixtures.ACTOR}\",\"count\":2}", audit)
        assertEquals(false, audit.contains("Alex"))
    }

    @Test
    fun assignSenderIsIdempotentAndRefusesBadInputs() = runBlocking<Unit> {
        standardCase()
        alex("synthetic-a1", "synthetic-app-1", "synthetic-chat-1")

        assertApplied(review.assignSender(caseId, alexOne, actor), "synthetic-a1")
        assertEquals(ReviewResult.NoChange, review.assignSender(caseId, alexOne, actor))
        assertEquals(ReviewResult.NoChange, review.assignSender(caseId, alexOne, ActorId(EventFixtures.OTHER_ACTOR)))

        assertEquals(ReviewResult.NotFound, assertWritesNothing { review.assignSender(org.sakshi.core.model.CaseId("synthetic-none"), alexOne, actor) })
        val empty = assertWritesNothing { review.assignSender(caseId, SenderSelector(null, null, null), actor) }
        assertEquals(ReviewProblem.EMPTY_SELECTOR, assertIs<ReviewResult.Invalid>(empty).problem)
        val ghost = assertWritesNothing { review.assignSender(caseId, alexOne, ActorId("synthetic-ghost")) }
        assertEquals(ViolationCode.ACTOR_UNKNOWN, assertIs<ReviewResult.Invalid>(ghost).violations.single().code)
    }

    @Test
    fun actorOfAnotherCaseIsRefused() = runBlocking<Unit> {
        standardCase()
        insertCase("synthetic-case-2")
        insertActor("synthetic-case-2", "synthetic-actor-x")
        alex("synthetic-a1", "synthetic-app-1", "synthetic-chat-1")

        val result = assertWritesNothing { review.assignSender(caseId, alexOne, ActorId("synthetic-actor-x")) }

        assertEquals(ViolationCode.ACTOR_CROSS_CASE, assertIs<ReviewResult.Invalid>(result).violations.single().code)
    }

    @Test
    fun unassignKeepsTheLabelAndRejectsTheAssociation() = runBlocking<Unit> {
        standardCase()
        val a1 = alex("synthetic-a1", "synthetic-app-1", "synthetic-chat-1")
        review.assignSender(caseId, alexOne, actor)
        val assigned = latest("synthetic-a1")

        val result = review.unassignSender(listOf(EventId("synthetic-a1"), EventId("synthetic-a1")))

        assertApplied(result, "synthetic-a1")
        assertEquals(next(assigned) { it.copy(sender = it.sender.copy(actorId = null, associationReview = AssociationReview.REJECTED)) }, latest("synthetic-a1"))
        assertEquals("Alex", latest("synthetic-a1").sender.displayLabel)
        assertEquals(assigned, store.load(a1.eventId, 2))
        assertEquals(listOf(ReviewAction.ACCEPT, ReviewAction.REJECT), review.decisions("synthetic-a1").map { it.action })
        assertEquals(ReviewResult.NoChange, review.unassignSender(listOf(EventId("synthetic-a1"))))
        assertEquals(ReviewResult.NotFound, assertWritesNothing { review.unassignSender(listOf(EventId("synthetic-a1"), EventId("synthetic-none"))) })
    }

    @Test
    fun markOwnMessagesSetsOutgoingForTheSelectorOnly() = runBlocking<Unit> {
        standardCase()
        val mine = alex("synthetic-a1", "synthetic-app-1", "synthetic-chat-1", label = "synthetic-me")
        val theirs = alex("synthetic-a2", "synthetic-app-1", "synthetic-chat-1")

        val result = review.markOwnMessages(caseId, SenderSelector("synthetic-me", "synthetic-app-1", ScopeId("synthetic-chat-1")))

        assertApplied(result, "synthetic-a1")
        assertEquals(next(mine) { it.copy(direction = Direction.OUTGOING) }, latest("synthetic-a1"))
        assertEquals(theirs, latest("synthetic-a2"))
        assertEquals(ReviewResult.NoChange, review.markOwnMessages(caseId, SenderSelector("synthetic-me", "synthetic-app-1", ScopeId("synthetic-chat-1"))))
        assertEquals("review.direction|case|${EventFixtures.CASE}|{\"case_id\":\"${EventFixtures.CASE}\",\"direction\":\"outgoing\",\"count\":1}", lastAudit("review.direction"))
        assertEquals(ReviewResult.NotFound, assertWritesNothing { review.markOwnMessages(org.sakshi.core.model.CaseId("synthetic-none"), alexOne) })
    }
}
