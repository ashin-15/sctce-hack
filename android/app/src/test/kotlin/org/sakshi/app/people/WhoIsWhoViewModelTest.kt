package org.sakshi.app.people

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.sakshi.app.support.AnalysisTestBase
import org.sakshi.app.support.SyntheticChats
import org.sakshi.app.ui.components.NoteKind
import org.sakshi.app.ui.resolve
import org.sakshi.core.model.AssociationReview
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.Direction
import org.sakshi.core.model.IdentityBasis
import org.sakshi.core.vault.SenderSelector

class WhoIsWhoViewModelTest : AnalysisTestBase() {
    private lateinit var caseId: String

    private fun modelFor(focus: SenderSelector? = null) = WhoIsWhoViewModel(caseId, focus, vault, scope)

    private fun analyseOne(owner: String? = null) {
        analyseExport(importText(caseId, SyntheticChats.EIGHT_MESSAGES), owner)
    }

    private fun claimOf(state: WhoIsWhoState, name: String): List<SenderClaimView> = state.claims.filter { it.selector.displayLabel == name }

    @Test
    fun claimsAreListedWithTheirMessageCounts() {
        caseId = newCase()
        analyseOne()
        val state = await(modelFor().state) { it.loaded && it.claims.size == 2 }
        assertEquals(6, claimOf(state, SyntheticChats.OTHER).single().messageCount)
        assertEquals(2, claimOf(state, SyntheticChats.OWNER).single().messageCount)
        assertTrue(state.assignments.isEmpty())
        assertTrue(state.claims.all { it.sourceKind == org.sakshi.core.model.SourceKind.SELECTED_EXPORT })
    }

    @Test
    fun thisIsMeMakesTheClaimsMessagesOutgoing() {
        caseId = newCase()
        analyseOne(owner = null)
        val model = modelFor()
        val selector = await(model.state) { it.claims.size == 2 }.claims.first { it.selector.displayLabel == SyntheticChats.OWNER }.selector
        assertTrue(events(caseId).all { it.direction == Direction.UNKNOWN })
        model.markOwn(selector)
        val state = await(model.state) { s -> s.claims.any { it.markedOwn } && s.notice != null }
        assertTrue(claimOf(state, SyntheticChats.OWNER).single().markedOwn)
        assertEquals(2, events(caseId).count { it.direction == Direction.OUTGOING })
        assertEquals("Marked as from you.", state.notice?.text?.resolve(context.resources))
    }

    @Test
    fun namingAPersonCreatesAConfirmedPersonAndLinksTheClaim() {
        caseId = newCase()
        analyseOne()
        val model = modelFor()
        val selector = await(model.state) { it.claims.size == 2 }.claims.first { it.selector.displayLabel == SyntheticChats.OTHER }.selector
        model.assignToNewPerson(selector, "  synthetic-person ")
        val state = await(model.state) { it.assignments.isNotEmpty() && it.notice != null }
        val link = state.assignments.single()
        assertEquals("synthetic-person", link.personLabel)
        assertEquals(6, link.eventIds.size)
        assertTrue(claimOf(state, SyntheticChats.OTHER).isEmpty())
        val person = state.people.single()
        assertEquals(IdentityBasis.USER_ASSERTED, person.identityBasis)
        assertEquals(AssociationReview.CONFIRMED, person.associationReview)
        val linked = events(caseId).filter { it.sender.displayLabel == SyntheticChats.OTHER }
        assertTrue(linked.all { it.sender.actorId == person.id && it.sender.associationReview == AssociationReview.CONFIRMED })
        assertEquals("Linked to the person.", state.notice?.text?.resolve(context.resources))
    }

    @Test
    fun aClaimCanBeLinkedToAnExistingPerson() {
        caseId = newCase()
        analyseOne()
        val actor = runBlocking {
            vault.actors.create(CaseId(caseId), "synthetic-existing", IdentityBasis.USER_ASSERTED, AssociationReview.CONFIRMED)
        }
        val model = modelFor()
        val selector = await(model.state) { it.claims.size == 2 }.claims.first { it.selector.displayLabel == SyntheticChats.OTHER }.selector
        model.assignToPerson(selector, actor)
        val state = await(model.state) { it.assignments.isNotEmpty() }
        assertEquals(actor, state.assignments.single().actorId)
        assertEquals(1, state.people.size)
    }

