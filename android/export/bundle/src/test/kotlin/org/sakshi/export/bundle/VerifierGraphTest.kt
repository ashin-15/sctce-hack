package org.sakshi.export.bundle

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VerifierGraphTest {
    @get:Rule
    val tmp: TemporaryFolder = TemporaryFolder()

    private val signer: SoftwareP256Signer = SoftwareP256Signer.generate()

    private fun bundle(content: BundleContent = sampleContent()): Path =
        tmp.newFolder().toPath().also { BundleWriter.write(content, it, signer) }

    private fun rewriteProvenance(dir: Path, edit: (Provenance) -> Provenance) {
        val current = BundleDocuments.parseProvenance(Files.readAllBytes(dir.resolve("provenance.json")))
        Files.write(dir.resolve("provenance.json"), BundleDocuments.provenance(edit(current)))
        Resigner.resign(dir, signer)
    }

    @Test
    fun everyListedFileIsProtectedByItsHash() {
        val dir = bundle()
        val listed = Manifest.parse(JsonInput.parse(Files.readString(dir.resolve("manifest.json")))).files.map { it.path }
        assertTrue(listed.containsAll(listOf("events.jsonl", "findings.json", "corrections.json", "patterns.json", "provenance.json", "report.pdf")))
        for (path in listed) {
            val copy = bundle()
            flipByte(copy.resolve(path), 3)
            val report = BundleVerifier.verify(copy)
            assertEquals(Verdict.INCONSISTENT, report.verdict, path)
            assertTrue(check(report, "file_hashes").detail.contains(path), "$path in ${check(report, "file_hashes").detail}")
        }
    }

    @Test
    fun removedAnchorsEdgeFailsReferencesEvenWhenResigned() {
        val dir = bundle()
        rewriteProvenance(dir) { p ->
            p.copy(edges = p.edges.filterNot { it.from == CITED_ARTIFACT && it.to == "synthetic-event-2" })
        }
        val report = BundleVerifier.verify(dir)
        assertEquals(Verdict.INCONSISTENT, report.verdict)
        assertEquals(CheckStatus.PASSED, check(report, "signature").status)
        assertEquals(CheckStatus.FAILED, check(report, "references").status)
        assertTrue(check(report, "references").detail.contains("synthetic-event-2"), check(report, "references").detail)
        assertTrue(check(report, "references").detail.contains(CITED_ARTIFACT))
    }

    @Test
    fun removedEdgeWithoutResigningFailsNamingProvenanceFile() {
        val dir = bundle()
        val current = BundleDocuments.parseProvenance(Files.readAllBytes(dir.resolve("provenance.json")))
        Files.write(dir.resolve("provenance.json"), BundleDocuments.provenance(current.copy(edges = current.edges.drop(1))))
        val report = BundleVerifier.verify(dir)
        assertEquals(Verdict.INCONSISTENT, report.verdict)
        assertTrue(check(report, "file_hashes").detail.contains("provenance.json"))
    }

    @Test
    fun anchorToAnArtefactWithoutAnyEntryFailsReferences() {
        val dir = bundle()
        rewriteProvenance(dir) { p -> p.copy(nodes = p.nodes.filterNot { it.id == CITED_ARTIFACT }, edges = p.edges.filterNot { it.from == CITED_ARTIFACT }) }
        val report = BundleVerifier.verify(dir)
        assertEquals(CheckStatus.FAILED, check(report, "references").status)
        assertTrue(check(report, "references").detail.contains("no provenance entry"), check(report, "references").detail)
    }

    @Test
    fun writerRefusesAnAnchorWithoutAnEntry() {
        val provenance = sampleProvenance().let { p -> p.copy(nodes = p.nodes.filterNot { it.id == CITED_ARTIFACT }, edges = p.edges.filterNot { it.from == CITED_ARTIFACT }) }
        val error = assertFailsWith<IllegalArgumentException> {
            BundleWriter.write(sampleContent(provenance = provenance), tmp.newFolder().toPath(), signer)
        }
        assertTrue(error.message.orEmpty().contains("no provenance entry"), error.message)
    }

    @Test
    fun omittedAnchorsAreReportedAsUnverifiableNotAsFailures() {
        val report = BundleVerifier.verify(bundle())
        assertEquals(Verdict.CONSISTENT, report.verdict)
        assertTrue(report.unverifiable.any { it.contains("2 event anchors") }, report.unverifiable.toString())
        assertTrue(report.unverifiable.any { it.contains("2 saved files were left out") })
        assertTrue(report.unverifiable.any { it.contains("3 records were left out") })
        assertTrue(check(report, "references").detail.contains("every event anchor resolved"))
    }

    @Test
    fun anAnchorToAnIncludedOriginalIsNotReportedAsOmitted() {
        val provenance = sampleProvenance().let { p ->
            p.copy(
                edges = p.edges + listOf(
                    ProvenanceEdge(ORIGINAL_ID, CITED_ARTIFACT, ProvenanceRelation.DERIVED_FROM),
                    ProvenanceEdge(ORIGINAL_ID, "synthetic-event-2", ProvenanceRelation.ANCHORS),
                ),
            )
        }
        val report = BundleVerifier.verify(bundle(sampleContent(provenance = provenance)))
        assertEquals(Verdict.CONSISTENT, report.verdict, report.checks.toString())
        assertTrue(report.unverifiable.none { it.contains("event anchors") }, report.unverifiable.toString())
    }

    @Test
    fun substitutedSignerKeyIsReportedWithADifferentFingerprint() {
        val dir = tmp.newFolder().toPath()
        val summary = BundleWriter.write(sampleContent(), dir, signer)
        Resigner.resign(dir, SoftwareP256Signer.generate())
        val report = BundleVerifier.verify(dir)
        assertTrue(report.signerKeyId != null && report.signerKeyId != summary.signerKeyId)
    }

    @Test
    fun readmeAndCliStateTheOriginalsMetadataSentenceAndTheOpaqueNames() {
        val readme = Files.readString(bundle().resolve("verification/README.txt"))
        assertTrue(readme.contains("exact saved file"))
        assertTrue(readme.contains("camera, device or location metadata"))
        assertTrue(readme.contains("random identifiers"))
    }
}
