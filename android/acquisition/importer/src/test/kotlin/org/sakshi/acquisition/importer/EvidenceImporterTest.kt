package org.sakshi.acquisition.importer

import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.sakshi.core.vault.AcquisitionKind
import org.sakshi.core.vault.AuditVerification

class EvidenceImporterTest : ImporterTestBase() {
    private fun commitAll(batch: PendingBatch, importer: EvidenceImporter = importer(), id: String = caseId): ImportReport =
        runBlocking { importer.commit(id, batch, batch.items.map { it.index }.toSet()) }

    private fun saved(outcome: ItemOutcome): ItemOutcome.Saved = assertIs<ItemOutcome.Saved>(outcome)

    @Test
    fun storedBytesMatchIndependentDigest() {
        val bytes = ByteArray(50_000) { (it * 7).toByte() }
        val uri = serve("a", bytes, Entry(mime = "application/octet-stream"))
        val report = commitAll(batch(pendingStream(uri, ItemKind.OTHER, mime = "application/octet-stream")))

        val outcome = saved(report.outcomes.single())
        assertEquals(sha256Hex(bytes), outcome.sha256)
        assertEquals(bytes.size.toLong(), outcome.byteSize)
        assertContentEquals(bytes, original(outcome.evidenceId))
        assertEquals("application/octet-stream", outcome.declaredMime)
        assertEquals(1, rowCount())
        assertTrue(runBlocking { vault.audit.verify() } is AuditVerification.Valid)
    }

