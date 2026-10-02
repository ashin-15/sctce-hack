package org.sakshi.app.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.ZoneId
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
import org.sakshi.app.SessionServices
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.Event
import org.sakshi.core.model.Timestamp
import org.sakshi.core.temporal.GapReason
import org.sakshi.core.vault.StoredCoverageGap
import org.sakshi.core.vault.Vault
import org.sakshi.processing.analysis.EventText

data class TimelineUiState(
    val loaded: Boolean = false,
    val filter: TimelineFilter = TimelineFilter.ALL,
    val view: TimelineView = TimelineView(emptyList(), 0, 0),
    val zone: ZoneId = ZoneId.systemDefault(),
)

/** Result of the last "Add a period with no records" attempt. */
sealed interface GapOutcome {
    data object None : GapOutcome

    data object Saved : GapOutcome

    data class Problem(val problem: GapProblem) : GapOutcome
}

private class Loaded(
    val events: List<Event>,
    val gaps: List<StoredCoverageGap>,
    val bodies: Map<String, String>,
    val actorLabels: Map<ActorId, String>,
)

/**
 * Rows of the case timeline. Message bodies are cut out of the stored text once per change and held only in this
 * view model, which the activity clears when the session locks.
 */
class TimelineViewModel(
    private val caseId: String,
    private val vault: Vault,
    private val zone: ZoneId = ZoneId.systemDefault(),
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel(scope) {
    private val filter = MutableStateFlow(TimelineFilter.ALL)
    private val reload = MutableStateFlow(0)
    private val bodyCache = HashMap<String, String>()
    private val eventText = EventText(vault)
    private val mutableGapOutcome = MutableStateFlow<GapOutcome>(GapOutcome.None)
    val gapOutcome: StateFlow<GapOutcome> = mutableGapOutcome

    private val loaded: StateFlow<Loaded?> = combine(vault.events.observeLatest(CaseId(caseId)), reload) { events, _ -> events }
        .map { events -> load(events) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<TimelineUiState> = combine(loaded, filter) { data, selected ->
        if (data == null) {
            TimelineUiState(zone = zone, filter = selected)
        } else {
            val view = TimelineRows.build(data.events, data.gaps, data.bodies, data.actorLabels, zone, selected)
            TimelineUiState(loaded = true, filter = selected, view = view, zone = zone)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TimelineUiState(zone = zone))

    private suspend fun load(events: List<Event>): Loaded {
        val missing = events.filter { it.eventId.value !in bodyCache }
        eventText.bodiesOf(missing).forEach { (id, body) -> if (body != null) bodyCache[id.value] = body }
        return Loaded(
            events = events,
            gaps = vault.events.coverageGaps(CaseId(caseId)),
            bodies = bodyCache.toMap(),
            actorLabels = vault.actors.list(CaseId(caseId)).associate { it.id to it.displayLabel },
        )
    }

    fun setFilter(value: TimelineFilter) {
        filter.value = value
    }

    /** Saves a period with no records. The result is in [gapOutcome]. */
    fun addGap(startText: String, endText: String, reason: GapReason) {
        when (val parsed = GapInput.parse(startText, endText, zone)) {
            is GapParse.Invalid -> mutableGapOutcome.value = GapOutcome.Problem(parsed.problem)
            is GapParse.Valid -> viewModelScope.launch {
                mutableGapOutcome.value = try {
                    vault.events.addCoverageGap(
                        CaseId(caseId),
                        parsed.start?.let { Timestamp(it.toString()) },
                        parsed.end?.let { Timestamp(it.toString()) },
                        reason.name.lowercase(),
                    )
                    reload.update { it + 1 }
                    GapOutcome.Saved
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: IllegalArgumentException) {
                    GapOutcome.Problem(GapProblem.NOT_SAVED)
                }
            }
        }
    }

    fun gapOutcomeShown() {
        mutableGapOutcome.value = GapOutcome.None
    }

    companion object {
        fun factory(caseId: String, services: SessionServices): ViewModelProvider.Factory =
            viewModelFactory { initializer { TimelineViewModel(caseId, services.vault) } }
    }
}
