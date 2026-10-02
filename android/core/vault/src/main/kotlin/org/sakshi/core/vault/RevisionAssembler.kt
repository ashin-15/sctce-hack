package org.sakshi.core.vault

import org.sakshi.core.database.EventRevisionEntity
import org.sakshi.core.database.SakshiDatabase
import org.sakshi.core.model.Event

/**
 * Rebuilds events from chosen revision rows with a fixed number of queries per [CHUNK] events: dependent rows are
 * fetched by event id for the whole chunk, never per event. Must run inside the caller's transaction.
 */
internal class RevisionAssembler(private val database: SakshiDatabase) {
    /** The given (event id, revision) pairs, in the order given. Pairs that do not exist are left out. */
    suspend fun assembleKeys(caseId: String, keys: List<Pair<String, Int>>): List<Event> {
        val built = ArrayList<Event>(keys.size)
        for (chunk in keys.chunked(CHUNK)) {
            val wanted = chunk.toSet()
            val rows = database.eventDao().getRevisionsByEventIds(chunk.map { it.first }.distinct())
                .filter { (it.eventId to it.revision) in wanted }
            built += assemble(caseId, rows)
        }
        return built
    }

    suspend fun assemble(caseId: String, revisions: List<EventRevisionEntity>): List<Event> {
        if (revisions.isEmpty()) return emptyList()
        val dao = database.eventDao()
        val findingDao = database.findingDao()
        val built = ArrayList<Event>(revisions.size)
        for (chunk in revisions.chunked(CHUNK)) {
            val ids = chunk.map { it.eventId }.distinct()
            val anchors = dao.getAnchorsByEventIds(ids).groupBy { it.eventId to it.eventRevision }
            val findings = findingDao.getForEvents(ids).groupBy { it.eventId to it.eventRevision }
            val findingAnchors = findingDao.getFindingAnchorsForEvents(ids).groupBy { it.findingId }
            val links = dao.getLinksFromEvents(ids).groupBy { it.fromEvent }
            val boundaries = dao.getBoundariesByEventIds(ids).associateBy { it.id }
            for (row in chunk) {
                val key = row.eventId to row.revision
                val prefix = RowKeys.prefix(row.eventId, row.revision)
                built += EventRowReader.assemble(
                    caseId,
                    RevisionRows(
                        row,
                        anchors[key].orEmpty(),
                        findings[key].orEmpty().map { finding ->
                            FindingRows(finding, findingAnchors[finding.id].orEmpty().map { it.anchorId })
                        },
                        links[row.eventId].orEmpty().filter { it.id.startsWith(prefix) },
                        boundaries[RowKeys.boundary(row.eventId, row.revision)],
                    ),
                )
            }
        }
        return built
    }

    private companion object {
        /** Below SQLite's bound-variable limit on every Android version. */
        const val CHUNK: Int = 500
    }
}
