package org.sakshi.export.bundle

import java.nio.file.Path
import org.sakshi.core.integrity.Sha256

/** A parsed manifest together with the directory it describes. */
internal class BundleView(val root: Path, val manifest: Manifest, val limits: VerifierLimits) {
    private val unique: Map<String, ManifestFile> =
        manifest.files.groupBy { it.path }.filterValues { it.size == 1 }.mapValues { it.value.first() }

    fun entry(path: String): ManifestFile? = unique[path]

    /** Reads a listed document and confirms its bytes still match the manifest entry. */
    fun loadVerified(path: String): ByteArray {
        val entry = entry(path) ?: throw BundleReadException("$path is not listed exactly once in the manifest")
        if (PathRules.roleOf(path) == null) throw BundleReadException("$path is not an allowed path")
        if (entry.size > limits.maxJsonBytes) throw BundleReadException("$path exceeds the document size limit")
        val data = FileAccess.readBounded(root.resolve(path), limits.maxJsonBytes)
        if (data.size.toLong() != entry.size || Sha256.hex(Sha256.digest(data)) != entry.sha256) {
            throw BundleReadException("$path does not match the manifest")
        }
        return data
    }
}
