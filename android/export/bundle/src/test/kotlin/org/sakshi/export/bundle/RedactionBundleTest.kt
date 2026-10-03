package org.sakshi.export.bundle

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RedactionBundleTest {
    @get:Rule
    val tmp: TemporaryFolder = TemporaryFolder()

    private val signer: SoftwareP256Signer = SoftwareP256Signer.generate()

    private fun bundle(redactions: RedactionSummary): Path {
        val base = sampleContent()
        val content = BundleContent(
            base.snapshotId, base.caseId, base.createdAt, base.generator, base.auditChainHead, base.omitted, base.events,
            base.findings, base.corrections, base.patterns, base.provenance, base.originals, base.derivatives, base.reportPdf,
            redactions,
        )
        return tmp.newFolder().toPath().also { BundleWriter.write(content, it, signer) }
    }

    @Test
    fun aBundleWithoutRedactionsHasNoRedactionKeyAndNoRemovedTextNotes() {
        val dir = bundle(RedactionSummary.NONE)
        assertFalse(Files.readString(dir.resolve("manifest.json")).contains("redactions"))
        assertFalse(Files.readString(dir.resolve("verification/README.txt")).contains("Removed text"))
        assertEquals(VerificationReadme.text, Files.readString(dir.resolve("verification/README.txt")))
        assertTrue(BundleVerifier.verify(dir).unverifiable.none { it.contains("removed") && it.contains("passages") })
    }

    @Test
    fun redactionCountsAreSignedAndReportedAsUnverifiable() {
        val dir = bundle(RedactionSummary(eventCount = 1, passageCount = 2))
        val manifest = Files.readString(dir.resolve("manifest.json"))
        assertTrue(manifest.contains("\"redactions\":{\"event_count\":1,\"original_may_hold_removed_content\":false,\"passage_count\":2}"), manifest)
        val report = BundleVerifier.verify(dir)
        assertEquals(Verdict.CONSISTENT, report.verdict, report.checks.toString())
        val statement = report.unverifiable.single { it.contains("passages") }
        assertTrue(statement.contains("2 passages from 1 records"))
        assertTrue(statement.contains("nothing here shows that the removal was correct"))
        assertTrue(report.unverifiable.none { it.startsWith("Warning") })
        val readme = Files.readString(dir.resolve("verification/README.txt"))
        assertTrue(readme.contains("Removed text:") && readme.contains(Redactor.MARKER))
        assertFalse(readme.contains("Warning"))
    }

    @Test
    fun theOriginalWarningIsCarriedByManifestReadmeAndVerifier() {
        val dir = bundle(RedactionSummary(1, 1, originalMayHoldRemovedContent = true))
        assertTrue(Files.readString(dir.resolve("manifest.json")).contains("\"original_may_hold_removed_content\":true"))
        assertTrue(Files.readString(dir.resolve("verification/README.txt")).contains("Warning: an original file included"))
        val report = BundleVerifier.verify(dir)
        assertEquals(Verdict.CONSISTENT, report.verdict)
        assertTrue(report.unverifiable.any { it.startsWith("Warning: an included original") })
        val out = ByteArrayOutputStream()
        PrintStream(out, true, Charsets.UTF_8).use { VerifierCli.run(arrayOf("verify", dir.toString()), it) }
        val text = out.toString(Charsets.UTF_8)
        assertTrue(text.contains("Cannot be checked from this bundle:"))
        assertTrue(text.contains("Warning: an included original"))
    }

    @Test
    fun editingTheRedactionRecordWithoutResigningFailsTheSignature() {
        val dir = bundle(RedactionSummary(1, 1))
        val manifest = dir.resolve("manifest.json")
        Files.writeString(manifest, Files.readString(manifest).replace("\"passage_count\":1", "\"passage_count\":9"))
        val report = BundleVerifier.verify(dir)
        assertEquals(CheckStatus.FAILED, check(report, "signature").status)
        assertEquals(Verdict.INCONSISTENT, report.verdict)
    }

    @Test
    fun inconsistentRedactionCountsAreRejected() {
        assertFailsWith<IllegalArgumentException> { RedactionSummary(2, 1) }
        assertFailsWith<IllegalArgumentException> { RedactionSummary(0, 0, originalMayHoldRemovedContent = true) }
        assertFailsWith<IllegalArgumentException> { RedactionSummary(-1, 0) }
    }
}
