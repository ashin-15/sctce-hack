package org.sakshi.export.report

import java.io.ByteArrayInputStream
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.sakshi.core.integrity.Sha256
import org.sakshi.core.model.ArtifactId
import org.sakshi.core.model.CodePointSpan
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventId
import org.sakshi.core.model.Locator
import org.sakshi.core.temporal.TemporalInput
import org.sakshi.core.temporal.fixtures.SyntheticTimelines
import org.sakshi.core.vault.AccessClass
import org.sakshi.core.vault.AcquisitionKind
import org.sakshi.core.vault.BatchSaveResult
import org.sakshi.core.vault.ImportRequest
import org.sakshi.export.bundle.BundleVerifier
import org.sakshi.export.bundle.CheckStatus
import org.sakshi.export.bundle.Redactor
import org.sakshi.export.bundle.SoftwareP256Signer
import org.sakshi.export.bundle.Verdict

/** Writes every paragraph the PDF would print as UTF-8 text, so the bundle can be searched for hidden words. */
private class TextRenderer : ReportRenderer {
    override fun render(model: ReportModel, output: OutputStream): RenderSummary {
        output.write("%PDF-1.4\n".toByteArray())
        ReportParagraphs.of(model).forEach { output.write((it.text + "\n").toByteArray(Charsets.UTF_8)) }
        return RenderSummary(1)
    }
}

class RedactionExportTest : ReportTestBase() {
    private val secret = "synthetic-secret-phrase"
    private val quote = "start $secret end"
    private val secretSpan = CodePointSpan(6, 6 + secret.length)
    private val signer = SoftwareP256Signer.generate()

    private fun service() = ExportService(context, vault, builder(FakeQuotes(mapOf("synthetic-a1" to quote))), TextRenderer(), signer, { FIXED_NOW }, ids)

    private fun quotesBuilder(text: String = quote) = builder(FakeQuotes(mapOf("synthetic-a1" to text)))

    private fun selection(input: TemporalInput, redactions: Map<EventId, List<CodePointSpan>>, originals: Set<String> = emptySet()) =
        selectAll(input).copy(redactions = redactions, includeOriginalsFor = originals)

    private fun build(builder: ReportBuilder, selection: ReportSelection) = runBlocking { builder.build(selection) }

    private fun unzip(zip: File): Path {
        val target = Files.createTempDirectory("synthetic-unzip")
        ZipFile(zip).use { archive ->
            archive.entries().asSequence().forEach { entry ->
                val out = target.resolve(entry.name)
                Files.createDirectories(out.parent)
                archive.getInputStream(entry).use { Files.copy(it, out) }
            }
        }
        return target
    }

    private fun allFiles(dir: Path): List<Path> = Files.walk(dir).use { s -> s.filter { Files.isRegularFile(it) }.toList() }

    private fun exported(result: ExportResult): ExportResult.Exported = result as? ExportResult.Exported ?: error("Expected an export but got $result")

    private val a1 = EventId("synthetic-a1")

    @Test
    fun theReportShowsTheMarkerAndTheNoteAndWithholdsHashAndLocation() {
        val input = SyntheticTimelines.a()
        store(input)
        val model = built(build(quotesBuilder(), selection(input, mapOf(a1 to listOf(secretSpan))))).model
        val observed = model.events.single { it.eventId == "synthetic-a1" }.observed.single()
        assertEquals("start ${Redactor.MARKER} end", observed.quote)
        assertEquals(1, observed.redactedPassages)
        assertNull(observed.sha256)
        assertEquals(ReportText.LOCATOR_WITHHELD, observed.locator)
        val paragraphs = ReportParagraphs.of(model)
        assertTrue(paragraphs.any { it.text == ReportText.REDACTION_NOTE })
        assertTrue(paragraphs.any { it.text.contains("1 passages in 1 records were removed") })
        assertTrue(paragraphs.any { it.text.contains(ReportText.HASH_WITHHELD) })
        assertFalse(paragraphs.any { it.text.contains(secret) })
        assertTrue(model.events.filter { it.eventId != "synthetic-a1" }.all { e -> e.observed.all { it.redactedPassages == 0 } })
        paragraphs.filter { !it.verbatim }.forEach { assertEquals(emptyList(), ForbiddenPhraseGuard.violations(it.text), it.text) }
    }

    @Test
    fun theBundleCopyOfTheEventLosesTheHashAndTheExactRange() {
        val input = SyntheticTimelines.a()
        val original = input.events.single { it.eventId.value == "synthetic-a1" }
        store(input, input.events.map { if (it === original) it.copy(evidenceReferences = it.evidenceReferences.map { r -> r.copy(sha256 = "ab".repeat(32), locator = Locator.Text(0, 23)) }) else it })
        val inputs = built(build(quotesBuilder(), selection(input, mapOf(a1 to listOf(secretSpan))))).bundleInputs
        val copy = inputs.events.single { it.eventId.value == "synthetic-a1" }.evidenceReferences.single()
        assertNull(copy.sha256)
        assertEquals(Locator.WholeArtifact, copy.locator)
        val untouched = inputs.events.single { it.eventId.value == "synthetic-a2" }
        assertEquals(input.events.single { it.eventId.value == "synthetic-a2" }.evidenceReferences, untouched.evidenceReferences)
        assertEquals(1, inputs.redactions.eventCount)
        assertEquals(1, inputs.redactedCopies.size)
        assertEquals("start ${Redactor.MARKER} end", inputs.redactedCopies.single().text)
    }

