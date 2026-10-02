package org.sakshi.app.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import org.sakshi.app.R
import org.sakshi.app.SessionServices
import org.sakshi.app.review.ReviewMessages
import org.sakshi.app.review.ReviewNotice
import org.sakshi.app.ui.UiText
import org.sakshi.app.ui.components.NoteKind
import org.sakshi.app.ui.res
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.AssociationReview
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.Direction
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventId
import org.sakshi.core.model.IdentityBasis
import org.sakshi.core.model.SourceKind
import org.sakshi.core.vault.ReviewResult
import org.sakshi.core.vault.SenderSelector
import org.sakshi.core.vault.StoredActor
import org.sakshi.core.vault.Vault

/**
 * A sender name as the source gave it, not yet linked to a person. Two claims with the same name from different
 * exports differ in [selector] and are never merged.
 */
data class SenderClaimView(
    val selector: SenderSelector,
    val sourceKind: SourceKind,
    val savedAt: Instant?,
    val messageCount: Int,
    /** True when every message of the claim is marked as the person's own. */
    val markedOwn: Boolean,
    val focused: Boolean,
)

/** A claim the person linked to a confirmed person. */
data class AssignmentView(
    val selector: SenderSelector,
    val actorId: ActorId,
    val personLabel: String,
    val eventIds: List<EventId>,
    val sourceKind: SourceKind,
    val savedAt: Instant?,
)

data class WhoIsWhoState(
    val loaded: Boolean = false,
    val claims: List<SenderClaimView> = emptyList(),
    val assignments: List<AssignmentView> = emptyList(),
    val people: List<StoredActor> = emptyList(),
    val notice: ReviewNotice? = null,
)

/**
 * Sender claims of a case and the people the person has linked them to. Linking records the person's statement; it
 * never confirms who sent a message, and it is made one claim at a time.
 */
class WhoIsWhoViewModel(
    private val caseId: String,
    private val focusClaim: SenderSelector?,
    private val vault: Vault,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel(scope) {
    private val notice = MutableStateFlow<ReviewNotice?>(null)

    val state: StateFlow<WhoIsWhoState> = combine(
        vault.events.observeLatest(CaseId(caseId)),
        vault.actors.observe(CaseId(caseId)),
        notice,
    ) { events, actors, current -> Triple(events, actors, current) }
        .map { (events, actors, current) -> build(events, actors, current) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, WhoIsWhoState())

    private suspend fun build(events: List<Event>, actors: List<StoredActor>, current: ReviewNotice?): WhoIsWhoState {
        var people = actors
        val labels = HashMap(people.associate { it.id to it.displayLabel })
        val claims = LinkedHashMap<SenderSelector, MutableList<Event>>()
        val assigned = LinkedHashMap<Pair<SenderSelector, ActorId>, MutableList<Event>>()
        for (event in events) {
            val selector = selectorOf(event) ?: continue
            val actor = event.sender.actorId
            if (actor != null && event.sender.associationReview == AssociationReview.CONFIRMED) {
                assigned.getOrPut(selector to actor) { mutableListOf() } += event
                if (actor !in labels) {
                    people = vault.actors.list(CaseId(caseId))
                    people.forEach { labels[it.id] = it.displayLabel }
                }
            } else {
                claims.getOrPut(selector) { mutableListOf() } += event
            }
        }
        return WhoIsWhoState(
            loaded = true,
            claims = claims.map { (selector, events) ->
                SenderClaimView(
                    selector = selector,
                    sourceKind = events.first().source.kind,
                    savedAt = events.minOf { it.observedAt.instant },
                    messageCount = events.size,
                    markedOwn = events.all { it.direction == Direction.OUTGOING },
                    focused = selector == focusClaim,
                )
            }.sortedWith(compareByDescending<SenderClaimView> { it.focused }.thenBy { it.selector.displayLabel.orEmpty() }),
            assignments = assigned.map { (key, events) ->
                AssignmentView(
                    selector = key.first,
                    actorId = key.second,
                    personLabel = labels[key.second].orEmpty(),
                    eventIds = events.map { it.eventId },
                    sourceKind = events.first().source.kind,
                    savedAt = events.minOf { it.observedAt.instant },
                )
            }.sortedBy { it.personLabel },
            people = people,
            notice = current,
        )
    }

    /** "This is me": every message of the claim becomes the person's own outgoing message. */
    fun markOwn(selector: SenderSelector) = act(res(R.string.people_done_own)) {
        vault.review.markOwnMessages(CaseId(caseId), selector)
    }

    /** Links the claim to a person already in the case. */
    fun assignToPerson(selector: SenderSelector, actorId: ActorId) = act(res(R.string.people_done_assigned)) {
        vault.review.assignSender(CaseId(caseId), selector, actorId)
    }

    /** Creates a person from the person's own words, then links the claim to them. */
    fun assignToNewPerson(selector: SenderSelector, name: String) {
        val label = name.trim()
        if (label.isEmpty() || label.length > MAX_NAME_LENGTH) {
            notice.value = ReviewNotice(NoteKind.Problem, res(R.string.people_problem_name))
            return
        }
        act(res(R.string.people_done_assigned)) {
            vault.review.assignSenderToNewPerson(CaseId(caseId), selector, label)
        }
    }

    /** Withdraws the link of one claim. The person stays in the case. */
    fun undo(assignment: AssignmentView) = act(res(R.string.people_done_undo)) {
        vault.review.unassignSender(assignment.eventIds)
    }

    fun noticeShown() {
        notice.value = null
    }

    private fun act(applied: UiText, action: suspend () -> ReviewResult) {
        viewModelScope.launch {
            val result = try {
                ReviewMessages.of(action(), applied)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IllegalArgumentException) {
                ReviewMessages.failed()
            }
            notice.value = result
        }
    }

    private fun selectorOf(event: Event): SenderSelector? {
        val label = event.sender.displayLabel ?: return null
        return SenderSelector(label, event.source.sourceApp, event.source.conversationScopeId)
    }

    companion object {
        const val MAX_NAME_LENGTH: Int = 256

        fun factory(caseId: String, focusClaim: SenderSelector?, services: SessionServices): ViewModelProvider.Factory =
            viewModelFactory { initializer { WhoIsWhoViewModel(caseId, focusClaim, services.vault) } }
    }
}
