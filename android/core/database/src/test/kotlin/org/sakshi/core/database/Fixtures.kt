package org.sakshi.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider

const val CASE_ID = "case-1"
const val EVIDENCE_ID = "evidence-1"
const val EVENT_ID = "event-1"
const val T0 = 1_700_000_000_000L

fun newDatabase(): SakshiDatabase =
    SakshiDatabaseFactory.openInMemoryForTests(ApplicationProvider.getApplicationContext<Context>())

fun SakshiDatabase.count(table: String): Int =
    openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
        it.moveToFirst()
        it.getInt(0)
    }

fun SakshiDatabase.exec(sql: String) = openHelper.writableDatabase.execSQL(sql)

fun caseRow(id: String = CASE_ID) = CaseEntity(id, "Title $id", "2026-10-02T10:00:00+05:30", CaseStatus.ACTIVE, null)

fun actorRow(id: String = "actor-1", caseId: String = CASE_ID) =
    ActorEntity(id, caseId, "Label", "unknown", "unreviewed")

fun scopeRow(id: String = "scope-1", caseId: String = CASE_ID) =
    SourceScopeEntity(id, caseId, "app", "profile", "conversation")

fun evidenceRow(
    id: String = EVIDENCE_ID,
    caseId: String = CASE_ID,
    sha256: String = "ab".repeat(32),
    receivedAtEpochMs: Long = T0,
) = EvidenceEntity(
    id, caseId, "share", "user_shared", "2026-10-02T10:00:00.123+05:30", receivedAtEpochMs,
    "origin", "text/plain", "text/plain", 12L, sha256, "confirmed_vault",
)

fun blobRow(evidenceId: String = EVIDENCE_ID) =
    EvidenceBlobEntity(evidenceId, "blobs/$evidenceId", 1, byteArrayOf(1, 2, 3), 65536, 1L, 12L)

fun metadataRow(evidenceId: String = EVIDENCE_ID) =
    CaptureMetadataEntity(evidenceId, "sharesheet", "authority", "chat.txt", "{}", ProviderTransform.UNKNOWN, "s1", 55L)

fun stateRow(evidenceId: String = EVIDENCE_ID) = EvidenceStateEntity(evidenceId, SupportState.SAVED, T0)

suspend fun SakshiDatabase.insertEvidenceSet(id: String = EVIDENCE_ID, caseId: String = CASE_ID, sha256: String = "ab".repeat(32)) {
    evidenceDao().insertFull(evidenceRow(id, caseId, sha256), blobRow(id), metadataRow(id), stateRow(id))
}

fun modelRow(id: String = "model-1") =
    ModelVersionEntity(id, "name", "classifier", "cd".repeat(32), null, "onnx", "1.0", "int8", "Apache-2.0", null, "[\"en\"]")

fun derivativeRow(id: String = "deriv-1", evidenceId: String = EVIDENCE_ID, revision: Int = 1, text: String = "hello", parent: String? = null, modelId: String? = null) =
    DerivativeEntity(id, evidenceId, parent, revision, DerivativeKind.PARSED_TEXT, text, "[]", null, "tool", "1", modelId, "2026-10-02T10:00:00Z")

fun regionRow(id: String = "region-1", derivativeId: String = "deriv-1") =
    RegionEntity(id, derivativeId, 0, "[[0,0],[1,1]]", null)

fun eventRow(id: String = EVENT_ID, caseId: String = CASE_ID) = EventEntity(id, caseId)

fun revisionRow(
    eventId: String = EVENT_ID,
    revision: Int = 1,
    availableAtEpochMs: Long = T0,
    tsEarliest: String? = "2026-10-02T10:00:00.250+05:30",
    tsEarliestEpochMs: Long? = T0,
    tsLatest: String? = null,
    tsLatestEpochMs: Long? = null,
    actorId: String? = null,
    sourceScopeId: String? = null,
) = EventRevisionEntity(
    eventId, revision, "message_observation", "2026-10-02T10:00:00Z", "2026-10-02T10:00:00Z", availableAtEpochMs,
    tsEarliest, tsEarliestEpochMs, tsLatest, tsLatestEpochMs, "source_claim", "millisecond", "Asia/Kolkata", "s1", 5L,
    actorId, sourceScopeId, null, "unknown", "unreviewed", "selected_text", null, null, null, "rec-1", "parser-1",
    "incoming", "ordinary", "unknown", "[]", "pending", "not_reviewed", null,
    "distinct_observation", null, null, "unknown", "available", "unknown", "[]", "unknown", "confirmed_vault", null, 1,
)

fun anchorRow(
    id: String = "anchor-1",
    eventId: String = EVENT_ID,
    revision: Int = 1,
    evidenceId: String? = EVIDENCE_ID,
    derivativeId: String? = null,
    regionId: String? = null,
    referenceId: String = "ref-$id",
) = EvidenceAnchorEntity(
    id, eventId, revision, referenceId, "artifact-$evidenceId", evidenceId, derivativeId, "ab".repeat(32),
    "preserved_import", "text_span", 0, 5, null, null, null, regionId,
)

fun linkRow(id: String = "link-1", from: String = EVENT_ID, to: String = "event-2") =
    EventLinkEntity(id, from, to, "reply_to", "rule", 0.5, "uncalibrated_bounded_score", null, "unreviewed")

