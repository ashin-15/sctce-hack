package org.sakshi.core.vault

import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.sakshi.core.database.AuditRecordEntity
import org.sakshi.core.database.CaseEntity
import org.sakshi.core.database.SakshiDatabase
import org.sakshi.core.database.SakshiDatabaseFactory
import org.sakshi.core.database.SakshiSchema

/**
 * Megaplan 27.3 on a real SQLCipher database file: opening without the key and a single flipped byte must both fail
 * closed. Everything lives in a scratch directory of the test package sandbox; the data is synthetic.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseSecurityDeviceTest : DeviceTestBase() {
    private fun passphrase(): ByteArray = ByteArray(32).also(SecureRandom()::nextBytes)

    private fun openDirect(file: File, passphrase: ByteArray): SakshiDatabase =
        SakshiDatabaseFactory.openEncrypted(context, passphrase.copyOf(), file.absolutePath)

    private fun SupportSQLiteDatabase.rowCount(sql: String): Int = query(sql).use { it.count }

    private fun SupportSQLiteDatabase.firstString(sql: String): String? =
        query(sql).use { if (it.moveToFirst()) it.getString(0) else null }

    /** Creates an encrypted database of a few hundred pages holding a synthetic case and audit rows. */
    private fun createDatabase(directory: File, key: ByteArray): File = runBlocking {
        val file = File(directory, "sakshi.db")
        val created = openDirect(file, key)
        try {
            created.caseDao().insert(CaseEntity("synthetic-case-security", "synthetic-security", "2026-10-02T10:00:00Z", "active"))
            repeat(300) { i ->
                created.auditDao().append(
                    AuditRecordEntity(
                        at = "2026-10-02T10:00:00Z",
                        atEpochMs = 1L,
                        action = "synthetic.action",
                        subjectType = "synthetic",
                        subjectId = "synthetic-$i",
                        payloadSha256 = "0".repeat(64),
                        prevHash = ByteArray(32),
                        thisHash = ByteArray(32) { i.toByte() },
                    ),
                )
            }
        } finally {
            created.close()
        }
        file
    }

    @Test
    fun theDatabaseFileCannotBeOpenedAsAPlainSqliteFile() {
        val file = createDatabase(scratchDirectory(), passphrase())
        val plain = assertFails {
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
                database.rawQuery("SELECT count(*) FROM sqlite_master", null).use { it.moveToFirst() }
            }
        }
        record("open_without_key_plain_sqlite", "exception" to plain.javaClass.name)
    }

    @Test
    fun theDatabaseFileCannotBeOpenedWithAnEmptyOrAWrongPassphrase() {
        val file = createDatabase(scratchDirectory(), passphrase())
        for ((label, key) in listOf("empty" to ByteArray(0), "wrong" to passphrase(), "zeros" to ByteArray(32))) {
            val failure = assertFails("$label passphrase") {
                val database = openDirect(file, key)
                try {
                    database.openHelper.writableDatabase.rowCount("SELECT * FROM ${SakshiSchema.CASE_FILE}")
                } finally {
                    runCatching { database.close() }
                }
            }
            record("open_with_bad_key", "kind" to label, "exception" to failure.javaClass.name)
        }
    }

    @Test
    fun theRightPassphraseStillOpensTheUntouchedFile() {
        val key = passphrase()
        val file = createDatabase(scratchDirectory(), key)
        val reopened = openDirect(file, key)
        try {
            val sql = reopened.openHelper.writableDatabase
            assertEquals(1, sql.rowCount("SELECT * FROM ${SakshiSchema.CASE_FILE}"))
            assertEquals(0, sql.rowCount("PRAGMA cipher_integrity_check"))
            assertEquals("ok", sql.firstString("PRAGMA integrity_check"))
        } finally {
            reopened.close()
        }
    }

    @Test
    fun oneFlippedByteAnywhereInTheDatabaseFileFailsClosed() {
        val key = passphrase()
        val pristine = createDatabase(scratchDirectory(), key)
        val size = pristine.length()
        assertTrue(size > 64 * 1024, "the database must be large enough to have several pages")
        val pageSize = 4096L
        val positions = listOf(0L, 15L, 16L, 100L, pageSize - 1, pageSize, pageSize + 100, size / 2, size - pageSize, size - 1)
        val silent = ArrayList<Long>()
        for (position in positions) {
            val damaged = File(scratchDirectory(), "damaged-$position.db")
            pristine.copyTo(damaged)
            flipByte(damaged, position)
            val outcome = try {
                val reopened = openDirect(damaged, key)
                try {
                    val sql = reopened.openHelper.writableDatabase
                    SakshiSchema.allTables.forEach { sql.rowCount("SELECT * FROM $it") }
                    val hmacFailures = sql.rowCount("PRAGMA cipher_integrity_check")
                    val integrity = sql.firstString("PRAGMA integrity_check")
                    if (hmacFailures > 0 || integrity != "ok") "corruption_reported" else "SILENT"
                } finally {
                    runCatching { reopened.close() }
                }
            } catch (e: Exception) {
                "exception ${e.javaClass.simpleName}"
            }
            record("flip_one_byte", "position" to position, "outcome" to outcome)
            if (outcome == "SILENT") silent += position
        }
        assertEquals(emptyList(), silent, "flipped byte positions that nothing noticed")
    }
}