    @Test
    fun aFullExportContainsTheHiddenTextNowhereAndStillVerifies() {
        val input = SyntheticTimelines.a()
        store(input)
        val result = exported(runBlocking { service().export(selection(input, mapOf(a1 to listOf(secretSpan, CodePointSpan(8, 12))))) })
        val dir = unzip(result.zipFile)
        val needle = secret.toByteArray(Charsets.UTF_8)
        for (file in allFiles(dir)) {
            assertFalse(indexOf(Files.readAllBytes(file), needle), "hidden text found in ${dir.relativize(file)}")
        }
        val unredactedHash = Sha256.hex(Sha256.digest(quote.toByteArray(Charsets.UTF_8)))
        assertTrue(allFiles(dir).none { indexOf(Files.readAllBytes(it), unredactedHash.toByteArray()) })
        assertTrue(String(Files.readAllBytes(dir.resolve("report.pdf"))).contains(Redactor.MARKER))
        val copies = Files.list(dir.resolve("derivatives")).use { s -> s.toList() }
        assertEquals(1, copies.size)
        assertEquals("start ${Redactor.MARKER} end", String(Files.readAllBytes(copies.single()), Charsets.UTF_8))
        assertTrue(copies.single().fileName.toString().startsWith("redacted-"))

        val report = BundleVerifier.verify(dir)
        assertEquals(Verdict.CONSISTENT, report.verdict, report.checks.toString())
        assertEquals(CheckStatus.PASSED, report.checks.single { it.name == "references" }.status)
        assertTrue(report.unverifiable.any { it.contains("removed 1 passages from 1 records") }, report.unverifiable.toString())
        assertEquals(1, result.summary.redactedEventCount)
        assertEquals(1, result.summary.redactedPassageCount)
        assertFalse(result.summary.includedOriginalHoldsRemovedText)
        assertFalse(String(Files.readAllBytes(dir.resolve("verification/README.txt"))).contains("Warning: an original"))
        assertTrue(String(Files.readAllBytes(dir.resolve("manifest.json"))).contains("\"passage_count\":1"))
    }

    @Test
    fun anIncludedOriginalThatHoldsRemovedTextRaisesTheWarningEverywhere() {
        val input = SyntheticTimelines.a()
        val imported = runBlocking {
            store(input, emptyList())
            vault.evidence.import(
                ImportRequest(
                    caseId = input.caseId.value,
                    acquisitionKind = AcquisitionKind.SHARED_STREAM,
                    accessClass = AccessClass.USER_MEDIATED,
                    importerMechanism = "synthetic-test",
                    declaredMime = null,
                    claimedOrigin = null,
                    displayNameClaim = "synthetic-original-name.txt",
                    uriAuthorityClaim = "synthetic.authority.example",
                    maxPlaintextBytes = 1_000_000L,
                ),
                ByteArrayInputStream(quote.toByteArray(Charsets.UTF_8)),
            )
        }
        val events: List<Event> = input.events.map { event ->
            if (event.eventId.value != "synthetic-a1") {
                event
            } else {
                event.copy(evidenceReferences = event.evidenceReferences.map { it.copy(artifactId = ArtifactId(imported.id), sha256 = imported.sha256) })
            }
        }
        assertEquals(BatchSaveResult.Saved(events.size), runBlocking { vault.events.saveAll(events) })
        val redactions = mapOf(a1 to listOf(secretSpan))

        val without = built(build(quotesBuilder(), selection(input, redactions)))
        assertFalse(without.includedOriginalHoldsRemovedText)
        assertTrue(ReportParagraphs.of(without.model).none { it.text == ReportText.SCOPE_REDACTED_ORIGINAL })

        val with = built(build(quotesBuilder(), selection(input, redactions, setOf(imported.id))))
        assertTrue(with.includedOriginalHoldsRemovedText)
        val lines = ReportParagraphs.of(with.model).map { it.text }
        assertTrue(ReportText.SCOPE_REDACTED_ORIGINAL in lines)
        assertTrue(ReportText.SCOPE_ORIGINALS_NOTE in lines)

        val result = exported(runBlocking { service().export(selection(input, redactions, setOf(imported.id))) })
        assertTrue(result.summary.includedOriginalHoldsRemovedText)
        val dir = unzip(result.zipFile)
        assertTrue(String(Files.readAllBytes(dir.resolve("verification/README.txt"))).contains("Warning: an original file included"))
        assertTrue(BundleVerifier.verify(dir).unverifiable.any { it.startsWith("Warning: an included original") })
        // The original is kept exactly as saved, so it is the one file that still holds the text.
        val holders = allFiles(dir).filter { indexOf(Files.readAllBytes(it), secret.toByteArray(Charsets.UTF_8)) }
        assertEquals(listOf("evidence/${imported.id}"), holders.map { dir.relativize(it).toString() })
        // Neither the display name claim nor the authority claim reaches any exported file.
        for (file in allFiles(dir)) {
            val bytes = Files.readAllBytes(file)
            assertFalse(indexOf(bytes, "synthetic-original-name".toByteArray()), file.toString())
            assertFalse(indexOf(bytes, "synthetic.authority.example".toByteArray()), file.toString())
        }
    }

