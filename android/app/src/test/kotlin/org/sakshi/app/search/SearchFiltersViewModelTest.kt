package org.sakshi.app.search

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import org.sakshi.app.support.VaultTestBase
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.AssociationReview
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.IdentityBasis
import org.sakshi.core.model.SourceKind
import org.sakshi.core.vault.EvidenceSearch
import org.sakshi.core.vault.SearchFilters
import org.sakshi.core.vault.SearchHit
import org.sakshi.core.vault.SearchResults
import org.sakshi.core.vault.SearchScope
import org.sakshi.core.vault.StoredActor

/** Records what reached the library, through both overloads. */
private class RecordingSearch(private val result: () -> SearchResults = { SearchResults.EMPTY }) : EvidenceSearch {
    val calls = mutableListOf<Triple<CaseId, String, SearchFilters?>>()

    override suspend fun search(caseId: CaseId, query: String): SearchResults {
        calls += Triple(caseId, query, null)
        return result()
    }

    override suspend fun search(caseId: CaseId, query: String, filters: SearchFilters): SearchResults {
        calls += Triple(caseId, query, filters)
        return result()
    }
}

class SearchFiltersViewModelTest : VaultTestBase() {
    private val search = RecordingSearch()
    private val people = MutableStateFlow<List<StoredActor>>(emptyList())
    private val kolkata = ZoneId.of("Asia/Kolkata")
    private val caseId = "synthetic-case"

    private fun model(search: EvidenceSearch = this.search) = SearchViewModel(caseId, search, scope, people, { kolkata })

    private fun SearchViewModel.ready(query: String = "hello"): SearchViewModel = apply { onQueryChanged(query) }

    private fun lastFilters(): SearchFilters = requireNotNull(search.calls.last().third)

    private fun actor(id: String, label: String) =
        StoredActor(ActorId(id), CaseId(caseId), label, IdentityBasis.USER_ASSERTED, AssociationReview.CONFIRMED)

    @Test
    fun aSearchWithoutFiltersIsAskedWithFiltersThatNarrowNothing() {
        model().ready()
        assertTrue(lastFilters().isUnfiltered)
        assertEquals("hello", search.calls.last().second)
    }

    @Test
    fun eachFilterReachesTheLibraryAndSearchesAgain() {
        val model = model().ready()
        val before = search.calls.size

        model.setScope(SearchScope.ACCEPTED_FINDINGS_ONLY)
        assertEquals(SearchScope.ACCEPTED_FINDINGS_ONLY, lastFilters().scope)
        model.setPerson(ActorId("synthetic-person-1"))
        assertEquals(ActorId("synthetic-person-1"), lastFilters().actorId)
        model.toggleSource(SourceChoice.TEXT)
        assertEquals(setOf(SourceKind.SELECTED_TEXT), lastFilters().sourceKinds)
        model.toggleSource(SourceChoice.NOTE)
        assertEquals(setOf(SourceKind.SELECTED_TEXT, SourceKind.MANUAL_ENTRY), lastFilters().sourceKinds)
        model.toggleSource(SourceChoice.TEXT)
        assertEquals(setOf(SourceKind.MANUAL_ENTRY), lastFilters().sourceKinds)
        model.setFromText("03/10/2026")
        assertEquals(Instant.parse("2026-10-02T18:30:00Z"), lastFilters().from)
        model.setUntilText("03/10/2026")
        assertEquals(Instant.parse("2026-10-03T18:29:59.999Z"), lastFilters().until)

        assertEquals(before + 7, search.calls.size)
        val last = lastFilters()
        assertEquals(SearchScope.ACCEPTED_FINDINGS_ONLY, last.scope)
        assertEquals(ActorId("synthetic-person-1"), last.actorId)
    }

    @Test
    fun clearingTheFiltersSearchesAgainWithNoFilters() {
        val model = model().ready()
        model.setPerson(ActorId("synthetic-person-1"))
        model.setFromText("03/10/2026")
        model.clearFilters()
        assertEquals(SearchFilterState(), model.state.value.filters)
        assertTrue(lastFilters().isUnfiltered)
    }

    @Test
    fun invalidDatesRunNoSearchAndLeaveNoHitsBehind() {
        val hit = SearchHit("e", "d", "ocr", null, null, null, emptyList())
        val search = RecordingSearch { SearchResults(listOf(hit), true) }
        val model = SearchViewModel(caseId, search, scope, people, { kolkata }).ready()
        assertEquals(1, model.state.value.hits.size)
        val calls = search.calls.size

        model.setFromText("31/02/2026")
        assertEquals(DateProblem.FROM_INVALID, model.state.value.dateProblem)
        assertTrue(model.state.value.hits.isEmpty())
        assertFalse(model.state.value.limitReached)
        assertFalse(model.state.value.isSearching)
        assertEquals(calls, search.calls.size)
        model.setUntilText("04/10/2026")
        assertEquals(DateProblem.FROM_INVALID, model.state.value.dateProblem)
        model.setFromText("05/10/2026")
        assertEquals(DateProblem.FROM_AFTER_UNTIL, model.state.value.dateProblem)
        assertEquals(calls, search.calls.size)

        model.setUntilText("06/10/2026")
        assertNull(model.state.value.dateProblem)
        assertEquals(calls + 1, search.calls.size)
        assertEquals(1, model.state.value.hits.size)
    }

    @Test
    fun aBlankQueryRunsNoSearchButKeepsTheFilters() {
        val model = model()
        model.setPerson(ActorId("synthetic-person-1"))
        assertTrue(search.calls.isEmpty())
        model.onQueryChanged("hello")
        model.onQueryChanged("  ")
        assertEquals(1, search.calls.size)
        assertEquals(ActorId("synthetic-person-1"), model.state.value.filters.personId)
    }

    @Test
    fun aFailureWithFiltersIsShownAsAFailureAndAChangeOfFilterTriesAgain() {
        var fail = true
        val flaky = RecordingSearch { if (fail) error("synthetic failure") else SearchResults.EMPTY }
        val model = SearchViewModel(caseId, flaky, scope, people, { kolkata }).ready()
        model.setPerson(ActorId("synthetic-person-1"))
        assertTrue(model.state.value.failed)
        assertTrue(model.state.value.hits.isEmpty())
        fail = false
        model.setPerson(null)
        assertFalse(model.state.value.failed)
    }

    @Test
    fun theFiltersAndTheOpenPanelStayWhileTheViewModelLives() {
        val model = model().ready()
        model.setFiltersOpen(true)
        model.setPerson(ActorId("synthetic-person-1"))
        model.setFromText("03/10/2026")
        model.onQueryChanged("another")
        val state = model.state.value
        assertTrue(state.filtersOpen)
        assertEquals(ActorId("synthetic-person-1"), state.filters.personId)
        assertEquals("03/10/2026", state.filters.fromText)
        assertEquals(state, model.state.value)
    }

    @Test
    fun thePeopleOfTheCaseAreListedByNameAndFollowChanges() {
        val model = model()
        people.value = listOf(actor("p2", "Synthetic Zed"), actor("p1", "synthetic ann"))
        assertEquals(listOf("synthetic ann", "Synthetic Zed"), model.state.value.people.map { it.label })
        people.value = emptyList()
        assertTrue(model.state.value.people.isEmpty())
    }
}
