package org.sakshi.core.database

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the schema is able to hold, read from the live database. Encryption at rest is a property of the SQLCipher
 * open path and is not testable here: these tests run on a plain in-memory SQLite database and so say nothing
 * about the database file on a device.
 */
class SchemaSecretsTest : DatabaseTestBase() {
    private class Column(val table: String, val name: String, val type: String)

    private fun columns(): List<Column> = SakshiSchema.allTables.flatMap { table ->
        db.openHelper.readableDatabase.query("PRAGMA table_info($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val typeIndex = cursor.getColumnIndexOrThrow("type")
            generateSequence { if (cursor.moveToNext()) Column(table, cursor.getString(nameIndex), cursor.getString(typeIndex)) else null }
                .toList()
        }
    }

    @Test
    fun theOnlyBinaryColumnsAreWrappedKeysAndAuditHashes() {
        val binary = columns().filter { it.type.equals("BLOB", ignoreCase = true) }.map { "${it.table}.${it.name}" }.toSet()
        assertEquals(setOf("evidence_blob.wrapped_key", "audit_record.prev_hash", "audit_record.this_hash"), binary)
    }

    @Test
    fun onlyTheWrappedKeyHoldsKeyMaterial() {
        val suspicious = Regex("passphrase|password|secret|master|plain|(^|_)key($|_)", RegexOption.IGNORE_CASE)
        val found = columns().filter { suspicious.containsMatchIn(it.name) }.map { "${it.table}.${it.name}" }
        // plaintext_length is a size and signer_key_id is the hex hash of a public key; only wrapped_key is key material.
        assertEquals(listOf("evidence_blob.wrapped_key", "evidence_blob.plaintext_length", "report_snapshot.signer_key_id"), found)
    }

    @Test
    fun theOnlyUriInformationKeptIsTheAuthorityClaim() {
        val uriColumns = columns().filter { it.name.contains("uri", ignoreCase = true) }.map { "${it.table}.${it.name}" }
        assertEquals(listOf("capture_metadata.uri_authority_claim"), uriColumns)
    }

    @Test
    fun theOnlyColumnThatNamesAFileIsTheBlobFileName() {
        val fileColumns = columns().filter { it.name.contains("path", ignoreCase = true) }.map { "${it.table}.${it.name}" }
        assertEquals(listOf("evidence_blob.path"), fileColumns)
    }
}
