package org.sakshi.app.review

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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.sakshi.app.R
import org.sakshi.app.SessionServices
import org.sakshi.app.ui.UiText
import org.sakshi.app.ui.components.NoteKind
import org.sakshi.app.ui.res
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.AssociationReview
import org.sakshi.core.model.BoundaryMarker
import org.sakshi.core.model.CategoryAssessment
import org.sakshi.core.model.CategoryBasis
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.CategoryReviewStatus
import org.sakshi.core.model.Direction
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventId
import org.sakshi.core.model.SourceKind
import org.sakshi.core.model.TextStatus
import org.sakshi.core.model.UnwantedContact
import org.sakshi.core.vault.DecisionTargetKind
import org.sakshi.core.vault.DerivativeKind
import org.sakshi.core.vault.ReviewResult
import org.sakshi.core.vault.SenderSelector
import org.sakshi.core.vault.StoredActor
import org.sakshi.core.vault.Vault
import org.sakshi.core.database.ThreatAnalysisRunEntity
import org.sakshi.processing.analysis.EventText

/** One category of the event with the cue words that make it a suggestion. */
data class CategoryView(val index: Int, val category: CategoryAssessment, val cues: List<CueMark>) {
    val isOwnTag: Boolean get() = category.basis == CategoryBasis.USER_TAG
}

/** The saved picture an event's text was read from, as the review screen shows it. */
sealed interface PictureState {
    /** The event does not come from a picture. */
    data object None : PictureState

    data object Loading : PictureState

    /** [image] is held in memory only and dropped when the review is closed. Outlines are drawn over it, never into it. */
    class Ready(val image: LoadedImage, val outlines: List<RegionOutline>) : PictureState

    /** The picture cannot be shown; [message] says why in plain words. The text review is not affected. */
    data class Unavailable(val message: UiText) : PictureState
}

data class EventReviewState(
    val loaded: Boolean = false,
    /** Null once loaded means the event is not in this case. */
    val event: Event? = null,
    val body: String? = null,
    val marks: List<CueMark> = emptyList(),
    val categories: List<CategoryView> = emptyList(),
    val people: List<StoredActor> = emptyList(),
    val history: List<HistoryLine> = emptyList(),
    val threatAnalysis: ThreatAnalysisRunEntity? = null,
    val notice: ReviewNotice? = null,
    val picture: PictureState = PictureState.None,
    /** How many recognised lines scored low, when the text came from a picture and that is known. */
    val uncertainLines: Int? = null,
) {
    /** The text was read from a screenshot or photo by software, so it can differ from what the picture says. */
    val fromPicture: Boolean get() = event?.source?.kind == SourceKind.SELECTED_IMAGE

    /** The recognised text is marked as uncertain. */
    val textUncertain: Boolean get() = event?.coverage?.textStatus == TextStatus.EXTRACTION_UNCERTAIN

    /** Boundary actions are for the person's own outgoing messages only. */
    val boundaryOffered: Boolean get() = event?.direction == Direction.OUTGOING

    /** The sender claim to open in "Who is who", when the message names a sender. */
    val senderClaim: SenderSelector?
        get() = event?.takeIf { it.sender.displayLabel != null }?.let {
            SenderSelector(it.sender.displayLabel, it.source.sourceApp, it.source.conversationScopeId)
        }
}

/**
 * Review of one event: what was saved, who and when, and the suggestions and tags. Every action goes through the
 * review coordinator and then reloads the latest revision. The view model lives in the activity's store, so the
 * loaded text and picture are dropped when the session locks. A picture is decoded once, only for an event read from
 * an image, and [onCleared] only drops the reference (never recycles the bitmap, which a frame may still draw).
 */