    @Test
    fun detectedMimeComesFromTheBytesNotTheClaim() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(64)
        val uri = serve("b", png)
        val outcome = saved(commitAll(batch(pendingStream(uri, ItemKind.PDF, mime = "application/pdf"))).outcomes.single())
        assertEquals("image/png", outcome.detectedMime)
        assertEquals("application/pdf", outcome.declaredMime)
    }

    @Test
    fun textIsStoredAsExactUtf8() {
        val text = "synthetic ഇത് यह परीक्षण 😀 end\n"
        val report = commitAll(batch(PendingItem.Text(0, text), mechanism = ImportMechanism.SHARE_SEND))
        val outcome = saved(report.outcomes.single())
        assertContentEquals(text.toByteArray(Charsets.UTF_8), original(outcome.evidenceId))
        assertEquals("text/plain; charset=utf-8", outcome.declaredMime)
        assertEquals(AnalysisState.READY_FOR_TEXT_ANALYSIS, outcome.analysisState)
        val kind = runBlocking { vault.evidence.observeForCase(caseId).first().single().acquisitionKind }
        assertEquals(AcquisitionKind.SHARED_TEXT, kind)
    }

    @Test
    fun acquisitionKindFollowsTheMechanism() {
        val kinds = mapOf(
            ImportMechanism.PASTE to AcquisitionKind.PASTED_TEXT,
            ImportMechanism.SHARE_SEND to AcquisitionKind.SHARED_TEXT,
        )
        for ((mechanism, expected) in kinds) {
            val id = runBlocking { vault.cases.create("Synthetic $mechanism").id }
            commitAll(batch(PendingItem.Text(0, "synthetic"), mechanism = mechanism), id = id)
            assertEquals(expected, runBlocking { vault.evidence.observeForCase(id).first().single().acquisitionKind })
        }
        val streamKinds = mapOf(
            ImportMechanism.SHARE_SEND to AcquisitionKind.SHARED_STREAM,
            ImportMechanism.SHARE_SEND_MULTIPLE to AcquisitionKind.SHARED_STREAM,
            ImportMechanism.DOCUMENT_PICKER to AcquisitionKind.SELECTED_DOCUMENT,
            ImportMechanism.PHOTO_PICKER to AcquisitionKind.SELECTED_VISUAL_MEDIA,
        )
        for ((mechanism, expected) in streamKinds) {
            val id = runBlocking { vault.cases.create("Synthetic $mechanism").id }
            val uri = serve("kind-$mechanism", ByteArray(10))
            commitAll(batch(pendingStream(uri, ItemKind.OTHER), mechanism = mechanism), id = id)
            assertEquals(expected, runBlocking { vault.evidence.observeForCase(id).first().single().acquisitionKind })
        }
    }

    @Test
    fun limitIsEnforcedWhileStreamingEvenIfSizeClaimIsSmall() {
        val uri = serveStream("big", Entry(size = 10)) { ByteArrayInputStream(ByteArray(10_000)) }
        val claimed = pendingStream(uri, ItemKind.IMAGE).copy(sizeClaim = 10)
        val report = commitAll(batch(claimed), importer(ImportLimits(maxImageBytes = 4096)))
        assertEquals(ItemOutcome.Failed(0, ImportFailure.TOO_LARGE), report.outcomes.single())
        assertEquals(0, rowCount())
        assertNoBlobs()
    }

    @Test
    fun limitsDifferPerKind() {
        val uri = serve("kinds", ByteArray(3000))
        val limits = ImportLimits(maxPdfBytes = 1000, maxVideoBytes = 5000)
        val pdf = commitAll(batch(pendingStream(uri, ItemKind.PDF)), importer(limits))
        val video = commitAll(batch(pendingStream(uri, ItemKind.VIDEO)), importer(limits))
        assertEquals(ImportFailure.TOO_LARGE, assertIs<ItemOutcome.Failed>(pdf.outcomes.single()).reason)
        saved(video.outcomes.single())
    }

    @Test
    fun securityExceptionGivesAccessDenied() {
        val uri = serveStream("denied") { throw SecurityException("synthetic") }
        val report = commitAll(batch(pendingStream(uri, ItemKind.IMAGE)))
        assertEquals(ItemOutcome.Failed(0, ImportFailure.ACCESS_DENIED), report.outcomes.single())
        assertEquals(0, rowCount())
    }

    @Test
    fun missingStreamIsUnreadable() {
        val uri = contentUri("missing")
        val report = commitAll(batch(pendingStream(uri, ItemKind.IMAGE)))
        assertEquals(ItemOutcome.Failed(0, ImportFailure.UNREADABLE), report.outcomes.single())
    }

    @Test
    fun streamFailingMidwayLeavesNothing() {
        val uri = serveStream("midway") { FailingStream(100_000) }
        val report = commitAll(batch(pendingStream(uri, ItemKind.OTHER)))
        assertEquals(ItemOutcome.Failed(0, ImportFailure.STORAGE_ERROR), report.outcomes.single())
        assertEquals(0, rowCount())
        assertNoBlobs()
    }

    @Test
    fun keyFailureIsReported() {
        val broken = newVault(org.sakshi.acquisition.importer.BrokenWrapper())
        try {
            val id = runBlocking { broken.cases.create("Synthetic broken").id }
            val uri = serve("key", ByteArray(10))
            val report = runBlocking {
                importer(vault = broken).commit(id, batch(pendingStream(uri, ItemKind.OTHER)), setOf(0))
            }
            assertEquals(ItemOutcome.Failed(0, ImportFailure.KEY_UNAVAILABLE), report.outcomes.single())
        } finally {
            broken.close()
        }
    }

    @Test
    fun archivedOrUnknownCaseFailsEveryItem() {
        val uri = serve("c", ByteArray(10))
        val items = batch(pendingStream(uri, ItemKind.OTHER, 0), PendingItem.Text(1, "synthetic"), pendingStream(uri, ItemKind.IMAGE, 2))
        runBlocking { vault.cases.archive(caseId) }
        val archived = commitAll(items)
        val unknown = commitAll(items, id = "no-such-case")
        for (report in listOf(archived, unknown)) {
            assertEquals(listOf(0, 1, 2), report.outcomes.map { it.index })
            assertTrue(report.outcomes.all { it == ItemOutcome.Failed(it.index, ImportFailure.CASE_UNAVAILABLE) })
        }
        runBlocking { vault.cases.unarchive(caseId) }
        assertEquals(0, rowCount())
        assertNoBlobs()
    }

    @Test
    fun oneFailureDoesNotStopTheOthers() {
        val good1 = serve("g1", ByteArray(20) { 1 })
        val bad = serveStream("bad") { throw SecurityException("synthetic") }
        val good2 = serve("g2", ByteArray(20) { 2 })
        val report = commitAll(
            batch(
                pendingStream(good1, ItemKind.OTHER, 0),
                pendingStream(bad, ItemKind.OTHER, 1),
                pendingStream(good2, ItemKind.OTHER, 2),
            ),
        )
        assertEquals(listOf(0, 1, 2), report.outcomes.map { it.index })
        saved(report.outcomes[0])
        assertEquals(ItemOutcome.Failed(1, ImportFailure.ACCESS_DENIED), report.outcomes[1])
        saved(report.outcomes[2])
        assertEquals(2, rowCount())
        assertEquals(2, blobNames().size)
    }

    @Test
    fun onlySelectedIndexesAreSaved() {
        val uris = (0..2).map { serve("s$it", ByteArray(5) { b -> (b + it).toByte() }) }
        val items = uris.mapIndexed { i, uri -> pendingStream(uri, ItemKind.OTHER, i) }
        val pending = batch(*items.toTypedArray(), PendingItem.Rejected(3, Rejection.UNSUPPORTED_SCHEME))
        val report = runBlocking { importer().commit(caseId, pending, setOf(0, 2, 3, 99)) }
        assertEquals(listOf(0, 2, 3), report.outcomes.map { it.index })
        assertEquals(ItemOutcome.Skipped(3, Rejection.UNSUPPORTED_SCHEME), report.outcomes[2])
        assertEquals(2, rowCount())
    }

    @Test
    fun duplicatesAreSavedAndListTheEarlierId() {
        val bytes = ByteArray(100) { 9 }
        val first = serve("d1", bytes)
        val second = serve("d2", bytes)
        val report = commitAll(batch(pendingStream(first, ItemKind.OTHER, 0), pendingStream(second, ItemKind.OTHER, 1)))
        val one = saved(report.outcomes[0])
        val two = saved(report.outcomes[1])
        assertEquals(emptyList(), one.duplicateOf)
        assertEquals(listOf(one.evidenceId), two.duplicateOf)
        assertEquals(2, rowCount())
    }

    @Test
    fun analysisStateByKind() {
        val expected = mapOf(
            ItemKind.AUDIO to AnalysisState.PRESERVED_NOT_ANALYSED,
            ItemKind.VIDEO to AnalysisState.PRESERVED_NOT_ANALYSED,
            ItemKind.PDF to AnalysisState.PRESERVED_NOT_ANALYSED,
            ItemKind.ARCHIVE to AnalysisState.PRESERVED_NOT_ANALYSED,
            ItemKind.OTHER to AnalysisState.PRESERVED_NOT_ANALYSED,
            ItemKind.TEXT_FILE to AnalysisState.READY_FOR_TEXT_ANALYSIS,
            ItemKind.IMAGE to AnalysisState.READY_FOR_TEXT_ANALYSIS,
        )
        expected.entries.forEachIndexed { i, (kind, state) ->
            val uri = serve("an$i", ByteArray(8) { b -> (b + i).toByte() })
            val outcome = saved(commitAll(batch(pendingStream(uri, kind))).outcomes.single())
            assertEquals(state, outcome.analysisState, kind.name)
        }
    }

    private fun stateFor(declared: String, kind: ItemKind, bytes: ByteArray, path: String): AnalysisState {
        val uri = serve(path, bytes)
        return saved(commitAll(batch(pendingStream(uri, kind, mime = declared))).outcomes.single()).analysisState
    }

    @Test
    fun analysisStateUsesDetectedMimeOverLyingClaim() {
        val pdf = "%PDF-1.7 synthetic".toByteArray() + ByteArray(32)
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(32)
        assertEquals(AnalysisState.PRESERVED_NOT_ANALYSED, stateFor("image/jpeg", ItemKind.IMAGE, pdf, "lie1"))
        assertEquals(AnalysisState.READY_FOR_TEXT_ANALYSIS, stateFor("application/octet-stream", ItemKind.OTHER, png, "lie2"))
        assertEquals(AnalysisState.READY_FOR_TEXT_ANALYSIS, stateFor("text/plain", ItemKind.TEXT_FILE, "plain synthetic".toByteArray(), "lie3"))
        assertEquals(AnalysisState.PRESERVED_NOT_ANALYSED, stateFor("application/octet-stream", ItemKind.OTHER, "plain synthetic".toByteArray(), "lie4"))
    }

    @Test
    fun progressCountsSelectedItemsIncludingFailures() {
        val ok = serve("pr1", ByteArray(4))
        val bad = serveStream("pr2") { throw SecurityException("synthetic") }
        val calls = ArrayList<Pair<Int, Int>>()
        val pending = batch(pendingStream(ok, ItemKind.OTHER, 0), pendingStream(bad, ItemKind.OTHER, 1), PendingItem.Text(2, "synthetic"))
        runBlocking { importer().commit(caseId, pending, setOf(0, 1)) { done, total -> calls += done to total } }
        assertEquals(listOf(1 to 2, 2 to 2), calls)
    }

    @Test
    fun streamIsClosedOnSuccessAndOnFailure() {
        val streams = ArrayList<TrackingStream>()
        val ok = serveStream("cl1") { TrackingStream(ByteArrayInputStream(ByteArray(10))).also { streams += it } }
        val large = serveStream("cl2") { TrackingStream(ByteArrayInputStream(ByteArray(10_000))).also { streams += it } }
        val failing = serveStream("cl3") { TrackingStream(FailingStream(5)).also { streams += it } }
        val pending = batch(
            pendingStream(ok, ItemKind.OTHER, 0),
            pendingStream(large, ItemKind.IMAGE, 1),
            pendingStream(failing, ItemKind.OTHER, 2),
        )
        commitAll(pending, importer(ImportLimits(maxImageBytes = 100)))
        assertEquals(3, streams.size)
        assertTrue(streams.all { it.closed })
    }

    @Test
    fun cancellationPropagatesAndLeavesNothing() {
        val reads = AtomicInteger()
        lateinit var job: Job
        val uri = serveStream("cancel") {
            object : java.io.InputStream() {
                override fun read(): Int = throw UnsupportedOperationException()

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (reads.incrementAndGet() == 2) job.cancel()
                    return minOf(length, 1024).also { java.util.Arrays.fill(buffer, offset, offset + it, 1) }
                }
            }
        }
        val pending = batch(pendingStream(uri, ItemKind.OTHER), PendingItem.Text(1, "synthetic"))
        val finished = AtomicInteger()
        runBlocking {
            job = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
                importer().commit(caseId, pending, setOf(0, 1))
                finished.incrementAndGet()
            }
            job.start()
            job.join()
        }
        assertTrue(job.isCancelled)
        assertEquals(0, finished.get())
        assertEquals(0, rowCount())
        assertNoBlobs()
    }

    @Test
    fun hostileDisplayNamesNeverInfluenceFilePaths() {
        val names = listOf("../../databases/sakshi.db", "evil\u0000name\n.txt", "/etc/passwd", "..\\..\\x", "con:\r\nX: y")
        names.forEachIndexed { i, name ->
            val uri = serve("host$i", ByteArray(16) { b -> (b + i).toByte() }, Entry(name = name))
            val item = assertIs<PendingItem.Stream>(PickerReader.fromPickedUris(listOf(uri), ImportMechanism.DOCUMENT_PICKER, resolver).items.single())
            assertEquals(name, item.displayNameClaim)
            saved(commitAll(PendingBatch(ImportMechanism.DOCUMENT_PICKER, listOf(item), null)).outcomes.single())
        }
        assertEquals(names.size, rowCount())
        assertOnlyBlobFilesInVault()
        assertEquals(names.size, blobNames().size)
        assertTrue(!File(context.noBackupFilesDir, "databases").exists())
        assertTrue(!File(context.noBackupFilesDir.parentFile, "etc").exists())
    }
}