    @Test
    fun theSameNameFromTwoExportsStaysTwoClaimsAndIsLinkedOneAtATime() {
        caseId = newCase()
        analyseOne()
        analyseOne()
        val model = modelFor()
        val state = await(model.state) { s -> s.claims.count { it.selector.displayLabel == SyntheticChats.OTHER } == 2 }
        val others = claimOf(state, SyntheticChats.OTHER)
        assertEquals(2, others.map { it.selector.conversationScopeId }.toSet().size)
        assertTrue(others.all { it.messageCount == 6 })

        model.assignToNewPerson(others.first().selector, "synthetic-person")
        val after = await(model.state) { it.assignments.isNotEmpty() }
        assertEquals(1, claimOf(after, SyntheticChats.OTHER).size)
        assertEquals(others.first().selector, after.assignments.single().selector)
        assertEquals(others.last().selector, claimOf(after, SyntheticChats.OTHER).single().selector)
        assertEquals(12, events(caseId).count { it.sender.displayLabel == SyntheticChats.OTHER })
        assertEquals(6, events(caseId).count { it.sender.displayLabel == SyntheticChats.OTHER && it.sender.actorId != null })
    }

    @Test
    fun undoWithdrawsTheLinkAndTheClaimComesBack() {
        caseId = newCase()
        analyseOne()
        val model = modelFor()
        val selector = await(model.state) { it.claims.size == 2 }.claims.first { it.selector.displayLabel == SyntheticChats.OTHER }.selector
        model.assignToNewPerson(selector, "synthetic-person")
        val linked = await(model.state) { it.assignments.isNotEmpty() }
        model.undo(linked.assignments.single())
        val state = await(model.state) { it.assignments.isEmpty() && claimOf(it, SyntheticChats.OTHER).isNotEmpty() }
        assertEquals(6, claimOf(state, SyntheticChats.OTHER).single().messageCount)
        val sender = events(caseId).first { it.sender.displayLabel == SyntheticChats.OTHER }.sender
        assertNull(sender.actorId)
        assertEquals(AssociationReview.REJECTED, sender.associationReview)
        assertEquals(1, state.people.size)
        assertEquals("The link was withdrawn.", state.notice?.text?.resolve(context.resources))
    }

    @Test
    fun aBlankNameIsRefusedWithoutCreatingAPerson() {
        caseId = newCase()
        analyseOne()
        val model = modelFor()
        val selector = await(model.state) { it.claims.size == 2 }.claims.first().selector
        model.assignToNewPerson(selector, "   ")
        val state = await(model.state) { it.notice != null }
        assertEquals(NoteKind.Problem, state.notice?.kind)
        assertTrue(state.people.isEmpty())
        model.noticeShown()
        assertNull(await(model.state) { it.notice == null }.notice)
    }

    @Test
    fun theFocusedClaimIsListedFirst() {
        caseId = newCase()
        analyseOne()
        val all = await(modelFor().state) { it.claims.size == 2 }.claims
        val owner = all.first { it.selector.displayLabel == SyntheticChats.OWNER }
        val state = await(modelFor(owner.selector).state) { it.claims.size == 2 }
        assertEquals(owner.selector, state.claims.first().selector)
        assertTrue(state.claims.first().focused)
        assertTrue(state.claims.drop(1).none { it.focused })
    }

    @Test
    fun messagesWithoutASenderNameAreNotListed() {
        caseId = newCase()
        analysis.let { runBlocking { it.analyse(importText(caseId, "synthetic plain text without a sender")) } }
        val state = await(modelFor().state) { it.loaded }
        assertTrue(state.claims.isEmpty())
        assertTrue(state.assignments.isEmpty())
    }
}
