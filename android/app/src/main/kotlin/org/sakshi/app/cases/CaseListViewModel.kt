package org.sakshi.app.cases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.sakshi.core.database.CaseStatus
import org.sakshi.core.vault.CaseRepository
import org.sakshi.core.vault.CaseSummary

/** One-shot notices. Never carries case titles or exception text. */
enum class CaseMessage { TITLE_BLANK, TITLE_TOO_LONG, SAVE_FAILED }

data class CaseListUiState(
    val active: List<CaseSummary> = emptyList(),
    val archived: List<CaseSummary> = emptyList(),
    val message: CaseMessage? = null,
)

class CaseListViewModel(
    private val cases: CaseRepository,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel(scope) {
    private val message = MutableStateFlow<CaseMessage?>(null)

    val uiState: StateFlow<CaseListUiState> = combine(cases.observe(), message) { all, notice ->
        val (archived, active) = all.partition { it.status == CaseStatus.ARCHIVED }
        CaseListUiState(active, archived, notice)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CaseListUiState())

    fun create(title: String) = withValidTitle(title) { cases.create(it) }

    fun rename(id: String, title: String) = withValidTitle(title) { cases.rename(id, it) }

    fun archive(id: String) = change { cases.archive(id) }

    fun unarchive(id: String) = change { cases.unarchive(id) }

    fun delete(id: String) = change { cases.delete(id) }

    fun messageShown() = message.update { null }

    private fun withValidTitle(title: String, block: suspend (String) -> Unit) {
        val clean = title.trim()
        when {
            clean.isEmpty() -> message.value = CaseMessage.TITLE_BLANK
            clean.length > CaseRepository.MAX_TITLE_LENGTH -> message.value = CaseMessage.TITLE_TOO_LONG
            else -> change { block(clean) }
        }
    }

    private fun change(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message.value = CaseMessage.SAVE_FAILED
            }
        }
    }

    companion object {
        fun factory(cases: CaseRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { CaseListViewModel(cases) }
        }
    }
}
