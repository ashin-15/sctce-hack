package org.sakshi.core.crypto


/** Summary of a completed [BlobWriter.encrypt] call. */
public class BlobWriteResult(
    public val plaintextLength: Long,
    plaintextSha256: ByteArray,
    public val chunkCount: Long,
    public val ciphertextLength: Long,
) {
    private val sha256: ByteArray = plaintextSha256.copyOf()

    /** SHA-256 of the exact plaintext bytes (32 bytes). */
    public val plaintextSha256: ByteArray
        get() = sha256.copyOf()

    /** Lower-case hexadecimal form of [plaintextSha256]. */
    public fun plaintextSha256Hex(): String = sha256.joinToString("") { byte -> HEX[(byte.toInt() ushr 4) and 0x0f].toString() + HEX[byte.toInt() and 0x0f] }

    override fun equals(other: Any?): Boolean =
        other is BlobWriteResult &&
            plaintextLength == other.plaintextLength &&
            chunkCount == other.chunkCount &&
            ciphertextLength == other.ciphertextLength &&
            sha256.contentEquals(other.sha256)

    override fun hashCode(): Int {
        var result = plaintextLength.hashCode()
        result = 31 * result + sha256.contentHashCode()
        result = 31 * result + chunkCount.hashCode()
        result = 31 * result + ciphertextLength.hashCode()
        return result
    }

    override fun toString(): String =
        "BlobWriteResult(plaintextLength=$plaintextLength, sha256=${plaintextSha256Hex()}, " +
            "chunkCount=$chunkCount, ciphertextLength=$ciphertextLength)"
}

// java.util.HexFormat needs Android API 34; the app supports API 26.
private const val HEX: String = "0123456789abcdef"
