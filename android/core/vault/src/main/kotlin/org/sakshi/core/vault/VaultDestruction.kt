package org.sakshi.core.vault

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import org.sakshi.core.crypto.KeyWrapper

/** A [KeyWrapper] whose master key can be destroyed, so that everything wrapped by it becomes unreadable. */
public interface DestroyableKeyWrapper : KeyWrapper {
    /**
     * Deletes the master key. Idempotent.
     *
     * @throws VaultKeyException if the key store refuses.
     */
    public fun destroyKey()
}

/** Removes one file. A seam so that tests can simulate a file that cannot be deleted. */
public fun interface VaultFileDeleter {
    /** True when [file] no longer exists afterwards. */
    public fun delete(file: File): Boolean

    public companion object {
        /** Plain deletion with `File.delete`. */
        public val DEFAULT: VaultFileDeleter = VaultFileDeleter { it.delete() || !it.exists() }
    }
}

/** Outcome of destroying the vault. Never thrown: a partial failure is a value the caller must show. */
public sealed interface VaultDestroyResult {
    /** The key is gone and no vault file remains. */
    public data object Destroyed : VaultDestroyResult

    /**
     * Something could not be removed. [remaining] are paths relative to the vault directory of files or
     * directories that still exist (a directory is listed only when it failed on its own, not because a listed
     * file inside it remains). [keyStoreKeyRemains] is true when the key store refused to delete the master key.
     * Calling destroy again retries.
     */
    public data class Incomplete(public val remaining: List<String>, public val keyStoreKeyRemains: Boolean) : VaultDestroyResult
}

/**
 * Whole-vault deletion. The order is: delete the wrapped database passphrase file (crypto-erase: without it the
 * database cannot be opened and the per-blob keys inside it are unreachable), delete the master key when the
 * wrapper owns one, then delete every file under the vault directory (database and its `-wal`, `-shm`, `-journal`
 * files, blobs, temporary files) and the directory itself. Nothing outside the vault directory is touched and
 * symbolic links are removed, never followed.
 */
internal class VaultDestruction(
    private val root: File,
    private val wrapper: KeyWrapper,
    private val deleter: VaultFileDeleter,
) {
    fun run(): VaultDestroyResult {
        val remaining = ArrayList<String>()
        VaultKeyFile(root).files().filterNot { deleter.delete(it) }.mapTo(remaining) { relative(it) }
        val keyRemains = try {
            (wrapper as? DestroyableKeyWrapper)?.destroyKey()
            false
        } catch (_: VaultKeyException) {
            true
        }
        if (Files.exists(root.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            removeTree(root, remaining)
        }
        val distinct = remaining.distinct()
        return if (distinct.isEmpty() && !keyRemains) VaultDestroyResult.Destroyed else VaultDestroyResult.Incomplete(distinct, keyRemains)
    }

    /** Deletes [entry]; for a directory its children first. Records what is left in [remaining]. */
    private fun removeTree(entry: File, remaining: MutableList<String>) {
        val isDirectory = entry.isDirectory && !Files.isSymbolicLink(entry.toPath())
        if (isDirectory) {
            val children = entry.listFiles()
            if (children == null) {
                remaining += relative(entry)
                return
            }
            val before = remaining.size
            children.forEach { removeTree(it, remaining) }
            if (remaining.size != before) return
        }
        if (!deleter.delete(entry)) remaining += relative(entry)
    }

    private fun relative(file: File): String = if (file == root) "." else file.relativeTo(root).path
}
