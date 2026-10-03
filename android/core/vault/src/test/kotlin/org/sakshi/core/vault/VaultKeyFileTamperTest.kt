package org.sakshi.core.vault

import java.io.File
import java.nio.file.Files
import java.security.GeneralSecurityException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.sakshi.core.crypto.KeyWrapper
import org.sakshi.core.crypto.SoftwareKeyWrapper

/**
 * The wrapped database passphrase file: a damaged, replaced or unusable file must fail closed with a security
 * exception and must never be overwritten, because a fresh passphrase would orphan the encrypted database.
 */
class VaultKeyFileTamperTest {
    private lateinit var directory: File
    private val wrapper: KeyWrapper = SoftwareKeyWrapper(ByteArray(32) { it.toByte() })
    private val file: File get() = File(directory, "db.key.wrapped")

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("synthetic-keyfile").toFile()
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    /** Asserts that loading fails with [GeneralSecurityException] and changes nothing in the directory. */
    private fun assertFailsClosed(using: KeyWrapper, label: String) {
        val before = file.readBytes()
        val names = directory.list().orEmpty().sorted()
        assertFailsWith<GeneralSecurityException>(label) { VaultKeyFile(directory).loadOrCreate(using).fill(0) }
        assertContentEquals(before, file.readBytes(), "$label: key file was rewritten")
        assertEquals(names, directory.list().orEmpty().sorted(), "$label: files were created or removed")
    }

    private fun createKeyFile(): ByteArray = VaultKeyFile(directory).loadOrCreate(wrapper)

    @Test
    fun anUntouchedFileReturnsTheSamePassphraseEveryTime() {
        val first = createKeyFile()
        val second = VaultKeyFile(directory).loadOrCreate(wrapper)
        assertEquals(32, first.size)
        assertContentEquals(first, second)
    }

    @Test
    fun everySingleByteChangeFailsClosed() {
        createKeyFile().fill(0)
        val original = file.readBytes()
        for (offset in original.indices) {
            file.writeBytes(original.copyOf().also { it[offset] = (it[offset].toInt() xor 1).toByte() })
            assertFailsClosed(wrapper, "byte $offset")
        }
    }

    @Test
    fun everyTruncationAndExtensionFailsClosed() {
        createKeyFile().fill(0)
        val original = file.readBytes()
        for (length in original.indices) {
            file.writeBytes(original.copyOf(length))
            assertFailsClosed(wrapper, "length $length")
        }
        file.writeBytes(original + byteArrayOf(0))
        assertFailsClosed(wrapper, "one byte appended")
        file.writeBytes(original + original)
        assertFailsClosed(wrapper, "doubled")
    }

    @Test
    fun aFileMadeByAnotherMasterKeyFailsClosed() {
        createKeyFile().fill(0)
        assertFailsClosed(SoftwareKeyWrapper(ByteArray(32) { (it + 1).toByte() }), "other master key")
    }

    @Test
    fun aPassphraseOfTheWrongSizeIsRefused() {
        file.writeBytes(wrapper.wrap(ByteArray(16)))
        assertFailsClosed(wrapper, "16 byte passphrase")
        file.writeBytes(wrapper.wrap(ByteArray(33)))
        assertFailsClosed(wrapper, "33 byte passphrase")
        file.writeBytes(wrapper.wrap(ByteArray(0)))
        assertFailsClosed(wrapper, "empty passphrase")
    }

    @Test
    fun aMasterKeyThatCannotBeUsedKeepsItsTypedErrorAndTheFile() {
        createKeyFile().fill(0)
        val failures = listOf<VaultKeyException>(
            VaultKeyException.NotAuthenticated(null),
            VaultKeyException.Invalidated(null),
            VaultKeyException.Unavailable(null),
        )
        for (failure in failures) {
            val before = file.readBytes()
            val unusable = object : KeyWrapper {
                override fun wrap(secret: ByteArray): ByteArray = throw failure

                override fun unwrap(wrapped: ByteArray): ByteArray = throw failure
            }
            val thrown = assertFailsWith<VaultKeyException> { VaultKeyFile(directory).loadOrCreate(unusable) }
            assertEquals(failure::class, thrown::class)
            assertContentEquals(before, file.readBytes())
            assertEquals(listOf("db.key.wrapped"), directory.list().orEmpty().toList())
        }
    }

    @Test
    fun aMasterKeyThatCannotWrapLeavesNoFileOnFirstUse() {
        val failing = object : KeyWrapper {
            override fun wrap(secret: ByteArray): ByteArray = throw VaultKeyException.Invalidated(null)

            override fun unwrap(wrapped: ByteArray): ByteArray = throw VaultKeyException.Invalidated(null)
        }
        assertFailsWith<VaultKeyException.Invalidated> { VaultKeyFile(directory).loadOrCreate(failing) }
        assertTrue(directory.list().orEmpty().isEmpty(), "a failed first use left files behind")
    }

    @Test
    fun theFileNeverHoldsThePassphraseInTheClear() {
        val passphrase = createKeyFile()
        val bytes = file.readBytes()
        val found = (0..bytes.size - passphrase.size).any { start -> passphrase.indices.all { bytes[start + it] == passphrase[it] } }
        assertTrue(!found, "the passphrase appears in the wrapped file")
    }
}