fun boundaryRow(id: String = "boundary-1", eventId: String = EVENT_ID, actorId: String? = null) =
    BoundaryEntity(id, CASE_ID, eventId, "do_not_contact", actorId, "unreviewed", "user_reported", null)

fun gapRow(id: String = "gap-1", scopeId: String? = null) =
    CoverageGapEntity(id, CASE_ID, scopeId, "2026-10-01T00:00:00Z", T0 - 1000, null, null, "export_missing")

fun findingRow(id: String = "finding-1", eventId: String = EVENT_ID, revision: Int = 1, modelId: String? = null) =
    FindingEntity(id, CASE_ID, eventId, revision, "verbal_abuse", "insult", "classifier_suggestion", 0.5, "uncalibrated_bounded_score", null, "p1", modelId, EpistemicStatus.INFERRED, "2026-10-02T10:00:00Z", FindingReviewStatus.UNREVIEWED)

fun decisionRow(
    id: String,
    targetId: String = "finding-1",
    action: String = ReviewAction.ACCEPT,
    epochMs: Long = T0,
    targetType: String = ReviewTargetType.FINDING,
) = ReviewDecisionEntity(id, CASE_ID, targetType, targetId, 1, action, null, null, null, "2026-10-02T10:00:00Z", epochMs)

fun patternRow(id: String = "pattern-1", type: String = "repetition", status: String = AssessmentStatus.CANDIDATE) =
    PatternEntity(id, CASE_ID, type, "r1", null, EvidenceView.CONFIRMED_ONLY, "2026-10-02T10:00:00Z", null, null, "source_claim", "{}", "text", "[]", status, "2026-10-02T10:00:00Z")

fun supportRow(patternId: String = "pattern-1", eventId: String = EVENT_ID, revision: Int = 1) =
    PatternSupportEntity(patternId, eventId, revision, SupportRole.SUPPORTING)

fun reportRow(id: String = "report-1") = ReportEntity(id, CASE_ID, "Report", "2026-10-02T10:00:00Z")

fun snapshotRow(id: String = "snap-1", reportId: String = "report-1", version: Int = 1) =
    ReportSnapshotEntity(id, reportId, version, "2026-10-02T10:00:00Z", "ef".repeat(32), "01".repeat(32), null, null, null, "{}")

fun auditRow(seq: Long = 0, prev: Byte = 0, this_: Byte = 1) =
    AuditRecordEntity(seq, "2026-10-02T10:00:00Z", T0, "evidence_saved", "evidence", EVIDENCE_ID, "ab".repeat(32), ByteArray(32) { prev }, ByteArray(32) { this_ })

fun jobRow(id: String = "job-1", evidenceId: String = EVIDENCE_ID, sha: String = "ab".repeat(32), enqueuedAt: Long = T0) =
    ProcessingJobEntity(id, evidenceId, "ocr", JobStatus.PENDING, sha, 0, 1, null, enqueuedAt)

/** Inserts one row into every table, so cascade and immutability tests see all of them populated. */
suspend fun SakshiDatabase.insertFullGraph() {
    caseDao().insert(caseRow())
    modelVersionDao().insert(modelRow())
    labelMappingDao().insert(LabelMappingEntity("bench", "insult", "verbal_abuse", 1))
    insertEvidenceSet()
    derivativeDao().insertWithRegions(derivativeRow(modelId = "model-1"), listOf(regionRow()))
    val events = eventDao()
    events.insertActor(actorRow())
    events.insertSourceScope(scopeRow())
    events.insertEventWithRevision(eventRow(), revisionRow(actorId = "actor-1", sourceScopeId = "scope-1"), listOf(anchorRow(derivativeId = "deriv-1", regionId = "region-1")))
    events.insertEventWithRevision(eventRow("event-2"), revisionRow("event-2"), emptyList())
    events.insertLink(linkRow())
    events.insertBoundary(boundaryRow(actorId = "actor-1"))
    events.insertCoverageGap(gapRow(scopeId = "scope-1"))
    findingDao().insertWithAnchors(findingRow(modelId = "model-1"), listOf("anchor-1"))
    findingDao().insertDecision(decisionRow("decision-1"))
    threatAnalysisRunDao().insertIfAbsent(
        ThreatAnalysisRunEntity(
            id = "threat-run-1",
            caseId = CASE_ID,
            eventId = EVENT_ID,
            eventRevision = 1,
            derivativeId = "deriv-1",
            requestId = "request-1",
            status = "possible_threat_language",
            reasonCode = null,
            modelPreset = "qwen2.5-1.5b-instruct-q4_k_m",
            weightSha256 = "cd".repeat(32),
            runtimeCommit = "a7a98e0fffed794396b3fbad4dcdbbc184963645",
            runtimeVersion = "llama.cpp-b6500",
            taskVersion = "qwen-threat-language-v1",
            createdAt = "2026-10-03T10:00:00Z",
            findingId = "finding-1",
        ),
    )
    patternDao().insertWithSupport(patternRow(), listOf(supportRow()))
    reportDao().insertReport(reportRow())
    reportDao().insertSnapshot(snapshotRow())
    auditDao().append(auditRow())
    jobDao().enqueue(jobRow())
}
