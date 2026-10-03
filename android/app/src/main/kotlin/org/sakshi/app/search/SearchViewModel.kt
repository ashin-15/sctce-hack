package org.sakshi.app.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.sakshi.app.SessionServices
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.CaseId
import org.sakshi.core.vault.EvidenceSearch
import org.sakshi.core.vault.SearchHit
import org.sakshi.core.vault.SearchScope
import org.sakshi.core.vault.StoredActor

/**
 * [failed] means the last search stopped with an error; that is shown as such, never as "no matches". [people] are the
 * people of the case the search can be narrowed to. [dateProblem] is set while the dates cannot be searched with; no
 * search runs then and [hits] is empty, so the screen says what to fix instead of showing an empty list.
 */
data class SearchUiState(
    val query: String = "",
    val hits: List<SearchHit> = emptyList(),
    val isSearching: Boolean = false,
    val limitReached: Boolean = false,
    val failed: Boolean = false,
    val filters: SearchFilterState = SearchFilterState(),
    val filtersOpen: Boolean = false,
    val people: List<PersonChoice> = emptyList(),
) {
    val dateProblem: DateProblem? get() = filters.dateProblem
}

/**
 * Searches derivatives for lexical matches in one case. Results update as the query or a filter changes and
 * are cleared when the query is blank. Filters live here, so they are still set after a result is opened and the
 * person comes back, and they go away with the session like every other view model.
 */
class SearchViewModel(
    private val caseId: String,
    private val search: EvidenceSearch,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    people: Flow<List<StoredActor>> = emptyFlow(),
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel(scope) {
    private val mutableState = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = mutableState.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            people.collect { actors ->
                val choices = actors.map { PersonChoice(it.id, it.displayLabel) }.sortedBy { it.label.lowercase() }
                mutableState.update { it.copy(people = choices) }
            }
        }
    }

    fun onQueryChanged(query: String) {
        mutableState.update { it.copy(query = query) }
        runSearch()
    }

    fun setFiltersOpen(open: Boolean) = mutableState.update { it.copy(filtersOpen = open) }

    fun setScope(scope: SearchScope) = changeFilters { it.copy(scope = scope) }

    fun setPerson(id: ActorId?) = changeFilters { it.copy(personId = id) }

    fun toggleSource(source: SourceChoice) =
        changeFilters { it.copy(sources = if (source in it.sources) it.sources - source else it.sources + source) }

    fun setFromText(text: String) = changeFilters { it.copy(fromText = text) }

    fun setUntilText(text: String) = changeFilters { it.copy(untilText = text) }

    fun clearFilters() = changeFilters { SearchFilterState() }

    private fun changeFilters(change: (SearchFilterState) -> SearchFilterState) {
        mutableState.update { it.copy(filters = change(it.filters)) }
        runSearch()
    }

    private fun runSearch() {
        searchJob?.cancel()
        val current = mutableState.value
        val query = current.query
        val filters = current.filters.toSearchFilters(zone())
        if (query.isBlank() || filters == null) {
            mutableState.update { it.copy(hits = emptyList(), isSearching = false, limitReached = false, failed = false) }
            return
        }
        mutableState.update { it.copy(isSearching = true, failed = false) }
        searchJob = viewModelScope.launch {
            try {
                val results = search.search(CaseId(caseId), query, filters)
                mutableState.update { latest ->
                    if (latest.query == query && latest.filters == current.filters) {
                        latest.copy(hits = results.hits, limitReached = results.limitReached, isSearching = false)
                    } else {
                        latest
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update {
                    if (it.query == query && it.filters == current.filters) it.copy(hits = emptyList(), isSearching = false, failed = true) else it
                }
            }
        }
    }

    companion object {
        fun factory(caseId: String, services: SessionServices): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { SearchViewModel(caseId, services.vault.search, people = services.vault.actors.observe(CaseId(caseId))) }
            }
    }
}
