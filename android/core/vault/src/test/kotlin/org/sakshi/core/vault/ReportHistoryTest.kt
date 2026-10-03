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
import kotlinx.coroutines.withContext
import org.junit.Before
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.Direction
import org.sakshi.core.model.EventId

class ReportHistoryTest : ReviewTestBase() {
    private lateinit var history: ReportHistory

    @Before
    fun openHistory() {
        history = ReportHistory(db, store, recording, clock, ids, Dispatchers.IO)
    }

    private fun export(
        version: Int,
        snapshotId: String = "synthetic-snap-$version",
        revisions: Map<String, Int> = mapOf("synthetic-m1" to 1),
    ) = ExportRecord(
        snapshotId = snapshotId,
        version = version,
        createdAt = "2026-10-02T10:0$version:00Z",
        merkleRoot = "0$version".repeat(32),
        manifestSha256 = "a$version".repeat(32),
        signatureHex = "b$version".repeat(64),
        signerKeyId = "synthetic-key",
        originalCount = 2,
        dependencies = ExportDependencies(revisions, "c$version".repeat(32)),
    )

    private suspend fun count(table: String, where: String = "1 = 1"): Int = withContext(Dispatchers.IO) {
        db.query("SELECT COUNT(*) FROM $table WHERE $where", null).use {
            assertTrue(it.moveToFirst())
            it.getInt(0)
        }
    }

    private suspend fun savedWithExport(vararg ids: String) {
        standardCase()
        ids.forEach { saveOk(message(it)) }
        history.record(caseId, export(1, revisions = ids.associateWith { 1 }))
    }

    @Test
    fun nextVersionStartsAtOneAndFollowsRecords() = runBlocking<Unit> {
        standardCase()
        assertEquals(1, history.nextVersion(caseId))
        history.record(caseId, export(1))
        assertEquals(2, history.nextVersion(caseId))
        history.record(caseId, export(2))
        assertEquals(3, history.nextVersion(caseId))
    }

    @Test
    fun recordedSnapshotReadsBackWithEveryField() = runBlocking<Unit> {
        standardCase()
        val record = export(1, revisions = mapOf("synthetic-m2" to 3, "synthetic-m1" to 1))
        val returned = history.record(caseId, record)

        val expected = ExportedSnapshot(
            id = "synthetic-snap-1",
            version = 1,
            createdAt = record.createdAt,
            merkleRoot = record.merkleRoot,
            manifestSha256 = record.manifestSha256,
            signerKeyId = "synthetic-key",
            supersededBy = null,
            dependencies = ExportDependencies(mapOf("synthetic-m1" to 1, "synthetic-m2" to 3), "c1".repeat(32)),
        )
        assertEquals(expected, returned)
        assertEquals(expected, history.latest(caseId))
        val stored = assertNotNull(db.reportDao().getSnapshots(assertNotNull(db.reportDao().getReportForCase(caseId.value)).id).single())
        assertEquals(record.signatureHex, stored.signature)
    }

    @Test
    fun latestIsNullForACaseNeverExported() = runBlocking<Unit> {
        standardCase()
        assertNull(history.latest(caseId))
    }

    @Test
    fun secondRecordSupersedesTheFirstAndLeavesItOtherwiseUnchanged() = runBlocking<Unit> {
        standardCase()
        history.record(caseId, export(1))
        val reportId = assertNotNull(db.reportDao().getReportForCase(caseId.value)).id
        val before = db.reportDao().getSnapshots(reportId).single()

        history.record(caseId, export(2))

        val rows = db.reportDao().getSnapshots(reportId)
        assertEquals(listOf(1, 2), rows.map { it.version })
        assertEquals(before.copy(supersededBy = "synthetic-snap-2"), rows[0])
        assertNull(rows[1].supersededBy)
        assertEquals("synthetic-snap-2", history.latest(caseId)?.id)
        assertEquals(1, count("report", "case_id = '${caseId.value}'"))
    }

    @Test
    fun aVersionThatIsNotTheNextOneThrowsAndWritesNothing() = runBlocking<Unit> {
        standardCase()
        history.record(caseId, export(1))
        val audits = count("audit_record")

        assertFailsWith<IllegalStateException> { history.record(caseId, export(1, snapshotId = "synthetic-stale")) }
        assertFailsWith<IllegalStateException> { history.record(caseId, export(3, snapshotId = "synthetic-skipped")) }

        assertEquals(1, count("report_snapshot"))
        assertEquals(0, count("report_snapshot", "id IN ('synthetic-stale', 'synthetic-skipped')"))
        assertEquals(audits, count("audit_record"))
        assertNull(history.latest(caseId)?.supersededBy)
    }

    @Test
    fun aFirstExportWithTheWrongVersionCreatesNoReport() = runBlocking<Unit> {
        standardCase()
        assertFailsWith<IllegalStateException> { history.record(caseId, export(2)) }
        assertEquals(0, count("report"))
        assertEquals(0, count("audit_record", "action = 'export.created'"))
    }

