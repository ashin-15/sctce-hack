package org.sakshi.core.database

import android.database.SQLException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ImmutabilityTest : DatabaseTestBase() {
    private fun columns(table: String): List<String> =
        db.openHelper.readableDatabase.query("PRAGMA table_info($table)").use { cursor ->
            generateSequence { if (cursor.moveToNext()) cursor.getString(cursor.getColumnIndexOrThrow("name")) else null }.toList()
        }

    @Test
    fun updatesAbortOnEveryInsertOnlyTable() = runTest {
        db.insertFullGraph()
        SakshiSchema.insertOnlyTables.forEach { table ->
            val column = columns(table).first()
            val error = assertFailsWith<SQLException>(table) { db.exec("UPDATE $table SET $column = $column") }
            assertTrue(error.message.orEmpty().contains("insert-only"), table)
        }
    }

    @Test
    fun triggersAreInstalledOnCreate() {
        val installed = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger'").use {
            it.moveToFirst()
            it.getInt(0)
        }
        assertEquals(SakshiSchema.immutabilityTriggers.size, installed)
    }

    @Test
    fun guardedColumnListsMatchTheTables() {
        assertEquals(columns("pattern").filter { it != "assessment_status" }, SakshiSchema.patternGuardedColumns)
        assertEquals(columns("report_snapshot").filter { it != "superseded_by" }, SakshiSchema.snapshotGuardedColumns)
    }

    @Test
    fun patternAllowsOnlyAssessmentStatusUpdates() = runTest {
        db.insertFullGraph()
        db.exec("UPDATE pattern SET assessment_status = 'stale'")
        assertEquals(AssessmentStatus.STALE, db.patternDao().get("pattern-1")?.assessmentStatus)
        assertFailsWith<SQLException> { db.exec("UPDATE pattern SET type = 'other'") }
        assertFailsWith<SQLException> { db.exec("UPDATE pattern SET interpretation_text = 'x', assessment_status = 'candidate'") }
    }

    @Test
    fun snapshotAllowsOnlyAWriteOnceSupersededBy() = runTest {
        db.insertFullGraph()
        assertFailsWith<SQLException> { db.exec("UPDATE report_snapshot SET manifest_sha256 = 'x'") }
        db.exec("UPDATE report_snapshot SET superseded_by = 'snap-2'")
        assertFailsWith<SQLException> { db.exec("UPDATE report_snapshot SET superseded_by = 'snap-3'") }
    }

    @Test
    fun auditRecordsCanBeAppendedButNotDeleted() = runTest {
        db.auditDao().append(auditRow())
        val error = assertFailsWith<SQLException> { db.exec("DELETE FROM audit_record") }
        assertTrue(error.message.orEmpty().contains("append-only"))
        db.auditDao().append(auditRow(prev = 1, this_ = 2))
        assertEquals(2, db.auditDao().count())
    }

    @Test
    fun mutableTablesStillAllowUpdates() = runTest {
        db.insertFullGraph()
        db.exec("UPDATE case_file SET title = 'new'")
        db.exec("UPDATE actor SET display_label = 'new'")
        db.exec("UPDATE evidence_state SET support_state = 'failed'")
        db.exec("UPDATE processing_job SET attempt = 3")
        db.exec("UPDATE report SET title = 'new'")
    }
}
