package org.sakshi.export.report

import java.io.File
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.sakshi.core.integrity.Sha256
import org.sakshi.core.model.CategoryReviewStatus
import org.sakshi.core.model.EventId
import org.sakshi.core.temporal.TemporalInput
import org.sakshi.core.temporal.fixtures.SyntheticTimelines
import org.sakshi.core.vault.AuditActions
import org.sakshi.export.bundle.SoftwareP256Signer

class ReportVersionTest : ReportTestBase() {
    private val signer = SoftwareP256Signer.generate()

    private fun service(
        renderer: ReportRenderer = FakeRenderer(),
        snapshotIds: () -> String = ids,
        clock: () -> java.time.Instant = { FIXED_NOW },
    ) = ExportService(context, vault, ReportBuilder(vault, FakeQuotes(), clock, GENERATOR), renderer, signer, clock, snapshotIds)

    private fun exportDirectory(): File = File(context.cacheDir, "exports")

    private fun exported(result: ExportResult): ExportResult.Exported =
        result as? ExportResult.Exported ?: error("Expected an export but got $result")

    private suspend fun exportRows(): List<String> =
        vault.audit.records().filter { it.action == AuditActions.EXPORT_CREATED }.map { it.subjectId }

    private fun manifestSha256(zip: File): String = ZipFile(zip).use { archive ->
        Sha256.hex(Sha256.digest(archive.getInputStream(archive.getEntry("manifest.json")).use { it.readBytes() }))
    }

    private fun options(version: Int) = ReportOptions(reportVersion = version)

    @Test
    fun aFirstExportIsStoredAsVersionOne() = runBlocking<Unit> {
        val input = SyntheticTimelines.a()
        store(input)
        val selection = select(input, "synthetic-a1", "synthetic-a2", "synthetic-a3")

        val result = exported(service().export(selection))

        assertEquals(1, result.reportVersion)
        val stored = vault.reports.latest(input.caseId) ?: error("No snapshot was stored")
        assertEquals(result.snapshotId, stored.id)
        assertEquals(1, stored.version)
        assertEquals(result.summary.merkleRoot, stored.merkleRoot)
        assertEquals(manifestSha256(result.zipFile), stored.manifestSha256)
        assertEquals(result.signerKeyId, stored.signerKeyId)
        assertNull(stored.supersededBy)
        val revisions = vault.events.loadLatest(input.caseId, FIXED_NOW).filter { it.eventId.value in setOf("synthetic-a1", "synthetic-a2", "synthetic-a3") }
        assertEquals(3, revisions.size)
        assertEquals(revisions.associate { it.eventId.value to it.revision }, stored.dependencies.eventRevisions)
        assertEquals(listOf(result.snapshotId), exportRows())
    }

    @Test
    fun aSecondExportWithTheNextVersionSupersedesTheFirst() = runBlocking<Unit> {
        val input = SyntheticTimelines.a()
        store(input)
        val service = service()
        val first = exported(service.export(selectAll(input)))

        val second = exported(service.export(selectAll(input), options(2)))

        assertEquals(2, second.reportVersion)
        val latest = vault.reports.latest(input.caseId) ?: error("No snapshot was stored")
        assertEquals(second.snapshotId, latest.id)
        assertEquals(2, latest.version)
        assertNull(latest.supersededBy)
        assertEquals(listOf(first.snapshotId, second.snapshotId), exportRows())
        assertEquals(3, vault.reports.nextVersion(input.caseId))
    }

    @Test
    fun aStaleVersionIsRefusedAndLeavesNothing() = runBlocking<Unit> {
        val input = SyntheticTimelines.a()
        store(input)
        val service = service()
        val first = exported(service.export(selectAll(input)))
        service.clearExports()
        val auditBefore = vault.audit.count()

        val result = service.export(selectAll(input), options(1))

        assertEquals(ExportResult.Refused(RefusalReason.CHANGED_SINCE_PREVIEW), result)
        assertTrue(exportDirectory().list().orEmpty().isEmpty())
        assertEquals(auditBefore, vault.audit.count())
        assertEquals(first.snapshotId, vault.reports.latest(input.caseId)?.id)
        assertEquals(2, vault.reports.nextVersion(input.caseId))
        assertEquals(listOf(first.snapshotId), exportRows())
    }

    @Test
    fun aVersionAheadOfTheNextOneIsAlsoRefused() = runBlocking<Unit> {
        val input = SyntheticTimelines.a()
        store(input)

        val result = service().export(selectAll(input), options(2))

        assertEquals(ExportResult.Refused(RefusalReason.CHANGED_SINCE_PREVIEW), result)
        assertNull(vault.reports.latest(input.caseId))
        assertTrue(exportDirectory().list().orEmpty().isEmpty())
    }