class EventReviewViewModel(
    private val caseId: String,
    private val eventId: String,
    private val vault: Vault,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val images: EvidenceImageLoader = VaultImageLoader(vault.evidence),
) : ViewModel(scope) {
    private val mutableState = MutableStateFlow(EventReviewState())
    val state: StateFlow<EventReviewState> = mutableState.asStateFlow()
    private val eventText = EventText(vault)
    private var pictureRequested = false
    private var cleared = false

    init {
        viewModelScope.launch { refresh() }
    }

    /** Agrees with the suggestion at [index]. */
    fun agree(index: Int) = act(res(R.string.review_done_agreed)) {
        vault.review.reviewCategory(EventId(eventId), index, CategoryReviewStatus.ACCEPTED)
    }

    /** Disagrees with the suggestion at [index], giving one of [DISAGREE_REASONS]. */
    fun disagree(index: Int, reason: String) = act(res(R.string.review_done_disagreed)) {
        vault.review.reviewCategory(EventId(eventId), index, CategoryReviewStatus.REJECTED, reason)
    }

    fun notSure(index: Int) = act(res(R.string.review_done_not_sure)) {
        vault.review.reviewCategory(EventId(eventId), index, CategoryReviewStatus.UNCERTAIN)
    }

    /** Adds the person's own tag to the whole message body. */
    fun addOwnTag(label: CategoryLabel) {
        val body = mutableState.value.event?.evidenceReferences?.firstOrNull() ?: return
        act(res(R.string.review_done_tag)) { vault.review.addUserTag(EventId(eventId), label, listOf(body.referenceId)) }
    }

    fun setDirection(direction: Direction) = act(res(R.string.review_done_direction)) {
        vault.review.setDirection(listOf(EventId(eventId)), direction)
    }

    fun markWantedness(value: UnwantedContact) = act(res(R.string.review_done_wantedness)) {
        vault.review.markWantedness(listOf(EventId(eventId)), value)
    }

    /** Marks this outgoing message as a boundary that concerns [person], who must be a confirmed person of the case. */
    fun markBoundary(marker: BoundaryMarker, person: ActorId) {
        val current = mutableState.value
        val known = current.people.any { it.id == person && it.associationReview == AssociationReview.CONFIRMED }
        if (!current.boundaryOffered) {
            notify(ReviewNotice(NoteKind.Problem, res(R.string.review_boundary_outgoing_only)))
            return
        }
        if (!known) {
            notify(ReviewNotice(NoteKind.Problem, res(R.string.review_problem_person)))
            return
        }
        act(res(R.string.review_done_boundary)) { vault.review.markEventAsBoundary(EventId(eventId), marker, person) }
    }

    fun noticeShown() = notify(null)

    private fun notify(notice: ReviewNotice?) = mutableState.update { it.copy(notice = notice) }

    private fun act(applied: UiText, action: suspend () -> ReviewResult) {
        viewModelScope.launch {
            val notice = try {
                ReviewMessages.of(action(), applied)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IllegalArgumentException) {
                ReviewMessages.failed()
            }
            refresh()
            notify(notice)
        }
    }

    private suspend fun refresh() {
        val latest = vault.events.loadLatest(EventId(eventId))?.takeIf { it.caseId.value == caseId }
        if (latest == null) {
            mutableState.update { it.copy(loaded = true, event = null) }
            return
        }
        val body = eventText.bodyOf(latest)
        val marks = body?.let { CueMarks.of(latest, it) }.orEmpty()
        val categories = latest.categories.mapIndexed { index, category ->
            val ids = category.evidenceReferenceIds.map { it.value }
            CategoryView(index, category, CueMarks.forCategory(marks, ids))
        }
        val people = vault.actors.list(latest.caseId)
        val history = historyOf(latest)
        val threatAnalysis = vault.threatAnalysisRuns.forEvent(latest.eventId.value).firstOrNull()
        mutableState.update {
            it.copy(
                loaded = true,
                event = latest,
                body = body,
                marks = marks,
                categories = categories,
                people = people,
                history = history,
                threatAnalysis = threatAnalysis,
            )
        }
        if (latest.source.kind == SourceKind.SELECTED_IMAGE && !pictureRequested) {
            pictureRequested = true
            viewModelScope.launch { loadPicture(latest) }
        }
    }

    /** Finds the saved original behind the recognised text and decodes it. Failure leaves the text review as it is. */
    private suspend fun loadPicture(event: Event) {
        val derivativeId = event.evidenceReferences.firstOrNull()?.artifactId?.value
        val derivative = derivativeId?.let { vault.derivatives.get(it) }?.takeIf { it.kind == DerivativeKind.OCR }
        if (derivative == null) {
            setPicture(PictureState.Unavailable(res(R.string.review_picture_unreadable)))
            return
        }
        mutableState.update { it.copy(picture = PictureState.Loading, uncertainLines = lowLinesOf(derivative.qualityJson)) }
        val regions = vault.derivatives.regions(derivative.id)
        val state = when (val loaded = images.load(derivative.evidenceId)) {
            is ImageLoadResult.Loaded -> {
                val image = loaded.image
                PictureState.Ready(image, RegionOutlines.of(event, derivative.id, regions, image.uprightWidth, image.uprightHeight))
            }
            ImageLoadResult.TooLarge -> PictureState.Unavailable(res(R.string.review_picture_too_large))
            ImageLoadResult.Unreadable -> PictureState.Unavailable(res(R.string.review_picture_unreadable))
        }
        setPicture(state)
    }

    private fun setPicture(picture: PictureState) {
        if (cleared) return
        mutableState.update { it.copy(picture = picture) }
    }

    override fun onCleared() {
        cleared = true
        mutableState.update { it.copy(picture = PictureState.None) }
        super.onCleared()
    }

    /** The recogniser's count of low scoring lines from the derivative's quality record, or null when it says none. */
    private fun lowLinesOf(qualityJson: String?): Int? = try {
        qualityJson?.let { Json.parseToJsonElement(it).jsonObject["low_confidence_lines"]?.jsonPrimitive?.intOrNull }?.takeIf { it > 0 }
    } catch (_: IllegalArgumentException) {
        null
    }

    private suspend fun historyOf(latest: Event): List<HistoryLine> {
        val decisions = vault.review.decisionsForEvent(EventId(eventId))
        val categoriesByIndex = latest.categories.withIndex().associate { it.index to it.value }
        return decisions.map { decision ->
            val category = (decision.target as? DecisionTargetKind.Category)?.let { categoriesByIndex[it.categoryIndex] }
            HistoryRows.of(decision, category)
        }
    }

    companion object {
        fun factory(caseId: String, eventId: String, services: SessionServices): ViewModelProvider.Factory =
            viewModelFactory { initializer { EventReviewViewModel(caseId, eventId, services.vault, images = services.images) } }
    }
}