    @Test
    fun eachExportWritesOneAuditRowAndTheChainStillVerifies() = runBlocking<Unit> {
        standardCase()
        val before = count("audit_record")
        history.record(caseId, export(1))
        history.record(caseId, export(2, revisions = mapOf("synthetic-m1" to 1, "synthetic-m2" to 1)))

        assertEquals(2, count("audit_record", "action = 'export.created'"))
        assertEquals(before + 2, count("audit_record"))
        assertEquals(
            listOf(
                "export.created|export|synthetic-snap-1|{\"snapshot_id\":\"synthetic-snap-1\",\"case_id\":\"${caseId.value}\"," +
                    "\"event_count\":1,\"original_count\":2,\"signer_key_id\":\"synthetic-key\"}",
                "export.created|export|synthetic-snap-2|{\"snapshot_id\":\"synthetic-snap-2\",\"case_id\":\"${caseId.value}\"," +
                    "\"event_count\":2,\"original_count\":2,\"signer_key_id\":\"synthetic-key\"}",
            ),
            recording.calls.filter { it.startsWith("export.created") },
        )
        assertIs<AuditVerification.Valid>(recording.verify())
    }

    @Test
    fun versionsArePerCase() = runBlocking<Unit> {
        standardCase()
        insertCase("synthetic-case-2")
        val other = CaseId("synthetic-case-2")

        assertEquals(1, history.nextVersion(other))
        history.record(caseId, export(1))
        history.record(caseId, export(2))
        assertEquals(1, history.nextVersion(other))
        assertNull(history.latest(other))

        val second = history.record(other, export(1, snapshotId = "synthetic-other-1"))
        assertEquals("synthetic-other-1", history.latest(other)?.id)
        assertNull(second.supersededBy)
        assertEquals("synthetic-snap-2", history.latest(caseId)?.id)
        assertEquals(2, history.nextVersion(other))
        assertEquals(3, history.nextVersion(caseId))
    }

    @Test
    fun driftIsNullBeforeAnyExport() = runBlocking<Unit> {
        standardCase()
        saveOk(message("synthetic-m1"))
        assertNull(history.observeDrift(caseId).first())
    }

    @Test
    fun driftIsUpToDateRightAfterAnExport() = runBlocking<Unit> {
        savedWithExport("synthetic-m1", "synthetic-m2")
        val drift = assertNotNull(history.observeDrift(caseId).first())
        assertEquals(ExportDrift(assertNotNull(history.latest(caseId)), 0, 0), drift)
        assertTrue(drift.upToDate)
    }

    @Test
    fun aReviewedEventCountsAsChanged() = runBlocking<Unit> {
        savedWithExport("synthetic-m1", "synthetic-m2")
        review.setDirection(listOf(EventId("synthetic-m1")), Direction.OUTGOING)
        assertEquals(2, latest("synthetic-m1").revision)

        val drift = assertNotNull(history.observeDrift(caseId).first())
        assertEquals(1, drift.changedEvents)
        assertEquals(0, drift.removedEvents)
        assertFalse(drift.upToDate)
    }

    @Test
    fun eventsAddedAfterTheExportAreNotCounted() = runBlocking<Unit> {
        savedWithExport("synthetic-m1")
        saveOk(message("synthetic-late"))

        val drift = assertNotNull(history.observeDrift(caseId).first())
        assertTrue(drift.upToDate)
    }

    @Test
    fun anEventRemovedWithItsEvidenceCountsAsRemoved() = runBlocking<Unit> {
        standardCase()
        val stored = evidence.import(request(EventFixtures.CASE), ByteArrayInputStream(ByteArray(40)))
        saveOk(EventFixtures.plain("synthetic-m1").copy(evidenceReferences = listOf(EventFixtures.ref("r1", artifact = stored.id))))
        saveOk(message("synthetic-m2"))
        history.record(caseId, export(1, revisions = mapOf("synthetic-m1" to 1, "synthetic-m2" to 1)))

        evidence.delete(stored.id)

        val drift = assertNotNull(history.observeDrift(caseId).first())
        assertEquals(0, drift.changedEvents)
        assertEquals(1, drift.removedEvents)
    }

    @Test
    fun driftFollowsTheNewestSnapshotOnly() = runBlocking<Unit> {
        savedWithExport("synthetic-m1")
        review.setDirection(listOf(EventId("synthetic-m1")), Direction.OUTGOING)
        assertEquals(1, assertNotNull(history.observeDrift(caseId).first()).changedEvents)

        history.record(caseId, export(2, revisions = mapOf("synthetic-m1" to 2)))

        val drift = assertNotNull(history.observeDrift(caseId).first())
        assertEquals("synthetic-snap-2", drift.snapshot.id)
        assertTrue(drift.upToDate)
    }
}
