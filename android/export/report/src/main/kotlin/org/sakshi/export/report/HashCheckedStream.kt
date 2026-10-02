package org.sakshi.export.report

import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import org.sakshi.core.integrity.Sha256

/** The bytes of an original decrypted fine but do not hash to the value stored at import. */
internal class OriginalHashMismatchException : IOException("Original does not match its stored hash")

/** Passes [source] through and, at its end, fails with [OriginalHashMismatchException] unless it hashed to [expectedSha256]. */
internal class HashCheckedStream(private val source: InputStream, private val expectedSha256: String) : InputStream() {
    private val digest = MessageDigest.getInstance("SHA-256")
    private var matches: Boolean? = null

    override fun read(): Int {
        val value = source.read()
        if (value < 0) verify() else digest.update(value.toByte())
        return value
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val count = source.read(b, off, len)
        if (count < 0) verify() else digest.update(b, off, count)
        return count
    }

    override fun close() {
        source.close()
    }

    private fun verify() {
        val ok = matches ?: (Sha256.hex(digest.digest()) == expectedSha256).also { matches = it }
        if (!ok) throw OriginalHashMismatchException()
    }
}
