package org.sakshi.core.vault

import org.sakshi.core.database.BoundaryEntity
import org.sakshi.core.database.EventLinkEntity
import org.sakshi.core.database.EventRevisionEntity
import org.sakshi.core.database.EvidenceAnchorEntity
import org.sakshi.core.database.FindingEntity

/** A finding row with the ids of the anchors it rests on, in the order the producer listed them. */
internal class FindingRows(val finding: FindingEntity, val anchorIds: List<String>)

/** Every row that together describe one stored revision of an event. */
internal class RevisionRows(
    val revision: EventRevisionEntity,
    val anchors: List<EvidenceAnchorEntity>,
    val findings: List<FindingRows>,
    val links: List<EventLinkEntity>,
    val boundary: BoundaryEntity?,
)

/**
 * Primary keys of the rows of one event revision. Each key starts with `eventId/revision/` and ends with a
 * letter naming the kind of row and, for lists, a zero-padded position, so that sorting the keys of one
 * revision as text gives list order and a literal prefix test selects exactly one revision's rows.
 */
internal object RowKeys {
    /** Largest list the keys can order. */
    const val MAX_LIST_SIZE: Int = 999_999
    private const val WIDTH = 6

    fun prefix(eventId: String, revision: Int): String = "$eventId/$revision/"

    fun anchor(eventId: String, revision: Int, index: Int): String = listKey(eventId, revision, 'a', index)

    fun finding(eventId: String, revision: Int, index: Int): String = listKey(eventId, revision, 'c', index)

    fun link(eventId: String, revision: Int, index: Int): String = listKey(eventId, revision, 'l', index)

    fun boundary(eventId: String, revision: Int): String = prefix(eventId, revision) + 'b'

    private fun listKey(eventId: String, revision: Int, kind: Char, index: Int): String =
        prefix(eventId, revision) + kind + index.toString().padStart(WIDTH, '0')
}
