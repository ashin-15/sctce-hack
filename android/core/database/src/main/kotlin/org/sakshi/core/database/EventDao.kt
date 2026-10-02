package org.sakshi.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** The newest revision number of an event and the canonical event that revision names. */
public data class EventHead(
    @ColumnInfo(name = "event_id") val eventId: String,
    val revision: Int,
    @ColumnInfo(name = "canonical_event_id") val canonicalEventId: String?,
)

/** The newest revision of an event and how many evidence anchors it has, in the order [EventDao.getLatestHeads] lists. */
public data class LatestHead(
    @ColumnInfo(name = "event_id") val eventId: String,
    val revision: Int,
    @ColumnInfo(name = "anchor_count") val anchorCount: Int,
)

/**
 * One sender claim among the newest revisions of a case: the events that show the same sender label in the same app
 * and conversation. [earliestObservedAt] is the RFC 3339 text of the earliest `observed_at` of the group.
 */
public data class SenderClaimRow(
    @ColumnInfo(name = "display_label") val displayLabel: String,
    @ColumnInfo(name = "source_app") val sourceApp: String?,
    @ColumnInfo(name = "conversation_scope_id") val conversationScopeId: String?,
    @ColumnInfo(name = "message_count") val messageCount: Int,
    @ColumnInfo(name = "outgoing_count") val outgoingCount: Int,
    /** Julian day the earliest time was chosen by; it only picks the row and is not meant for display. */
    @ColumnInfo(name = "earliest_julian") val earliestJulian: Double?,
    @ColumnInfo(name = "earliest_observed_at") val earliestObservedAt: String,
)

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
    public abstract suspend fun insertEvents(events: List<EventEntity>)

    @Insert
    public abstract suspend fun insertRevisions(revisions: List<EventRevisionEntity>)

    @Insert
    public abstract suspend fun insertLinks(links: List<EventLinkEntity>)

    @Insert
    public abstract suspend fun insertBoundaries(boundaries: List<BoundaryEntity>)

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

    @Query("SELECT * FROM event WHERE id = :eventId")
    public abstract suspend fun getEvent(eventId: String): EventEntity?

    /** The events with these ids, whichever case they belong to. Keep the list below about 500 ids. */
    @Query("SELECT * FROM event WHERE id IN (:eventIds)")
    public abstract suspend fun getEventsByIds(eventIds: List<String>): List<EventEntity>

    /** Newest revision number and canonical pointer of every event in the case. */
    @Query(
        "SELECT r.event_id AS event_id, r.revision AS revision, r.canonical_event_id AS canonical_event_id " +
            "FROM event_revision r JOIN event e ON e.id = r.event_id WHERE e.case_id = :caseId " +
            "AND r.revision = (SELECT MAX(r2.revision) FROM event_revision r2 WHERE r2.event_id = r.event_id)",
    )
    public abstract suspend fun getHeadsForCase(caseId: String): List<EventHead>

    @Query("SELECT * FROM actor WHERE id = :actorId")
    public abstract suspend fun getActor(actorId: String): ActorEntity?

    /** The highest stored revision number of the event, or null when the event has no revision. */
    @Query("SELECT MAX(revision) FROM event_revision WHERE event_id = :eventId")
    public abstract suspend fun getLatestRevisionNumber(eventId: String): Int?

    @Query("SELECT * FROM event_revision WHERE event_id = :eventId AND revision = :revision")
    public abstract suspend fun getRevision(eventId: String, revision: Int): EventRevisionEntity?

    /** Every revision of every event in the case, by event id and revision. */
    @Query(
        "SELECT r.* FROM event_revision r JOIN event e ON e.id = r.event_id " +
            "WHERE e.case_id = :caseId ORDER BY r.event_id, r.revision",
    )
    public abstract suspend fun getRevisionsForCase(caseId: String): List<EventRevisionEntity>

    /** The canonical event named by the newest revision of the event, or null when there is none. */
    @Query("SELECT canonical_event_id FROM event_revision WHERE event_id = :eventId ORDER BY revision DESC LIMIT 1")
    public abstract suspend fun getLatestCanonicalEventId(eventId: String): String?

    @Query(
        "SELECT a.* FROM evidence_anchor a JOIN event e ON e.id = a.event_id " +
            "WHERE e.case_id = :caseId ORDER BY a.id",
    )
    public abstract suspend fun getAnchorsForCase(caseId: String): List<EvidenceAnchorEntity>

    /** Links leaving [eventId] whose id starts with [idPrefix]; the prefix is compared literally. */
    @Query(
        "SELECT * FROM event_link WHERE from_event = :eventId " +
            "AND substr(id, 1, length(:idPrefix)) = :idPrefix ORDER BY id",
    )
    public abstract suspend fun getLinksFrom(eventId: String, idPrefix: String): List<EventLinkEntity>

    @Query(
        "SELECT l.* FROM event_link l JOIN event e ON e.id = l.from_event " +
            "WHERE e.case_id = :caseId ORDER BY l.id",
    )
    public abstract suspend fun getLinksForCase(caseId: String): List<EventLinkEntity>

    @Query("SELECT * FROM boundary WHERE id = :boundaryId")
    public abstract suspend fun getBoundary(boundaryId: String): BoundaryEntity?

    @Query("SELECT * FROM coverage_gap WHERE case_id = :caseId ORDER BY start_at_epoch_ms, id")
    public abstract suspend fun getCoverageGaps(caseId: String): List<CoverageGapEntity>

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

    /**
     * Newest revision of each event in the case with its anchor count, in the order of [getLatestRevisions]. The
     * anchor count lets a cache notice that evidence deletion removed anchors from an older, unchanged revision.
     */
    @Query(
        "SELECT r.event_id AS event_id, r.revision AS revision, " +
            "(SELECT COUNT(*) FROM evidence_anchor a WHERE a.event_id = r.event_id AND a.event_revision = r.revision) " +
            "AS anchor_count FROM event_revision r JOIN event e ON e.id = r.event_id " +
            "WHERE e.case_id = :caseId AND r.revision = " +
            "(SELECT MAX(r2.revision) FROM event_revision r2 WHERE r2.event_id = r.event_id) " +
            "ORDER BY r.ts_earliest_epoch_ms, r.event_id",
    )
    public abstract suspend fun getLatestHeads(caseId: String): List<LatestHead>

    /** Every revision of the events with these ids. Keep the list below about 500 ids. */
    @Query("SELECT * FROM event_revision WHERE event_id IN (:eventIds) ORDER BY event_id, revision")
    public abstract suspend fun getRevisionsByEventIds(eventIds: List<String>): List<EventRevisionEntity>

    /** Every anchor of the events with these ids. Keep the list below about 500 ids. */
    @Query("SELECT * FROM evidence_anchor WHERE event_id IN (:eventIds) ORDER BY id")
    public abstract suspend fun getAnchorsByEventIds(eventIds: List<String>): List<EvidenceAnchorEntity>

    /** Every link leaving the events with these ids. Keep the list below about 500 ids. */
    @Query("SELECT * FROM event_link WHERE from_event IN (:eventIds) ORDER BY id")
    public abstract suspend fun getLinksFromEvents(eventIds: List<String>): List<EventLinkEntity>

    /** Every boundary row of the events with these ids. Keep the list below about 500 ids. */
    @Query("SELECT * FROM boundary WHERE event_id IN (:eventIds) ORDER BY id")
    public abstract suspend fun getBoundariesByEventIds(eventIds: List<String>): List<BoundaryEntity>

    /**
     * Sender claims among the newest revisions of the case: those with a sender label and no actor whose
     * association is [confirmedReview] (the stored name of the confirmed review). [outgoingDirection] is the stored
     * name of the outgoing direction.
     */
    @Query(
        "SELECT r.sender_display_label AS display_label, r.source_app AS source_app, " +
            "r.conversation_scope_id AS conversation_scope_id, COUNT(*) AS message_count, " +
            "SUM(CASE WHEN r.direction = :outgoingDirection THEN 1 ELSE 0 END) AS outgoing_count, " +
            "MIN(julianday(r.observed_at)) AS earliest_julian, r.observed_at AS earliest_observed_at " +
            "FROM event_revision r JOIN event e ON e.id = r.event_id " +
            "WHERE e.case_id = :caseId AND r.revision = " +
            "(SELECT MAX(r2.revision) FROM event_revision r2 WHERE r2.event_id = r.event_id) " +
            "AND r.sender_display_label IS NOT NULL " +
            "AND (r.actor_id IS NULL OR r.sender_association_review <> :confirmedReview) " +
            "GROUP BY r.sender_display_label, r.source_app, r.conversation_scope_id " +
            "ORDER BY r.sender_display_label, r.source_app, r.conversation_scope_id",
    )
    public abstract suspend fun getSenderClaims(caseId: String, confirmedReview: String, outgoingDirection: String): List<SenderClaimRow>
}
