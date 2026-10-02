package org.sakshi.export.report

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.ArtifactId
import org.sakshi.core.model.AssociationReview
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.IdentityBasis
import org.sakshi.core.model.Locator
import org.sakshi.core.temporal.EvidenceView
import org.sakshi.core.temporal.fixtures.SyntheticTimelines
import org.sakshi.core.vault.AccessClass
import org.sakshi.core.vault.AcquisitionKind
import org.sakshi.core.vault.BatchSaveResult
import org.sakshi.core.vault.DerivativeKind
import org.sakshi.core.vault.ImportRequest
import org.sakshi.export.bundle.BundleVerifier
import org.sakshi.export.bundle.GeneratorInfo
import org.sakshi.export.bundle.Verdict

/** A full export on the device with the Keystore signer and the platform PDF writer. All data is synthetic. */
@RunWith(AndroidJUnit4::class)
class ExportDeviceTest : DeviceTestBase() {
    private val english = "Please reply to me. I will keep messaging until you answer."
    private val malayalam = "ദയവായി മറുപടി തരൂ. " +
        "ഞാൻ വീണ്ടും വിളിക്കും."
    private val hindi = "कृपया जवाब दीजिए। " +
        "मैं फिर से संदेश भेजूँगा।"
    private val now = Instant.parse("2026-10-04T00:00:00Z")
    private val generator = GeneratorInfo("synthetic-app-1", listOf("synthetic-rules-1"), listOf("synthetic-model-1"))

    private fun request(caseId: String) = ImportRequest(
        caseId = caseId,
        acquisitionKind = AcquisitionKind.SHARED_TEXT,
        accessClass = AccessClass.USER_MEDIATED,
        importerMechanism = "synthetic-device-test",
        declaredMime = "text/plain",
        claimedOrigin = null,
        displayNameClaim = null,
        uriAuthorityClaim = null,
        maxPlaintextBytes = 1_000_000L,
    )

    private fun brightPixels(bitmap: Bitmap): Pair<Int, Int> {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { it != Color.WHITE } to pixels.size
    }

    @Test
    fun exportsAVerifiableBundleWithAReadablePdf() = runBlocking<Unit> {
        val vault = openVault(newWrapper())
        vault.startUp()
        val input = SyntheticTimelines.a()
        val caseId = CaseId(vault.cases.create("synthetic-case-export").id)
        val actor = ActorId("synthetic-actor-a")
        vault.actors.create(caseId, "synthetic-actor-label", IdentityBasis.USER_ASSERTED, AssociationReview.CONFIRMED, actor)

        val texts = listOf(english, malayalam, hindi)
        val stored = texts.map { text ->
            val imported = vault.evidence.import(request(caseId.value), ByteArrayInputStream(text.toByteArray()))
            vault.derivatives.save(imported.id, DerivativeKind.PARSED_TEXT, text, "synthetic-tool", "1")
            imported
        }
        val lengths = stored.mapIndexed { i, e -> e.id to texts[i].codePointCount(0, texts[i].length) }.toMap()
        val events = input.events.mapIndexed { index, event ->
            val evidence = stored[index % stored.size]
            val length = lengths.getValue(evidence.id)
            event.copy(
                caseId = caseId,
                evidenceReferences = event.evidenceReferences.map {
                    it.copy(
                        artifactId = ArtifactId(evidence.id),
                        sha256 = evidence.sha256,
                        locator = Locator.Text(if (index % 2 == 0) 0 else 2, length),
                    )
                },
            )
        }
        assertEquals(BatchSaveResult.Saved(events.size), vault.events.saveAll(events) { lengths[it.value] })

        val signer = newSigner()
        val builder = ReportBuilder(vault, VaultQuoteSource(vault), { now }, generator)
        val service = ExportService(context, vault, builder, ReportPdfRenderer(), signer, { now }, { UUID.randomUUID().toString() })
        val selection = ReportSelection(
            caseId = caseId,
            eventIds = events.map { it.eventId }.toSet(),
            includeOriginalsFor = setOf(stored.first().id),
            view = EvidenceView.CONFIRMED_ONLY,
            zone = java.time.ZoneOffset.ofHoursMinutes(5, 30),
        )

        val start = System.nanoTime()
        val result = service.export(selection) as ExportResult.Exported
        val totalMs = (System.nanoTime() - start) / NANOS_PER_MILLI

        assertEquals(signer.keyId(), result.signerKeyId)
        val dir = scratchDirectory()
        ZipFile(result.zipFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val out = File(dir, entry.name)
                out.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input -> FileOutputStream(out).use { input.copyTo(it) } }
            }
        }
        val report = BundleVerifier.verify(dir.toPath())
        assertEquals(Verdict.CONSISTENT, report.verdict, report.checks.toString())
        assertContentEquals(english.toByteArray(), File(dir, "evidence/${stored.first().id}").readBytes())

        val pdf = File(dir, "report.pdf")
        assertEquals("%PDF", String(pdf.readBytes().copyOf(4)))
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                assertEquals(result.summary.pageCount, renderer.pageCount)
                assertTrue(renderer.pageCount >= 1)
                val bitmap = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                renderer.openPage(0).use { it.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                val (inked, total) = brightPixels(bitmap)
                assertTrue(inked > total / 200, "first page is blank: $inked of $total pixels differ from white")
                bitmap.recycle()
            }
        }

        val model = (builder.build(selection, signerKeyId = result.signerKeyId) as ReportBuildResult.Built).model
        val renderStart = System.nanoTime()
        val pdfBytes = java.io.ByteArrayOutputStream().also { ReportPdfRenderer().render(model, it) }.size()
        val renderMs = (System.nanoTime() - renderStart) / NANOS_PER_MILLI
        record(
            "export_full",
            *deviceState(context),
            "events" to result.summary.eventCount,
            "pages" to result.summary.pageCount,
            "pdf_bytes" to pdfBytes,
            "render_ms" to renderMs,
            "export_total_ms" to totalMs,
            "zip_bytes" to result.summary.zipBytes,
            "files" to result.summary.fileCount,
        )
        assertEquals(1, service.clearExports())
        assertTrue(Files.notExists(result.zipFile.toPath()))
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
