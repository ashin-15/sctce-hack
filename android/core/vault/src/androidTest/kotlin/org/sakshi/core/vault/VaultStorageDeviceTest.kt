package org.sakshi.core.vault

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultStorageDeviceTest : DeviceTestBase() {
    private val payloadSize = 300 * 1024

    private fun payload(): ByteArray = ByteArray(payloadSize).also { Random(42L).nextBytes(it) }

    private fun blobFile(): File {
        val blobs = File(vaultDirectory, "blobs").listFiles { file -> file.name.endsWith(".skb") }.orEmpty()
        assertEquals(1, blobs.size, "Exactly one blob file")
        return blobs.single()
    }

    @Test
    fun importRoundTripsAndVerifiesWithIntactAuditChain() = runBlocking<Unit> {
        val vault = openVault(newWrapper())
        vault.startUp()
        val case = vault.cases.create("synthetic-case-import")
        val bytes = payload()

        val imported = vault.evidence.import(importRequest(case.id, 1_000_000L), ByteArrayInputStream(bytes))

        assertEquals(bytes.size.toLong(), imported.byteSize)
        vault.evidence.openOriginal(imported.id).use { reader ->
            assertContentEquals(bytes, reader.inputStream().readBytes())
        }
        assertEquals(VerificationResult.Intact, vault.evidence.verify(imported.id))
        val audit = assertIs<AuditVerification.Valid>(vault.audit.verify())
        record("import_round_trip", "payload_bytes" to bytes.size, "audit_records" to audit.count)
    }

    @Test
    fun flippedBlobByteIsDetectedAndEvidenceRowSurvives() = runBlocking<Unit> {
        val vault = openVault(newWrapper())
        vault.startUp()
        val case = vault.cases.create("synthetic-case-tamper-blob")
        val imported = vault.evidence.import(importRequest(case.id, 1_000_000L), ByteArrayInputStream(payload()))
        val file = blobFile()
        flipByte(file, file.length() / 2)

        val result = vault.evidence.verify(imported.id)

        assertEquals(VerificationResult.Unreadable(UnreadableReason.AUTHENTICATION_FAILED), result)
        assertEquals(listOf(imported.id), vault.evidence.observeForCase(case.id).first().map { it.id })
        record("tamper_blob", "result" to result.toString())
    }

    @Test
    fun everythingLivesUnderNoBackupAndNothingLeaksElsewhere() = runBlocking<Unit> {
        val filesBefore = context.filesDir.walkTopDown().map { it.path }.toSet()
        val vault = openVault(newWrapper())
        vault.startUp()
        val case = vault.cases.create("synthetic-case-locations")
        vault.evidence.import(
            importRequest(case.id, 1_000_000L),
            ByteArrayInputStream(DEVICE_MARKER.repeat(5000).toByteArray()),
        )

        assertTrue(vaultFiles().all { it.path.startsWith(context.noBackupFilesDir.path) })
        assertTrue(vaultFiles().isNotEmpty())
        val filesAfter = context.filesDir.walkTopDown().map { it.path }.toSet()
        assertEquals(emptySet(), filesAfter - filesBefore, "Nothing new under filesDir")
        assertFalse(context.getDatabasePath("sakshi.db").exists(), "No database in the default database directory")
        val marker = DEVICE_MARKER.toByteArray()
        val leaked = context.cacheDir.walkTopDown().filter { it.isFile }.count { containsBytes(it.readBytes(), marker) }
        assertEquals(0, leaked, "No cache file contains the plaintext marker")
    }

    @Test
    fun blobKeyAndDatabaseFilesAreOwnerOnly() = runBlocking<Unit> {
        val vault = openVault(newWrapper())
        vault.startUp()
        val case = vault.cases.create("synthetic-case-permissions")
        vault.evidence.import(importRequest(case.id, 1_000_000L), ByteArrayInputStream(payload()))

        val checked = listOf(blobFile(), File(vaultDirectory, "sakshi.db"), File(vaultDirectory, "db.key.wrapped"))
        val others = setOf(
            PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_EXECUTE,
        )
        for (file in checked) {
            val permissions = Files.getPosixFilePermissions(file.toPath())
            assertTrue(permissions.none { it in others }, "${file.extension} file grants group or other access")
            assertTrue(PosixFilePermission.OWNER_READ in permissions, "Owner can read ${file.extension}")
        }
        record("file_permissions", "files_checked" to checked.size)
    }
}
