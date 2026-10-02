package org.sakshi.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One link of the hash chain. Append-only: no update, and no delete short of wiping the vault.
 * [prevHash] and [thisHash] are 32 bytes; the chain is computed by a higher layer.
 */
@Entity(tableName = SakshiSchema.AUDIT_RECORD)
public data class AuditRecordEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val at: String,
    @ColumnInfo(name = "at_epoch_ms") val atEpochMs: Long,
    val action: String,
    @ColumnInfo(name = "subject_type") val subjectType: String,
    @ColumnInfo(name = "subject_id") val subjectId: String,
    @ColumnInfo(name = "payload_sha256") val payloadSha256: String,
    @ColumnInfo(name = "prev_hash", typeAffinity = ColumnInfo.BLOB) val prevHash: ByteArray,
    @ColumnInfo(name = "this_hash", typeAffinity = ColumnInfo.BLOB) val thisHash: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (
            other is AuditRecordEntity &&
                seq == other.seq && at == other.at && atEpochMs == other.atEpochMs &&
                action == other.action && subjectType == other.subjectType &&
                subjectId == other.subjectId && payloadSha256 == other.payloadSha256 &&
                prevHash.contentEquals(other.prevHash) && thisHash.contentEquals(other.thisHash)
            )

    override fun hashCode(): Int = 31 * seq.hashCode() + thisHash.contentHashCode()
}

/** A unit of background work for one evidence item. Mutable while it runs. */
@Entity(
    tableName = SakshiSchema.PROCESSING_JOB,
    foreignKeys = [
        ForeignKey(EvidenceEntity::class, ["id"], ["evidence_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("status"), Index("evidence_id")],
)
public data class ProcessingJobEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "evidence_id") val evidenceId: String,
    val stage: String,
    /** One of [JobStatus]. */
    val status: String,
    @ColumnInfo(name = "source_sha256") val sourceSha256: String,
    val attempt: Int,
    @ColumnInfo(name = "consent_generation") val consentGeneration: Int,
    @ColumnInfo(name = "last_error_code") val lastErrorCode: String?,
    /** Orders the queue; the id breaks ties. */
    @ColumnInfo(name = "enqueued_at_epoch_ms") val enqueuedAtEpochMs: Long,
)
