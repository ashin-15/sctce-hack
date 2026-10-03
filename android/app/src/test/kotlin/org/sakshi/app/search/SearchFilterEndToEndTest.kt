package org.sakshi.app.search

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import org.sakshi.app.support.AnalysisTestBase
import org.sakshi.app.support.SyntheticChats
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.CategoryReviewStatus
import org.sakshi.core.vault.ReviewResult
import org.sakshi.core.vault.SearchScope
import org.sakshi.core.vault.SenderSelector

/** Filters through the real view model, search and vault over an analysed synthetic export. */
class SearchFilterEndToEndTest : AnalysisTestBase() {
    private lateinit var id: String

    private fun analysedCase(): String {
        id = newCase("synthetic-search-filters")
        analyseExport(importText(id, SyntheticChats.EIGHT_MESSAGES))
        return id
    }

    private fun model(zone: ZoneId = ZoneId.of("Asia/Kolkata")) =
        SearchViewModel(id, vault.search, scope, vault.actors.observe(CaseId(id)), { zone })

    private fun SearchViewModel.hitsFor(query: String) = run {
        onQueryChanged(query)
        await(state) { it.query == query && !it.isSearching }.hits
    }

    @Test
    fun theWholeCaseIsSearchedUntilAPersonIsChosen() {
        analysedCase()
        val model = model()
        assertEquals(2, model.hitsFor("you").mapNotNull { it.eventId }.distinct().size)

        runBlocking {
            val source = events(id).first { it.sender.displayLabel == SyntheticChats.OWNER }.source
            val selector = SenderSelector(SyntheticChats.OWNER, source.sourceApp, source.conversationScopeId)
            val result = vault.review.assignSenderToNewPerson(CaseId(id), selector, "Synthetic Alex")
            assertNotNull(result as? ReviewResult.Applied)
        }
        val person = await(model.state) { it.people.isNotEmpty() }.people.single()
        assertEquals("Synthetic Alex", person.label)

        model.setPerson(person.id)
        assertEquals(0, model.hitsFor("idiot").size)
        assertEquals(1, model.hitsFor("no more").size)
        model.clearFilters()
        assertEquals(1, model.hitsFor("idiot").size)
    }

    @Test
    fun theAcceptedScopeKeepsOnlyMessagesWithATagTheUserAgreedWith() {
        analysedCase()
        val insult = events(id).first { e -> e.categories.any { it.label == CategoryLabel.VERBAL_ABUSE } }
        val model = model()
        model.setScope(SearchScope.ACCEPTED_FINDINGS_ONLY)
        assertEquals(0, model.hitsFor("you").size, "a suggestion nobody agreed with does not count")

        runBlocking { vault.review.reviewCategory(insult.eventId, 0, CategoryReviewStatus.ACCEPTED) }
        val hits = model.hitsFor("you")
        assertEquals(insult.eventId.value, hits.single().eventId)

        model.setScope(SearchScope.ALL_PRESERVED_TEXT)
        assertEquals(2, model.hitsFor("you").mapNotNull { it.eventId }.distinct().size)
    }

    @Test
    fun theDayRangeNarrowsTheMessages() {
        analysedCase()
        val model = model()
        model.setFromText("24/09/2026")
        model.setUntilText("24/09/2026")
        assertEquals(1, model.hitsFor("hello").size)
        assertEquals(0, model.hitsFor("idiot").size, "the insult is from the next day")
        model.setFromText("25/09/2026")
        model.setUntilText("25/09/2026")
        assertEquals(1, model.hitsFor("idiot").size)
        assertEquals(Instant.parse("2026-09-24T18:30:00Z"), requireNotNull(model.state.value.filters.toSearchFilters(ZoneId.of("Asia/Kolkata"))).from)
    }
}
