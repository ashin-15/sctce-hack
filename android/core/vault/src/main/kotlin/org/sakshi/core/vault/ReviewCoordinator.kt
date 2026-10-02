package org.sakshi.core.vault

import androidx.room.withTransaction
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import org.sakshi.core.database.ReviewAction
import org.sakshi.core.database.ReviewDecisionEntity
import org.sakshi.core.database.ReviewTargetType
import org.sakshi.core.database.SakshiDatabase
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.AssociationReview
import org.sakshi.core.model.BoundaryMarker
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.CategoryReviewStatus
import org.sakshi.core.model.DedupStatus
import org.sakshi.core.model.Direction
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventId
import org.sakshi.core.model.EventKind
import org.sakshi.core.model.IdentityBasis
import org.sakshi.core.model.ReferenceId
import org.sakshi.core.model.TimeBounds
import org.sakshi.core.model.Timestamp
import org.sakshi.core.model.UnwantedContact
import org.sakshi.core.model.Violation
import org.sakshi.core.model.ViolationCode

/**
 * Turns a person's review actions into new event revisions and insert-only `review_decision` rows.
 *
 * Every operation is ONE transaction: it loads the latest revisions, saves the changed copies (revision plus
 * one, `availableAt` = now, `observedAt` kept) through [EventStore.saveAll], inserts one decision per changed
 * target and appends one audit row of ids, counts and enum strings. [EventStore] joins the surrounding
 * transaction because Room's `withTransaction` nests, so a failure anywhere rolls everything back. Earlier
 * revisions are never touched.
 */
