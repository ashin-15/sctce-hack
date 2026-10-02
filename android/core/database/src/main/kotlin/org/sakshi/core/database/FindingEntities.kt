package org.sakshi.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A labelled suggestion about one event revision. Insert-only: a person's response is a
 * [ReviewDecisionEntity], never an edit of the finding.
 */
@Entity(
    tableName = SakshiSchema.FINDING,
    foreignKeys = [
        ForeignKey(CaseEntity::class, ["id"], ["case_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(
            EventRevisionEntity::class, ["event_id", "revision"], ["event_id", "event_revision"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(ModelVersionEntity::class, ["id"], ["model_version_id"]),
    ],
    indices = [Index("case_id"), Index("event_id", "event_revision"), Index("model_version_id")],
)
public data class FindingEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "case_id") val caseId: String,
    @ColumnInfo(name = "event_id") val eventId: String,
    @ColumnInfo(name = "event_revision") val eventRevision: Int,
    /** Event schema label. */
    val label: String,
    /** The producer's own label, kept verbatim. */
    @ColumnInfo(name = "source_label") val sourceLabel: String?,
    val basis: String,
    @ColumnInfo(name = "confidence_value") val confidenceValue: Double?,
    @ColumnInfo(name = "confidence_semantics") val confidenceSemantics: String,
    @ColumnInfo(name = "calibration_version") val calibrationVersion: String?,
    @ColumnInfo(name = "producer_version") val producerVersion: String,
    @ColumnInfo(name = "model_version_id") val modelVersionId: String?,
    /** One of [EpistemicStatus]. */
    @ColumnInfo(name = "epistemic_status") val epistemicStatus: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    /** One of [FindingReviewStatus]: the status an imported event carried, before any decision. */
    @ColumnInfo(name = "review_status_at_import") val reviewStatusAtImport: String,
)

/** Joins a finding to the evidence anchors that support it. Insert-only. */
@Entity(
    tableName = SakshiSchema.FINDING_ANCHOR,
    primaryKeys = ["finding_id", "anchor_id"],
    foreignKeys = [
        ForeignKey(FindingEntity::class, ["id"], ["finding_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(EvidenceAnchorEntity::class, ["id"], ["anchor_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("anchor_id")],
)
public data class FindingAnchorEntity(
    @ColumnInfo(name = "finding_id") val findingId: String,
    @ColumnInfo(name = "anchor_id") val anchorId: String,
)

/**
 * A person's response to a finding, association, pattern or explanation. Insert-only; the latest
 * decision for a target wins. `target_id` is polymorphic and has no foreign key, so removal with
 * the target is done by the DAO that deletes it.
 */
@Entity(
    tableName = SakshiSchema.REVIEW_DECISION,
    foreignKeys = [
        ForeignKey(CaseEntity::class, ["id"], ["case_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("id", unique = true), Index("target_type", "target_id"), Index("case_id")],
)
public data class ReviewDecisionEntity(
    val id: String,
    @ColumnInfo(name = "case_id") val caseId: String,
    /** One of [ReviewTargetType]. */
    @ColumnInfo(name = "target_type") val targetType: String,
    @ColumnInfo(name = "target_id") val targetId: String,
    @ColumnInfo(name = "target_revision") val targetRevision: Int?,
    /** One of [ReviewAction]. */
    val action: String,
    @ColumnInfo(name = "reason_code") val reasonCode: String?,
    @ColumnInfo(name = "edited_value_json") val editedValueJson: String?,
    val note: String?,
    @ColumnInfo(name = "decided_at") val decidedAt: String,
    @ColumnInfo(name = "decided_at_epoch_ms") val decidedAtEpochMs: Long,
    /** Insertion order; the highest value for a target is its latest decision. */
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
)
