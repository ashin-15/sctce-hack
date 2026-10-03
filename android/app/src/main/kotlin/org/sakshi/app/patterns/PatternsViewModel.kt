package org.sakshi.app.patterns

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
import org.sakshi.core.vault.PatternReview
import org.sakshi.core.vault.PatternReviewAction
import org.sakshi.core.vault.PatternReviewResult
import org.sakshi.core.vault.StoredPattern
import org.sakshi.core.vault.Vault
import org.sakshi.processing.analysis.CasePatternView
import org.sakshi.processing.analysis.CasePatterns
import org.sakshi.processing.analysis.SupportingEventView

/** One event a pattern rests on: when it happened, how to say so, and the start of its text. */
data class SupportItem(val eventId: String, val time: TimeReading, val snippet: String?)

/**
 * A pattern as the screen draws it. Wording comes from the engine's explanation; this adds only titles and lists.
 * [storedId] is the id the description is stored under, so an answer applies to exactly this wording. [review] is
 * what the person has said about it and [reviewReason] the reason they gave when they said it does not match, if any,
 * as the vault stores it. [reviewable] is true only for a confirmed-view description that states
 * something about the records.
 */
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
    val storedId: String? = null,
    val review: PatternReview = PatternReview.NOT_REVIEWED,
    val reviewReason: String? = null,
    val reviewable: Boolean = false,
)

/** A plain sentence shown above the cards after something happened to an answer. */
enum class PatternNotice { CHANGED_BEFORE_SAVE, NOT_SAVED }

data class PatternsUiState(
    val loading: Boolean = true,
    val view: EvidenceView = EvidenceView.CONFIRMED_ONLY,
    val cards: List<PatternCardView> = emptyList(),
    /** The descriptions could not be computed; the screen says so in words. */
    val failed: Boolean = false,
    val zone: ZoneId = ZoneId.systemDefault(),
    /** The descriptions are being worked out again because something in the case changed; the cards stay visible. */
    val refreshing: Boolean = false,
    val notice: PatternNotice? = null,
)

/** Works out and stores the descriptions of a case, as [CasePatterns.refresh] does with supporting events. */
typealias PatternRefresh = suspend (CaseId, EvidenceView, ZoneId) -> CasePatternView

/**
 * Works out and stores the pattern descriptions of a case each time the screen is entered, and whenever the person
 * changes the view. While the screen is open it also watches the stored descriptions: when a correction elsewhere
 * marks one out of date it works them out again, once per change. A failed refresh shows the failure notice and the
 * next refresh waits for the screen to be entered again or the view to change, so a failure never loops. The person's
 * answers go through the vault and apply only to exactly the description they looked at. Nothing is computed before
 * the first call.
 */
