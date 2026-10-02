package org.sakshi.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A report draft container. Mutable. */
@Entity(
    tableName = SakshiSchema.REPORT,
    foreignKeys = [
        ForeignKey(CaseEntity::class, ["id"], ["case_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("case_id")],
)
public data class ReportEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "case_id") val caseId: String,
    val title: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
)

/**
 * A frozen report version. Insert-only except that [supersededBy] may be set once, because a
 * superseded snapshot is marked rather than deleted. [signature] is lowercase hex.
 */
@Entity(
    tableName = SakshiSchema.REPORT_SNAPSHOT,
    foreignKeys = [
        ForeignKey(ReportEntity::class, ["id"], ["report_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("report_id", "version", unique = true)],
)
public data class ReportSnapshotEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "report_id") val reportId: String,
    val version: Int,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "manifest_sha256") val manifestSha256: String,
    @ColumnInfo(name = "merkle_root") val merkleRoot: String,
    val signature: String?,
    @ColumnInfo(name = "signer_key_id") val signerKeyId: String?,
    @ColumnInfo(name = "superseded_by") val supersededBy: String?,
    @ColumnInfo(name = "dependency_json") val dependencyJson: String,
)
