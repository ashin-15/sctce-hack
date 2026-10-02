package org.sakshi.export.bundle

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.sakshi.core.integrity.Sha256

class TamperTest {
    @get:Rule
    val tmp: TemporaryFolder = TemporaryFolder()

    private val signer: SoftwareP256Signer = SoftwareP256Signer.generate()

    private fun bundle(): Path = tmp.newFolder().toPath().also { BundleWriter.write(sampleContent(), it, signer) }

    private fun assertFailed(dir: Path, vararg names: String): VerificationReport {
        val report = BundleVerifier.verify(dir)
        assertEquals(Verdict.INCONSISTENT, report.verdict, report.checks.toString())
        names.forEach { assertEquals(CheckStatus.FAILED, check(report, it).status, "$it in ${report.checks}") }
        return report
    }

    @Test
    fun flippedByteInOriginalFailsFileHashes() {
        val dir = bundle()
        flipByte(dir.resolve("evidence/$ORIGINAL_ID"), 1_000_000)
        val report = assertFailed(dir, "file_hashes")
        assertEquals(CheckStatus.PASSED, check(report, "signature").status)
        assertEquals(true, check(report, "file_hashes").detail.contains("evidence/$ORIGINAL_ID"))
    }

    @Test
    fun flippedByteInDerivativeFailsFileHashes() {
        val dir = bundle()
        flipByte(dir.resolve("derivatives/$DERIVATIVE_ID"), 10)
        assertFailed(dir, "file_hashes")
    }

    @Test
    fun flippedByteInEventsFailsFileHashes() {
        val dir = bundle()
        flipByte(dir.resolve("events.jsonl"), 20)
        assertFailed(dir, "file_hashes")
    }

    @Test
    fun flippedByteInFindingsFailsFileHashes() {
        val dir = bundle()
        flipByte(dir.resolve("findings.json"), 30)
        assertFailed(dir, "file_hashes")
    }

    @Test
    fun editedManifestWithOldSignatureFailsSignature() {
        val dir = bundle()
        val manifest = dir.resolve("manifest.json")
        Files.writeString(manifest, Files.readString(manifest).replace("synthetic-snapshot-1", "synthetic-snapshot-2"))
        val report = assertFailed(dir, "signature")
        assertEquals(CheckStatus.PASSED, check(report, "manifest_canonical").status)
    }

    @Test
    fun resignedWithDifferentKeyButOldSignerFileFailsSignature() {
        val dir = bundle()
        val oldSigner = Files.readAllBytes(dir.resolve("signer.json"))
        Resigner.resign(dir, SoftwareP256Signer.generate())
        Files.write(dir.resolve("signer.json"), oldSigner)
        assertFailed(dir, "signature")
    }

    @Test
    fun replacingSignatureAndSignerFileIsDetectedOnlyByComparingTheKeyId() {
        val original = tmp.newFolder().toPath()
        val summary = BundleWriter.write(sampleContent(), original, signer)
        val other = SoftwareP256Signer.generate()
        Resigner.resign(original, other)

        val report = BundleVerifier.verify(original)

        // The signature check passes because the bundle is consistent with the key it carries. Nothing inside the
        // bundle can say whether that key is the one the author used. Only comparing the reported key id with a
        // value obtained out of band (for example read aloud by the exporter) reveals the replacement.
        assertEquals(CheckStatus.PASSED, check(report, "signature").status)
        assertEquals(Verdict.CONSISTENT, report.verdict)
        assertNotEquals(summary.signerKeyId, report.signerKeyId)
        assertEquals(Sha256.hex(Sha256.digest(other.publicKeySpkiDer)), report.signerKeyId)
    }

    @Test
    fun deletedListedFileFailsFileHashes() {
        val dir = bundle()
        Files.delete(dir.resolve("evidence/$ORIGINAL_ID"))
        assertFailed(dir, "file_hashes")
    }

    @Test
    fun unlistedFileFailsLayout() {
        val dir = bundle()
        Files.writeString(dir.resolve("evidence/extra"), "x")
        assertFailed(dir, "layout")
    }

    @Test
    fun renamedFileFailsLayoutAndFileHashes() {
        val dir = bundle()
        Files.move(dir.resolve("evidence/$ORIGINAL_ID"), dir.resolve("evidence/renamed"))
        assertFailed(dir, "layout", "file_hashes")
    }

    @Test
    fun swappedFileContentsFailFileHashes() {
        val dir = bundle()
        val a = dir.resolve("evidence/$ORIGINAL_ID")
        val b = dir.resolve("derivatives/$DERIVATIVE_ID")
        val temp = dir.resolve("verification/swap")
        Files.move(a, temp)
        Files.move(b, a)
        Files.move(temp, b)
        assertFailed(dir, "file_hashes")
    }

    @Test
    fun truncatedSignatureFailsSignature() {
        val dir = bundle()
        val sig = dir.resolve("manifest.sig")
        Files.write(sig, Files.readAllBytes(sig).copyOf(10))
        assertFailed(dir, "signature")
    }

    @Test
    fun prettyPrintedSignedManifestFailsCanonicalCheck() {
        val dir = bundle()
        Resigner.resign(dir, signer, pretty = true)
        val report = assertFailed(dir, "manifest_canonical")
        assertEquals(CheckStatus.PASSED, check(report, "signature").status)
    }

    @Test
    fun manifestListingParentPathFailsLayoutAndFileHashes() {
        val dir = bundle()
        Files.writeString(dir.parent.resolve("escape"), "outside")
        Resigner.resign(dir, signer) { it.copy(files = (it.files + ManifestFile("evidence/../../escape", "evidence_original", "00".repeat(32), 7)).sortedBy { f -> f.path }) }
        val report = assertFailed(dir, "layout", "file_hashes")
        assertEquals(true, check(report, "file_hashes").detail.contains("not read"))
    }

    @Test
    fun symlinkInsideBundleFailsLayout() {
        val dir = bundle()
        symlink(dir.resolve("evidence/link"), dir.resolve("events.jsonl"))
        assertFailed(dir, "layout")
    }

    @Test
    fun listedFileReplacedBySymlinkFailsFileHashesAndLayout() {
        val dir = bundle()
        val file = dir.resolve("derivatives/$DERIVATIVE_ID")
        val outside = tmp.newFile().toPath()
        Files.copy(file, outside, StandardCopyOption.REPLACE_EXISTING)
        Files.delete(file)
        symlink(file, outside)
        val report = assertFailed(dir, "layout", "file_hashes")
        assertEquals(true, check(report, "file_hashes").detail.contains("not a regular file"))
    }

    @Test
    fun duplicatePathInManifestFailsMerkleCheck() {
        val dir = bundle()
        Resigner.resign(dir, signer) { m -> m.copy(files = (m.files + m.files.first()).sortedBy { it.path }) }
        assertFailed(dir, "merkle_root")
    }

    @Test
    fun wrongMerkleRootWithValidSignatureFailsMerkleCheck() {
        val dir = bundle()
        Resigner.resign(dir, signer, fixMerkle = false) { it.copy(merkleRoot = "00".repeat(32)) }
        val report = assertFailed(dir, "merkle_root")
        assertEquals(CheckStatus.PASSED, check(report, "signature").status)
        assertEquals(CheckStatus.PASSED, check(report, "file_hashes").status)
    }

    private fun symlink(link: Path, target: Path) {
        try {
            Files.createSymbolicLink(link, target)
        } catch (e: UnsupportedOperationException) {
            assumeTrue("file system lacks symbolic links", false)
        } catch (e: IOException) {
            assumeTrue("symbolic links not permitted here", false)
        }
    }
}
