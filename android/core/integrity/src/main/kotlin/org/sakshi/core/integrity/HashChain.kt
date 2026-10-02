package org.sakshi.core.integrity

import java.nio.ByteBuffer

public object HashChain {
    private const val HASH_SIZE: Int = 32

    /** Returns a fresh copy of the 32 zero byte genesis value. */
    public fun genesis(): ByteArray = ByteArray(HASH_SIZE)

    public fun next(previous: ByteArray, entry: ByteArray): ByteArray {
        require(previous.size == HASH_SIZE) { "Previous hash must be $HASH_SIZE bytes, was ${previous.size}" }
        val buffer = ByteBuffer.allocate(HASH_SIZE + Long.SIZE_BYTES + entry.size)
        buffer.put(previous).putLong(entry.size.toLong()).put(entry)
        return Sha256.digest(buffer.array())
    }

    public fun head(entries: List<ByteArray>): ByteArray = entries.fold(genesis(), ::next)
}
