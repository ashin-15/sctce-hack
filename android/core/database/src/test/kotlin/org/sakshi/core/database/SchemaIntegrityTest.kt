package org.sakshi.core.database

import android.database.SQLException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SchemaIntegrityTest : DatabaseTestBase() {
    private val notCaseOwned = setOf("audit_record", "model_version", "label_mapping")

    @Test
    fun foreignKeysRejectChildrenWithoutParent() = runTest {
        assertFailsWith<SQLException> { db.caseDao().let { db.evidenceDao().insertEvidence(evidenceRow()) } }
        db.caseDao().insert(caseRow())
        assertFailsWith<SQLException> { db.eventDao().insertActor(actorRow(caseId = "missing")) }
        assertFailsWith<SQLException> { db.eventDao().insertRevision(revisionRow()) }
        assertFailsWith<SQLException> { db.jobDao().enqueue(jobRow()) }
        assertFailsWith<SQLException> { db.derivativeDao().insert(derivativeRow()) }
    }

    @Test
    fun everyTableIsCreated() {
        val names = db.openHelper.readableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%' AND name NOT LIKE 'android_%'",
        ).use { cursor -> generateSequence { if (cursor.moveToNext()) cursor.getString(0) else null }.toSet() }
        assertEquals(SakshiSchema.allTables.toSet(), names)
    }

    @Test
    fun deletingACaseRemovesEverythingItOwns() = runTest {
        db.insertFullGraph()
        SakshiSchema.allTables.forEach { assertTrue(db.count(it) > 0, "$it should be populated") }
        assertEquals(1, db.caseDao().delete(CASE_ID))
        SakshiSchema.allTables.forEach { table ->
            if (table in notCaseOwned) assertTrue(db.count(table) > 0, "$table is not case-owned") else assertEquals(0, db.count(table), table)
        }
    }

    @Test
    fun deletingEvidenceRemovesItsDependantsOnly() = runTest {
        db.insertFullGraph()
        db.insertEvidenceSet("evidence-2", sha256 = "12".repeat(32))
        db.derivativeDao().insert(derivativeRow("deriv-2", "evidence-2"))
        db.eventDao().insertEventWithRevision(eventRow("event-3"), revisionRow("event-3"), listOf(anchorRow("anchor-3", "event-3", evidenceId = "evidence-2", derivativeId = "deriv-2")))
        db.findingDao().insertWithAnchors(findingRow("finding-3", "event-3"), listOf("anchor-3"))
        db.findingDao().insertDecision(decisionRow("decision-3", "finding-3"))
        db.jobDao().enqueue(jobRow("job-2", "evidence-2"))

        db.evidenceDao().deleteWithDependants(EVIDENCE_ID)

        listOf("evidence_blob", "capture_metadata", "evidence_state", "region", "event_link", "boundary", "finding_anchor", "processing_job")
            .forEach { table -> assertTrue(db.count(table) <= 1, table) }
        assertEquals(null, db.evidenceDao().get(EVIDENCE_ID))
        assertEquals(null, db.derivativeDao().get("deriv-1"))
        assertEquals(null, db.findingDao().get("finding-1"))
        assertTrue(db.findingDao().getDecisionHistory(ReviewTargetType.FINDING, "finding-1").isEmpty())
        assertTrue(db.eventDao().getRevisions(EVENT_ID).isEmpty())
        assertEquals(0, db.count("region"))
        assertEquals(0, db.count("boundary"))
        assertEquals(0, db.count("event_link"))
        assertEquals("evidence-2", db.evidenceDao().get("evidence-2")?.id)
        assertEquals("deriv-2", db.derivativeDao().get("deriv-2")?.id)
        assertEquals("finding-3", db.findingDao().get("finding-3")?.id)
        assertEquals(1, db.findingDao().getDecisionHistory(ReviewTargetType.FINDING, "finding-3").size)
        assertEquals(listOf("anchor-3"), db.findingDao().getAnchorIds("finding-3"))
        assertEquals("job-2", db.jobDao().get("job-2")?.id)
        assertEquals(null, db.jobDao().get("job-1"))
        assertEquals(1, db.eventDao().getRevisions("event-2").size)
        assertEquals(AssessmentStatus.STALE, db.patternDao().get("pattern-1")?.assessmentStatus)
        assertEquals(1, db.caseDao().count())
    }
}
