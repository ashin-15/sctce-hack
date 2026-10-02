package org.sakshi.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Identity of an event. All content lives in [EventRevisionEntity]. Insert-only. */
@Entity(
    tableName = SakshiSchema.EVENT,
    foreignKeys = [
        ForeignKey(CaseEntity::class, ["id"], ["case_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("case_id")],
)
public data class EventEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "case_id") val caseId: String,
)

/**
 * One immutable revision of an event, following `data/sakshi-event-schema.json`.
 *
 * Enum-like columns hold the schema's snake_case strings. Time columns keep the source RFC 3339
 * text; the `*EpochMs` columns are derived for ordering and filtering and are null when the
 * source text is null.
 *
 * Sender and source claims live on the revision. [actorId] and [sourceScopeId] are optional
 * grouping aids and may be null while the label and scope ids are still present.
 */
@Entity(
    tableName = SakshiSchema.EVENT_REVISION,
    primaryKeys = ["event_id", "revision"],
    foreignKeys = [
        ForeignKey(EventEntity::class, ["id"], ["event_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ActorEntity::class, ["id"], ["actor_id"]),
        ForeignKey(SourceScopeEntity::class, ["id"], ["source_scope_id"]),
    ],
    indices = [
        Index("ts_earliest_epoch_ms"),
        Index("available_at_epoch_ms"),
        Index("actor_id"),
        Index("source_scope_id"),
    ],
)
public data class EventRevisionEntity(
    @ColumnInfo(name = "event_id") val eventId: String,
    val revision: Int,
    val kind: String,
    @ColumnInfo(name = "observed_at") val observedAt: String,
    @ColumnInfo(name = "available_at") val availableAt: String,
    @ColumnInfo(name = "available_at_epoch_ms") val availableAtEpochMs: Long,
    @ColumnInfo(name = "ts_earliest") val tsEarliest: String?,
    @ColumnInfo(name = "ts_earliest_epoch_ms") val tsEarliestEpochMs: Long?,
    @ColumnInfo(name = "ts_latest") val tsLatest: String?,
    @ColumnInfo(name = "ts_latest_epoch_ms") val tsLatestEpochMs: Long?,
    @ColumnInfo(name = "ts_basis") val tsBasis: String,
    @ColumnInfo(name = "ts_precision") val tsPrecision: String,
    @ColumnInfo(name = "ts_timezone") val tsTimezone: String?,
    @ColumnInfo(name = "collector_session_id") val collectorSessionId: String?,
    @ColumnInfo(name = "monotonic_ms") val monotonicMs: Long?,
    @ColumnInfo(name = "actor_id") val actorId: String?,
    @ColumnInfo(name = "source_scope_id") val sourceScopeId: String?,
    @ColumnInfo(name = "sender_display_label") val senderDisplayLabel: String?,
    @ColumnInfo(name = "sender_identity_basis") val senderIdentityBasis: String,
    @ColumnInfo(name = "sender_association_review") val senderAssociationReview: String,
    @ColumnInfo(name = "source_kind") val sourceKind: String,
    @ColumnInfo(name = "source_app") val sourceApp: String?,
    @ColumnInfo(name = "profile_scope_id") val profileScopeId: String?,
    @ColumnInfo(name = "conversation_scope_id") val conversationScopeId: String?,
    @ColumnInfo(name = "source_record_id") val sourceRecordId: String?,
    @ColumnInfo(name = "parser_version") val parserVersion: String?,
    val direction: String,
    @ColumnInfo(name = "review_priority") val reviewPriority: String,
    @ColumnInfo(name = "severity_basis") val severityBasis: String,
    /** JSON array of reference ids. */
    @ColumnInfo(name = "severity_reference_ids_json") val severityReferenceIdsJson: String,
    @ColumnInfo(name = "confirmation_status") val confirmationStatus: String,
    @ColumnInfo(name = "confirmation_scope") val confirmationScope: String,
    @ColumnInfo(name = "reviewed_at") val reviewedAt: String?,
    @ColumnInfo(name = "dedup_status") val dedupStatus: String,
    @ColumnInfo(name = "canonical_event_id") val canonicalEventId: String?,
    @ColumnInfo(name = "dedup_method") val dedupMethod: String?,
    @ColumnInfo(name = "coverage_context") val coverageContext: String,
    @ColumnInfo(name = "text_status") val textStatus: String,
    @ColumnInfo(name = "outgoing_coverage") val outgoingCoverage: String,
    /** JSON array of coverage gap reference ids. */
    @ColumnInfo(name = "gap_reference_ids_json") val gapReferenceIdsJson: String,
    @ColumnInfo(name = "unwanted_contact") val unwantedContact: String,
    @ColumnInfo(name = "retention_mode") val retentionMode: String,
    @ColumnInfo(name = "expires_at") val expiresAt: String?,
    @ColumnInfo(name = "consent_generation") val consentGeneration: Int,
)

/**
 * Points an event revision at the exact evidence it rests on. Insert-only.
 *
 * [referenceId] is the schema's per-revision reference id and [artifactId] the schema artifact id.
 * [evidenceId] is null when the artifact is not stored in this vault. [sha256] is null when the reference
 * carries no digest. [regionId] is the schema's region id as given; it has no foreign key because the region
 * may not exist as a `region` row (for example in imported events). Text locators use
 * half-open Unicode code point offsets; audio locators use milliseconds.
 */
@Entity(
    tableName = SakshiSchema.EVIDENCE_ANCHOR,
    foreignKeys = [
        ForeignKey(
            EventRevisionEntity::class, ["event_id", "revision"], ["event_id", "event_revision"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(EvidenceEntity::class, ["id"], ["evidence_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(DerivativeEntity::class, ["id"], ["derivative_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index("event_id", "event_revision", "reference_id", unique = true),
        Index("evidence_id"),
        Index("derivative_id"),
    ],
)
public data class EvidenceAnchorEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "event_id") val eventId: String,
    @ColumnInfo(name = "event_revision") val eventRevision: Int,
    @ColumnInfo(name = "reference_id") val referenceId: String,
    @ColumnInfo(name = "artifact_id") val artifactId: String,
    @ColumnInfo(name = "evidence_id") val evidenceId: String?,
    @ColumnInfo(name = "derivative_id") val derivativeId: String?,
    val sha256: String?,
    val representation: String,
    @ColumnInfo(name = "locator_kind") val locatorKind: String,
    @ColumnInfo(name = "start_cp") val startCp: Int?,
    @ColumnInfo(name = "end_cp") val endCp: Int?,
    @ColumnInfo(name = "start_ms") val startMs: Long?,
    @ColumnInfo(name = "end_ms") val endMs: Long?,
    @ColumnInfo(name = "page_index") val pageIndex: Int?,
    @ColumnInfo(name = "region_id") val regionId: String?,
)

/** A typed link between two events. Insert-only; review of a link is a decision row. */
@Entity(
    tableName = SakshiSchema.EVENT_LINK,
    foreignKeys = [
        ForeignKey(EventEntity::class, ["id"], ["from_event"], onDelete = ForeignKey.CASCADE),
        ForeignKey(EventEntity::class, ["id"], ["to_event"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("from_event"), Index("to_event")],
)
public data class EventLinkEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "from_event") val fromEvent: String,
    @ColumnInfo(name = "to_event") val toEvent: String,
    val type: String,
    val basis: String,
    @ColumnInfo(name = "confidence_value") val confidenceValue: Double?,
    @ColumnInfo(name = "confidence_semantics") val confidenceSemantics: String,
    @ColumnInfo(name = "calibration_version") val calibrationVersion: String?,
    @ColumnInfo(name = "review_status") val reviewStatus: String,
)

/**
 * A boundary the user set or reported. A row exists only when the event's marker is not `none`;
 * the unwanted-contact marking lives on the event revision. Insert-only.
 */
@Entity(
    tableName = SakshiSchema.BOUNDARY,
    foreignKeys = [
        ForeignKey(CaseEntity::class, ["id"], ["case_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(EventEntity::class, ["id"], ["event_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ActorEntity::class, ["id"], ["actor_id"]),
    ],
    indices = [Index("case_id"), Index("event_id"), Index("actor_id")],
)
public data class BoundaryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "case_id") val caseId: String,
    @ColumnInfo(name = "event_id") val eventId: String,
    val marker: String,
    @ColumnInfo(name = "actor_id") val actorId: String?,
    @ColumnInfo(name = "review_status") val reviewStatus: String,
    @ColumnInfo(name = "communication_status") val communicationStatus: String,
    @ColumnInfo(name = "scope_note") val scopeNote: String?,
)

/** A period the selected evidence does not cover. A null bound means unknown. Insert-only. */
@Entity(
    tableName = SakshiSchema.COVERAGE_GAP,
    foreignKeys = [
        ForeignKey(CaseEntity::class, ["id"], ["case_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(SourceScopeEntity::class, ["id"], ["source_scope_id"]),
    ],
    indices = [Index("case_id", "start_at_epoch_ms"), Index("source_scope_id")],
)
public data class CoverageGapEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "case_id") val caseId: String,
    @ColumnInfo(name = "source_scope_id") val sourceScopeId: String?,
    @ColumnInfo(name = "start_at") val startAt: String?,
    @ColumnInfo(name = "start_at_epoch_ms") val startAtEpochMs: Long?,
    @ColumnInfo(name = "end_at") val endAt: String?,
    @ColumnInfo(name = "end_at_epoch_ms") val endAtEpochMs: Long?,
    val reason: String,
)
