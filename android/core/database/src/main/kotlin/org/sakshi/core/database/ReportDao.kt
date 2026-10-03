package org.sakshi.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Access to reports and their frozen snapshots. */
@Dao
public abstract class ReportDao {
    @Insert
    public abstract suspend fun insertReport(report: ReportEntity)

    @Update
    public abstract suspend fun updateReport(report: ReportEntity): Int

    @Insert
    public abstract suspend fun insertSnapshot(snapshot: ReportSnapshotEntity)

    @Query("SELECT * FROM report WHERE id = :id")
    public abstract suspend fun getReport(id: String): ReportEntity?

    @Query("SELECT * FROM report WHERE case_id = :caseId ORDER BY created_at DESC, id")
    public abstract fun observeReports(caseId: String): Flow<List<ReportEntity>>

    @Query("SELECT * FROM report_snapshot WHERE report_id = :reportId ORDER BY version")
    public abstract suspend fun getSnapshots(reportId: String): List<ReportSnapshotEntity>

    /** Marks a snapshot as superseded. Succeeds once per snapshot; the schema rejects a second time. */
    @Query("UPDATE report_snapshot SET superseded_by = :supersededBy WHERE id = :id")
    public abstract suspend fun markSuperseded(id: String, supersededBy: String): Int

    /** The case's report container, the oldest if there were ever several. */
    @Query("SELECT * FROM report WHERE case_id = :caseId ORDER BY created_at, id LIMIT 1")
    public abstract suspend fun getReportForCase(caseId: String): ReportEntity?

    /** The newest snapshot of any report of the case. */
    @Query(
        "SELECT s.* FROM report_snapshot s JOIN report r ON r.id = s.report_id WHERE r.case_id = :caseId " +
            "ORDER BY s.version DESC, s.created_at DESC LIMIT 1",
    )
    public abstract suspend fun getLatestSnapshotForCase(caseId: String): ReportSnapshotEntity?

    /** [getLatestSnapshotForCase] as a flow, emitted again when snapshots change. */
    @Query(
        "SELECT s.* FROM report_snapshot s JOIN report r ON r.id = s.report_id WHERE r.case_id = :caseId " +
            "ORDER BY s.version DESC, s.created_at DESC LIMIT 1",
    )
    public abstract fun observeLatestSnapshotForCase(caseId: String): Flow<ReportSnapshotEntity?>
}
