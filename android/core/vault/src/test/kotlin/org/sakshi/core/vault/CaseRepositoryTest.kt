package org.sakshi.core.vault

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.sakshi.core.database.CaseStatus

class CaseRepositoryTest : VaultTestBase() {
    @Test
    fun createsRenamesArchivesAndUnarchives() = runBlocking<Unit> {
        val created = cases.create("  Synthetic case  ")
        assertEquals("Synthetic case", created.title)
        assertEquals(CaseStatus.ACTIVE, created.status)
        assertEquals(0, created.evidenceCount)

        cases.rename(created.id, "Renamed")
        assertEquals("Renamed", db.caseDao().get(created.id)!!.title)
        cases.archive(created.id)
        assertEquals(CaseStatus.ARCHIVED, db.caseDao().get(created.id)!!.status)
        cases.unarchive(created.id)
        assertEquals(CaseStatus.ACTIVE, db.caseDao().get(created.id)!!.status)
        assertEquals(4, assertIs<AuditVerification.Valid>(audit.verify()).count)
    }

    @Test
    fun rejectsInvalidTitlesAndUnknownCases() = runBlocking<Unit> {
        assertFailsWith<IllegalArgumentException> { cases.create("") }
        assertFailsWith<IllegalArgumentException> { cases.create("   ") }
        assertFailsWith<IllegalArgumentException> { cases.create("x".repeat(121)) }
        assertEquals(120, cases.create("x".repeat(120)).title.length)
        val id = cases.observe().first().single().id
        assertFailsWith<IllegalArgumentException> { cases.rename(id, " ") }
        assertFailsWith<IllegalArgumentException> { cases.rename("synthetic-missing", "Title") }
        assertFailsWith<IllegalArgumentException> { cases.archive("synthetic-missing") }
        assertFailsWith<IllegalArgumentException> { cases.delete("synthetic-missing") }
    }

    @Test
    fun observeEmitsCaseCounts() = runBlocking<Unit> {
        val case = cases.create("Counted")
        assertEquals(0, cases.observe().first().single().evidenceCount)
        evidence.import(request(case.id), ByteArrayInputStream(MARKER.toByteArray()))
        evidence.import(request(case.id), ByteArrayInputStream(MARKER.toByteArray()))
        val summary = cases.observe().first().single()
        assertEquals(case.id, summary.id)
        assertEquals(2, summary.evidenceCount)
    }

    @Test
    fun deleteRemovesRowsAndBlobFiles() = runBlocking<Unit> {
        val case = cases.create("To delete")
        val other = cases.create("To keep")
        evidence.import(request(case.id), ByteArrayInputStream(ByteArray(5000)))
        evidence.import(request(case.id), ByteArrayInputStream(ByteArray(10)))
        val kept = evidence.import(request(other.id), ByteArrayInputStream(ByteArray(20)))
        assertEquals(3, blobFiles().size)

        cases.delete(case.id)

        assertNull(db.caseDao().get(case.id))
        assertTrue(evidence.observeForCase(case.id).first().isEmpty())
        assertEquals(1, blobFiles().size)
        assertNotNull(db.evidenceDao().getBlob(kept.id))
        assertEquals(listOf(other.id), cases.observe().first().map { it.id })
    }

    @Test
    fun auditCallsCarryNoTitleText() = runBlocking<Unit> {
        // The log stores only a digest, so the honest check is on what callers hand to it: record the calls.
        val title = "SYNTHETIC-TITLE-MARKER"
        val recording = RecordingAuditLog(db, clock)
        val recorded = CaseRepository(db, blobs, recording, clock, ids, Dispatchers.IO)
        val case = recorded.create(title)
        recorded.rename(case.id, "$title-renamed")
        recorded.archive(case.id)
        recorded.delete(case.id)

        assertEquals(4, recording.calls.size)
        assertTrue(recording.calls.none { it.contains("SYNTHETIC-TITLE") })
        assertTrue(recording.calls.last().contains("\"evidence_count\":0"))
        assertFalse(recording.calls.any { it.contains(title) })
    }
}
