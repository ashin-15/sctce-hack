package org.sakshi.export.bundle

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReferenceFailureTest {
    @get:Rule
    val tmp: TemporaryFolder = TemporaryFolder()

    private val signer: SoftwareP256Signer = SoftwareP256Signer.generate()

    private fun bundle(): Path = tmp.newFolder().toPath().also { BundleWriter.write(sampleContent(), it, signer) }

    private fun assertReferencesFail(dir: Path, vararg more: String) {
        val report = BundleVerifier.verify(dir)
        assertEquals(Verdict.INCONSISTENT, report.verdict, report.checks.toString())
        assertEquals(CheckStatus.PASSED, check(report, "signature").status)
        assertEquals(CheckStatus.PASSED, check(report, "file_hashes").status)
        (listOf("references") + more).forEach { assertEquals(CheckStatus.FAILED, check(report, it).status, it) }
    }

    @Test
    fun findingCitingEventOutsideBundle() {
        val dir = bundle()
        Files.write(dir.resolve("findings.json"), BundleDocuments.findings(listOf(sampleFinding(eventId = "synthetic-missing"))))
        Resigner.resign(dir, signer)
        assertReferencesFail(dir)
    }

    @Test
    fun findingCitingUnknownAnchor() {
        val dir = bundle()
        val finding = sampleFinding().copy(anchorReferenceIds = listOf("synthetic-no-such-ref"))
        Files.write(dir.resolve("findings.json"), BundleDocuments.findings(listOf(finding)))
        Resigner.resign(dir, signer)
        assertReferencesFail(dir)
    }

    @Test
    fun patternCitingRevisionNotPresent() {
        val dir = bundle()
        Files.write(dir.resolve("patterns.json"), BundleDocuments.patterns(listOf(samplePattern(revision = 9))))
        Resigner.resign(dir, signer)
        assertReferencesFail(dir)
    }

    @Test
    fun provenanceEdgeToMissingNode() {
        val dir = bundle()
        val graph = sampleProvenance()
        val broken = graph.copy(edges = graph.edges + ProvenanceEdge("synthetic-event-1", "synthetic-ghost", ProvenanceRelation.CITED_BY))
        Files.write(dir.resolve("provenance.json"), BundleDocuments.provenance(broken))
        Resigner.resign(dir, signer)
        assertReferencesFail(dir)
    }

    @Test
    fun includedNodeWithoutFile() {
        val dir = bundle()
        val graph = sampleProvenance()
        val ghost = ProvenanceNode("synthetic-ghost", ProvenanceKind.EVIDENCE, "11".repeat(32), true)
        Files.write(dir.resolve("provenance.json"), BundleDocuments.provenance(graph.copy(nodes = graph.nodes + ghost)))
        Resigner.resign(dir, signer)
        assertReferencesFail(dir)
    }

    @Test
    fun eventFromAnotherCase() {
        val dir = bundle()
        val events = listOf(event("synthetic-event-1"), event("synthetic-event-2", caseId = "synthetic-case-other"))
        Files.writeString(dir.resolve("events.jsonl"), events.joinToString("") { org.sakshi.core.model.EventSchemaAdapter.toJson(it) + "\n" })
        Resigner.resign(dir, signer)
        val report = BundleVerifier.verify(dir)
        assertEquals(Verdict.INCONSISTENT, report.verdict)
        assertEquals(CheckStatus.FAILED, check(report, "no_cross_case").status)
        assertEquals(CheckStatus.FAILED, check(report, "events").status)
    }

    @Test
    fun malformedEventLine() {
        val dir = bundle()
        Files.writeString(dir.resolve("events.jsonl"), "{\"not\":\"an event\"}\n")
        Resigner.resign(dir, signer)
        val report = BundleVerifier.verify(dir)
        assertEquals(CheckStatus.FAILED, check(report, "events").status)
        assertEquals(Verdict.INCONSISTENT, report.verdict)
    }
}
