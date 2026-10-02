package org.sakshi.core.crypto

import java.security.SecureRandom

/** Generators for random secrets and identifiers used by the vault. */
public object VaultSecrets {
    private const val PASSPHRASE_SIZE = 32

    /** Returns 32 random bytes for use as the database passphrase. */
    public fun newDatabasePassphrase(random: SecureRandom = SecureRandom()): ByteArray =
        ByteArray(PASSPHRASE_SIZE).also(random::nextBytes)

    /** Returns 16 random bytes for use as a blob identifier. */
    public fun newBlobId(random: SecureRandom = SecureRandom()): ByteArray =
        ByteArray(BlobFormat.BLOB_ID_SIZE).also(random::nextBytes)
}
