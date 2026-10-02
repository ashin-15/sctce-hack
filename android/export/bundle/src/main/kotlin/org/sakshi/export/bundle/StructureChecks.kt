package org.sakshi.export.bundle

import java.nio.file.Files
import java.nio.file.LinkOption
import kotlinx.serialization.json.JsonElement
import org.sakshi.core.integrity.CanonicalJson
import org.sakshi.core.integrity.MerkleV2
import org.sakshi.core.integrity.Sha256

private val HEX_64: Regex = Regex("[0-9a-f]{64}")

/** Checks that need only the manifest and the directory listing. */
internal object StructureChecks {
    fun layout(view: BundleView): Check {
        val manifest = view.manifest
        val problems = mutableListOf<String>()
        val scan = LayoutScanner.scan(view.root, view.limits.maxFiles)
        problems += scan.issues
        if (manifest.files.size > view.limits.maxFiles) {
            problems += "manifest lists ${manifest.files.size} files, limit is ${view.limits.maxFiles}"
        }
        for (name in BundleFormat.UNLISTED) {
            if (name !in scan.files) problems += "missing $name"
        }
        val listed = manifest.files.map { it.path }.toSet()
        for (name in BundleFormat.REQUIRED_LISTED) {
            if (name !in listed) problems += "manifest does not list $name"
        }
        for (file in manifest.files) {
            val expected = PathRules.roleOf(file.path)
            when {
                expected == null -> problems += "path not allowed: ${safe(file.path)}"
                expected != file.role -> problems += "role of ${safe(file.path)} should be $expected"
            }
        }
        for (name in scan.files) {
            if (name !in listed && name !in BundleFormat.UNLISTED) problems += "unlisted file ${safe(name)}"
        }
        return if (problems.isEmpty()) {
            Check("layout", CheckStatus.PASSED, "${scan.files.size} files present, none unlisted, no links")
        } else {
            Check("layout", CheckStatus.FAILED, summarise(problems))
        }
    }

    fun canonical(manifestBytes: ByteArray, parsed: JsonElement): Check {
        val canonical = try {
            CanonicalJson.encode(parsed)
        } catch (e: IllegalArgumentException) {
            return Check("manifest_canonical", CheckStatus.FAILED, "manifest.json cannot be put in canonical form")
        }
        return if (canonical.contentEquals(manifestBytes)) {
            Check("manifest_canonical", CheckStatus.PASSED, "manifest.json is in canonical form")
        } else {
            Check("manifest_canonical", CheckStatus.FAILED, "manifest.json differs from the canonical encoding of its own content")
        }
    }

    fun fileHashes(view: BundleView): Check {
        val files = view.manifest.files
        if (files.size > view.limits.maxFiles) {
            return Check("file_hashes", CheckStatus.FAILED, "not checked: ${files.size} files listed, limit is ${view.limits.maxFiles}")
        }
        val problems = mutableListOf<String>()
        for (file in files) {
            fileProblem(view, file)?.let { problems += "${safe(file.path)}: $it" }
        }
        return if (problems.isEmpty()) {
            Check("file_hashes", CheckStatus.PASSED, "${files.size} listed files have the listed size and SHA-256")
        } else {
            Check("file_hashes", CheckStatus.FAILED, summarise(problems))
        }
    }

    private fun fileProblem(view: BundleView, file: ManifestFile): String? {
        if (PathRules.roleOf(file.path) == null) return "path not allowed, not read"
        if (file.size < 0 || file.size > view.limits.maxFileBytes) return "listed size is outside the limit"
        val path = view.root.resolve(file.path)
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return "missing"
        val actual = FileAccess.regularFileSize(path) ?: return "not a regular file"
        if (actual != file.size) return "size differs from the manifest"
        val digest = FileAccess.hash(path, file.size) ?: return "could not be read"
        return if (digest.sha256 == file.sha256 && digest.length == file.size) null else "SHA-256 differs from the manifest"
    }

    fun merkle(manifest: Manifest): Check {
        val paths = manifest.files.map { it.path }
        val problems = mutableListOf<String>()
        if (paths.toSet().size != paths.size) problems += "duplicate path in the manifest"
        if (paths != paths.sorted()) problems += "files are not in sorted order"
        if (manifest.files.any { !HEX_64.matches(it.sha256) }) {
            problems += "a listed hash is not 64 lowercase hex characters"
        } else {
            val root = Sha256.hex(MerkleV2.root(manifest.files.map { Sha256.fromHex(it.sha256) }))
            if (root != manifest.merkleRoot) problems += "recomputed root $root differs from the manifest"
        }
        return if (problems.isEmpty()) {
            Check("merkle_root", CheckStatus.PASSED, "root ${manifest.merkleRoot} matches ${manifest.files.size} sorted file hashes")
        } else {
            Check("merkle_root", CheckStatus.FAILED, summarise(problems))
        }
    }

    fun omitted(manifest: Manifest): Check = Check(
        "omitted",
        CheckStatus.NOT_APPLICABLE,
        "left out of this bundle, not checkable: ${manifest.omitted.evidenceCount} evidence, " +
            "${manifest.omitted.derivativeCount} derivatives, ${manifest.omitted.eventCount} events",
    )
}