    @Test
    fun theFingerprintOfThePreviewExportsAndAChangedCaseIsRefused() = runBlocking<Unit> {
        val input = SyntheticTimelines.b()
        store(input)
        val selection = selectAll(input)
        val preview = built(builder().build(selection, options(1))).contentSha256

        val first = exported(service().export(selection, options(1), preview))
        assertEquals(1, first.reportVersion)

        // The preview of version 2 is taken, then the person reviews a suggestion before exporting.
        val secondPreview = built(builder().build(selection, options(2))).contentSha256
        vault.review.reviewCategory(EventId("synthetic-b1"), 0, CategoryReviewStatus.UNCERTAIN)
        val service = service()
        service.clearExports()
        val auditBefore = vault.audit.count()

        val result = service.export(selection, options(2), secondPreview)

        assertEquals(ExportResult.Refused(RefusalReason.CHANGED_SINCE_PREVIEW), result)
        assertTrue(exportDirectory().list().orEmpty().isEmpty())
        assertEquals(auditBefore, vault.audit.count())
        assertEquals(first.snapshotId, vault.reports.latest(input.caseId)?.id)

        val fresh = built(builder().build(selection, options(2))).contentSha256
        assertNotEquals(secondPreview, fresh)
        assertEquals(2, exported(service.export(selection, options(2), fresh)).reportVersion)
    }

    @Test
    fun theFingerprintIgnoresBuildTimeAndSigningValues() = runBlocking<Unit> {
        val input = SyntheticTimelines.a()
        store(input)
        val selection = selectAll(input)
        val early = built(ReportBuilder(vault, FakeQuotes(), { FIXED_NOW }, GENERATOR).build(selection, options(1)))
        val late = built(ReportBuilder(vault, FakeQuotes(), { FIXED_NOW.plusSeconds(86_400) }, GENERATOR).build(selection, options(1), "synthetic-key-id"))

        assertNotEquals(early.model.generatedAt, late.model.generatedAt)
        assertEquals(early.contentSha256, late.contentSha256)

        val signed = early.model.copy(
            integrity = early.model.integrity.copy(
                manifestHash = "synthetic-manifest-hash",
                merkleRoot = "synthetic-merkle-root",
                signerKeyId = "synthetic-key-id",
                auditChainHead = "synthetic-audit-head",
            ),
        )
        assertEquals(early.contentSha256, ReportFingerprint.of(signed))
    }

    @Test
    fun theFingerprintChangesWithTheContent() = runBlocking<Unit> {
        val input: TemporalInput = SyntheticTimelines.a()
        store(input)
        val all = built(builder().build(selectAll(input), options(1))).contentSha256

        val fewer = built(builder().build(select(input, "synthetic-a1", "synthetic-a2"), options(1))).contentSha256
        val otherVersion = built(builder().build(selectAll(input), options(2))).contentSha256

        assertNotEquals(all, fewer)
        assertNotEquals(all, otherVersion)
        assertEquals(all, built(builder().build(selectAll(input), options(1))).contentSha256)
    }

    @Test
    fun theVersionFromTheOptionsIsPrintedInTheModelAndParagraphs() = runBlocking<Unit> {
        val input = SyntheticTimelines.a()
        store(input)

        val model = built(builder().build(selectAll(input), options(3))).model

        assertEquals(3, model.reportVersion)
        assertTrue(ReportParagraphs.of(model).any { it.text.startsWith("Report version 3.") })
        assertEquals("Page 1 of 4 - Report version 3", ReportText.fill(ReportText.PAGE_FOOTER, 1, 4, model.reportVersion))
    }

    @Test
    fun aDatabaseFailureWhileStoringTheSnapshotLeavesNoFile() = runBlocking<Unit> {
        val input = SyntheticTimelines.a()
        store(input)
        val sameId = { "synthetic-snapshot-fixed" }
        val first = exported(service(snapshotIds = sameId).export(selectAll(input)))
        val service = service(snapshotIds = sameId)
        service.clearExports()
        val auditBefore = vault.audit.count()

        // The second export reuses the first snapshot id, so inserting its row violates the primary key.
        assertFailsWith<Exception> { service.export(selectAll(input), options(2)) }

        assertTrue(exportDirectory().list().orEmpty().isEmpty())
        assertEquals(auditBefore, vault.audit.count())
        assertEquals(first.snapshotId, vault.reports.latest(input.caseId)?.id)
        assertEquals(2, vault.reports.nextVersion(input.caseId))
    }

    @Test
    fun refusedAndFailedExportsStoreNoSnapshot() = runBlocking<Unit> {
        val input = SyntheticTimelines.a()
        store(input)
        val broken = object : ReportRenderer {
            override fun render(model: ReportModel, output: java.io.OutputStream): RenderSummary = throw IllegalArgumentException("synthetic")
        }

        assertEquals(ExportResult.Refused(RefusalReason.EMPTY_SELECTION), service().export(select(input)))
        assertEquals(ExportResult.Failed(ExportFailure.RENDER_FAILED), service(broken).export(selectAll(input)))

        assertNull(vault.reports.latest(input.caseId))
        assertEquals(1, vault.reports.nextVersion(input.caseId))
    }
}
