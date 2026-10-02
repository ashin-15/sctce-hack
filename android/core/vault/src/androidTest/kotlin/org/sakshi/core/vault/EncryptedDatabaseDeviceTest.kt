package org.sakshi.core.vault

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.File
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.sakshi.core.database.AuditRecordEntity
import org.sakshi.core.database.CaseEntity
import org.sakshi.core.database.SakshiDatabase
import org.sakshi.core.database.SakshiDatabaseFactory
import org.sakshi.core.database.SakshiSchema

@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseDeviceTest : DeviceTestBase() {
    private val databaseFile: File get() = File(vaultDirectory, "sakshi.db")

    private fun passphrase(): ByteArray = ByteArray(32).also(SecureRandom()::nextBytes)

    private fun openDirect(file: File, passphrase: ByteArray): SakshiDatabase =
        SakshiDatabaseFactory.openEncrypted(context, passphrase.copyOf(), file.absolutePath)

    private fun SupportSQLiteDatabase.rowCount(sql: String): Int =
        query(sql).use { cursor ->
            var rows = 0
            while (cursor.moveToNext()) rows++
            rows
        }

    private fun SupportSQLiteDatabase.firstString(sql: String): String? =
        query(sql).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    @Test
    fun vaultOpensCreatesCaseAndKeepsItAcrossReopen() = runBlocking<Unit> {
        val wrapper = newWrapper()
        val first = openVault(wrapper)
        first.startUp()
        val case = first.cases.create("synthetic-case-open")
        assertEquals(listOf(case.id), first.cases.observe().first().map { it.id })
        closeVault(first)

        val second = openVault(wrapper)
        second.startUp()

        val ids = second.cases.observe().first().map { it.id }
        assertEquals(listOf(case.id), ids)
        assertIs<AuditVerification.Valid>(second.audit.verify())
    }

    @Test
    fun databaseAndBlobFilesContainNoPlaintext() = runBlocking<Unit> {
        val title = "synthetic-title-Zq9x-confidential"
        val needles = listOf(title.toByteArray(Charsets.UTF_8), title.toByteArray(Charsets.UTF_16LE))
        val markers = listOf(DEVICE_MARKER.toByteArray(Charsets.UTF_8), DEVICE_MARKER.toByteArray(Charsets.UTF_16LE))
        val vault = openVault(newWrapper())
        vault.startUp()
        val case = vault.cases.create(title)
        vault.evidence.import(
            importRequest(case.id, 1_000_000L),
            ByteArrayInputStream(DEVICE_MARKER.repeat(3000).toByteArray()),
        )

        fun assertNoPlaintext(stage: String) {
            val files = vaultFiles()
            assertTrue(files.any { it.name == "sakshi.db" }, "database file exists ($stage)")
            for (file in files) {
                val bytes = file.readBytes()
                for (needle in needles + markers) {
                    assertFalse(containsBytes(bytes, needle), "Plaintext found in a ${file.extension} file ($stage)")
                }
            }
        }

        assertNoPlaintext("open")
        closeVault(vault)
        assertNoPlaintext("closed")

        val header = ByteArray(16)
        databaseFile.inputStream().use { assertEquals(16, it.read(header)) }
        assertFalse(
            header.contentEquals("SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1)),
            "Database starts with the plain SQLite header",
        )
        record(
            "database_on_disk",
            "db_bytes" to databaseFile.length(),
            "files_scanned" to vaultFiles().size,
        )
    }

    @Test
    fun wrongPassphraseCannotReadTheDatabase() = runBlocking<Unit> {
        val vault = openVault(newWrapper())
        vault.startUp()
        vault.cases.create("synthetic-case-wrong-key")
        closeVault(vault)

        val wrong = openDirect(databaseFile, passphrase())
        try {
            val failure = assertFails { wrong.openHelper.writableDatabase.rowCount("SELECT * FROM ${SakshiSchema.CASE_FILE}") }
            record("wrong_passphrase", "exception" to failure.javaClass.name)
        } finally {
            runCatching { wrong.close() }
        }
    }

    @Test
    fun tamperedDatabaseFileIsNotReadSilently() = runBlocking<Unit> {
        val key = passphrase()
        val file = File(scratchDirectory(), "tamper.db")
        val created = openDirect(file, key)
        try {
            created.caseDao().insert(
                CaseEntity("synthetic-case-tamper", "synthetic-tamper", "2026-10-02T10:00:00Z", "active"),
            )
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
        val size = file.length()
        assertTrue(size > 64 * 1024, "Database is large enough to tamper with")
        flipByte(file, size / 2)

        val outcome = try {
            val reopened = openDirect(file, key)
            try {
                val sql = reopened.openHelper.writableDatabase
                SakshiSchema.allTables.forEach { sql.rowCount("SELECT * FROM $it") }
                val hmacFailures = sql.rowCount("PRAGMA cipher_integrity_check")
                val integrity = sql.firstString("PRAGMA integrity_check")
                if (hmacFailures > 0 || integrity != "ok") {
                    "corruption_reported hmac_failures=$hmacFailures integrity_ok=${integrity == "ok"}"
                } else {
                    "SILENT"
                }
            } finally {
                runCatching { reopened.close() }
            }
        } catch (e: Exception) {
            "exception ${e.javaClass.name}: ${e.message.orEmpty().take(120)}"
        }

        record("tamper_database", "db_bytes" to size, "flipped_at" to size / 2, "outcome" to outcome)
        assertFalse(outcome == "SILENT", "Tampered database was read without any error")
    }

    @Test
    fun triggersAndForeignKeysAreActiveUnderSqlCipher() {
        val file = File(scratchDirectory(), "triggers.db")
        val database = openDirect(file, passphrase())
        try {
            val sql = database.openHelper.writableDatabase
            sql.execSQL("INSERT INTO case_file(id, title, created_at, status) VALUES ('synthetic-case-t', 'synthetic-t', 't', 'active')")
            sql.execSQL(
                "INSERT INTO evidence(id, case_id, acquisition_kind, access_class, received_at, received_at_epoch_ms, " +
                    "claimed_origin, declared_mime, detected_mime, byte_size, sha256, retention_mode) " +
                    "VALUES ('synthetic-evidence-t', 'synthetic-case-t', 'shared_stream', 'user_mediated', 't', 1, " +
                    "NULL, NULL, NULL, 10, 'aa', 'confirmed_vault')",
            )
            sql.execSQL(
                "INSERT INTO audit_record(at, at_epoch_ms, action, subject_type, subject_id, payload_sha256, prev_hash, this_hash) " +
                    "VALUES ('t', 1, 'synthetic.action', 'synthetic', 'synthetic-id', 'aa', X'00', X'01')",
            )

            val update = assertFails { sql.execSQL("UPDATE evidence SET byte_size = 11 WHERE id = 'synthetic-evidence-t'") }
            val delete = assertFails { sql.execSQL("DELETE FROM audit_record") }
            val orphan = assertFails {
                sql.execSQL("INSERT INTO evidence_state(evidence_id, support_state, updated_at_epoch_ms) VALUES ('synthetic-missing', 'saved', 1)")
            }

            assertContains(update.message.orEmpty(), "insert-only", ignoreCase = true)
            assertContains(delete.message.orEmpty(), "append-only", ignoreCase = true)
            assertContains(orphan.message.orEmpty(), "FOREIGN KEY", ignoreCase = true)
            assertEquals(1, sql.rowCount("SELECT * FROM audit_record"))
            assertEquals(10L, sql.query("SELECT byte_size FROM evidence").use { it.moveToFirst(); it.getLong(0) })
            record(
                "triggers_and_foreign_keys",
                "update_exception" to update.javaClass.name,
                "delete_exception" to delete.javaClass.name,
                "foreign_key_exception" to orphan.javaClass.name,
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun sqlCipherLibraryLoadsAndReportsItsVersion() {
        val database = openDirect(File(scratchDirectory(), "version.db"), passphrase())
        try {
            val version = database.openHelper.writableDatabase.firstString("PRAGMA cipher_version")

            assertTrue(!version.isNullOrBlank(), "cipher_version must be non-empty")
            record("sqlcipher_version", "cipher_version" to version.orEmpty())
        } finally {
            database.close()
        }
    }
}
