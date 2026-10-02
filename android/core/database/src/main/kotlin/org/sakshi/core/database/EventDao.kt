package org.sakshi.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Access to events, their revisions and the rows that describe them. */
@Dao
public abstract class EventDao {
    @Insert
    public abstract suspend fun insertActor(actor: ActorEntity)

    @Update
    public abstract suspend fun updateActor(actor: ActorEntity): Int

    @Insert
    public abstract suspend fun insertSourceScope(scope: SourceScopeEntity)

    @Insert
    public abstract suspend fun insertEvent(event: EventEntity)

    @Insert
    public abstract suspend fun insertRevision(revision: EventRevisionEntity)

    @Insert
    public abstract suspend fun insertAnchors(anchors: List<EvidenceAnchorEntity>)

    @Insert
    public abstract suspend fun insertLink(link: EventLinkEntity)

    @Insert
    public abstract suspend fun insertBoundary(boundary: BoundaryEntity)

    @Insert
    public abstract suspend fun insertCoverageGap(gap: CoverageGapEntity)

    /** Inserts a new event with its first revision and anchors atomically. */
    @Transaction
    public open suspend fun insertEventWithRevision(
        event: EventEntity,
        revision: EventRevisionEntity,
        anchors: List<EvidenceAnchorEntity>,
    ) {
        insertEvent(event)
        insertRevision(revision)
        if (anchors.isNotEmpty()) insertAnchors(anchors)
    }

    /** Adds a later revision of an existing event with its anchors atomically. */
    @Transaction
    public open suspend fun addRevision(revision: EventRevisionEntity, anchors: List<EvidenceAnchorEntity>) {
        insertRevision(revision)
        if (anchors.isNotEmpty()) insertAnchors(anchors)
    }

    @Query("SELECT * FROM actor WHERE case_id = :caseId ORDER BY id")
    public abstract suspend fun getActors(caseId: String): List<ActorEntity>

    @Query("SELECT * FROM source_scope WHERE case_id = :caseId ORDER BY id")
    public abstract suspend fun getSourceScopes(caseId: String): List<SourceScopeEntity>

    @Query("SELECT * FROM event_revision WHERE event_id = :eventId ORDER BY revision")
    public abstract suspend fun getRevisions(eventId: String): List<EventRevisionEntity>

    @Query("SELECT * FROM evidence_anchor WHERE event_id = :eventId AND event_revision = :revision ORDER BY id")
    public abstract suspend fun getAnchors(eventId: String, revision: Int): List<EvidenceAnchorEntity>

    @Query("SELECT * FROM event_link WHERE from_event = :eventId OR to_event = :eventId ORDER BY id")
    public abstract suspend fun getLinks(eventId: String): List<EventLinkEntity>

    @Query("SELECT * FROM boundary WHERE case_id = :caseId ORDER BY id")
    public abstract suspend fun getBoundaries(caseId: String): List<BoundaryEntity>

    @Query("SELECT * FROM coverage_gap WHERE case_id = :caseId ORDER BY start_at_epoch_ms, id")
    public abstract fun observeCoverageGaps(caseId: String): Flow<List<CoverageGapEntity>>

    /**
     * The newest revision of each event in the case that was available at [cutoffEpochMs], in
     * time order. Events with no revision available by then are omitted.
     */
    @Query(
        "SELECT r.* FROM event_revision r JOIN event e ON e.id = r.event_id " +
            "WHERE e.case_id = :caseId AND r.available_at_epoch_ms <= :cutoffEpochMs " +
            "AND r.revision = (SELECT MAX(r2.revision) FROM event_revision r2 " +
            "WHERE r2.event_id = r.event_id AND r2.available_at_epoch_ms <= :cutoffEpochMs) " +
            "ORDER BY r.ts_earliest_epoch_ms, r.event_id",
    )
    public abstract suspend fun getLatestRevisions(caseId: String, cutoffEpochMs: Long): List<EventRevisionEntity>

    /** Like [getLatestRevisions] with no cutoff, re-emitting when events change. */
    @Query(
        "SELECT r.* FROM event_revision r JOIN event e ON e.id = r.event_id " +
            "WHERE e.case_id = :caseId AND r.revision = " +
            "(SELECT MAX(r2.revision) FROM event_revision r2 WHERE r2.event_id = r.event_id) " +
            "ORDER BY r.ts_earliest_epoch_ms, r.event_id",
    )
    public abstract fun observeLatestRevisions(caseId: String): Flow<List<EventRevisionEntity>>
}
