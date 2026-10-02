package org.sakshi.core.vault

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VaultTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var counter = 0
    private lateinit var vault: Vault
    private val blobDirectory get() = File(context.noBackupFilesDir, "vault/blobs")

    @Before
    fun open() {
        vault = Vault.openForTests(context, testWrapper(), { Instant.parse("2026-10-02T10:00:00Z") }, { "synthetic-${++counter}" })
    }

    @After
    fun close() {
        vault.close()
        File(context.noBackupFilesDir, "vault").deleteRecursively()
    }

    private fun importRequest(caseId: String) = ImportRequest(
        caseId, AcquisitionKind.SHARED_TEXT, AccessClass.USER_MEDIATED, "synthetic-test", "text/plain",
        null, "synthetic-name.txt", null, 1_000_000L,
    )

    @Test
    fun startUpSweepsTemporaryFilesRecoversJobsAndAuditsOpening() = runBlocking<Unit> {
        val case = vault.cases.create("Synthetic")
        val imported = vault.evidence.import(importRequest(case.id), ByteArrayInputStream(ByteArray(30)))
        vault.jobs.enqueue(imported.id, "ocr", imported.sha256, 1)
        vault.jobs.claimNext()
        File(blobDirectory, "${"e".repeat(32)}.tmp").writeBytes(ByteArray(8))
        val headBefore = vault.audit.head()

        val report = vault.startUp()

        assertEquals(1, report.sweep.temporaryFilesDeleted)
        assertEquals(0, report.sweep.orphanFilesDeleted)
        assertEquals(1, report.jobsRecovered)
        assertFalse(vault.audit.head().contentEquals(headBefore))
        assertEquals(3, assertIs<AuditVerification.Valid>(vault.audit.verify()).count)
        assertEquals(imported.id, vault.jobs.claimNext()?.evidenceId)
    }

    @Test
    fun noStoredFileContainsPlaintextOrCaseTitle() = runBlocking<Unit> {
        val title = "SYNTHETIC-CASE-TITLE-91b2"
        val case = vault.cases.create(title)
        val plaintext = MARKER.repeat(2000).toByteArray()
        val imported = vault.evidence.import(importRequest(case.id), ByteArrayInputStream(plaintext))
        vault.evidence.verify(imported.id)
        vault.startUp()

        val files = context.noBackupFilesDir.walkTopDown().filter { it.isFile }.toList()
        assertTrue(files.isNotEmpty())
        for (file in files) {
            val bytes = file.readBytes()
            assertFalse(contains(bytes, MARKER.toByteArray()))
            assertFalse(contains(bytes, title.toByteArray()))
        }
    }
}
