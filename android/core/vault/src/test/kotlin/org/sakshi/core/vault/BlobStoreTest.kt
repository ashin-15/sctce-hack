package org.sakshi.core.vault

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.sakshi.core.crypto.BlobIntegrityException
import org.sakshi.core.crypto.BlobTooLargeException
import org.sakshi.core.integrity.Sha256

class BlobStoreTest {
    private lateinit var directory: File
    private lateinit var store: BlobStore

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("synthetic-blobs").toFile()
        store = BlobStore(directory, testWrapper(), chunkSize = 4096)
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun readAll(stored: StoredBlob): ByteArray =
        store.open(stored.relativePath, stored.blobId, stored.wrappedKey).use { it.inputStream().readBytes() }

    @Test
    fun roundTripsEmptySmallAndMultiChunkInputs() {
        for (size in listOf(0, 100, 4096, 4096 * 3 + 17)) {
            val plaintext = ByteArray(size) { (it * 31).toByte() }
            val stored = store.write(ByteArrayInputStream(plaintext), 1_000_000L)
            assertContentEquals(plaintext, readAll(stored), "size $size")
            assertEquals(size.toLong(), stored.result.plaintextLength)
            assertEquals(Sha256.hex(Sha256.digest(plaintext)), stored.result.plaintextSha256Hex())
        }
    }

    @Test
    fun storedFileNeverContainsPlaintext() {
        val plaintext = MARKER.repeat(500).toByteArray()
        val stored = store.write(ByteArrayInputStream(plaintext), 1_000_000L)
        val onDisk = File(directory, stored.relativePath).readBytes()
        assertFalse(contains(onDisk, MARKER.toByteArray()))
    }

    @Test
    fun nameIsDerivedFromBlobIdOnly() {
        val stored = store.write(ByteArrayInputStream(ByteArray(3)), 100L)
        assertTrue(Regex("[0-9a-f]{32}\\.skb").matches(stored.relativePath))
        assertContentEquals(stored.blobId, BlobStore.blobIdOf(stored.relativePath))
    }

    @Test
    fun leavesOnlyTheFinalFileAfterSuccess() {
        val stored = store.write(ByteArrayInputStream(ByteArray(10)), 100L)
        assertEquals(listOf(stored.relativePath), directory.list()!!.toList())
    }

    @Test
    fun removesTemporaryFileWhenTheInputFailsMidway() {
        assertFailsWith<java.io.IOException> { store.write(FailingInputStream(good = 6000), 1_000_000L) }
        assertEquals(emptyList(), directory.list()!!.toList())
    }

    @Test
    fun removesTemporaryFileWhenTheInputIsTooLarge() {
        assertFailsWith<BlobTooLargeException> { store.write(ByteArrayInputStream(ByteArray(9000)), 100L) }
        assertEquals(emptyList(), directory.list()!!.toList())
    }

    @Test
    fun rejectsNamesThatAreNotPlainBlobFileNames() {
        val hex = "a".repeat(32)
        val bad = listOf("../x", "/etc/passwd", "$hex.bin", "${hex.uppercase()}.skb", "sub/$hex.skb", "$hex.skb/", "")
        for (name in bad) {
            assertFailsWith<IllegalArgumentException>(name) { store.open(name, ByteArray(16), ByteArray(60)) }
            assertFailsWith<IllegalArgumentException>(name) { store.delete(name) }
            assertFailsWith<IllegalArgumentException>(name) { store.exists(name) }
        }
    }

    @Test
    fun opensWithTheWrongBlobIdFail() {
        val stored = store.write(ByteArrayInputStream(ByteArray(10)), 100L)
        assertFailsWith<BlobIntegrityException> {
            store.open(stored.relativePath, ByteArray(16), stored.wrappedKey)
        }
    }

    @Test
    fun deleteRemovesTheFile() {
        val stored = store.write(ByteArrayInputStream(ByteArray(5000)), 100_000L)
        assertTrue(store.exists(stored.relativePath))
        assertTrue(store.delete(stored.relativePath))
        assertFalse(store.exists(stored.relativePath))
        assertTrue(store.delete(stored.relativePath))
    }

    @Test
    fun sweepTemporaryFilesRemovesOnlyTemporaryFiles() {
        val stored = store.write(ByteArrayInputStream(ByteArray(5)), 100L)
        File(directory, "${"1".repeat(32)}.tmp").writeBytes(ByteArray(3))
        File(directory, "${"2".repeat(32)}.tmp").writeBytes(ByteArray(3))
        assertEquals(2, store.sweepTemporaryFiles())
        assertEquals(listOf(stored.relativePath), directory.list()!!.toList())
        assertEquals(0, store.sweepTemporaryFiles())
    }
}
