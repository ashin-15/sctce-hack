package org.sakshi.core.crypto

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** Wraps small secrets (blob keys, the database passphrase) under a master key. */
public interface KeyWrapper {
    public fun wrap(secret: ByteArray): ByteArray

    /** @throws java.security.GeneralSecurityException if the wrapped value was not produced by this wrapper or was modified. */
    public fun unwrap(wrapped: ByteArray): ByteArray
}

/**
 * Software AES-256-GCM wrapper for JVM tests and tools. The device build uses an Android Keystore implementation
 * instead. Format: version byte 0x01 || 12-byte random nonce || ciphertext and tag. The version byte is
 * authenticated as additional data.
 */
public class SoftwareKeyWrapper(masterKey: ByteArray, private val random: SecureRandom = SecureRandom()) : KeyWrapper {
    private val key: SecretKeySpec

    init {
        require(masterKey.size == BlobKey.KEY_SIZE) { "Master key must be ${BlobKey.KEY_SIZE} bytes" }
        key = SecretKeySpec(masterKey, "AES")
    }

    override fun wrap(secret: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_SIZE).also(random::nextBytes)
        val sealed = cipher(Cipher.ENCRYPT_MODE, nonce).doFinal(secret)
        return byteArrayOf(VERSION) + nonce + sealed
    }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        if (wrapped.size < 1 + NONCE_SIZE + BlobFormat.TAG_SIZE) {
            throw GeneralSecurityException("Wrapped value is too short")
        }
        if (wrapped[0] != VERSION) throw GeneralSecurityException("Unsupported wrapped value version")
        val nonce = wrapped.copyOfRange(1, 1 + NONCE_SIZE)
        return cipher(Cipher.DECRYPT_MODE, nonce).doFinal(wrapped, 1 + NONCE_SIZE, wrapped.size - 1 - NONCE_SIZE)
    }

    private fun cipher(mode: Int, nonce: ByteArray): Cipher =
        BlobFormat.newGcmCipher(mode, key, nonce, byteArrayOf(VERSION))

    private companion object {
        const val VERSION: Byte = 1
        const val NONCE_SIZE = 12
    }
}
