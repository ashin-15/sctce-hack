package org.sakshi.export.bundle

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import org.sakshi.core.integrity.CanonicalJson
import org.sakshi.core.integrity.MerkleV2
import org.sakshi.core.integrity.Sha256
import org.sakshi.core.model.EventSchemaAdapter

/** Writes a `sakshi-bundle/1` directory. */
public object BundleWriter {
    /**
     * Writes the bundle into the empty directory [target].
     *
     * @throws IllegalArgumentException naming the first violated invariant; nothing is written in that case.
     * @throws IOException if writing fails; partial output is removed.
     */
    public fun write(content: BundleContent, target: Path, signer: ManifestSigner): BundleSummary {
        require(Files.isDirectory(target)) { "Target is not a directory" }
        require(Files.newDirectoryStream(target).use { !it.iterator().hasNext() }) { "Target directory is not empty" }
        val digests = ContentValidator.validate(content)
        try {
            return writeAll(content, target, signer, digests)
        } catch (e: IOException) {
            clear(target)
            throw e
        } catch (e: IllegalStateException) {
            clear(target)
            throw e
        }
    }

    private fun writeAll(
        content: BundleContent,
        target: Path,
        signer: ManifestSigner,
        digests: Map<String, StreamDigest>,
    ): BundleSummary {
        val files = mutableListOf<ManifestFile>()
        fun bytes(path: String, role: String, data: ByteArray) {
            write(target, path, data)
            files += ManifestFile(path, role, Sha256.hex(Sha256.digest(data)), data.size.toLong())
        }
        fun stream(path: String, role: String, file: BundleFile) {
            val digest = copy(target, path, file)
            check(digest.sha256 == digests.getValue(path).sha256) { "File ${file.opaqueId} changed while it was being written" }
            files += ManifestFile(path, role, digest.sha256, digest.length)
        }

        val eventLines = content.events.joinToString("") { EventSchemaAdapter.toJson(it) + "\n" }
        bytes(BundleFormat.EVENTS, BundleFormat.ROLE_EVENTS, eventLines.toByteArray(Charsets.UTF_8))
        bytes(BundleFormat.FINDINGS, BundleFormat.ROLE_FINDINGS, BundleDocuments.findings(content.findings))
        bytes(BundleFormat.CORRECTIONS, BundleFormat.ROLE_CORRECTIONS, BundleDocuments.corrections(content.corrections))
        bytes(BundleFormat.PATTERNS, BundleFormat.ROLE_PATTERNS, BundleDocuments.patterns(content.patterns))
        bytes(BundleFormat.PROVENANCE, BundleFormat.ROLE_PROVENANCE, BundleDocuments.provenance(content.provenance))
        bytes(BundleFormat.README, BundleFormat.ROLE_README, VerificationReadme.text.toByteArray(Charsets.UTF_8))
        content.originals.forEach { stream("${BundleFormat.EVIDENCE_DIR}/${it.opaqueId}", BundleFormat.ROLE_ORIGINAL, it) }
        content.derivatives.forEach { stream("${BundleFormat.DERIVATIVES_DIR}/${it.opaqueId}", BundleFormat.ROLE_DERIVATIVE, it) }
        content.reportPdf?.let { stream(BundleFormat.REPORT, BundleFormat.ROLE_REPORT, it) }

        val sorted = files.sortedBy { it.path }
        val manifest = Manifest(
            snapshotId = content.snapshotId,
            caseId = content.caseId,
            createdAt = content.createdAt,
            generator = content.generator,
            files = sorted,
            merkleRoot = Sha256.hex(MerkleV2.root(sorted.map { Sha256.fromHex(it.sha256) })),
            auditChainHead = content.auditChainHead,
            omitted = content.omitted,
            limits = BundleFormat.LIMITS,
        )
        val manifestBytes = CanonicalJson.encode(manifest.toJson())
        val spki = signer.publicKeySpkiDer
        val keyId = Sha256.hex(Sha256.digest(spki))
        write(target, BundleFormat.SIGNER, SignerFile.encode(spki, keyId))
        write(target, BundleFormat.MANIFEST, manifestBytes)
        write(target, BundleFormat.SIGNATURE, signer.sign(manifestBytes))
        return BundleSummary(content.snapshotId, keyId, manifest.merkleRoot, sorted.size)
    }

    private fun write(root: Path, relative: String, data: ByteArray) {
        val path = root.resolve(relative)
        path.parent?.let { Files.createDirectories(it) }
        Files.write(path, data, StandardOpenOption.CREATE_NEW)
    }

    private fun copy(root: Path, relative: String, file: BundleFile): StreamDigest {
        val path = root.resolve(relative)
        path.parent?.let { Files.createDirectories(it) }
        val out = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)
        return out.use { sink -> file.open().use { source -> digestStream(TeeInputStream(source, sink), file.length) } }
    }

    private fun clear(target: Path) {
        Files.walk(target).use { stream ->
            stream.sorted(Comparator.reverseOrder()).filter { it != target }.forEach { Files.deleteIfExists(it) }
        }
    }

    private class TeeInputStream(private val source: InputStream, private val sink: OutputStream) : InputStream() {
        override fun read(): Int = throw UnsupportedOperationException("Use the buffered read")

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val count = source.read(b, off, len)
            if (count > 0) sink.write(b, off, count)
            return count
        }
    }
}
