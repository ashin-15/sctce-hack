package org.sakshi.core.temporal

import org.sakshi.core.model.DedupStatus
import org.sakshi.core.model.EventId
import org.sakshi.core.model.RelationshipReviewStatus
import org.sakshi.core.model.RelationshipType

/**
 * One distinct contact. [members] are all event revisions merged into it, [representative] first.
 * Groups that are only possible duplicates of each other share a [clusterKey].
 */
internal class ContactGroup(
    val representative: Selected,
    val members: List<Selected>,
    val clusterKey: EventId,
    val clusterSize: Int,
    val scope: ActorScope,
) {
    val interval: Interval? get() = representative.interval
    val id: EventId get() = representative.event.eventId
    val pending: Boolean = members.any { it.pending }
}

internal class DisjointSets<T : Any> {
    private val parent = HashMap<T, T>()

    fun find(item: T): T {
        var root = item
        while (true) {
            val next = parent[root] ?: break
            if (next == root) break
            root = next
        }
        var current = item
        while (current != root) {
            val next = parent[current] ?: root
            parent[current] = root
            current = next
        }
        return root
    }

    fun union(a: T, b: T) {
        val rootA = find(a)
        val rootB = find(b)
        if (rootA != rootB) parent[rootA] = rootB
    }
}

/**
 * Turns eligible contact events into distinct contacts. Only source-supported or reviewed links merge
 * events; text, hash, time or label equality never does.
 */
internal object ContactCanonicalizer {
    fun group(selected: List<Selected>): List<ContactGroup> {
        val contacts = selected.filter { it.event.isContact() }
        val known = contacts.associateBy { it.event.eventId }
        val definite = DisjointSets<EventId>()
        val possibleLinks = mutableListOf<Pair<EventId, EventId>>()
        for (contact in contacts) {
            val id = contact.event.eventId
            val canonical = contact.event.deduplication.canonicalEventId
            if (canonical != null) {
                when (contact.event.deduplication.status) {
                    DedupStatus.SAME_REPRESENTATION -> definite.union(id, canonical)
                    DedupStatus.POSSIBLE_DUPLICATE -> if (canonical in known) possibleLinks += id to canonical
                    DedupStatus.DISTINCT_OBSERVATION, DedupStatus.LIFECYCLE_ONLY -> Unit
                }
            }
            for (relationship in contact.event.relationshipToPreviousEvents) {
                if (relationship.type != RelationshipType.POSSIBLE_SAME_OCCURRENCE) continue
                if (relationship.targetEventId !in known) continue
                when (relationship.reviewStatus) {
                    RelationshipReviewStatus.CONFIRMED -> definite.union(id, relationship.targetEventId)
                    RelationshipReviewStatus.UNREVIEWED, RelationshipReviewStatus.UNKNOWN ->
                        possibleLinks += id to relationship.targetEventId
                    RelationshipReviewStatus.REJECTED -> Unit
                }
            }
        }
        val components =
            contacts
                .groupBy { definite.find(it.event.eventId) }
                .values
                .map { members -> members.sortedWith(selectedOrder) }
                .sortedWith { a, b -> selectedOrder.compare(a.first(), b.first()) }
        val indexByMember = HashMap<EventId, Int>()
        components.forEachIndexed { index, members -> members.forEach { indexByMember[it.event.eventId] = index } }
        val clusters = DisjointSets<Int>()
        for ((a, b) in possibleLinks) {
            val indexA = indexByMember.getValue(a)
            val indexB = indexByMember.getValue(b)
            if (indexA != indexB) clusters.union(indexA, indexB)
        }
        val clusterMembers = components.indices.groupBy { clusters.find(it) }
        val clusterOfComponent = HashMap<Int, List<Int>>()
        clusterMembers.values.forEach { indices -> indices.forEach { clusterOfComponent[it] = indices } }
        return components.mapIndexed { index, members ->
            val cluster = clusterOfComponent.getValue(index)
            ContactGroup(
                representative = members.first(),
                members = members,
                clusterKey = components[cluster.min()].first().event.eventId,
                clusterSize = cluster.size,
                scope = members.first().event.actorScope(),
            )
        }
    }
}
