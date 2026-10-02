package org.sakshi.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A recomputed description of events over time. Only [assessmentStatus] may be updated; stale
 * rows are kept until the caller replaces them.
 */
@Entity(
    tableName = SakshiSchema.PATTERN,
    foreignKeys = [
        ForeignKey(CaseEntity::class, ["id"], ["case_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("case_id", "type")],
)
public data class PatternEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "case_id") val caseId: String,
    val type: String,
    @ColumnInfo(name = "rule_version") val ruleVersion: String,
    @ColumnInfo(name = "actor_scope") val actorScope: String?,
    /** One of [EvidenceView]. */
    @ColumnInfo(name = "evidence_view") val evidenceView: String,
    @ColumnInfo(name = "knowledge_cutoff") val knowledgeCutoff: String,
    @ColumnInfo(name = "window_start") val windowStart: String?,
    @ColumnInfo(name = "window_end") val windowEnd: String?,
    @ColumnInfo(name = "clock_basis") val clockBasis: String,
    @ColumnInfo(name = "measurements_json") val measurementsJson: String,
    @ColumnInfo(name = "interpretation_text") val interpretationText: String?,
    @ColumnInfo(name = "limitations_json") val limitationsJson: String,
    /** One of [AssessmentStatus]. */
    @ColumnInfo(name = "assessment_status") val assessmentStatus: String,
    @ColumnInfo(name = "generated_at") val generatedAt: String,
)

/** An event revision a pattern rests on. Insert-only. */
@Entity(
    tableName = SakshiSchema.PATTERN_SUPPORT,
    primaryKeys = ["pattern_id", "event_id", "role"],
    foreignKeys = [
        ForeignKey(PatternEntity::class, ["id"], ["pattern_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(
            EventRevisionEntity::class, ["event_id", "revision"], ["event_id", "event_revision"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("event_id", "event_revision")],
)
public data class PatternSupportEntity(
    @ColumnInfo(name = "pattern_id") val patternId: String,
    @ColumnInfo(name = "event_id") val eventId: String,
    @ColumnInfo(name = "event_revision") val eventRevision: Int,
    /** One of [SupportRole]. */
    val role: String,
)
