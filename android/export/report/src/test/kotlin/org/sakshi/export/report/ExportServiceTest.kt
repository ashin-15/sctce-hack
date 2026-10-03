package org.sakshi.export.report

import java.io.ByteArrayInputStream
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.sakshi.core.model.ArtifactId
import org.sakshi.core.model.CategoryReviewStatus
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventId
import org.sakshi.core.temporal.TemporalInput
import org.sakshi.core.temporal.fixtures.SyntheticTimelines
import org.sakshi.core.vault.AccessClass
import org.sakshi.core.vault.BatchSaveResult
import org.sakshi.core.vault.AcquisitionKind
import org.sakshi.core.vault.ImportRequest
import org.sakshi.export.bundle.BundleVerifier
import org.sakshi.export.bundle.SoftwareP256Signer
import org.sakshi.export.bundle.Verdict

/** Stands in for the platform PDF writer, which the JVM cannot run meaningfully. */
class FakeRenderer(private val pages: Int = 3) : ReportRenderer {
    override fun render(model: ReportModel, output: OutputStream): RenderSummary {
        output.write("%PDF-1.4 synthetic ${model.events.size} events".toByteArray())
        return RenderSummary(pages)
    }
}

class ExportServiceTest : ReportTestBase() {
    private val signer = SoftwareP256Signer.generate()

    private fun service(renderer: ReportRenderer = FakeRenderer()) =
        ExportService(context, vault, builder(), renderer, signer, { FIXED_NOW }, ids)

    private fun exportDirectory(): File = File(context.cacheDir, "exports")

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

    private fun exported(result: ExportResult): ExportResult.Exported =
        result as? ExportResult.Exported ?: error("Expected an export but got $result")

    /** Imports synthetic bytes as evidence and points the first two contacts at them. */
    private fun withEvidence(input: TemporalInput): Pair<List<Event>, Map<String, ByteArray>> {
        val bytes = mapOf(
            "synthetic-a1" to ByteArray(3000) { (it * 7).toByte() },
            "synthetic-a2" to ByteArray(10) { (it + 1).toByte() },
        )
        val imported = runBlocking {
            store(input, emptyList())
            bytes.mapValues { (_, data) ->
                vault.evidence.import(
                    ImportRequest(
                        caseId = input.caseId.value,
                        acquisitionKind = AcquisitionKind.SHARED_STREAM,
                        accessClass = AccessClass.USER_MEDIATED,
                        importerMechanism = "synthetic-test",
                        declaredMime = null,
                        claimedOrigin = null,
                        displayNameClaim = null,
                        uriAuthorityClaim = null,
                        maxPlaintextBytes = 1_000_000L,
                    ),
                    ByteArrayInputStream(data),
                )
            }
        }
        val events = input.events.map { event ->
            val evidence = imported[event.eventId.value] ?: return@map event
            event.copy(
                evidenceReferences = event.evidenceReferences.map {
                    it.copy(artifactId = ArtifactId(evidence.id), sha256 = evidence.sha256)
                },
            )
        }
        assertEquals(BatchSaveResult.Saved(events.size), runBlocking { vault.events.saveAll(events) })
        return events to bytes
    }

    @Test
    fun theZipUnpacksToABundleThatVerifies() {
        val input = SyntheticTimelines.a()
        store(input)
        val result = exported(runBlocking { service().export(selectAll(input)) })
        assertTrue(result.zipFile.isFile)
        assertEquals(setOf(result.zipFile.name), exportDirectory().list()!!.toSet())
        val dir = unzip(result.zipFile)
        val report = BundleVerifier.verify(dir)
        assertEquals(Verdict.CONSISTENT, report.verdict, report.checks.toString())
        assertEquals(result.signerKeyId, report.signerKeyId)
        assertEquals(ReportText.LIMITS, report.limits)
        assertTrue(String(Files.readAllBytes(dir.resolve("report.pdf"))).startsWith("%PDF"))
        assertEquals(7, result.summary.eventCount)
        assertEquals(3, result.summary.pageCount)
        assertEquals(0, result.summary.originalCount)
        assertFalse(Files.exists(dir.resolve("evidence")))
    }

    @Test
    fun aFlippedByteMakesTheExtractedBundleInconsistent() {
        val input = SyntheticTimelines.a()
        store(input)
        val dir = unzip(exported(runBlocking { service().export(selectAll(input)) }).zipFile)
        val events = dir.resolve("events.jsonl")
        val data = Files.readAllBytes(events)
        data[data.size / 2] = (data[data.size / 2].toInt() xor 1).toByte()
        Files.write(events, data)
        assertEquals(Verdict.INCONSISTENT, BundleVerifier.verify(dir).verdict)
    }

