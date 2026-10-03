package org.sakshi.core.vault

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.GeneralSecurityException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.sakshi.core.crypto.KeyWrapper
import org.sakshi.core.crypto.SoftwareKeyWrapper

/**
 * [Vault.open] with a wrapped database passphrase file that cannot be used: the open must fail with a security
 * exception (the typed [VaultKeyException] when the master key itself is the problem), must leave the key file as it
 * was and must not create a database, so nothing is silently recreated. The database half of "open fails closed"
 * needs the SQLCipher native library and is covered by the device tests.
 */
@RunWith(RobolectricTestRunner::class)
class VaultOpenFailsClosedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val wrapper: KeyWrapper = SoftwareKeyWrapper(ByteArray(32) { it.toByte() })
    private val root: File get() = File(context.noBackupFilesDir, "vault")
    private val keyFile: File get() = File(root, "db.key.wrapped")

    @Before
    fun prepare() {
        root.deleteRecursively()
        root.mkdirs()
        keyFile.writeBytes(wrapper.wrap(ByteArray(32) { (it * 5).toByte() }))
    }

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun assertOpenFailsClosed(using: KeyWrapper, label: String): GeneralSecurityException {
        val before = keyFile.readBytes()
        val names = root.list().orEmpty().sorted()
        val thrown = assertFailsWith<GeneralSecurityException>(label) { Vault.open(context, using).close() }
        assertContentEquals(before, keyFile.readBytes(), "$label: the key file was rewritten")
        assertEquals(names, root.list().orEmpty().sorted(), "$label: files were created or removed")
        assertTrue(!File(root, "sakshi.db").exists(), "$label: a database was created")
        return thrown
    }

    @Test
    fun aFlippedByteInTheWrappedKeyFileFailsClosed() {
        val original = keyFile.readBytes()
        for (offset in listOf(0, original.size / 2, original.size - 1)) {
            keyFile.writeBytes(original.copyOf().also { it[offset] = (it[offset].toInt() xor 1).toByte() })
            assertOpenFailsClosed(wrapper, "byte $offset")
        }
    }

    @Test
    fun aTruncatedOrEmptyOrExtendedWrappedKeyFileFailsClosed() {
        val original = keyFile.readBytes()
        for (length in listOf(0, 1, original.size / 2, original.size - 1)) {
            keyFile.writeBytes(original.copyOf(length))
            assertOpenFailsClosed(wrapper, "length $length")
        }
        keyFile.writeBytes(original + byteArrayOf(0))
        assertOpenFailsClosed(wrapper, "one byte appended")
    }

    @Test
    fun aKeyFileMadeByAnotherMasterKeyFailsClosed() {
        assertOpenFailsClosed(SoftwareKeyWrapper(ByteArray(32) { (it + 9).toByte() }), "other master key")
    }

    @Test
    fun anUnusableMasterKeyKeepsItsTypedErrorThroughVaultOpen() {
        val failures = listOf<() -> VaultKeyException>(
            { VaultKeyException.NotAuthenticated(null) },
            { VaultKeyException.Invalidated(null) },
            { VaultKeyException.Unavailable(null) },
        )
        for (make in failures) {
            val expected = make()
            val unusable = object : KeyWrapper {
                override fun wrap(secret: ByteArray): ByteArray = throw make()

                override fun unwrap(wrapped: ByteArray): ByteArray = throw make()
            }
            val thrown = assertOpenFailsClosed(unusable, expected::class.simpleName.orEmpty())
            assertEquals(expected::class, thrown::class)
        }
    }
}
