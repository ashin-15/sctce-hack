package org.sakshi.core.vault

import org.sakshi.core.database.EventDao
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.ArtifactId
import org.sakshi.core.model.CaseContext
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventId

/**
 * A [CaseContext] over rows read ahead of validation, because the validator's interface is not suspending.
 * [load] reads exactly what [org.sakshi.core.model.EventValidator] can ask about for one event.
 */
internal class StoredCaseContext private constructor(
    private val eventCases: Map<EventId, CaseId>,
    private val actorCases: Map<ActorId, CaseId>,
    private val canonicals: Map<EventId, EventId?>,
    private val lengths: (ArtifactId) -> Int?,
) : CaseContext {
    override fun caseOfEvent(eventId: EventId): CaseId? = eventCases[eventId]

    override fun caseOfActor(actorId: ActorId): CaseId? = actorCases[actorId]

    override fun canonicalOf(eventId: EventId): EventId? = canonicals[eventId]

    override fun codePointLength(artifactId: ArtifactId): Int? = lengths(artifactId)

    companion object {
        private const val MAX_CANONICAL_HOPS = 1024

        suspend fun load(dao: EventDao, event: Event, lengths: (ArtifactId) -> Int?): StoredCaseContext {
            val eventCases = mutableMapOf<EventId, CaseId>()
            suspend fun remember(id: EventId) {
                if (id !in eventCases) dao.getEvent(id.value)?.let { eventCases[id] = CaseId(it.caseId) }
            }
            event.relationshipToPreviousEvents.forEach { remember(it.targetEventId) }

            val canonicals = mutableMapOf<EventId, EventId?>()
            var current = event.deduplication.canonicalEventId
            var hops = 0
            while (current != null && current !in canonicals && hops < MAX_CANONICAL_HOPS) {
                remember(current)
                val next = dao.getLatestCanonicalEventId(current.value)?.let(::EventId)
                canonicals[current] = next
                current = next
                hops++
            }

            val actorCases = mutableMapOf<ActorId, CaseId>()
            for (actorId in listOfNotNull(event.sender.actorId, event.boundary.actorId)) {
                dao.getActor(actorId.value)?.let { actorCases[actorId] = CaseId(it.caseId) }
            }
            return StoredCaseContext(eventCases, actorCases, canonicals, lengths)
        }
    }
}
