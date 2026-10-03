package org.sakshi.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Append-only status and provenance for one explicit threat-language analysis attempt. */
@Entity(
    tableName = SakshiSchema.THREAT_ANALYSIS_RUN,
    foreignKeys = [
        ForeignKey(CaseEntity::class, ["id"], ["case_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(
            EventRevisionEntity::class, ["event_id", "revision"], ["event_id", "event_revision"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(DerivativeEntity::class, ["id"], ["derivative_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(FindingEntity::class, ["id"], ["finding_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index("case_id"), Index("event_id", "event_revision"), Index("derivative_id"), Index("finding_id"),
        Index(value = ["event_id", "event_revision", "request_id"], unique = true),
    ],
)
public data class ThreatAnalysisRunEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "case_id") val caseId: String,
    @ColumnInfo(name = "event_id") val eventId: String,
    @ColumnInfo(name = "event_revision") val eventRevision: Int,
    @ColumnInfo(name = "derivative_id") val derivativeId: String?,
    @ColumnInfo(name = "request_id") val requestId: String,
    val status: String,
    @ColumnInfo(name = "reason_code") val reasonCode: String?,
    @ColumnInfo(name = "model_preset") val modelPreset: String?,
    @ColumnInfo(name = "weight_sha256") val weightSha256: String?,
    @ColumnInfo(name = "runtime_commit") val runtimeCommit: String?,
    @ColumnInfo(name = "runtime_version") val runtimeVersion: String?,
    @ColumnInfo(name = "task_version") val taskVersion: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "finding_id") val findingId: String?,
)
