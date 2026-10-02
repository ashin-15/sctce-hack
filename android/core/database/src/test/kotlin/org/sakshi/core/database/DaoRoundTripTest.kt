package org.sakshi.core.database

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DaoRoundTripTest : DatabaseTestBase() {
    @Test
    fun caseRoundTripAndUpdate() = runTest {
        val dao = db.caseDao()
        dao.insert(caseRow())
        assertEquals(caseRow(), dao.get(CASE_ID))
        assertEquals(1, dao.update(caseRow().copy(status = CaseStatus.ARCHIVED, ownerNote = "note")))
        assertEquals(CaseStatus.ARCHIVED, dao.get(CASE_ID)?.status)
        assertEquals(listOf(CASE_ID), dao.observeAll().first().map { it.id })
        assertEquals(1, dao.count())
    }

    @Test
    fun evidenceFullInsertReadsBackEveryPart() = runTest {
        db.caseDao().insert(caseRow())
        db.insertEvidenceSet()
        val dao = db.evidenceDao()
        assertEquals(evidenceRow(), dao.get(EVIDENCE_ID))
        assertEquals(blobRow(), dao.getBlob(EVIDENCE_ID))
        assertEquals(metadataRow(), dao.getMetadata(EVIDENCE_ID))
        assertEquals(stateRow(), dao.getState(EVIDENCE_ID))
        val item = dao.observeForCase(CASE_ID).first().single()
        assertEquals(SupportState.SAVED, item.supportState)
        assertEquals(1, dao.updateState(EVIDENCE_ID, SupportState.ANALYZED, T0 + 1))
        assertEquals(SupportState.ANALYZED, dao.getState(EVIDENCE_ID)?.supportState)
    }

    @Test
    fun evidenceFullInsertIsAtomic() = runTest {
        db.caseDao().insert(caseRow())
        assertFailsWith<Exception> {
            db.evidenceDao().insertFull(evidenceRow(), blobRow("missing"), metadataRow(), stateRow())
        }
        assertEquals(0, db.count("evidence"))
    }

    @Test
    fun sameBytesMayBeImportedTwice() = runTest {
        db.caseDao().insert(caseRow())
        db.insertEvidenceSet("e1")
        db.insertEvidenceSet("e2")
        assertEquals(2, db.evidenceDao().findBySha256(CASE_ID, "ab".repeat(32)).size)
    }

    @Test
    fun derivativesAndRegionsRoundTrip() = runTest {
        db.caseDao().insert(caseRow())
        db.insertEvidenceSet()
        val dao = db.derivativeDao()
        dao.insertWithRegions(derivativeRow(), listOf(regionRow()))
        dao.insertWithRegions(derivativeRow("deriv-2", revision = 2, parent = "deriv-1"), emptyList())
        assertEquals(derivativeRow(), dao.get("deriv-1"))
        assertEquals("deriv-2", dao.getLatest(EVIDENCE_ID, DerivativeKind.PARSED_TEXT)?.id)
        assertEquals(listOf(regionRow()), dao.getRegions("deriv-1"))
        assertEquals(listOf("deriv-2", "deriv-1"), dao.observeSummaries(EVIDENCE_ID).first().map { it.id })
    }

    @Test
    fun eventsAndRelatedRowsRoundTrip() = runTest {
        db.caseDao().insert(caseRow())
        db.insertEvidenceSet()
        val dao = db.eventDao()
        dao.insertActor(actorRow())
        dao.insertSourceScope(scopeRow())
        val revision = revisionRow(actorId = "actor-1", sourceScopeId = "scope-1")
        dao.insertEventWithRevision(eventRow(), revision, listOf(anchorRow()))
        dao.insertEventWithRevision(eventRow("event-2"), revisionRow("event-2"), emptyList())
        dao.insertLink(linkRow())
        dao.insertBoundary(boundaryRow(actorId = "actor-1"))
        dao.insertCoverageGap(gapRow(scopeId = "scope-1"))
        assertEquals(listOf(actorRow()), dao.getActors(CASE_ID))
        dao.updateActor(actorRow().copy(displayLabel = "Renamed"))
        assertEquals("Renamed", dao.getActors(CASE_ID).single().displayLabel)
        assertEquals(listOf(scopeRow()), dao.getSourceScopes(CASE_ID))
        assertEquals(listOf(revision), dao.getRevisions(EVENT_ID))
        assertEquals(listOf(anchorRow()), dao.getAnchors(EVENT_ID, 1))
        assertEquals(listOf(linkRow()), dao.getLinks("event-2"))
        assertEquals(listOf(boundaryRow(actorId = "actor-1")), dao.getBoundaries(CASE_ID))
        assertEquals(listOf(gapRow(scopeId = "scope-1")), dao.observeCoverageGaps(CASE_ID).first())
    }

    @Test
    fun findingsAnchorsAndDecisionsRoundTrip() = runTest {
        db.caseDao().insert(caseRow())
        db.insertEvidenceSet()
        db.eventDao().insertEventWithRevision(eventRow(), revisionRow(), listOf(anchorRow()))
        val dao = db.findingDao()
        dao.insertWithAnchors(findingRow(), listOf("anchor-1"))
        assertEquals(findingRow(), dao.get("finding-1"))
        assertEquals(listOf(findingRow()), dao.getForEventRevision(EVENT_ID, 1))
        assertEquals(listOf("anchor-1"), dao.getAnchorIds("finding-1"))
        assertEquals(listOf(findingRow()), dao.observeForCase(CASE_ID).first())
        dao.insertDecision(decisionRow("d1"))
        assertEquals(listOf(decisionRow("d1")), dao.getDecisionHistory(ReviewTargetType.FINDING, "finding-1").map { it.copy(seq = 0) })
    }

    @Test
    fun patternsRoundTrip() = runTest {
        db.caseDao().insert(caseRow())
        db.insertEvidenceSet()
        db.eventDao().insertEventWithRevision(eventRow(), revisionRow(), emptyList())
        val dao = db.patternDao()
        dao.insertWithSupport(patternRow(), listOf(supportRow()))
        assertEquals(patternRow(), dao.get("pattern-1"))
        assertEquals(listOf(supportRow()), dao.getSupport("pattern-1"))
        assertEquals(listOf(patternRow()), dao.observeForCase(CASE_ID).first())
        assertEquals(1, dao.updateAssessment("pattern-1", AssessmentStatus.SUPPORTED_DESCRIPTION))
        assertEquals(AssessmentStatus.SUPPORTED_DESCRIPTION, dao.get("pattern-1")?.assessmentStatus)
    }

    @Test
    fun reportsAndSnapshotsRoundTrip() = runTest {
        db.caseDao().insert(caseRow())
        val dao = db.reportDao()
        dao.insertReport(reportRow())
        dao.insertSnapshot(snapshotRow())
        dao.insertSnapshot(snapshotRow("snap-2", version = 2))
        assertEquals(reportRow(), dao.getReport("report-1"))
        assertEquals(1, dao.updateReport(reportRow().copy(title = "New")))
        assertEquals(listOf(reportRow().copy(title = "New")), dao.observeReports(CASE_ID).first())
        assertEquals(1, dao.markSuperseded("snap-1", "snap-2"))
        assertEquals(listOf("snap-2", null), dao.getSnapshots("report-1").map { it.supersededBy })
        assertFailsWith<Exception> { dao.markSuperseded("snap-1", "snap-3") }
        assertFailsWith<Exception> { dao.insertSnapshot(snapshotRow("snap-3", version = 2)) }
    }

    @Test
    fun auditAppendsAndReadsInOrder() = runTest {
        val dao = db.auditDao()
        assertNull(dao.last())
        val first = dao.appendNext { previous ->
            assertNull(previous)
            auditRow(prev = 0, this_ = 1)
        }
        val second = dao.appendNext { previous ->
            assertContentEquals(ByteArray(32) { 1 }, previous?.thisHash)
            auditRow(prev = 1, this_ = 2)
        }
        assertEquals(listOf(first, second), dao.all().map { it.seq })
        assertEquals(2, dao.count())
        assertEquals(second, dao.last()?.seq)
        assertContentEquals(ByteArray(32) { 2 }, dao.last()?.thisHash)
    }

    @Test
    fun modelVersionsAndLabelMappingsRoundTrip() = runTest {
        db.modelVersionDao().insert(modelRow())
        assertEquals(modelRow(), db.modelVersionDao().get("model-1"))
        assertEquals(1, db.modelVersionDao().all().size)
        val dao = db.labelMappingDao()
        dao.insert(LabelMappingEntity("bench", "insult", "verbal_abuse", 1))
        dao.insert(LabelMappingEntity("bench", "insult", "intimidation", 2))
        assertEquals("intimidation", dao.latest("bench", "insult")?.schemaLabel)
        assertNull(dao.latest("bench", "threat"))
        assertEquals(2, dao.forProducer("bench").size)
    }

    @Test
    fun timestampsAndMultilingualTextArePreservedExactly() = runTest {
        val text = "നമസ്കാരം नमस्ते 😀 👩‍💻 é \u0000 end"
        val stamp = "2026-10-02T10:00:00.123+05:30"
        db.caseDao().insert(caseRow().copy(title = text, ownerNote = text))
        db.insertEvidenceSet()
        db.derivativeDao().insert(derivativeRow(text = text))
        db.eventDao().insertEventWithRevision(
            eventRow(), revisionRow(tsEarliest = stamp, tsLatest = stamp, tsLatestEpochMs = T0), emptyList(),
        )
        assertEquals(text, db.caseDao().get(CASE_ID)?.ownerNote)
        assertContentEquals(text.toByteArray(), db.derivativeDao().get("deriv-1")?.text?.toByteArray())
        val revision = db.eventDao().getRevisions(EVENT_ID).single()
        assertEquals(stamp, revision.tsEarliest)
        assertEquals(stamp, revision.tsLatest)
        assertNotNull(db.evidenceDao().get(EVIDENCE_ID)).let { assertEquals("2026-10-02T10:00:00.123+05:30", it.receivedAt) }
    }

    @Test
    fun fullEventRevisionWithEveryColumnRoundTrips() = runTest {
        val label = "\u0d05\u0d1c\u0d4d\u0d1e\u0d3e\u0d24\u0d28\u0d4d \u0d2a\u0d4d\u0d30\u0d47\u0d37\u0d15\u0d7b"
        db.caseDao().insert(caseRow())
        val full = revisionRow(
            revision = 1, tsLatest = "2026-10-02T10:05:00.500+05:30", tsLatestEpochMs = T0 + 300_500, actorId = null,
            sourceScopeId = null,
        ).copy(
            senderDisplayLabel = label, senderIdentityBasis = "user_asserted", senderAssociationReview = "confirmed",
            sourceApp = "whatsapp", profileScopeId = "profile-9", conversationScopeId = "conv-7",
            severityBasis = "policy_suggestion", severityReferenceIdsJson = "[\"ref-1\",\"ref-2\"]",
            gapReferenceIdsJson = "[\"gap-1\"]", unwantedContact = "user_marked_unwanted",
            reviewedAt = "2026-10-03T08:00:00Z", canonicalEventId = "event-0", dedupMethod = "dedup-1",
            expiresAt = "2026-11-01T00:00:00Z", tsTimezone = "+05:30", monotonicMs = 99L,
        )
        db.eventDao().insertEventWithRevision(eventRow(), full, emptyList())
        assertEquals(full, db.eventDao().getRevisions(EVENT_ID).single())
        assertEquals(label, db.eventDao().getRevisions(EVENT_ID).single().senderDisplayLabel)
    }

    @Test
    fun anchorMayPointAtAnArtifactOutsideTheVault() = runTest {
        db.caseDao().insert(caseRow())
        val anchor = anchorRow(evidenceId = null).copy(pageIndex = 2, regionId = null)
        db.eventDao().insertEventWithRevision(eventRow(), revisionRow(), listOf(anchor))
        assertEquals(listOf(anchor), db.eventDao().getAnchors(EVENT_ID, 1))
        assertFailsWith<Exception> { db.eventDao().insertAnchors(listOf(anchorRow("anchor-2", evidenceId = null, referenceId = anchor.referenceId))) }
    }
}