    @Test
    fun originalsAreIncludedOnlyWhenSelectedAndKeepTheirBytes() {
        val input = SyntheticTimelines.a()
        val (events, bytes) = withEvidence(input)
        val firstId = events.single { it.eventId.value == "synthetic-a1" }.evidenceReferences.single().artifactId.value

        val without = exported(runBlocking { service().export(selectAll(input)) })
        assertFalse(Files.exists(unzip(without.zipFile).resolve("evidence")))
        assertEquals(2, without.summary.omitted.evidenceCount)

        val with = exported(runBlocking { service().export(selectAll(input).copy(includeOriginalsFor = setOf(firstId)), ReportOptions(reportVersion = 2)) })
        val dir = unzip(with.zipFile)
        assertEquals(Verdict.CONSISTENT, BundleVerifier.verify(dir).verdict)
        assertEquals(listOf(firstId), Files.list(dir.resolve("evidence")).use { s -> s.map { it.fileName.toString() }.toList() })
        assertContentEquals(bytes.getValue("synthetic-a1"), Files.readAllBytes(dir.resolve("evidence").resolve(firstId)))
        assertEquals(1, with.summary.omitted.evidenceCount)
        assertEquals(1, with.summary.originalCount)
    }

    @Test
    fun omittedEventCountsAreCorrect() {
        val input = SyntheticTimelines.a()
        store(input)
        val result = exported(runBlocking { service().export(select(input, "synthetic-a1", "synthetic-a2", "synthetic-a3")) })
        assertEquals(4, result.summary.omitted.eventCount)
        val manifest = String(Files.readAllBytes(unzip(result.zipFile).resolve("manifest.json")))
        assertTrue(manifest.contains("\"event_count\":4"))
    }

    @Test
    fun refusedSelectionsWriteNothing() {
        val input = SyntheticTimelines.a()
        store(input)
        val refused = runBlocking { service().export(select(input)) }
        assertEquals(ExportResult.Refused(RefusalReason.EMPTY_SELECTION), refused)
        val unknown = runBlocking { service().export(select(input, "synthetic-nope")) }
        assertEquals(ExportResult.Refused(RefusalReason.UNKNOWN_EVENT), unknown)
        assertTrue(exportDirectory().list().orEmpty().isEmpty())
    }

    @Test
    fun aFailingRendererLeavesNothingBehind() {
        val input = SyntheticTimelines.a()
        store(input)
        val broken = object : ReportRenderer {
            override fun render(model: ReportModel, output: OutputStream): RenderSummary = throw IllegalArgumentException("synthetic")
        }
        val result = runBlocking { service(broken).export(selectAll(input)) }
        assertEquals(ExportResult.Failed(ExportFailure.RENDER_FAILED), result)
        assertTrue(exportDirectory().list().orEmpty().isEmpty())
    }

    @Test
    fun clearExportsEmptiesTheDirectoryAndZipEntriesAreSafe() {
        val input = SyntheticTimelines.a()
        store(input)
        val service = service()
        val result = exported(runBlocking { service.export(selectAll(input)) })
        ZipFile(result.zipFile).use { archive ->
            val names = archive.entries().asSequence().map { it.name }.toList()
            assertEquals(names.sorted(), names)
            assertTrue(names.all { !it.startsWith("/") && ".." !in it.split('/') && "\\" !in it })
            assertTrue(names.containsAll(listOf("manifest.json", "manifest.sig", "signer.json", "report.pdf", "events.jsonl")))
            assertTrue(names.none { it.contains("title") })
        }
        assertTrue(service.clearExports() >= 1)
        assertTrue(exportDirectory().list().orEmpty().isEmpty())
        assertEquals(0, service.clearExports())
    }

    @Test
    fun exportedCorrectionsResolveAndTheBundleStaysConsistent() {
        val input = SyntheticTimelines.b()
        store(input)
        runBlocking {
            vault.review.reviewCategory(EventId("synthetic-b1"), 0, CategoryReviewStatus.UNCERTAIN)
            vault.review.reviewCategory(EventId("synthetic-b2"), 0, CategoryReviewStatus.REJECTED)
        }
        val result = exported(runBlocking { service().export(selectAll(input)) })
        val dir = unzip(result.zipFile)
        val report = BundleVerifier.verify(dir)
        assertEquals(Verdict.CONSISTENT, report.verdict, report.checks.toString())
        val corrections = String(Files.readAllBytes(dir.resolve("corrections.json")))
        assertTrue(corrections.contains("synthetic-b1/2/c000000"))
        assertTrue(corrections.contains("synthetic-b2/2/c000000"))
    }
}