public class ReviewCoordinator(
    private val database: SakshiDatabase,
    private val events: EventStore,
    private val audit: AuditLog,
    private val clock: () -> Instant,
    private val ids: () -> String = { UUID.randomUUID().toString() },
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val actors = ActorRegistry(database, audit, ids, dispatcher)

    /**
     * Sets the review status of one category in a new revision; nothing else changes and a rejection keeps the
     * category. [reasonCode] must be one of [ReviewReason].
     *
     * @throws IllegalArgumentException for an unknown reason code.
     */
    public suspend fun reviewCategory(
        eventId: EventId,
        categoryIndex: Int,
        decision: CategoryReviewStatus,
        reasonCode: String? = null,
    ): ReviewResult {
        require(reasonCode == null || reasonCode in ReviewReason.all) { "Unknown reason code" }
        return atomically {
            val event = latest(eventId) ?: return@atomically ReviewResult.NotFound
            if (categoryIndex !in event.categories.indices) {
                return@atomically ReviewResult.Invalid(emptyList(), ReviewProblem.CATEGORY_INDEX)
            }
            val action = when (decision) {
                CategoryReviewStatus.ACCEPTED -> ReviewAction.ACCEPT
                CategoryReviewStatus.REJECTED -> ReviewAction.REJECT
                CategoryReviewStatus.UNCERTAIN, CategoryReviewStatus.UNREVIEWED -> ReviewAction.MARK_UNKNOWN
            }
            commit(
                listOf(event),
                { ReviewEdits.categoryStatus(it, categoryIndex, decision) },
                { listOf(DecisionDraft(ReviewTargetType.FINDING, DecisionTargets.category(eventId, it.revision, categoryIndex), action, reasonCode)) },
                Audit(AuditActions.REVIEW_CATEGORY, EVENT, eventId.value) { revised ->
                    listOfNotNull(
                        "event_id" to eventId.value,
                        "revision" to revised.first().revision,
                        "category_index" to categoryIndex,
                        "decision" to Codecs.categoryReviewStatus.name(decision),
                        reasonCode?.let { "reason_code" to it },
                    )
                },
            )
        }
    }

    /**
     * Appends a user-tag category (confidence not applicable, producer `manual-review-v1`, accepted) that rests on
     * [referenceIds], which must be a non-empty list of distinct reference ids of the event.
     */
    public suspend fun addUserTag(eventId: EventId, label: CategoryLabel, referenceIds: List<ReferenceId>): ReviewResult =
        atomically {
            val event = latest(eventId) ?: return@atomically ReviewResult.NotFound
            val problem = when {
                referenceIds.isEmpty() -> ReviewProblem.REFERENCES_REQUIRED
                referenceIds.toSet().size != referenceIds.size -> ReviewProblem.REFERENCE_DUPLICATE
                referenceIds.any { id -> event.evidenceReferences.none { it.referenceId == id } } -> ReviewProblem.REFERENCE_UNKNOWN
                else -> null
            }
            if (problem != null) return@atomically ReviewResult.Invalid(emptyList(), problem)
            if (ReviewEdits.hasUserTag(event, label, referenceIds)) return@atomically ReviewResult.NoChange
            commit(
                listOf(event),
                { ReviewEdits.userTag(it, label, referenceIds) },
                { listOf(DecisionDraft(ReviewTargetType.FINDING, DecisionTargets.category(eventId, it.revision, it.categories.lastIndex), ReviewAction.ACCEPT)) },
                Audit(AuditActions.REVIEW_USER_TAG, EVENT, eventId.value) { revised ->
                    listOf(
                        "event_id" to eventId.value,
                        "revision" to revised.first().revision,
                        "label" to Codecs.categoryLabel.name(label),
                        "reference_count" to referenceIds.size,
                    )
                },
            )
        }

    /**
     * Confirms [actorId] as the sender of every latest event of the case that has no confirmed actor and shows
     * exactly the [selector]'s label, app and conversation. Other selectors are never merged in. The actor must
     * already exist in the case (see [ActorRegistry.create]).
     */
    public suspend fun assignSender(caseId: CaseId, selector: SenderSelector, actorId: ActorId): ReviewResult =
        atomically {
            if (selector.isEmpty()) return@atomically ReviewResult.Invalid(emptyList(), ReviewProblem.EMPTY_SELECTOR)
            val matching = caseEvents(caseId) ?: return@atomically ReviewResult.NotFound
            actorProblem(caseId, actorId)?.let { return@atomically it }
            commit(
                matching.filter { ReviewEdits.matches(it, selector) && !ReviewEdits.hasConfirmedActor(it.sender) },
                { ReviewEdits.assigned(it, actorId) },
                { listOf(DecisionDraft(ReviewTargetType.ASSOCIATION, it.eventId.value, ReviewAction.ACCEPT, editedValueJson = jsonObjectOf("actor_id" to actorId.value).toString())) },
                Audit(AuditActions.REVIEW_SENDER, CASE, caseId.value) { revised ->
                    listOf("case_id" to caseId.value, "operation" to "assign", "actor_id" to actorId.value, "count" to revised.size)
                },
            )
        }

    /** Withdraws the actor from the events: no actor, association rejected, display label kept. */
    public suspend fun unassignSender(eventIds: List<EventId>): ReviewResult = editEvents(
        eventIds,
        ReviewEdits::unassigned,
        { listOf(DecisionDraft(ReviewTargetType.ASSOCIATION, it.eventId.value, ReviewAction.REJECT)) },
        AuditActions.REVIEW_SENDER,
    ) { caseId, revised -> listOf("case_id" to caseId, "operation" to "unassign", "count" to revised.size) }

    public suspend fun setDirection(eventIds: List<EventId>, direction: Direction): ReviewResult = editEvents(
        eventIds,
        { ReviewEdits.direction(it, direction) },
        { directionDraft(it, direction) },
        AuditActions.REVIEW_DIRECTION,
    ) { caseId, revised -> directionDetails(caseId, direction, revised.size) }

    /** Sets `OUTGOING` on every latest event of the case that shows exactly the [selector]: "this name is me". */
    public suspend fun markOwnMessages(caseId: CaseId, selector: SenderSelector): ReviewResult = atomically {
        if (selector.isEmpty()) return@atomically ReviewResult.Invalid(emptyList(), ReviewProblem.EMPTY_SELECTOR)
        val matching = caseEvents(caseId) ?: return@atomically ReviewResult.NotFound
        commit(
            matching.filter { ReviewEdits.matches(it, selector) },
            { ReviewEdits.direction(it, Direction.OUTGOING) },
            { directionDraft(it, Direction.OUTGOING) },
            Audit(AuditActions.REVIEW_DIRECTION, CASE, caseId.value) { directionDetails(caseId.value, Direction.OUTGOING, it.size) },
        )
    }

    /** Records whether the person wanted this contact. Only the marked-unwanted, marked-wanted and unknown values are allowed. */
    public suspend fun markWantedness(eventIds: List<EventId>, value: UnwantedContact): ReviewResult {
        if (value == UnwantedContact.NOT_APPLICABLE) return ReviewResult.Invalid(emptyList(), ReviewProblem.VALUE_NOT_ALLOWED)
        val name = Codecs.unwantedContact.name(value)
        return editEvents(
            eventIds,
            { ReviewEdits.unwanted(it, value) },
            { listOf(DecisionDraft(ReviewTarget.WANTEDNESS, it.eventId.value, ReviewAction.EDIT, editedValueJson = jsonObjectOf("unwanted_contact" to name).toString())) },
            AuditActions.REVIEW_WANTEDNESS,
        ) { caseId, revised -> listOf("case_id" to caseId, "unwanted_contact" to name, "count" to revised.size) }
    }

    /**
     * Makes an existing event (normally the person's own outgoing message) a confirmed boundary marker for
     * [actorId]. Communication is supported by selected evidence only for an outgoing event that rests on
     * something other than a manual statement; otherwise it is user reported.
     */
    public suspend fun markEventAsBoundary(eventId: EventId, marker: BoundaryMarker, actorId: ActorId): ReviewResult =
        atomically {
            if (!marker.isBoundary()) return@atomically ReviewResult.Invalid(emptyList(), ReviewProblem.VALUE_NOT_ALLOWED)
            val event = latest(eventId) ?: return@atomically ReviewResult.NotFound
            actorProblem(event.caseId, actorId)?.let { return@atomically it }
            commit(
                listOf(event),
                { ReviewEdits.boundary(it, marker, actorId) },
                { boundaryDraft(it, marker, actorId) },
                boundaryAudit(event.caseId.value, marker, actorId),
            )
        }

    /**
     * Records a boundary the person described in a manual note as a NEW `USER_BOUNDARY` event. The note must be
     * evidence of the case with acquisition kind `manual_note`. A statement never gives
     * `SUPPORTED_BY_SELECTED_EVIDENCE`: the status is user reported, or not communicated.
     */
    public suspend fun addBoundaryFromNote(
        caseId: CaseId,
        noteEvidenceId: String,
        marker: BoundaryMarker,
        actorId: ActorId,
        time: TimeBounds,
        communicated: Boolean,
    ): ReviewResult = atomically {
        if (!marker.isBoundary()) return@atomically ReviewResult.Invalid(emptyList(), ReviewProblem.VALUE_NOT_ALLOWED)
        val note = database.evidenceDao().get(noteEvidenceId)
        if (note == null || note.caseId != caseId.value) return@atomically ReviewResult.NotFound
        if (note.acquisitionKind != AcquisitionKind.MANUAL_NOTE) {
            return@atomically ReviewResult.Invalid(emptyList(), ReviewProblem.WRONG_EVIDENCE_KIND)
        }
        actorProblem(caseId, actorId)?.let { return@atomically it }
        val event = ReviewEdits.boundaryNote(
            EventId(ids()), caseId, now(), noteEvidenceId, note.sha256, ReferenceId(ids()), time, marker, actorId, communicated,
        )
        persist(listOf(event), { boundaryDraft(it, marker, actorId) }, boundaryAudit(caseId.value, marker, actorId))
    }

    /**
     * Records that an event repeats another. [DedupStatus.SAME_REPRESENTATION] and [DedupStatus.POSSIBLE_DUPLICATE]
     * need a canonical event of the same case other than itself; [DedupStatus.DISTINCT_OBSERVATION] clears it.
     * Self links, unknown or foreign events and cycles come back as `Invalid`.
     */
    public suspend fun setDuplicate(eventId: EventId, canonicalEventId: EventId?, status: DedupStatus): ReviewResult =
        atomically {
            val canonical = when (status) {
                DedupStatus.DISTINCT_OBSERVATION -> null
                DedupStatus.SAME_REPRESENTATION, DedupStatus.POSSIBLE_DUPLICATE ->
                    canonicalEventId ?: return@atomically ReviewResult.Invalid(emptyList(), ReviewProblem.CANONICAL_REQUIRED)
                DedupStatus.LIFECYCLE_ONLY -> return@atomically ReviewResult.Invalid(emptyList(), ReviewProblem.VALUE_NOT_ALLOWED)
            }
            val event = latest(eventId) ?: return@atomically ReviewResult.NotFound
            val statusName = Codecs.dedupStatus.name(status)
            commit(
                listOf(event),
                { ReviewEdits.duplicate(it, status, canonical) },
                {
                    val value = listOfNotNull("status" to statusName, canonical?.let { c -> "canonical_event_id" to c.value })
                    listOf(DecisionDraft(ReviewTarget.DUPLICATE, eventId.value, ReviewAction.EDIT, editedValueJson = jsonObjectOf(*value.toTypedArray()).toString()))
                },
                Audit(AuditActions.REVIEW_DUPLICATE, EVENT, eventId.value) { revised ->
                    listOf("event_id" to eventId.value, "revision" to revised.first().revision, "status" to statusName)
                },
            )
        }

    /**
     * Creates a person labelled [displayLabel] and confirms them as the sender of the [selector]'s claim (see
     * [assignSender]) in ONE transaction: if nothing is assigned, or anything fails, no person is left behind.
     * A blank or over-long label comes back as `Invalid` with [ReviewProblem.VALUE_NOT_ALLOWED].
     */
    public suspend fun assignSenderToNewPerson(caseId: CaseId, selector: SenderSelector, displayLabel: String): ReviewResult {
        if (displayLabel.isBlank() || displayLabel.length > ActorRegistry.MAX_LABEL_LENGTH) {
            return ReviewResult.Invalid(emptyList(), ReviewProblem.VALUE_NOT_ALLOWED)
        }
        if (selector.isEmpty()) return ReviewResult.Invalid(emptyList(), ReviewProblem.EMPTY_SELECTOR)
        return try {
            atomically {
                if (database.caseDao().get(caseId.value) == null) return@atomically ReviewResult.NotFound
                val actorId = actors.create(caseId, displayLabel, IdentityBasis.USER_ASSERTED, AssociationReview.CONFIRMED)
                val result = assignSender(caseId, selector, actorId)
                if (result !is ReviewResult.Applied) throw RolledBack(result)
                result
            }
        } catch (refused: RolledBack) {
            refused.result
        }
    }

    /**
     * Takes the boundary marker off an event: a new revision with marker `NONE`, no actor and review and
     * communication status not applicable; the wantedness marking stays. `NoChange` when the latest revision has no
     * marker. An event created from a note (`USER_BOUNDARY`) needs its marker, so it comes back as `Invalid` with
     * the violation `BOUNDARY_MARKER_NOT_APPLICABLE` at `/boundary/marker`.
     */
    public suspend fun clearBoundary(eventId: EventId): ReviewResult = atomically {
        val event = latest(eventId) ?: return@atomically ReviewResult.NotFound
        if (event.boundary.marker == BoundaryMarker.NONE) return@atomically ReviewResult.NoChange
        if (event.eventKind == EventKind.USER_BOUNDARY) {
            val violation = Violation(ViolationCode.BOUNDARY_MARKER_NOT_APPLICABLE, "/boundary/marker", "a boundary note event needs a marker")
            return@atomically ReviewResult.Invalid(listOf(violation), ReviewProblem.VALUE_NOT_ALLOWED)
        }
        val cleared = event.boundary.marker
        commit(
            listOf(event),
            ReviewEdits::boundaryCleared,
            { listOf(DecisionDraft(ReviewTarget.BOUNDARY, it.eventId.value, ReviewAction.REJECT, editedValueJson = jsonObjectOf("marker" to Codecs.boundaryMarker.name(BoundaryMarker.NONE)).toString())) },
            Audit(AuditActions.REVIEW_BOUNDARY, EVENT, eventId.value) { revised ->
                listOf(
                    "case_id" to event.caseId.value,
                    "event_id" to eventId.value,
                    "revision" to revised.first().revision,
                    "marker" to Codecs.boundaryMarker.name(BoundaryMarker.NONE),
                    "cleared_marker" to Codecs.boundaryMarker.name(cleared),
                )
            },
        )
    }

    /**
     * Every decision that targets the event or any of its categories, across all its revisions, oldest first, with
     * the target and change already typed.
     */
    public suspend fun decisionsForEvent(eventId: EventId): List<StoredDecision> =
        withContext(dispatcher) { database.withTransaction { DecisionReader(database).forEvent(eventId) } }

    /** Every decision about [targetId] of any target type, oldest first. */
    public suspend fun decisions(targetId: String): List<StoredDecision> =
        withContext(dispatcher) { database.findingDao().getDecisionsForTarget(targetId).map { it.toStored() } }

    /** The newest decision about the target, or null. */
    public suspend fun latestDecision(targetType: String, targetId: String): StoredDecision? =
        withContext(dispatcher) { database.findingDao().getLatestDecision(targetType, targetId)?.toStored() }

    private class RolledBack(val result: ReviewResult) : RuntimeException()

    private class Audit(
        val action: String,
        val subjectType: String,
        val subjectId: String?,
        val details: (List<Event>) -> List<Pair<String, Any>>,
    )

    private suspend fun <T> atomically(block: suspend () -> T): T =
        withContext(dispatcher) { database.withTransaction { block() } }

    private fun now(): Timestamp = Timestamp(clock().toString())

    private suspend fun latest(eventId: EventId): Event? {
        val revision = database.eventDao().getLatestRevisionNumber(eventId.value) ?: return null
        return events.load(eventId, revision)
    }

    private suspend fun caseEvents(caseId: CaseId): List<Event>? {
        if (database.caseDao().get(caseId.value) == null) return null
        return events.loadLatest(caseId, Instant.ofEpochMilli(Long.MAX_VALUE))
    }

    private suspend fun actorProblem(caseId: CaseId, actorId: ActorId): ReviewResult.Invalid? {
        val owner = database.eventDao().getActor(actorId.value)?.caseId
        val code = when (owner) {
            null -> ViolationCode.ACTOR_UNKNOWN
            caseId.value -> return null
            else -> ViolationCode.ACTOR_CROSS_CASE
        }
        return ReviewResult.Invalid(listOf(Violation(code, "/actor_id", "actor is not in the case")), ReviewProblem.ACTOR_UNKNOWN)
    }

    private suspend fun editEvents(
        eventIds: List<EventId>,
        change: (Event) -> Event,
        decide: (Event) -> List<DecisionDraft>,
        auditAction: String,
        details: (String, List<Event>) -> List<Pair<String, Any>>,
    ): ReviewResult = atomically {
        val originals = eventIds.distinct().map { latest(it) ?: return@atomically ReviewResult.NotFound }
        if (originals.isEmpty()) return@atomically ReviewResult.NoChange
        val caseId = originals.first().caseId
        if (originals.any { it.caseId != caseId }) return@atomically ReviewResult.Invalid(emptyList(), ReviewProblem.MIXED_CASE)
        commit(originals, change, decide, Audit(auditAction, CASE, caseId.value) { details(caseId.value, it) })
    }

    /** Builds the revisions of the events that [change] really alters, then [persist]s them. */
    private suspend fun commit(
        originals: List<Event>,
        change: (Event) -> Event,
        decide: (Event) -> List<DecisionDraft>,
        audit: Audit,
    ): ReviewResult {
        val at = now()
        val revised = originals.mapNotNull { original ->
            val changed = change(original)
            if (changed == original) null else changed.copy(revision = original.revision + 1, availableAt = at)
        }
        return if (revised.isEmpty()) ReviewResult.NoChange else persist(revised, decide, audit)
    }

    private suspend fun persist(revised: List<Event>, decide: (Event) -> List<DecisionDraft>, audit: Audit): ReviewResult {
        when (val saved = events.saveAll(revised)) {
            is BatchSaveResult.Saved -> Unit
            BatchSaveResult.UnknownCase -> return ReviewResult.NotFound
            is BatchSaveResult.Invalid -> return ReviewResult.Invalid(
                saved.failures.flatMap { it.violations },
                saved.failures.firstNotNullOfOrNull { it.problem }?.let {
                    if (it == BatchProblem.MIXED_CASE) ReviewProblem.MIXED_CASE else ReviewProblem.NOT_STORABLE
                },
            )
        }
        val at = clock()
        for (event in revised) {
            for (draft in decide(event)) {
                database.findingDao().insertDecision(
                    ReviewDecisionEntity(
                        id = ids(),
                        caseId = event.caseId.value,
                        targetType = draft.targetType,
                        targetId = draft.targetId,
                        targetRevision = event.revision,
                        action = draft.action,
                        reasonCode = draft.reasonCode,
                        editedValueJson = draft.editedValueJson,
                        note = null,
                        decidedAt = at.toString(),
                        decidedAtEpochMs = at.toEpochMilli(),
                    ),
                )
            }
        }
        this.audit.append(audit.action, audit.subjectType, audit.subjectId ?: revised.first().eventId.value, jsonObjectOf(*audit.details(revised).toTypedArray()))
        return ReviewResult.Applied(revised.map { it.eventId })
    }

    private fun directionDraft(event: Event, direction: Direction): List<DecisionDraft> = listOf(
        DecisionDraft(
            ReviewTarget.DIRECTION, event.eventId.value, ReviewAction.EDIT,
            editedValueJson = jsonObjectOf("direction" to Codecs.direction.name(direction)).toString(),
        ),
    )

    private fun directionDetails(caseId: String, direction: Direction, count: Int): List<Pair<String, Any>> =
        listOf("case_id" to caseId, "direction" to Codecs.direction.name(direction), "count" to count)

    private fun boundaryDraft(event: Event, marker: BoundaryMarker, actorId: ActorId): List<DecisionDraft> = listOf(
        DecisionDraft(
            ReviewTarget.BOUNDARY, event.eventId.value, ReviewAction.ACCEPT,
            editedValueJson = jsonObjectOf("marker" to Codecs.boundaryMarker.name(marker), "actor_id" to actorId.value).toString(),
        ),
    )

    private fun boundaryAudit(caseId: String, marker: BoundaryMarker, actorId: ActorId): Audit =
        Audit(AuditActions.REVIEW_BOUNDARY, EVENT, null) { revised ->
            listOf(
                "case_id" to caseId,
                "event_id" to revised.first().eventId.value,
                "revision" to revised.first().revision,
                "marker" to Codecs.boundaryMarker.name(marker),
                "actor_id" to actorId.value,
                "communication_status" to Codecs.communicationStatus.name(revised.first().boundary.communicationStatus),
            )
        }

    private fun SenderSelector.isEmpty(): Boolean = displayLabel == null && sourceApp == null && conversationScopeId == null

    private fun BoundaryMarker.isBoundary(): Boolean = this != BoundaryMarker.NONE && this != BoundaryMarker.UNKNOWN

    private companion object {
        const val EVENT = "event"
        const val CASE = "case"
    }
}
