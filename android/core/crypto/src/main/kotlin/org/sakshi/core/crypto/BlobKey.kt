package org.sakshi.core.crypto

import java.security.SecureRandom
import javax.crypto.spec.SecretKeySpec

/** 256-bit key held in memory; [close] overwrites it. */
public class BlobKey private constructor(private val bytes: ByteArray) : AutoCloseable {
    private var closed = false

    /** Returns a copy of the key material. The caller is responsible for wiping the copy. */
    public fun copyBytes(): ByteArray {
        check(!closed) { "Key is closed" }
        return bytes.copyOf()
    }

    override fun close() {
        bytes.fill(0)
        closed = true
    }

    internal fun toSecretKeySpec(): SecretKeySpec {
        val copy = copyBytes()
        try {
            return SecretKeySpec(copy, "AES")
        } finally {
            copy.fill(0)
        }
    }

    public companion object {
        /** Creates a key from [random]. */
        public fun generate(random: SecureRandom = SecureRandom()): BlobKey =
            BlobKey(ByteArray(KEY_SIZE).also(random::nextBytes))

        /** Creates a key from a copy of [bytes], which must be exactly 32 bytes. */
        public fun fromBytes(bytes: ByteArray): BlobKey {
            require(bytes.size == KEY_SIZE) { "Key must be $KEY_SIZE bytes, was ${bytes.size}" }
            return BlobKey(bytes.copyOf())
        }

        internal const val KEY_SIZE: Int = 32
    }
}
