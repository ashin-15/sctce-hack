package org.sakshi.core.vault

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import org.sakshi.core.crypto.BlobKey
import org.sakshi.core.crypto.BlobReader
import org.sakshi.core.crypto.BlobWriteResult
import org.sakshi.core.crypto.BlobWriter
import org.sakshi.core.crypto.KeyWrapper
import org.sakshi.core.crypto.VaultSecrets
import org.sakshi.core.integrity.Sha256

/** Result of [BlobStore.write]. [relativePath] is a plain file name inside the store directory. */
public class StoredBlob(
    public val blobId: ByteArray,
    public val relativePath: String,
    public val wrappedKey: ByteArray,
    public val result: BlobWriteResult,
    public val chunkSize: Int,
)

/**
 * Encrypted blob files in [directory]. File names come only from the random blob id, never from a supplied name.
 * All methods block; call them off the main thread.
 */
public class BlobStore(
    private val directory: File,
    private val wrapper: KeyWrapper,
    private val chunkSize: Int = BlobWriter.DEFAULT_CHUNK_SIZE,
) {
    /**
     * Encrypts [input] into a new blob. The temporary file is removed on any failure, and no final file
     * exists unless the whole blob was written, synced and renamed. Does not close [input].
     */
    public fun write(input: InputStream, maxPlaintextBytes: Long): StoredBlob {
        check(directory.isDirectory || directory.mkdirs()) { "Blob directory is not available" }
        val blobId = VaultSecrets.newBlobId()
        val name = Sha256.hex(blobId)
        val temporary = File(directory, name + TEMPORARY_SUFFIX)
        val target = File(directory, name + BLOB_SUFFIX)
        var committed = false
        try {
            BlobKey.generate().use { key ->
                val result = FileOutputStream(temporary).use { output ->
                    BlobWriter.encrypt(input, output, key, blobId, chunkSize, maxPlaintextBytes).also {
                        output.fd.sync()
                    }
                }
                val keyBytes = key.copyBytes()
                val wrappedKey = try {
                    wrapper.wrap(keyBytes)
                } finally {
                    keyBytes.fill(0)
                }
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
                committed = true
                return StoredBlob(blobId, target.name, wrappedKey, result, chunkSize)
            }
        } finally {
            if (!committed) temporary.delete()
        }
    }

    /**
     * Opens the blob for authenticated reading. The caller closes the reader.
     *
     * @throws java.nio.file.NoSuchFileException if the file is missing.
     * @throws org.sakshi.core.crypto.BlobIntegrityException if the file was modified or does not match.
     * @throws java.security.GeneralSecurityException if the key cannot be unwrapped.
     */
    public fun open(relativePath: String, blobId: ByteArray, wrappedKey: ByteArray): BlobReader {
        val file = resolve(relativePath)
        val keyBytes = wrapper.unwrap(wrappedKey)
        try {
            BlobKey.fromBytes(keyBytes).use { key ->
                return BlobReader.open(FileChannel.open(file.toPath(), StandardOpenOption.READ), key, blobId)
            }
        } finally {
            keyBytes.fill(0)
        }
    }

    /**
     * Overwrites the file with zeros, then deletes it. Returns true if the file no longer exists.
     *
     * Physical erasure on flash storage is not guaranteed: the overwrite may land in a different block.
     * Destroying the wrapped key (deleting its database row) is the real erase.
     */
    public fun delete(relativePath: String): Boolean {
        val file = resolve(relativePath)
        if (!file.exists()) return true
        overwriteWithZeros(file)
        return file.delete() || !file.exists()
    }

    /** True if the blob file exists. */
    public fun exists(relativePath: String): Boolean = resolve(relativePath).isFile

    /** Names of all final blob files in the store. */
    public fun listBlobs(): List<String> = names(BLOB_SUFFIX)

    /** Deletes leftover temporary files from interrupted writes and returns how many were removed. */
    public fun sweepTemporaryFiles(): Int =
        names(TEMPORARY_SUFFIX).count { File(directory, it).delete() }

    private fun names(suffix: String): List<String> =
        directory.list { _, name -> name.endsWith(suffix) }?.sorted().orEmpty()

    private fun resolve(relativePath: String): File {
        require(NAME_PATTERN.matches(relativePath)) { "Not a blob file name" }
        return File(directory, relativePath)
    }

    private fun overwriteWithZeros(file: File) {
        try {
            RandomAccessFile(file, "rw").use { raf ->
                val zeros = ByteArray(ZERO_BLOCK)
                var remaining = raf.length()
                while (remaining > 0) {
                    val count = minOf(remaining, zeros.size.toLong()).toInt()
                    raf.write(zeros, 0, count)
                    remaining -= count
                }
                raf.fd.sync()
            }
        } catch (_: IOException) {
            // Best effort only: the file is deleted next and its key is destroyed by the caller.
        }
    }

    public companion object {
        private const val BLOB_SUFFIX = ".skb"
        private const val TEMPORARY_SUFFIX = ".tmp"
        private const val ZERO_BLOCK = 65536
        private val NAME_PATTERN = Regex("[0-9a-f]{32}\\.skb")

        /** Recovers the 16-byte blob id from a blob file name. */
        public fun blobIdOf(relativePath: String): ByteArray {
            require(NAME_PATTERN.matches(relativePath)) { "Not a blob file name" }
            return Sha256.fromHex(relativePath.removeSuffix(BLOB_SUFFIX))
        }
    }
}
