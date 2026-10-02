package org.sakshi.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Preserved evidence. Insert-only; processing state lives in [EvidenceStateEntity].
 *
 * [receivedAt] keeps the RFC 3339 source text, [receivedAtEpochMs] is derived for ordering.
 * [sha256] is lowercase hex of the received bytes.
 */
@Entity(
    tableName = SakshiSchema.EVIDENCE,
    foreignKeys = [
        ForeignKey(CaseEntity::class, ["id"], ["case_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index("case_id", "received_at_epoch_ms"),
        Index("case_id", "sha256"),
    ],
)
public data class EvidenceEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "case_id") val caseId: String,
    @ColumnInfo(name = "acquisition_kind") val acquisitionKind: String,
    @ColumnInfo(name = "access_class") val accessClass: String,
    @ColumnInfo(name = "received_at") val receivedAt: String,
    @ColumnInfo(name = "received_at_epoch_ms") val receivedAtEpochMs: Long,
    @ColumnInfo(name = "claimed_origin") val claimedOrigin: String?,
    @ColumnInfo(name = "declared_mime") val declaredMime: String?,
    @ColumnInfo(name = "detected_mime") val detectedMime: String?,
    @ColumnInfo(name = "byte_size") val byteSize: Long,
    val sha256: String,
    @ColumnInfo(name = "retention_mode") val retentionMode: String,
)

/** The one mutable fact about evidence: how far processing has got. */
@Entity(
    tableName = SakshiSchema.EVIDENCE_STATE,
    foreignKeys = [
        ForeignKey(EvidenceEntity::class, ["id"], ["evidence_id"], onDelete = ForeignKey.CASCADE),
    ],
)
public data class EvidenceStateEntity(
    @PrimaryKey @ColumnInfo(name = "evidence_id") val evidenceId: String,
    /** One of [SupportState]. */
    @ColumnInfo(name = "support_state") val supportState: String,
    @ColumnInfo(name = "updated_at_epoch_ms") val updatedAtEpochMs: Long,
)

/** Where and how the encrypted original is stored. Insert-only. */
@Entity(
    tableName = SakshiSchema.EVIDENCE_BLOB,
    foreignKeys = [
        ForeignKey(EvidenceEntity::class, ["id"], ["evidence_id"], onDelete = ForeignKey.CASCADE),
    ],
)
public data class EvidenceBlobEntity(
    @PrimaryKey @ColumnInfo(name = "evidence_id") val evidenceId: String,
    val path: String,
    @ColumnInfo(name = "envelope_version") val envelopeVersion: Int,
    @ColumnInfo(name = "wrapped_key", typeAffinity = ColumnInfo.BLOB) val wrappedKey: ByteArray,
    @ColumnInfo(name = "chunk_size") val chunkSize: Int,
    @ColumnInfo(name = "chunk_count") val chunkCount: Long,
    @ColumnInfo(name = "plaintext_length") val plaintextLength: Long,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (
            other is EvidenceBlobEntity &&
                evidenceId == other.evidenceId && path == other.path &&
                envelopeVersion == other.envelopeVersion && wrappedKey.contentEquals(other.wrappedKey) &&
                chunkSize == other.chunkSize && chunkCount == other.chunkCount &&
                plaintextLength == other.plaintextLength
            )

    override fun hashCode(): Int = 31 * evidenceId.hashCode() + wrappedKey.contentHashCode()
}

/** What the importer observed about how the evidence arrived. Claims, not facts. Insert-only. */
@Entity(
    tableName = SakshiSchema.CAPTURE_METADATA,
    foreignKeys = [
        ForeignKey(EvidenceEntity::class, ["id"], ["evidence_id"], onDelete = ForeignKey.CASCADE),
    ],
)
public data class CaptureMetadataEntity(
    @PrimaryKey @ColumnInfo(name = "evidence_id") val evidenceId: String,
    @ColumnInfo(name = "importer_mechanism") val importerMechanism: String,
    @ColumnInfo(name = "uri_authority_claim") val uriAuthorityClaim: String?,
    @ColumnInfo(name = "display_name_claim") val displayNameClaim: String?,
    @ColumnInfo(name = "exif_json") val exifJson: String?,
    /** One of [ProviderTransform]. */
    @ColumnInfo(name = "provider_transform") val providerTransform: String,
    @ColumnInfo(name = "collector_session_id") val collectorSessionId: String?,
    @ColumnInfo(name = "elapsed_realtime_ms") val elapsedRealtimeMs: Long?,
)