class PatternsViewModel(
    private val caseId: String,
    private val vault: Vault,
    private val refreshPatterns: PatternRefresh,
    private val zone: ZoneId = ZoneId.systemDefault(),
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel(scope) {
    private val mutableState = MutableStateFlow(PatternsUiState(zone = zone))
    val state: StateFlow<PatternsUiState> = mutableState.asStateFlow()
    private var job: Job? = null
    private var watch: Job? = null

    /** True after a failed refresh until the screen is entered again or the view changes. */
    private var autoRefreshBlocked = false

    /** Switches between confirmed items only and the preview that includes items waiting for review. */
    fun setView(view: EvidenceView) {
        mutableState.value = mutableState.value.copy(view = view, notice = null)
        recompute()
    }

    /** Works the descriptions out and stores them again. The screen calls this each time it is entered. */
    fun recompute() {
        autoRefreshBlocked = false
        mutableState.value = mutableState.value.copy(notice = null)
        refresh(showLoading = true)
        watchStored(mutableState.value.view)
    }

    /**
     * Records the person's answer to the description with [key]. [reason] is used only with a REJECT. A description
     * that changed in the meantime, or is no longer stored, is worked out again and the answer is not recorded.
     */
    fun answer(key: String, action: PatternReviewAction, reason: String? = null) {
        val card = mutableState.value.cards.firstOrNull { it.key == key } ?: return
        val id = card.storedId ?: return
        if (!card.reviewable) return
        viewModelScope.launch {
            val result = try {
                vault.patterns.review(id, action, reason)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IllegalStateException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
            when (result) {
                is PatternReviewResult.Recorded -> applyAnswer(id, result.review, reason)
                PatternReviewResult.Stale, PatternReviewResult.NotFound -> {
                    mutableState.value = mutableState.value.copy(notice = PatternNotice.CHANGED_BEFORE_SAVE)
                    refresh(showLoading = false)
                }
                PatternReviewResult.ReasonNotAllowed, null ->
                    mutableState.value = mutableState.value.copy(notice = PatternNotice.NOT_SAVED)
            }
        }
    }

    private fun applyAnswer(id: String, review: PatternReview, reason: String?) {
        val shownReason = reason.takeIf { review == PatternReview.REJECTED }
        mutableState.value = mutableState.value.let { current ->
            current.copy(
                notice = null,
                cards = current.cards.map { card ->
                    if (card.storedId == id) card.copy(review = review, reviewReason = shownReason) else card
                },
            )
        }
    }

    private fun refresh(showLoading: Boolean) {
        job?.cancel()
        val view = mutableState.value.view
        mutableState.value = mutableState.value.copy(loading = showLoading, refreshing = !showLoading)
        job = viewModelScope.launch {
            val notice = mutableState.value.notice
            mutableState.value = try {
                PatternsUiState(loading = false, view = view, cards = cardsFor(view), zone = zone, notice = notice)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IllegalStateException) {
                autoRefreshBlocked = true
                PatternsUiState(loading = false, view = view, failed = true, zone = zone)
            } catch (_: IllegalArgumentException) {
                autoRefreshBlocked = true
                PatternsUiState(loading = false, view = view, failed = true, zone = zone)
            }
        }
    }

    private fun watchStored(view: EvidenceView) {
        watch?.cancel()
        watch = viewModelScope.launch {
            vault.patterns.observe(CaseId(caseId), view).collect { stored -> onStored(stored, view) }
        }
    }

    /** Keeps the answers on the cards current, and works the descriptions out again when one went out of date. */
    private fun onStored(stored: List<StoredPattern>, view: EvidenceView) {
        if (mutableState.value.view != view) return
        val byId = stored.associateBy { it.id }
        mutableState.value = mutableState.value.let { current ->
            current.copy(
                cards = current.cards.map { card ->
                    val match = card.storedId?.let(byId::get) ?: return@map card
                    card.copy(review = match.review, reviewReason = match.reviewReason.takeIf { match.review == PatternReview.REJECTED })
                },
            )
        }
        if (stored.any { it.stale } && !autoRefreshBlocked && job?.isActive != true) refresh(showLoading = false)
    }

    private suspend fun cardsFor(view: EvidenceView): List<PatternCardView> {
        val computed = refreshPatterns(CaseId(caseId), view, zone)
        return computed.result.patterns.map { record ->
            val explanation = computed.explanations.getValue(record.patternKey)
            val storedId = computed.storedIds[record.patternKey]
            val review = computed.reviews[record.patternKey] ?: PatternReview.NOT_REVIEWED
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
                storedId = storedId,
                review = review,
                reviewReason = computed.reviewReasons[record.patternKey].takeIf { review == PatternReview.REJECTED },
                reviewable = view == EvidenceView.CONFIRMED_ONLY && storedId != null && isReviewableStatus(record.status),
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
            viewModelFactory {
                initializer {
                    PatternsViewModel(caseId, services.vault, { id, view, zone -> services.casePatterns.refresh(id, view, zone, withSupportingEvents = true) })
                }
            }
    }
}
