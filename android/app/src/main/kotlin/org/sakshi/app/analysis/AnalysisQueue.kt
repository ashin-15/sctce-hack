package org.sakshi.app.analysis

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.sakshi.core.database.CaseStatus
import org.sakshi.core.database.EvidenceListItem
import org.sakshi.core.database.SupportState
import org.sakshi.core.vault.AcquisitionKind
import org.sakshi.core.vault.Vault
import org.sakshi.processing.analysis.AnalysisOutcome

/**
 * What the automatic analysis is doing. [waiting] counts the items after [current]. [needsAnswers] counts chat exports
 * that wait for the person's answers, and [notAnalysed] counts items that were refused or failed in this session.
 */
data class AutoAnalysisState(
    val waiting: Int = 0,
    val current: String? = null,
    val analysed: Int = 0,
    val needsAnswers: Int = 0,
    val notAnalysed: Int = 0,
    val paused: Boolean = false,
)

/**
 * Analyses saved text one item at a time while the vault is open, without the person starting each run. It exists only
 * inside an unlocked session: it is made after the unlock and [close] stops it at the lock, so it never works on a
 * locked vault and nothing runs when the app is closed.
 *
 * [candidates] gives the ids of saved text that has no events yet, oldest first, and gives a new list when something
 * is imported. Each id is tried once per session; an item that is refused or fails is left for the person. A run
 * that the lock interrupts is not counted as tried, so it starts again after the next unlock.
 *
 * A run started by the person goes through [exclusive], so the two never analyse the same item at the same time.
 */
class AnalysisQueue(
    private val candidates: Flow<List<String>>,
    private val analyse: suspend (evidenceId: String) -> AnalysisOutcome,
    private val scope: CoroutineScope,
) : AutoCloseable {
    private val gate = Mutex()
    private val tried = mutableSetOf<String>()
    private val mutableState = MutableStateFlow(AutoAnalysisState())
    private var worker: Job? = null

    val state: StateFlow<AutoAnalysisState> = mutableState.asStateFlow()

    /** Starts the worker. Later calls do nothing. */
    fun start() {
        if (worker != null) return
        worker = scope.launch {
            candidates.conflate().collect { ids -> drain(ids.filterNot { it in tried }) }
        }
    }

    /** A paused queue finishes the item it is on and starts no other until it is resumed. */
    fun setPaused(paused: Boolean) = mutableState.update { it.copy(paused = paused) }

    /** Runs [block] while the worker is between items. */
    suspend fun <T> exclusive(block: suspend () -> T): T = gate.withLock { block() }

    override fun close() = scope.cancel()

    private suspend fun drain(ids: List<String>) {
        for ((index, id) in ids.withIndex()) {
            mutableState.update { it.copy(waiting = ids.size - index, current = null) }
            mutableState.first { !it.paused }
            mutableState.update { it.copy(waiting = ids.size - index - 1, current = id) }
            val outcome = try {
                gate.withLock { analyse(id) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            tried += id
            mutableState.update {
                when (outcome) {
                    is AnalysisOutcome.Analysed -> it.copy(analysed = it.analysed + 1)
                    is AnalysisOutcome.NeedsExportOptions -> it.copy(needsAnswers = it.needsAnswers + 1)
                    is AnalysisOutcome.NotAnalysable, null -> it.copy(notAnalysed = it.notAnalysed + 1)
                }
            }
        }
        mutableState.update { it.copy(waiting = 0, current = null) }
    }
}

/**
 * Saved text in active cases that has not been analysed, oldest first. Pictures and recordings are left out, because
 * reading them loads the text recognition or speech model and the person starts those runs. Notes the person wrote are
 * their own statements and are never analysed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun Vault.unanalysedText(): Flow<List<String>> = cases.observe()
    .flatMapLatest { all ->
        val active = all.filter { it.status == CaseStatus.ACTIVE }
        if (active.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(active.map { evidence.observeForCase(it.id) }) { lists ->
                lists.flatMap { it }.filter(::waitsForAutomaticAnalysis).sortedBy { it.receivedAt }.map { it.id }
            }
        }
    }
    .distinctUntilChanged()

internal fun waitsForAutomaticAnalysis(item: EvidenceListItem): Boolean =
    item.detectedMime == null &&
        item.acquisitionKind != AcquisitionKind.MANUAL_NOTE &&
        item.supportState in setOf(SupportState.SAVED, SupportState.ANALYSIS_PENDING)
