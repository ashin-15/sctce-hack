package org.sakshi.core.database

/** Table names, insert-only enforcement and the triggers that implement it. */
public object SakshiSchema {
    /** The table for a case. `case` is an SQL keyword, so the table is named `case_file`. */
    public const val CASE_FILE: String = "case_file"
    public const val ACTOR: String = "actor"
    public const val SOURCE_SCOPE: String = "source_scope"
    public const val EVIDENCE: String = "evidence"
    public const val EVIDENCE_STATE: String = "evidence_state"
    public const val EVIDENCE_BLOB: String = "evidence_blob"
    public const val CAPTURE_METADATA: String = "capture_metadata"
    public const val DERIVATIVE: String = "derivative"
    public const val REGION: String = "region"
    public const val EVENT: String = "event"
    public const val EVENT_REVISION: String = "event_revision"
    public const val EVIDENCE_ANCHOR: String = "evidence_anchor"
    public const val EVENT_LINK: String = "event_link"
    public const val BOUNDARY: String = "boundary"
    public const val COVERAGE_GAP: String = "coverage_gap"
    public const val FINDING: String = "finding"
    public const val FINDING_ANCHOR: String = "finding_anchor"
    public const val REVIEW_DECISION: String = "review_decision"
    public const val PATTERN: String = "pattern"
    public const val PATTERN_SUPPORT: String = "pattern_support"
    public const val REPORT: String = "report"
    public const val REPORT_SNAPSHOT: String = "report_snapshot"
    public const val AUDIT_RECORD: String = "audit_record"
    public const val MODEL_VERSION: String = "model_version"
    public const val PROCESSING_JOB: String = "processing_job"
    public const val LABEL_MAPPING: String = "label_mapping"

    /** Every table in the database. */
    public val allTables: List<String> = listOf(
        CASE_FILE, ACTOR, SOURCE_SCOPE, EVIDENCE, EVIDENCE_STATE, EVIDENCE_BLOB, CAPTURE_METADATA,
        DERIVATIVE, REGION, EVENT, EVENT_REVISION, EVIDENCE_ANCHOR, EVENT_LINK, BOUNDARY,
        COVERAGE_GAP, FINDING, FINDING_ANCHOR, REVIEW_DECISION, PATTERN, PATTERN_SUPPORT, REPORT,
        REPORT_SNAPSHOT, AUDIT_RECORD, MODEL_VERSION, PROCESSING_JOB, LABEL_MAPPING,
    )

    /**
     * Tables whose rows may never be updated at all.
     *
     * `pattern` and `report_snapshot` are not listed: each has exactly one mutable column and is
     * guarded by [partiallyMutableTables].
     */
    public val insertOnlyTables: List<String> = listOf(
        SOURCE_SCOPE, EVIDENCE, EVIDENCE_BLOB, CAPTURE_METADATA, DERIVATIVE, REGION, EVENT,
        EVENT_REVISION, EVIDENCE_ANCHOR, EVENT_LINK, BOUNDARY, COVERAGE_GAP, FINDING,
        FINDING_ANCHOR, REVIEW_DECISION, PATTERN_SUPPORT, AUDIT_RECORD, MODEL_VERSION,
        LABEL_MAPPING,
    )

    /** Tables that allow update of exactly one column, mapped to that column. */
    public val partiallyMutableTables: Map<String, String> = mapOf(
        PATTERN to "assessment_status",
        REPORT_SNAPSHOT to "superseded_by",
    )

    internal val patternGuardedColumns: List<String> = listOf(
        "id", "case_id", "type", "rule_version", "actor_scope", "evidence_view", "knowledge_cutoff",
        "window_start", "window_end", "clock_basis", "measurements_json", "interpretation_text",
        "limitations_json", "generated_at",
    )

    internal val snapshotGuardedColumns: List<String> = listOf(
        "id", "report_id", "version", "created_at", "manifest_sha256", "merkle_root", "signature",
        "signer_key_id", "dependency_json",
    )

    /** SQL that makes the immutable tables insert-only. Safe to run repeatedly. */
    public val immutabilityTriggers: List<String> = buildList {
        insertOnlyTables.forEach { table ->
            add(
                "CREATE TRIGGER IF NOT EXISTS ${table}_insert_only BEFORE UPDATE ON $table " +
                    "BEGIN SELECT RAISE(ABORT, '$table is insert-only'); END;",
            )
        }
        add(
            "CREATE TRIGGER IF NOT EXISTS audit_record_append_only BEFORE DELETE ON $AUDIT_RECORD " +
                "BEGIN SELECT RAISE(ABORT, 'audit_record is append-only'); END;",
        )
        add(
            "CREATE TRIGGER IF NOT EXISTS pattern_assessment_only " +
                "BEFORE UPDATE OF ${patternGuardedColumns.joinToString()} ON $PATTERN " +
                "BEGIN SELECT RAISE(ABORT, 'pattern allows only assessment_status updates'); END;",
        )
        add(
            "CREATE TRIGGER IF NOT EXISTS report_snapshot_immutable " +
                "BEFORE UPDATE OF ${snapshotGuardedColumns.joinToString()} ON $REPORT_SNAPSHOT " +
                "BEGIN SELECT RAISE(ABORT, 'report_snapshot is insert-only'); END;",
        )
        add(
            "CREATE TRIGGER IF NOT EXISTS report_snapshot_supersede_once " +
                "BEFORE UPDATE OF superseded_by ON $REPORT_SNAPSHOT WHEN OLD.superseded_by IS NOT NULL " +
                "BEGIN SELECT RAISE(ABORT, 'report_snapshot superseded_by is write-once'); END;",
        )
    }
}
