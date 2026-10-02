package org.sakshi.app.patterns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.Instant
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.sakshi.app.SessionServices
import org.sakshi.app.timeline.TimeLabel
import org.sakshi.app.timeline.TimeReading
import org.sakshi.app.timeline.TimelineRows
import org.sakshi.app.ui.UiText
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.EventId
import org.sakshi.core.model.TimeBasis
import org.sakshi.core.temporal.AssessmentStatus
import org.sakshi.core.temporal.EvidenceView
import org.sakshi.core.temporal.PatternRecord
import org.sakshi.core.temporal.PatternType
import org.sakshi.core.vault.Vault
import org.sakshi.processing.analysis.CasePatterns
import org.sakshi.processing.analysis.SupportingEventView

/** One event a pattern rests on: when it happened, how to say so, and the start of its text. */
data class SupportItem(val eventId: String, val time: TimeReading, val snippet: String?)

/** A pattern as the screen draws it. Wording comes from the engine's explanation; this adds only titles and lists. */
data class PatternCardView(
    val key: String,
    val type: PatternType,
    val status: AssessmentStatus,
    val title: UiText,
    val statusText: UiText,
    val observed: String,
    val interpretation: String?,
    val limitations: List<String>,
    val support: List<SupportItem>,
)

data class PatternsUiState(
    val loading: Boolean = true,
    val view: EvidenceView = EvidenceView.CONFIRMED_ONLY,
    val cards: List<PatternCardView> = emptyList(),
    /** The descriptions could not be computed; the screen says so in words. */
    val failed: Boolean = false,
    val zone: ZoneId = ZoneId.systemDefault(),
)

/**
 * Computes the pattern descriptions of a case each time the screen is entered, and whenever the person changes the
 * view, so a review change is picked up by opening the screen again. Nothing is computed before the first call.
 */
class PatternsViewModel(
    private val caseId: String,
    private val vault: Vault,
    private val patterns: CasePatterns,
    private val zone: ZoneId = ZoneId.systemDefault(),
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel(scope) {
    private val mutableState = MutableStateFlow(PatternsUiState(zone = zone))
    val state: StateFlow<PatternsUiState> = mutableState.asStateFlow()
    private var job: Job? = null

    /** Switches between confirmed items only and the preview that includes items waiting for review. */
    fun setView(view: EvidenceView) {
        mutableState.value = mutableState.value.copy(view = view)
        recompute()
    }

    /** Computes again from the stored events. The screen calls this each time it is entered. */
    fun recompute() {
        job?.cancel()
        val view = mutableState.value.view
        mutableState.value = mutableState.value.copy(loading = true)
        job = viewModelScope.launch {
            mutableState.value = try {
                PatternsUiState(loading = false, view = view, cards = cardsFor(view), zone = zone)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IllegalStateException) {
                PatternsUiState(loading = false, view = view, failed = true, zone = zone)
            } catch (_: IllegalArgumentException) {
                PatternsUiState(loading = false, view = view, failed = true, zone = zone)
            }
        }
    }

    private suspend fun cardsFor(view: EvidenceView): List<PatternCardView> {
        val computed = patterns.compute(CaseId(caseId), view, zone, withSupportingEvents = true)
        return computed.result.patterns.map { record ->
            val explanation = computed.explanations.getValue(record.patternKey)
            PatternCardView(
                key = record.patternKey,
                type = record.type,
                status = record.status,
                title = patternTitle(record.type),
                statusText = patternStatusText(record.status),
                observed = explanation.observed,
                interpretation = explanation.interpretation,
                limitations = explanation.limitations,
                support = supportOf(record, computed.supportingEvents),
            )
        }
    }

    private fun supportOf(record: PatternRecord, supporting: Map<EventId, SupportingEventView>): List<SupportItem> =
        record.supportingEvents.map { it.eventId }.distinct().mapNotNull { id ->
            val view = supporting[id] ?: return@mapNotNull null
            val earliest = view.earliest
            val latest = view.latest
            val label = when {
                earliest == null -> TimeLabel.Unknown
                latest != null && earliest != latest -> TimeLabel.Between(earliest, latest)
                else -> TimeLabel.At(earliest)
            }
            SupportItem(id.value, TimeReading(label, TimeBasis.SOURCE_CLAIM), view.bodyPreview?.let { TimelineRows.firstCodePoints(it, SNIPPET_CODE_POINTS) })
        }

    companion object {
        const val SNIPPET_CODE_POINTS: Int = 80

        fun factory(caseId: String, services: SessionServices): ViewModelProvider.Factory =
            viewModelFactory { initializer { PatternsViewModel(caseId, services.vault, services.casePatterns) } }
    }
}
