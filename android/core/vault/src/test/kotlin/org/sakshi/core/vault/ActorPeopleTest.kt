package org.sakshi.core.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.AssociationReview
import org.sakshi.core.model.IdentityBasis
import org.sakshi.core.model.ScopeId

class ActorPeopleTest : ReviewTestBase() {
    private val selector = SenderSelector("synthetic-name-synthetic-m1", "synthetic-app", ScopeId("synthetic-conversation-1"))

    private suspend fun actorCount(): Int = actors.list(caseId).size

    @Test
    fun renameChangesTheLabelAndAuditsIdsOnly() = runBlocking<Unit> {
        standardCase()
        actors.rename(ActorId(EventFixtures.ACTOR), "synthetic-new-label")

        assertEquals("synthetic-new-label", actors.list(caseId).first { it.id.value == EventFixtures.ACTOR }.displayLabel)
        val audit = recording.calls.last { it.startsWith("actor.renamed|") }
        assertEquals(
            "actor.renamed|actor|${EventFixtures.ACTOR}|{\"actor_id\":\"${EventFixtures.ACTOR}\",\"case_id\":\"${EventFixtures.CASE}\"}",
            audit,
        )
        assertEquals(false, audit.contains("synthetic-new-label"))
        val rows = recording.calls.size
        actors.rename(ActorId(EventFixtures.ACTOR), "synthetic-new-label")
        assertEquals(rows, recording.calls.size)
    }

    @Test
    fun renameRefusesUnknownActorsAndBadLabels() = runBlocking<Unit> {
        standardCase()
        assertFailsWith<IllegalArgumentException> { actors.rename(ActorId("synthetic-none"), "synthetic-x") }
        assertFailsWith<IllegalArgumentException> { actors.rename(ActorId(EventFixtures.ACTOR), " ") }
        assertFailsWith<IllegalArgumentException> { actors.rename(ActorId(EventFixtures.ACTOR), "x".repeat(257)) }
    }

    @Test
    fun observeEmitsTheActorsAndTheRename() = runBlocking<Unit> {
        standardCase()
        assertEquals(2, actors.observe(caseId).first().size)
        actors.rename(ActorId(EventFixtures.ACTOR), "synthetic-renamed")
        assertEquals(true, actors.observe(caseId).first().any { it.displayLabel == "synthetic-renamed" })
    }

    @Test
    fun assigningToANewPersonCreatesAndLinksInOneStep() = runBlocking<Unit> {
        standardCase()
        saveOk(message("synthetic-m1"))
        saveOk(message("synthetic-m2") { displayLabel = "synthetic-name-synthetic-m1" })
        val before = actorCount()

        assertApplied(review.assignSenderToNewPerson(caseId, selector, "synthetic-person"), "synthetic-m1", "synthetic-m2")

        val created = actors.list(caseId).single { it.displayLabel == "synthetic-person" }
        assertEquals(before + 1, actorCount())
        assertEquals(IdentityBasis.USER_ASSERTED, created.identityBasis)
        assertEquals(AssociationReview.CONFIRMED, created.associationReview)
        assertEquals(created.id, latest("synthetic-m1").sender.actorId)
        assertEquals(true, recording.calls.any { it.startsWith("actor.created|") })
        assertEquals(false, lastAudit("review.sender").contains("synthetic-person"))
    }

    @Test
    fun aRefusedAssignmentLeavesNoPersonBehind() = runBlocking<Unit> {
        standardCase()
        saveOk(message("synthetic-m1"))
        val before = actorCount()

        assertEquals(ReviewResult.NoChange, assertWritesNothing { review.assignSenderToNewPerson(caseId, selector.copy(displayLabel = "synthetic-nobody"), "synthetic-person") })
        assertEquals(
            ReviewProblem.EMPTY_SELECTOR,
            assertIs<ReviewResult.Invalid>(assertWritesNothing { review.assignSenderToNewPerson(caseId, SenderSelector(null, null, null), "synthetic-person") }).problem,
        )
        assertEquals(
            ReviewProblem.VALUE_NOT_ALLOWED,
            assertIs<ReviewResult.Invalid>(assertWritesNothing { review.assignSenderToNewPerson(caseId, selector, " ") }).problem,
        )
        assertEquals(ReviewResult.NotFound, assertWritesNothing { review.assignSenderToNewPerson(org.sakshi.core.model.CaseId("synthetic-none"), selector, "synthetic-person") })
        assertEquals(before, actorCount())
        assertEquals(false, recording.calls.any { it.startsWith("actor.created|") && it.contains("synthetic-person") })
    }
}
