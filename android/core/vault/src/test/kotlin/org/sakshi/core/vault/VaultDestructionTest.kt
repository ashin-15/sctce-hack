package org.sakshi.core.vault

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.sakshi.core.crypto.KeyWrapper

/** Whole-vault destruction over the vault directory. The SQLCipher database cannot run here, so its files are stand-ins. */
@RunWith(RobolectricTestRunner::class)
class VaultDestructionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var counter = 0
    private val root get() = File(context.noBackupFilesDir, "vault")
    private val sibling get() = File(context.noBackupFilesDir, "synthetic-sibling.txt")

    @Before
    fun prepare() {
        context.noBackupFilesDir.mkdirs()
        sibling.writeText("synthetic sibling")
    }

    @After
    fun clean() {
        root.deleteRecursively()
        sibling.delete()
    }

    private fun open(wrapper: KeyWrapper = testWrapper()): Vault =
        Vault.openForTests(context, wrapper, { Instant.parse("2026-10-02T10:00:00Z") }, { "synthetic-${++counter}" })

    private fun request(caseId: String) = ImportRequest(
        caseId, AcquisitionKind.SHARED_TEXT, AccessClass.USER_MEDIATED, "synthetic-test", "text/plain",
        null, "synthetic-name.txt", null, 1_000_000L,
    )

    /** Files a device vault would hold besides blobs. */
    private fun plantDeviceFiles(): List<File> {
        VaultKeyFile(root).loadOrCreate(testWrapper()).fill(0)
        val names = listOf("sakshi.db", "sakshi.db-wal", "sakshi.db-shm", "sakshi.db-journal", "db.key.wrapped.tmp")
        return names.map { File(root, it).apply { writeBytes(ByteArray(16) { 7 }) } } + File(root, "db.key.wrapped")
    }

    @Test
    fun instanceDestroyRemovesKeyDatabaseSideFilesBlobsAndDirectory() = runBlocking<Unit> {
        val vault = open()
        val case = vault.cases.create("Synthetic")
        vault.evidence.import(request(case.id), ByteArrayInputStream(ByteArray(40)))
        val planted = plantDeviceFiles()
        File(root, "blobs/${"a".repeat(32)}.tmp").writeBytes(ByteArray(4))
        assertTrue(File(root, "blobs").list().orEmpty().size >= 2)
        assertTrue(planted.all { it.exists() })

        val result = vault.destroy()

        assertEquals(VaultDestroyResult.Destroyed, result)
        assertFalse(root.exists())
        assertTrue(sibling.exists(), "files outside the vault directory are untouched")
        assertEquals("synthetic sibling", sibling.readText())
    }

    @Test
    fun deviceKeyFileIsDeletedFirstAndReopeningGivesFreshKeyMaterial() {
        val wrapper = testWrapper()
        val old = VaultKeyFile(root).loadOrCreate(wrapper)
        val oldWrapped = File(root, "db.key.wrapped").readBytes()

        assertEquals(VaultDestroyResult.Destroyed, Vault.destroy(context, wrapper))
        assertFalse(File(root, "db.key.wrapped").exists())

        val fresh = VaultKeyFile(root).loadOrCreate(wrapper)
        assertNotEquals(old.toList(), fresh.toList(), "a new database passphrase is generated")
        assertFalse(oldWrapped.contentEquals(File(root, "db.key.wrapped").readBytes()))
    }

    @Test
    fun reopeningAfterDestructionGivesAnEmptyVaultWithAFreshAuditChain() = runBlocking<Unit> {
        val first = open()
        first.cases.create("Synthetic")
        assertEquals(1, assertIs<AuditVerification.Valid>(first.audit.verify()).count)
        assertEquals(VaultDestroyResult.Destroyed, first.destroy())

        val second = open()
        try {
            val cases = second.cases.observe()
            assertEquals(0, cases.first().size)
            val verification = assertIs<AuditVerification.Valid>(second.audit.verify())
            assertEquals(0, verification.count)
            assertTrue(second.audit.records().isEmpty())
        } finally {
            second.close()
        }
    }

    @Test
    fun destroyIsIdempotentAndSucceedsOnAVaultThatWasNeverCreated() {
        assertFalse(root.exists())
        assertEquals(VaultDestroyResult.Destroyed, Vault.destroy(context, testWrapper()))
        plantDeviceFiles()
        assertEquals(VaultDestroyResult.Destroyed, Vault.destroy(context, testWrapper()))
        assertEquals(VaultDestroyResult.Destroyed, Vault.destroy(context, testWrapper()))
        assertTrue(sibling.exists())
    }

    @Test
    fun aFileThatCannotBeDeletedIsReportedAndNeverCountsAsSuccess() {
        plantDeviceFiles()
        File(root, "blobs").mkdirs()
        val stuck = File(root, "blobs/${"b".repeat(32)}.skb").apply { writeBytes(ByteArray(8)) }
        val refuse = VaultFileDeleter { file -> if (file == stuck) false else VaultFileDeleter.DEFAULT.delete(file) }

        val result = assertIs<VaultDestroyResult.Incomplete>(Vault.destroy(context, testWrapper(), refuse))

        assertEquals(listOf("blobs/${"b".repeat(32)}.skb"), result.remaining)
        assertFalse(result.keyStoreKeyRemains)
        assertFalse(File(root, "db.key.wrapped").exists(), "the key file went first and stays deleted")
        assertTrue(stuck.exists())
        assertTrue(sibling.exists())

        assertEquals(VaultDestroyResult.Destroyed, Vault.destroy(context, testWrapper()), "a retry finishes the job")
        assertFalse(root.exists())
    }

    @Test
    fun aKeyFileThatCannotBeDeletedIsReported() {
        VaultKeyFile(root).loadOrCreate(testWrapper()).fill(0)
        val keyFile = File(root, "db.key.wrapped")
        val refuse = VaultFileDeleter { file -> if (file == keyFile) false else VaultFileDeleter.DEFAULT.delete(file) }

        val result = assertIs<VaultDestroyResult.Incomplete>(Vault.destroy(context, testWrapper(), refuse))

        assertEquals(listOf("db.key.wrapped"), result.remaining)
    }

    @Test
    fun aDestroyableWrapperHasItsMasterKeyDeleted() {
        val wrapper = RecordingWrapper()
        plantDeviceFiles()

        assertEquals(VaultDestroyResult.Destroyed, Vault.destroy(context, wrapper))
        assertEquals(1, wrapper.destroyed)
        assertEquals(VaultDestroyResult.Destroyed, Vault.destroy(context, wrapper))
        assertEquals(2, wrapper.destroyed, "destroying again asks again; the wrapper is idempotent")
    }

    @Test
    fun aRefusingKeyStoreIsReportedButFilesAreStillRemoved() {
        plantDeviceFiles()

        val result = assertIs<VaultDestroyResult.Incomplete>(Vault.destroy(context, RecordingWrapper(refuse = true)))

        assertTrue(result.keyStoreKeyRemains)
        assertTrue(result.remaining.isEmpty())
        assertFalse(root.exists())
    }

    @Test
    fun symbolicLinksAreRemovedWithoutTouchingTheirTargets() {
        root.mkdirs()
        val outside = File(context.noBackupFilesDir, "synthetic-outside").apply { mkdirs() }
        val kept = File(outside, "kept.txt").apply { writeText("synthetic") }
        Files.createSymbolicLink(File(root, "link").toPath(), outside.toPath())

        assertEquals(VaultDestroyResult.Destroyed, Vault.destroy(context, testWrapper()))

        assertFalse(root.exists())
        assertContentEquals("synthetic".toByteArray(), kept.readBytes())
        outside.deleteRecursively()
    }

    private class RecordingWrapper(private val refuse: Boolean = false) : DestroyableKeyWrapper {
        private val delegate = testWrapper()
        var destroyed = 0

        override fun wrap(secret: ByteArray): ByteArray = delegate.wrap(secret)

        override fun unwrap(wrapped: ByteArray): ByteArray = delegate.unwrap(wrapped)

        override fun destroyKey() {
            destroyed++
            if (refuse) throw VaultKeyException.Unavailable(null)
        }
    }
}
