package org.sakshi.core.crypto

import java.security.GeneralSecurityException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class KeysAndSecretsTest {
    @Test
    fun blobKeyRejectsWrongLength() {
        for (length in listOf(0, 16, 31, 33, 64)) {
            assertFailsWith<IllegalArgumentException> { BlobKey.fromBytes(ByteArray(length)) }
        }
    }

    @Test
    fun blobKeyCannotBeReadAfterClose() {
        val key = BlobKey.generate()
        assertEquals(32, key.copyBytes().size)
        key.close()
        assertFailsWith<IllegalStateException> { key.copyBytes() }
    }

    @Test
    fun blobKeyCopiesItsInput() {
        val source = ByteArray(32) { it.toByte() }
        val key = BlobKey.fromBytes(source)
        source.fill(0)
        assertContentEquals(ByteArray(32) { it.toByte() }, key.copyBytes())
        val copy = key.copyBytes()
        copy.fill(0)
        assertContentEquals(ByteArray(32) { it.toByte() }, key.copyBytes())
    }

    @Test
    fun generatedKeysDiffer() {
        assertFalse(BlobKey.generate().copyBytes().contentEquals(BlobKey.generate().copyBytes()))
    }

    @Test
    fun wrapperRoundTrips() {
        val wrapper = SoftwareKeyWrapper(ByteArray(32) { 7 })
        for (secret in listOf(ByteArray(0), ByteArray(32) { it.toByte() }, ByteArray(100) { 1 })) {
            assertContentEquals(secret, wrapper.unwrap(wrapper.wrap(secret)))
        }
    }

    @Test
    fun wrapsOfTheSameSecretDiffer() {
        val wrapper = SoftwareKeyWrapper(ByteArray(32) { 7 })
        val secret = ByteArray(32) { 9 }
        assertFalse(wrapper.wrap(secret).contentEquals(wrapper.wrap(secret)))
    }

    @Test
    fun anySingleByteChangeBreaksUnwrap() {
        val wrapper = SoftwareKeyWrapper(ByteArray(32) { 7 })
        val wrapped = wrapper.wrap(ByteArray(32) { 9 })
        for (index in wrapped.indices) {
            val damaged = wrapped.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
            assertFailsWith<GeneralSecurityException>("byte $index") { wrapper.unwrap(damaged) }
        }
    }

    @Test
    fun truncatedOrExtendedWrapIsRejected() {
        val wrapper = SoftwareKeyWrapper(ByteArray(32) { 7 })
        val wrapped = wrapper.wrap(ByteArray(32) { 9 })
        assertFailsWith<GeneralSecurityException> { wrapper.unwrap(wrapped.copyOf(wrapped.size - 1)) }
        assertFailsWith<GeneralSecurityException> { wrapper.unwrap(wrapped + 0) }
        assertFailsWith<GeneralSecurityException> { wrapper.unwrap(ByteArray(0)) }
    }

    @Test
    fun otherMasterKeyCannotUnwrap() {
        val wrapped = SoftwareKeyWrapper(ByteArray(32) { 7 }).wrap(ByteArray(32) { 9 })
        assertFailsWith<GeneralSecurityException> { SoftwareKeyWrapper(ByteArray(32) { 8 }).unwrap(wrapped) }
    }

    @Test
    fun wrapperRejectsWrongMasterKeyLength() {
        assertFailsWith<IllegalArgumentException> { SoftwareKeyWrapper(ByteArray(16)) }
        assertFailsWith<IllegalArgumentException> { SoftwareKeyWrapper(ByteArray(33)) }
    }

    @Test
    fun vaultSecretsHaveExpectedLengthsAndDiffer() {
        assertEquals(32, VaultSecrets.newDatabasePassphrase().size)
        assertEquals(16, VaultSecrets.newBlobId().size)
        assertFalse(VaultSecrets.newDatabasePassphrase().contentEquals(VaultSecrets.newDatabasePassphrase()))
        assertFalse(VaultSecrets.newBlobId().contentEquals(VaultSecrets.newBlobId()))
    }
}
