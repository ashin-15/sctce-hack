package org.sakshi.app.search

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.sakshi.acquisition.importer.ImportMechanism
import org.sakshi.acquisition.importer.PendingBatch
import org.sakshi.acquisition.importer.PendingItem
import org.sakshi.app.SessionServices
import org.sakshi.app.support.VaultTestBase
import org.sakshi.core.model.CaseId
import org.sakshi.core.vault.DerivativeKind
import org.sakshi.core.vault.EvidenceSearch
import org.sakshi.core.vault.SearchResults

class SearchViewModelTest : VaultTestBase() {
    private fun modelFor(caseId: String) = SearchViewModel(caseId, vault.search, scope)

    private fun saveEvidence(caseId: String, text: String): String {
        val batch = PendingBatch(ImportMechanism.SHARE_SEND, listOf(PendingItem.Text(0, text)), referrerClaim = null)
        runBlocking { importer.commit(caseId, batch, setOf(0)) }
        return runBlocking { vault.evidence.observeForCase(caseId).first().first().id }
    }

    @Test
    fun queryChangeTriggersSearchInVaultAndPublishesHits() {
        val caseId = newCase("Test Case")
        val evidenceId = saveEvidence(caseId, "Evidence text")
        runBlocking {
            vault.derivatives.save(
                evidenceId,
                DerivativeKind.OCR,
                "threatening message call me tomorrow for details",
                "tool",
                "1",
            )
        }

        val model = modelFor(caseId)
        assertEquals("", model.state.value.query)
        assertTrue(model.state.value.hits.isEmpty())
        assertFalse(model.state.value.isSearching)

        model.onQueryChanged("call me")
        val state = await(model.state) { it.hits.isNotEmpty() }
        assertEquals("call me", state.query)
        assertFalse(state.isSearching)
        assertEquals(1, state.hits.size)

        val hit = state.hits.single()
        assertEquals(evidenceId, hit.evidenceId)
        assertEquals(DerivativeKind.OCR, hit.derivativeKind)
        assertEquals(1, hit.matches.size)

        val match = hit.matches.single()
        val highlighted = match.snippet.substring(match.matchStartInSnippet, match.matchEndInSnippet)
        assertEquals("call me", highlighted)
    }

    @Test
    fun blankQueryClearsHitsAndDoesNotTriggerSearch() {
        val caseId = newCase("Test Case")
        val evidenceId = saveEvidence(caseId, "Evidence text")
        runBlocking {
            vault.derivatives.save(
                evidenceId,
                DerivativeKind.OCR,
                "stop messaging me now",
                "tool",
                "1",
            )
        }

        val model = modelFor(caseId)
        model.onQueryChanged("stop")
        val stateWithHits = await(model.state) { it.hits.isNotEmpty() }
        assertEquals(1, stateWithHits.hits.size)

        model.onQueryChanged("")
        val clearedState = await(model.state) { it.query.isEmpty() }
        assertTrue(clearedState.hits.isEmpty())
        assertFalse(clearedState.isSearching)

        model.onQueryChanged("   ")
        assertTrue(model.state.value.hits.isEmpty())
        assertFalse(model.state.value.isSearching)
    }

    @Test
    fun nonMatchingQueryReturnsEmptyHits() {
        val caseId = newCase("Test Case")
        val evidenceId = saveEvidence(caseId, "Some text")
        runBlocking {
            vault.derivatives.save(evidenceId, DerivativeKind.OCR, "hello world", "tool", "1")
        }

        val model = modelFor(caseId)
        model.onQueryChanged("nonexistent")
        val state = await(model.state) { it.query == "nonexistent" && !it.isSearching }
        assertTrue(state.hits.isEmpty())
        assertEquals("nonexistent", state.query)
    }

    @Test
    fun factoryCreatesViewModel() {
        val caseId = newCase()
        val services = SessionServices(vault, context, Dispatchers.Unconfined)
        val factory = SearchViewModel.factory(caseId, services)
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }
        val created = ViewModelProvider(owner, factory)[SearchViewModel::class.java]
        assertNotNull(created)
        assertEquals("", created.state.value.query)
        owner.viewModelStore.clear()
    }

    @Test
    fun aFailedSearchIsShownAsFailedNotAsNoMatches() {
        val broken = object : EvidenceSearch {
            override suspend fun search(caseId: CaseId, query: String): SearchResults = error("synthetic failure")
        }
        val model = SearchViewModel(newCase(), broken, scope)
        model.onQueryChanged("anything")
        val state = await(model.state) { it.failed }
        assertFalse(state.isSearching)
        assertTrue(state.hits.isEmpty())
        model.onQueryChanged("")
        assertFalse(model.state.value.failed, "clearing the query clears the failure")
    }
}
