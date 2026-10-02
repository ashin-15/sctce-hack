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

    @Query("SELECT anchor_id FROM finding_anchor WHERE finding_id = :findingId ORDER BY anchor_id")
    public abstract suspend fun getAnchorIds(findingId: String): List<String>

    @Query(
        "SELECT * FROM review_decision WHERE target_type = :targetType AND target_id = :targetId " +
            "ORDER BY seq",
    )
    public abstract suspend fun getDecisionHistory(targetType: String, targetId: String): List<ReviewDecisionEntity>

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
}
