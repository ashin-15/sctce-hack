package org.sakshi.app.evidence

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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.sakshi.core.database.CaseStatus
import org.sakshi.core.vault.CaseRepository
import org.sakshi.core.vault.EvidenceRepository
import org.sakshi.core.vault.UnreadableReason
import org.sakshi.core.vault.VerificationResult

/** Outcome of "Check integrity" for one item. It says only how the stored file compares with its recorded fingerprint. */
enum class IntegrityStatus { CHECKING, INTACT, CHANGED, FILE_MISSING, NOT_AUTHENTICATED, KEY_UNAVAILABLE, NOT_COMPLETED }

/** One-shot notice. Never carries text from the evidence. */
enum class DetailMessage { DELETE_FAILED }

data class CaseDetailUiState(
    val loaded: Boolean = false,
    /** Null once loaded means the case no longer exists. */
    val title: String? = null,
    val archived: Boolean = false,
    val items: List<EvidenceRow> = emptyList(),
    val integrity: Map<String, IntegrityStatus> = emptyMap(),
    val message: DetailMessage? = null,
)

class CaseDetailViewModel(
    private val caseId: String,
    cases: CaseRepository,
    private val evidence: EvidenceRepository,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel(scope) {
    private val integrity = MutableStateFlow<Map<String, IntegrityStatus>>(emptyMap())
    private val message = MutableStateFlow<DetailMessage?>(null)

    private val rows = evidence.observeForCase(caseId).map { list ->
        list.map { item ->
            val details = evidence.details(item.id)
            EvidenceRow(
                id = item.id,
                receivedAt = item.receivedAt,
                byteSize = item.byteSize,
                kind = evidenceKindOf(item.acquisitionKind, item.detectedMime, details?.declaredMime),
                supportState = item.supportState,
            )
        }
    }

    val uiState: StateFlow<CaseDetailUiState> = combine(cases.observe(), rows, integrity, message) { all, items, checks, notice ->
        val case = all.firstOrNull { it.id == caseId }
        CaseDetailUiState(
            loaded = true,
            title = case?.title,
            archived = case?.status == CaseStatus.ARCHIVED,
            items = items,
            integrity = checks,
            message = notice,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CaseDetailUiState())

    fun checkIntegrity(evidenceId: String) {
        integrity.update { it + (evidenceId to IntegrityStatus.CHECKING) }
        viewModelScope.launch {
            val status = try {
                statusOf(evidence.verify(evidenceId))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                IntegrityStatus.NOT_COMPLETED
            }
            integrity.update { it + (evidenceId to status) }
        }
    }

    fun delete(evidenceId: String) {
        viewModelScope.launch {
            try {
                evidence.delete(evidenceId)
                integrity.update { it - evidenceId }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message.value = DetailMessage.DELETE_FAILED
            }
        }
    }

    fun messageShown() = message.update { null }

    private fun statusOf(result: VerificationResult): IntegrityStatus = when (result) {
        VerificationResult.Intact -> IntegrityStatus.INTACT
        VerificationResult.HashMismatch -> IntegrityStatus.CHANGED
        is VerificationResult.Unreadable -> when (result.reason) {
            UnreadableReason.MISSING_FILE -> IntegrityStatus.FILE_MISSING
            UnreadableReason.AUTHENTICATION_FAILED -> IntegrityStatus.NOT_AUTHENTICATED
            UnreadableReason.KEY_UNAVAILABLE -> IntegrityStatus.KEY_UNAVAILABLE
        }
    }

    companion object {
        fun factory(caseId: String, cases: CaseRepository, evidence: EvidenceRepository): ViewModelProvider.Factory =
            viewModelFactory { initializer { CaseDetailViewModel(caseId, cases, evidence) } }
    }
}
