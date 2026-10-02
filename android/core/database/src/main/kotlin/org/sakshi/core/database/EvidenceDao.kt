package org.sakshi.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Evidence row without any large or sensitive text, for list screens. */
public data class EvidenceListItem(
    val id: String,
    @ColumnInfo(name = "received_at") val receivedAt: String,
    @ColumnInfo(name = "acquisition_kind") val acquisitionKind: String,
    @ColumnInfo(name = "detected_mime") val detectedMime: String?,
    @ColumnInfo(name = "byte_size") val byteSize: Long,
    @ColumnInfo(name = "support_state") val supportState: String,
)

/** Access to evidence, its blob record, capture metadata and processing state. */
@Dao
public abstract class EvidenceDao {
    @Insert
    public abstract suspend fun insertEvidence(evidence: EvidenceEntity)

    @Insert
    public abstract suspend fun insertBlob(blob: EvidenceBlobEntity)

    @Insert
    public abstract suspend fun insertMetadata(metadata: CaptureMetadataEntity)

    @Insert
    public abstract suspend fun insertState(state: EvidenceStateEntity)

    /** Inserts evidence with its blob record, capture metadata and state atomically. */
    @Transaction
    public open suspend fun insertFull(
        evidence: EvidenceEntity,
        blob: EvidenceBlobEntity,
        metadata: CaptureMetadataEntity,
        state: EvidenceStateEntity,
    ) {
        insertEvidence(evidence)
        insertBlob(blob)
        insertMetadata(metadata)
        insertState(state)
    }

    @Query("SELECT * FROM evidence WHERE id = :id")
    public abstract suspend fun get(id: String): EvidenceEntity?

    @Query("SELECT * FROM evidence_blob WHERE evidence_id = :evidenceId")
    public abstract suspend fun getBlob(evidenceId: String): EvidenceBlobEntity?

    @Query("SELECT * FROM capture_metadata WHERE evidence_id = :evidenceId")
    public abstract suspend fun getMetadata(evidenceId: String): CaptureMetadataEntity?

    @Query("SELECT * FROM evidence_state WHERE evidence_id = :evidenceId")
    public abstract suspend fun getState(evidenceId: String): EvidenceStateEntity?

    @Query("SELECT * FROM evidence WHERE case_id = :caseId AND sha256 = :sha256 ORDER BY received_at_epoch_ms, id")
    public abstract suspend fun findBySha256(caseId: String, sha256: String): List<EvidenceEntity>

    @Query(
        "SELECT e.id AS id, e.received_at AS received_at, e.acquisition_kind AS acquisition_kind, " +
            "e.detected_mime AS detected_mime, e.byte_size AS byte_size, s.support_state AS support_state " +
            "FROM evidence e JOIN evidence_state s ON s.evidence_id = e.id " +
            "WHERE e.case_id = :caseId ORDER BY e.received_at_epoch_ms DESC, e.id",
    )
    public abstract fun observeForCase(caseId: String): Flow<List<EvidenceListItem>>

    /** Returns the number of rows changed (1 when the evidence exists). */
    @Query("UPDATE evidence_state SET support_state = :supportState, updated_at_epoch_ms = :nowEpochMs WHERE evidence_id = :evidenceId")
    public abstract suspend fun updateState(evidenceId: String, supportState: String, nowEpochMs: Long): Int

    /**
     * Deletes the evidence and everything that depends on it: blob record, metadata, derivatives,
     * regions, anchors and jobs (by cascade), then findings left without any anchor, events left
     * without any anchor, and review decisions of removed findings. Patterns that used a touched
     * event are marked stale. The caller must still erase the blob file and its key.
     */
    @Transaction
    public open suspend fun deleteWithDependants(evidenceId: String) {
        val caseId = caseIdOf(evidenceId) ?: return
        val events = eventIdsAnchoredTo(evidenceId)
        val findings = findingIdsAnchoredTo(evidenceId)
        if (events.isNotEmpty()) markPatternsStale(events)
        deleteEvidence(evidenceId)
        if (findings.isNotEmpty()) deleteFindingsWithoutAnchor(findings)
        if (events.isNotEmpty()) deleteEventsWithoutAnchor(events)
        deleteDecisionsOfMissingFindings(caseId)
    }

    @Query("SELECT case_id FROM evidence WHERE id = :evidenceId")
    protected abstract suspend fun caseIdOf(evidenceId: String): String?

    @Query("SELECT DISTINCT event_id FROM evidence_anchor WHERE evidence_id = :evidenceId")
    protected abstract suspend fun eventIdsAnchoredTo(evidenceId: String): List<String>

    @Query(
        "SELECT DISTINCT fa.finding_id FROM finding_anchor fa " +
            "JOIN evidence_anchor a ON a.id = fa.anchor_id WHERE a.evidence_id = :evidenceId",
    )
    protected abstract suspend fun findingIdsAnchoredTo(evidenceId: String): List<String>

    @Query(MARK_PATTERNS_STALE_FOR_EVENTS)
    protected abstract suspend fun markPatternsStale(eventIds: List<String>): Int

    @Query("DELETE FROM evidence WHERE id = :evidenceId")
    protected abstract suspend fun deleteEvidence(evidenceId: String): Int

    @Query("DELETE FROM finding WHERE id IN (:ids) AND id NOT IN (SELECT finding_id FROM finding_anchor)")
    protected abstract suspend fun deleteFindingsWithoutAnchor(ids: List<String>): Int

    @Query("DELETE FROM event WHERE id IN (:ids) AND id NOT IN (SELECT event_id FROM evidence_anchor)")
    protected abstract suspend fun deleteEventsWithoutAnchor(ids: List<String>): Int

    @Query(
        "DELETE FROM review_decision WHERE case_id = :caseId AND target_type = 'finding' " +
            "AND target_id NOT IN (SELECT id FROM finding)",
    )
    protected abstract suspend fun deleteDecisionsOfMissingFindings(caseId: String): Int
}
