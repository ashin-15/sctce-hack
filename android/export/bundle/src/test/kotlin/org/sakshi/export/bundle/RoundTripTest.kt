package org.sakshi.export.bundle

import java.nio.file.Files
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RoundTripTest {
    @get:Rule
    val tmp: TemporaryFolder = TemporaryFolder()

    @Test
    fun writtenBundleVerifiesAsConsistent() {
        val dir = tmp.newFolder().toPath()
        val signer = SoftwareP256Signer.generate()
        val summary = BundleWriter.write(sampleContent(), dir, signer)

        val report = BundleVerifier.verify(dir)

        assertEquals(Verdict.CONSISTENT, report.verdict, report.checks.toString())
        assertEquals(
            listOf("layout", "manifest_canonical", "signature", "file_hashes", "merkle_root", "events", "references", "no_cross_case", "omitted"),
            report.checks.map { it.name },
        )
        report.checks.forEach { assertTrue(it.status != CheckStatus.FAILED, it.toString()) }
        assertEquals(CheckStatus.NOT_APPLICABLE, check(report, "omitted").status)
        assertEquals(summary.signerKeyId, report.signerKeyId)
        assertEquals(mapOf("evidence_count" to 2, "derivative_count" to 1, "event_count" to 3), report.omitted)
        assertEquals(BundleFormat.LIMITS, report.limits)
        assertEquals(9, summary.fileCount)
        assertTrue(Files.size(dir.resolve("evidence/$ORIGINAL_ID")) == ORIGINAL_LENGTH)
        assertTrue(check(report, "references").detail.contains("1 target items outside"))
    }

    @Test
    fun manifestBytesAreDeterministicForTheSameContentAndSigner() {
        val signer = SoftwareP256Signer.generate()
        val first = tmp.newFolder().toPath()
        val second = tmp.newFolder().toPath()
        BundleWriter.write(sampleContent(), first, signer)
        BundleWriter.write(sampleContent(), second, signer)

        assertContentEquals(Files.readAllBytes(first.resolve("manifest.json")), Files.readAllBytes(second.resolve("manifest.json")))
        // manifest.sig is deliberately not compared: ECDSA signing is randomised, so the bytes differ between runs.
        assertEquals(BundleVerifier.verify(first).verdict, BundleVerifier.verify(second).verdict)
    }

    @Test
    fun bundleWithoutReportOrDerivativesStillVerifies() {
        val dir = tmp.newFolder().toPath()
        val provenance = Provenance(
            sampleProvenance().nodes.filter { it.kind == ProvenanceKind.EVIDENCE || it.kind == ProvenanceKind.EVENT || it.kind == ProvenanceKind.FINDING },
            emptyList(),
        )
        BundleWriter.write(sampleContent(provenance = provenance, derivatives = emptyList(), report = null), dir, SoftwareP256Signer.generate())

        assertEquals(Verdict.CONSISTENT, BundleVerifier.verify(dir).verdict)
    }
}
