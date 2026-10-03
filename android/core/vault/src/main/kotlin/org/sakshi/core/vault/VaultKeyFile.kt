package org.sakshi.core.vault

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException
import org.sakshi.core.crypto.KeyWrapper
import org.sakshi.core.crypto.VaultSecrets

/** Holds the database passphrase, wrapped by the master key, in a single file inside [directory]. */
public class VaultKeyFile(private val directory: File) {
    private val file = File(directory, FILE_NAME)

    /**
     * Returns the 32-byte passphrase, creating and storing a new one on first use. The caller wipes the result.
     * An existing file that cannot be unwrapped is never replaced: that would orphan the database.
     *
     * @throws GeneralSecurityException if the stored value cannot be unwrapped or has the wrong size.
     */
    public fun loadOrCreate(wrapper: KeyWrapper): ByteArray {
        if (file.exists()) {
            val passphrase = wrapper.unwrap(file.readBytes())
            if (passphrase.size != PASSPHRASE_SIZE) {
                passphrase.fill(0)
                throw GeneralSecurityException("Stored passphrase has an unexpected size")
            }
            return passphrase
        }
        val passphrase = VaultSecrets.newDatabasePassphrase()
        try {
            directory.mkdirs()
            writeAtomically(file, wrapper.wrap(passphrase))
            return passphrase.copyOf()
        } finally {
            passphrase.fill(0)
        }
    }

    /** The wrapped passphrase file and its temporary sibling, whether or not they exist. */
    internal fun files(): List<File> = listOf(file, File(directory, FILE_NAME + TEMPORARY_SUFFIX))

    private companion object {
        const val TEMPORARY_SUFFIX = ".tmp"
        const val FILE_NAME = "db.key.wrapped"
        const val PASSPHRASE_SIZE = 32
    }
}

/** Writes [bytes] to a temporary sibling, syncs it and renames it over [target]. */
internal fun writeAtomically(target: File, bytes: ByteArray) {
    val temporary = File(target.parentFile, target.name + ".tmp")
    try {
        FileOutputStream(temporary).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
    } finally {
        temporary.delete()
    }
}
