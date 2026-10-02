package org.sakshi.core.vault

import org.sakshi.core.database.EventDao
import org.sakshi.core.database.EvidenceDao
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.ArtifactId
import org.sakshi.core.model.CaseContext
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventId
import org.sakshi.core.model.EventValidator
import org.sakshi.core.model.Violation

/** Why an event of a batch was refused, when it is not a model violation. */
public enum class BatchProblem {
    /** The event belongs to a different case from the first event of the batch. */
    MIXED_CASE,

    /** The revision is not the next one for the event, or the event id belongs to another case. */
    REVISION_CONFLICT,

    /** The event holds fields the tables cannot store without loss; see [SaveResult.Unsupported]. */
    UNSUPPORTED,
}

/** One refused event of a batch: its position in the list, model violations and any other [problem]. */
public data class IndexedFailure(
    val index: Int,
    val violations: List<Violation>,
    val problem: BatchProblem? = null,
    val expectedRevision: Int? = null,
)

/** Outcome of [EventStore.saveAll]. Anything but [Saved] means nothing was written. */
public sealed interface BatchSaveResult {
    public data class Saved(val count: Int) : BatchSaveResult

    public data class Invalid(val failures: List<IndexedFailure>) : BatchSaveResult

    /** The case of the first event does not exist. */
    public data object UnknownCase : BatchSaveResult
}

/**
 * Everything validation of a batch needs, read in a few bulk queries and then updated in memory as each valid
 * event is accepted, so later events see earlier ones. Events may refer to stored events and to events that come
 * earlier in the same batch, because rows are written in list order.
 */
internal class EventBatch private constructor(
    private val caseId: CaseId,
    private val eventCases: MutableMap<EventId, CaseId>,
    private val actorCases: MutableMap<ActorId, CaseId>,
    private val canonicals: MutableMap<EventId, EventId?>,
    private val latest: MutableMap<String, Int>,
    private val foreign: Set<String>,
    val evidenceIds: Set<String>,
    private val lengths: (ArtifactId) -> Int?,
) : CaseContext {
    override fun caseOfEvent(eventId: EventId): CaseId? = eventCases[eventId]

    override fun caseOfActor(actorId: ActorId): CaseId? = actorCases[actorId]

    override fun canonicalOf(eventId: EventId): EventId? = canonicals[eventId]

    override fun codePointLength(artifactId: ArtifactId): Int? = lengths(artifactId)

    /** Returns why [event] cannot be written after the events accepted so far, or null and accepts it. */
    fun accept(index: Int, event: Event): IndexedFailure? {
        if (event.caseId != caseId) return IndexedFailure(index, emptyList(), BatchProblem.MIXED_CASE)
        val unsupported = EventRowWriter.unsupportedFields(event)
        if (unsupported.isNotEmpty()) return IndexedFailure(index, emptyList(), BatchProblem.UNSUPPORTED)
        val violations = EventValidator.validate(event, this)
        if (violations.isNotEmpty()) return IndexedFailure(index, violations)
        val id = event.eventId.value
        val expected = (latest[id] ?: 0) + 1
        if (event.revision != expected || id in foreign) {
            return IndexedFailure(index, emptyList(), BatchProblem.REVISION_CONFLICT, expected)
        }
        latest[id] = event.revision
        eventCases[event.eventId] = caseId
        canonicals[event.eventId] = event.deduplication.canonicalEventId
        return null
    }

    companion object {
        private const val CHUNK = 500
        private const val MAX_CANONICAL_HOPS = 1024

        suspend fun load(
            events: EventDao,
            evidence: EvidenceDao,
            batch: List<Event>,
            lengths: (ArtifactId) -> Int?,
        ): EventBatch {
            val caseId = batch.first().caseId
            val eventCases = mutableMapOf<EventId, CaseId>()
            val canonicals = mutableMapOf<EventId, EventId?>()
            val latest = mutableMapOf<String, Int>()
            for (head in events.getHeadsForCase(caseId.value)) {
                latest[head.eventId] = head.revision
                eventCases[EventId(head.eventId)] = caseId
                canonicals[EventId(head.eventId)] = head.canonicalEventId?.let(::EventId)
            }

            val foreign = mutableSetOf<String>()
            val batchIds = batch.map { it.eventId.value }.toSet()
            for (chunk in (batchIds - latest.keys).chunked(CHUNK)) {
                for (row in events.getEventsByIds(chunk)) {
                    foreign += row.id
                    eventCases[EventId(row.id)] = CaseId(row.caseId)
                }
            }

            val referenced = batch.flatMap { event ->
                event.relationshipToPreviousEvents.map { it.targetEventId } + listOfNotNull(event.deduplication.canonicalEventId)
            }.toSet()
            for (id in referenced) {
                if (id in eventCases || id.value in batchIds) continue
                events.getEvent(id.value)?.let { eventCases[id] = CaseId(it.caseId) }
            }
            for (id in referenced) {
                var current: EventId? = id
                var hops = 0
                while (current != null && current !in canonicals && current.value !in batchIds && hops < MAX_CANONICAL_HOPS) {
                    val next = events.getLatestCanonicalEventId(current.value)?.let(::EventId)
                    canonicals[current] = next
                    current = next
                    hops++
                }
            }

            val actorCases = mutableMapOf<ActorId, CaseId>()
            for (actor in events.getActors(caseId.value)) actorCases[ActorId(actor.id)] = caseId
            val actorIds = batch.flatMap { listOfNotNull(it.sender.actorId, it.boundary.actorId) }.toSet()
            for (id in actorIds) {
                if (id !in actorCases) events.getActor(id.value)?.let { actorCases[id] = CaseId(it.caseId) }
            }
            return EventBatch(
                caseId, eventCases, actorCases, canonicals, latest, foreign,
                evidence.getIdsForCase(caseId.value).toSet(), lengths,
            )
        }
    }
}