    @Test
    fun theFingerprintTracksTheRedactions() {
        val input = SyntheticTimelines.a()
        store(input)
        fun hash(vararg spans: CodePointSpan, mapEntries: Map<EventId, List<CodePointSpan>> = mapOf(a1 to spans.toList())) =
            built(build(quotesBuilder(), selection(input, mapEntries))).contentSha256
        val none = built(build(quotesBuilder(), selectAll(input))).contentSha256
        val one = hash(secretSpan)
        val other = hash(CodePointSpan(0, 5))
        assertNotEquals(none, one)
        assertNotEquals(one, other)
        assertEquals(one, hash(secretSpan))
        assertEquals(none, hash(mapEntries = mapOf(a1 to emptyList())))
        assertEquals(hash(CodePointSpan(6, 10), CodePointSpan(10, 18), CodePointSpan(18, 29)), one)
    }

    @Test
    fun anExportBoundToAPreviewRefusesDifferentRedactions() {
        val input = SyntheticTimelines.a()
        store(input)
        val preview = built(build(quotesBuilder(), selection(input, mapOf(a1 to listOf(secretSpan))))).contentSha256
        val changed = runBlocking { service().export(selection(input, mapOf(a1 to listOf(CodePointSpan(0, 5)))), previewedContentSha256 = preview) }
        assertEquals(ExportResult.Refused(RefusalReason.CHANGED_SINCE_PREVIEW), changed)
        assertTrue(File(context.cacheDir, "exports").list().orEmpty().isEmpty())
        exported(runBlocking { service().export(selection(input, mapOf(a1 to listOf(secretSpan))), previewedContentSha256 = preview) })
    }

    @Test
    fun badRedactionsRefuseTheBuildAndLeakNothing() {
        val input = SyntheticTimelines.a()
        store(input)
        val outside = build(quotesBuilder(), selection(input, mapOf(a1 to listOf(CodePointSpan(0, 500)))))
        assertEquals(ReportBuildResult.Refused(RefusalReason.CHANGED_SINCE_PREVIEW), outside)
        val empty = build(quotesBuilder(), selection(input, mapOf(a1 to listOf(CodePointSpan(2, 2)))))
        assertEquals(ReportBuildResult.Refused(RefusalReason.CHANGED_SINCE_PREVIEW), empty)
        val partial = select(input, "synthetic-a2").copy(redactions = mapOf(a1 to listOf(secretSpan)))
        assertEquals(ReportBuildResult.Refused(RefusalReason.UNKNOWN_EVENT), build(quotesBuilder(), partial))
        val unresolved = ReportBuilder(vault, { _, _ -> null }, { FIXED_NOW }, GENERATOR)
        assertEquals(ReportBuildResult.Refused(RefusalReason.CHANGED_SINCE_PREVIEW), build(unresolved, selection(input, mapOf(a1 to listOf(secretSpan)))))
    }

    @Test
    fun malayalamDevanagariAndEmojiQuotesAreRedactedByCodePoint() {
        val input = SyntheticTimelines.a()
        store(input)
        val text = "മല हिन्दी 😀😁 end"
        val model = built(build(quotesBuilder(text), selection(input, mapOf(a1 to listOf(CodePointSpan(3, 9), CodePointSpan(10, 11)))))).model
        assertEquals("മല ${Redactor.MARKER} ${Redactor.MARKER}😁 end", model.events.single { it.eventId == "synthetic-a1" }.observed.single().quote)
        assertEquals(2, model.events.single { it.eventId == "synthetic-a1" }.observed.single().redactedPassages)
    }

    @Test
    fun anExportWithoutRedactionsKeepsTheOldStructure() {
        val input = SyntheticTimelines.a()
        store(input)
        val result = exported(runBlocking { service().export(selectAll(input)) })
        val dir = unzip(result.zipFile)
        assertFalse(Files.exists(dir.resolve("derivatives")))
        assertFalse(String(Files.readAllBytes(dir.resolve("manifest.json"))).contains("redactions"))
        assertEquals(0, result.summary.redactedEventCount)
        val report = built(build(quotesBuilder(), selectAll(input))).model
        assertTrue(ReportParagraphs.of(report).none { it.text == ReportText.REDACTION_NOTE || it.text.contains("were removed by the person") })
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty() || haystack.size < needle.size) return false
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return true
        }
        return false
    }
}
