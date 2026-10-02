package org.sakshi.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Access to findings, their anchors and the review decisions about them. */
@Dao
public abstract class FindingDao {
    @Insert
    public abstract suspend fun insertFinding(finding: FindingEntity)

    @Insert
    public abstract suspend fun insertFindings(findings: List<FindingEntity>)

    @Insert
    public abstract suspend fun insertFindingAnchors(anchors: List<FindingAnchorEntity>)

    @Insert
    public abstract suspend fun insertDecision(decision: ReviewDecisionEntity)

    /** Inserts a finding with the anchors it rests on atomically. */
    @Transaction
    public open suspend fun insertWithAnchors(finding: FindingEntity, anchorIds: List<String>) {
        insertFinding(finding)
        if (anchorIds.isNotEmpty()) insertFindingAnchors(anchorIds.map { FindingAnchorEntity(finding.id, it) })
    }

    @Query("SELECT * FROM finding WHERE id = :id")
    public abstract suspend fun get(id: String): FindingEntity?

    @Query("SELECT * FROM finding WHERE case_id = :caseId ORDER BY created_at, id")
    public abstract fun observeForCase(caseId: String): Flow<List<FindingEntity>>

    @Query("SELECT * FROM finding WHERE event_id = :eventId AND event_revision = :revision ORDER BY id")
    public abstract suspend fun getForEventRevision(eventId: String, revision: Int): List<FindingEntity>

    @Query("SELECT * FROM finding WHERE case_id = :caseId ORDER BY id")
    public abstract suspend fun getForCase(caseId: String): List<FindingEntity>

    /** The anchors of a finding in the order they were inserted, which is the order the producer listed them. */
    @Query("SELECT * FROM finding_anchor WHERE finding_id = :findingId ORDER BY rowid")
    public abstract suspend fun getFindingAnchorsInOrder(findingId: String): List<FindingAnchorEntity>

    /** Like [getFindingAnchorsInOrder] for every finding of the case. */
    @Query(
        "SELECT fa.* FROM finding_anchor fa JOIN finding f ON f.id = fa.finding_id " +
            "WHERE f.case_id = :caseId ORDER BY fa.rowid",
    )
    public abstract suspend fun getFindingAnchorsForCase(caseId: String): List<FindingAnchorEntity>

    @Query("SELECT anchor_id FROM finding_anchor WHERE finding_id = :findingId ORDER BY anchor_id")
    public abstract suspend fun getAnchorIds(findingId: String): List<String>

    @Query(
        "SELECT * FROM review_decision WHERE target_type = :targetType AND target_id = :targetId " +
            "ORDER BY seq",
    )
    public abstract suspend fun getDecisionHistory(targetType: String, targetId: String): List<ReviewDecisionEntity>

    /** Every decision about [targetId] whatever the target type, in insertion order. */
    @Query("SELECT * FROM review_decision WHERE target_id = :targetId ORDER BY seq")
    public abstract suspend fun getDecisionsForTarget(targetId: String): List<ReviewDecisionEntity>

    @Query(
        "SELECT * FROM review_decision WHERE target_type = :targetType AND target_id = :targetId " +
            "ORDER BY seq DESC LIMIT 1",
    )
    public abstract suspend fun getLatestDecision(targetType: String, targetId: String): ReviewDecisionEntity?

    /** The newest decision for each target of [targetType] in the caseby insertion order. */
    @Query(
        "SELECT d.* FROM review_decision d WHERE d.case_id = :caseId AND d.target_type = :targetType " +
            "AND NOT EXISTS (SELECT 1 FROM review_decision n WHERE n.target_type = d.target_type " +
            "AND n.target_id = d.target_id AND n.seq > d.seq) " +
            "ORDER BY d.target_id",
    )
    public abstract fun observeLatestDecisions(caseId: String, targetType: String): Flow<List<ReviewDecisionEntity>>

    /** Every finding of the events with these ids, any revision. Keep the list below about 500 ids. */
    @Query("SELECT * FROM finding WHERE event_id IN (:eventIds) ORDER BY id")
    public abstract suspend fun getForEvents(eventIds: List<String>): List<FindingEntity>

    /** Like [getFindingAnchorsInOrder] for every finding of the events with these ids. Keep the list below about 500 ids. */
    @Query(
        "SELECT fa.* FROM finding_anchor fa JOIN finding f ON f.id = fa.finding_id " +
            "WHERE f.event_id IN (:eventIds) ORDER BY fa.rowid",
    )
    public abstract suspend fun getFindingAnchorsForEvents(eventIds: List<String>): List<FindingAnchorEntity>

    /**
     * Every decision whose target is the event itself or one of its category findings (target ids that start with
     * [findingPrefix], compared literally), in insertion order.
     */
    @Query(
        "SELECT * FROM review_decision WHERE target_id = :eventId " +
            "OR substr(target_id, 1, length(:findingPrefix)) = :findingPrefix ORDER BY seq",
    )
    public abstract suspend fun getDecisionsForEvent(eventId: String, findingPrefix: String): List<ReviewDecisionEntity>
}
