package org.sakshi.core.database

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReportDaoTest : DatabaseTestBase() {
    private suspend fun insertCase(id: String) {
        db.caseDao().insert(caseRow().copy(id = id))
    }

    @Test
    fun reportForCaseIsNullWithoutAReportAndOtherwiseTheOldestOne() = runTest {
        insertCase(CASE_ID)
        val dao = db.reportDao()
        assertNull(dao.getReportForCase(CASE_ID))

        dao.insertReport(reportRow("report-b").copy(createdAt = "2026-10-02T11:00:00Z"))
        dao.insertReport(reportRow("report-a").copy(createdAt = "2026-10-02T10:00:00Z"))

        assertEquals("report-a", dao.getReportForCase(CASE_ID)?.id)
    }

    @Test
    fun reportForCaseIgnoresOtherCases() = runTest {
        insertCase(CASE_ID)
        insertCase("case-2")
        val dao = db.reportDao()
        dao.insertReport(reportRow("report-2").copy(caseId = "case-2"))

        assertNull(dao.getReportForCase(CASE_ID))
        assertEquals("report-2", dao.getReportForCase("case-2")?.id)
    }

    @Test
    fun latestSnapshotIsNullWithoutSnapshots() = runTest {
        insertCase(CASE_ID)
        db.reportDao().insertReport(reportRow())

        assertNull(db.reportDao().getLatestSnapshotForCase(CASE_ID))
        assertNull(db.reportDao().observeLatestSnapshotForCase(CASE_ID).first())
    }

    @Test
    fun latestSnapshotIsTheHighestVersionAcrossReportsOfTheCase() = runTest {
        insertCase(CASE_ID)
        val dao = db.reportDao()
        dao.insertReport(reportRow("report-1"))
        dao.insertReport(reportRow("report-2").copy(createdAt = "2026-10-02T11:00:00Z"))
        dao.insertSnapshot(snapshotRow("snap-1", "report-1", 1))
        dao.insertSnapshot(snapshotRow("snap-2", "report-1", 2))
        dao.insertSnapshot(snapshotRow("snap-other", "report-2", 1))

        assertEquals("snap-2", dao.getLatestSnapshotForCase(CASE_ID)?.id)
        assertEquals("snap-2", dao.observeLatestSnapshotForCase(CASE_ID).first()?.id)
    }

    @Test
    fun latestSnapshotBreaksAVersionTieByCreationTime() = runTest {
        insertCase(CASE_ID)
        val dao = db.reportDao()
        dao.insertReport(reportRow("report-1"))
        dao.insertReport(reportRow("report-2"))
        dao.insertSnapshot(snapshotRow("snap-old", "report-1", 1))
        dao.insertSnapshot(snapshotRow("snap-new", "report-2", 1).copy(createdAt = "2026-10-02T12:00:00Z"))

        assertEquals("snap-new", dao.getLatestSnapshotForCase(CASE_ID)?.id)
    }

    @Test
    fun latestSnapshotIgnoresOtherCases() = runTest {
        insertCase(CASE_ID)
        insertCase("case-2")
        val dao = db.reportDao()
        dao.insertReport(reportRow("report-1"))
        dao.insertReport(reportRow("report-2").copy(caseId = "case-2"))
        dao.insertSnapshot(snapshotRow("snap-1", "report-1", 1))
        dao.insertSnapshot(snapshotRow("snap-2", "report-2", 5))

        assertEquals("snap-1", dao.getLatestSnapshotForCase(CASE_ID)?.id)
        assertEquals("snap-2", dao.getLatestSnapshotForCase("case-2")?.id)
    }

    @Test
    fun observedLatestSnapshotEmitsAgainWhenSnapshotsChange() = runTest {
        insertCase(CASE_ID)
        val dao = db.reportDao()
        dao.insertReport(reportRow())
        dao.insertSnapshot(snapshotRow("snap-1", version = 1))
        assertEquals("snap-1", dao.observeLatestSnapshotForCase(CASE_ID).first()?.id)

        dao.insertSnapshot(snapshotRow("snap-2", version = 2))
        assertEquals(1, dao.markSuperseded("snap-1", "snap-2"))

        val latest = dao.observeLatestSnapshotForCase(CASE_ID).first()
        assertEquals("snap-2", latest?.id)
        assertNull(latest?.supersededBy)
    }
}
