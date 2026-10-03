package org.sakshi.acquisition.importer

import android.content.Intent
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLog

/**
 * Security tests of the import path (megaplan 27.3): archives, limits against lying providers, revoked grants,
 * forged intents and damaged media. All content is synthetic.
 */
class SecurityImportTest : ImporterTestBase() {
    private fun commitAll(batch: PendingBatch, importer: EvidenceImporter = importer()): ImportReport =
        runBlocking { importer.commit(caseId, batch, batch.items.map { it.index }.toSet()) }

    private fun saved(outcome: ItemOutcome): ItemOutcome.Saved = assertIs<ItemOutcome.Saved>(outcome)

    private fun dataFiles(): Set<String> = context.dataDir.walkTopDown().filter { it.isFile }.map { it.absolutePath }.toSet()

    private fun zip(compression: Int = Deflater.DEFAULT_COMPRESSION, build: ZipOutputStream.() -> Unit): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use {
            it.setLevel(compression)
            it.build()
        }
        return output.toByteArray()
    }

    private fun ZipOutputStream.entry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun picked(uri: Uri, mechanism: ImportMechanism = ImportMechanism.DOCUMENT_PICKER): PendingBatch =
        PickerReader.fromPickedUris(listOf(uri), mechanism, resolver)

    // Archives: the importer has no expansion code, so an archive is an opaque file.

    @Test
    fun hostileArchivesArePreservedByteForByteAndNeverExpanded() {
        val inner = zip { entry("synthetic-inner.txt", "synthetic-inner-content".toByteArray()) }
        val hostile = zip {
            entry("../../synthetic-escape.txt", "synthetic-escape".toByteArray())
            entry("/synthetic-absolute.txt", "synthetic-absolute".toByteArray())
            entry("..\\synthetic-backslash.txt", "synthetic-backslash".toByteArray())
            entry("synthetic-nested.zip", inner)
            entry("synthetic-link", "../../../etc/passwd".toByteArray())
            entry("synthetic-dir/", ByteArray(0))
        }
        val bomb = zip(Deflater.BEST_COMPRESSION) { entry("synthetic-zeros.bin", ByteArray(30 * 1024 * 1024)) }
        assertTrue(bomb.size < 100_000, "the synthetic bomb must compress heavily")
        val before = dataFiles()

        val archives = listOf(hostile, bomb, inner)
        val items = archives.mapIndexed { i, bytes ->
            val uri = serve("archive$i", bytes, Entry(mime = "application/zip", name = "synthetic$i.zip"))
            assertIs<PendingItem.Stream>(picked(uri).items.single()).copy(index = i)
        }
        val report = commitAll(batch(*items.toTypedArray(), mechanism = ImportMechanism.DOCUMENT_PICKER))

        report.outcomes.forEachIndexed { i, outcome ->
            val outcomeSaved = saved(outcome)
            assertEquals("application/zip", outcomeSaved.detectedMime)
            assertEquals(AnalysisState.PRESERVED_NOT_ANALYSED, outcomeSaved.analysisState)
            assertEquals(archives[i].size.toLong(), outcomeSaved.byteSize)
            assertEquals(sha256Hex(archives[i]), outcomeSaved.sha256)
            assertContentEquals(archives[i], original(outcomeSaved.evidenceId))
        }
        val created = dataFiles() - before
        assertEquals(archives.size, created.size, "only one blob file per archive may appear")
        assertOnlyBlobFilesInVault()
        val leftovers = context.dataDir.walkTopDown().map { it.name }.filter { it.startsWith("synthetic-") }.toList()
        assertEquals(emptyList(), leftovers, "an archive entry name appeared on disk")
    }

    @Test
    fun anArchiveClaimedAsAnImageIsStillOnlyPreserved() {
        val archive = zip { entry("synthetic.txt", "synthetic".toByteArray()) }
        val uri = serve("claimed-image", archive, Entry(mime = "image/png"))
        val outcome = saved(commitAll(batch(pendingStream(uri, ItemKind.IMAGE, mime = "image/png"))).outcomes.single())
        assertEquals("application/zip", outcome.detectedMime)
        assertEquals(AnalysisState.PRESERVED_NOT_ANALYSED, outcome.analysisState)
    }

    // Hostile display names: claims are capped and stored as received, and never reach the file system.

    @Test
    fun everyShapeOfHostileDisplayNameIsCappedStoredAsAClaimAndNeverUsedAsAPath() {
        val names = listOf(
            "../../x",
            "/absolute/synthetic-escape.bin",
            "C:\\synthetic\\escape.bin",
            "nul\u0000synthetic.bin",
            "line one\nline two\r\nsynthetic-header: injected",
            "\u202Efdp.synthetic",
            "\u2066synthetic\u2069",
            "a".repeat(100_000),
            "😀".repeat(200),
            "..",
            ".",
            "",
        )
        val before = dataFiles()
        val ids = names.mapIndexed { i, name ->
            val uri = serve("shape$i", ByteArray(24) { b -> (b + i).toByte() }, Entry(name = name))
            val item = assertIs<PendingItem.Stream>(picked(uri).items.single())
            val claim = assertNotNull(item.displayNameClaim)
            assertTrue(claim.length <= 255, "claim $i is not capped")
            assertTrue(name.startsWith(claim), "claim $i is not a prefix of what the provider said")
            saved(commitAll(PendingBatch(ImportMechanism.DOCUMENT_PICKER, listOf(item), null)).outcomes.single()).evidenceId to claim
        }
        ids.forEach { (id, claim) ->
            assertEquals(claim, runBlocking { vault.evidence.details(id) }?.displayNameClaim)
        }
        val created = dataFiles() - before
        assertEquals(names.size, created.size, "exactly one blob file per item")
        assertOnlyBlobFilesInVault()
        created.forEach { assertEquals(blobDirectory.absoluteFile, File(it).parentFile?.absoluteFile, "file outside the blob directory") }
    }

    // Limits against providers that report no size or the wrong size.

    @Test
    fun theStoredSizeIsTheNumberOfBytesReceivedWhateverTheProviderClaims() {
        val bytes = ByteArray(3000) { (it * 3).toByte() }
        val claims = listOf<Long?>(null, 0L, 5L, 3000L, 10L * 1024 * 1024 * 1024)
        claims.forEachIndexed { i, claim ->
            val uri = serve("size$i", bytes, Entry(size = claim, mime = "application/octet-stream"))
            val item = assertIs<PendingItem.Stream>(picked(uri).items.single())
            assertEquals(claim?.takeIf { it >= 0 }, item.sizeClaim)
            val outcome = saved(commitAll(PendingBatch(ImportMechanism.DOCUMENT_PICKER, listOf(item), null), importer(ImportLimits(maxOtherBytes = 10_000))).outcomes.single())
            assertEquals(3000L, outcome.byteSize, "claim $claim")
            assertContentEquals(bytes, original(outcome.evidenceId))
        }
    }

    @Test
    fun anOverLimitStreamIsRefusedWhateverSizeItClaimsAndLeavesNothing() {
        val limits = ImportLimits(maxOtherBytes = 4096)
        listOf<Long?>(null, 0L, 10L, 4096L, 1L shl 40).forEachIndexed { i, claim ->
            val uri = serve("over$i", ByteArray(4097), Entry(size = claim))
            val item = assertIs<PendingItem.Stream>(picked(uri).items.single())
            val report = commitAll(PendingBatch(ImportMechanism.DOCUMENT_PICKER, listOf(item), null), importer(limits))
            assertEquals(ItemOutcome.Failed(0, ImportFailure.TOO_LARGE), report.outcomes.single(), "claim $claim")
            assertEquals(0, rowCount())
            assertNoBlobs()
        }
    }

    @Test
    fun theLimitIsExactlyTheConfiguredNumberOfBytes() {
        val limits = ImportLimits(maxPdfBytes = 5000)
        val atLimit = serve("at", ByteArray(5000) { 1 })
        val overLimit = serve("over", ByteArray(5001) { 1 })
        saved(commitAll(batch(pendingStream(atLimit, ItemKind.PDF)), importer(limits)).outcomes.single())
        val refused = commitAll(batch(pendingStream(overLimit, ItemKind.PDF)), importer(limits))
        assertEquals(ItemOutcome.Failed(0, ImportFailure.TOO_LARGE), refused.outcomes.single())
        assertEquals(1, rowCount())
        assertEquals(1, blobNames().size)
    }

    @Test
    fun aClaimedTextTypeCannotLiftTheTextFileLimit() {
        val limits = ImportLimits(maxTextFileBytes = 1000, maxOtherBytes = 100_000)
        val uri = serve("lie", ByteArray(5000), Entry(mime = "text/plain"))
        val item = assertIs<PendingItem.Stream>(picked(uri).items.single())
        assertEquals(ItemKind.TEXT_FILE, item.kind)
        val report = commitAll(PendingBatch(ImportMechanism.DOCUMENT_PICKER, listOf(item), null), importer(limits))
        assertEquals(ItemOutcome.Failed(0, ImportFailure.TOO_LARGE), report.outcomes.single())
        assertNoBlobs()
    }

    @Test
    fun anEndlessSourceIsStoppedByTheLimitAndLeavesNothing() {
        val uri = serveStream("endless") {
            object : InputStream() {
                override fun read(): Int = 9
            }
        }
        val report = commitAll(batch(pendingStream(uri, ItemKind.OTHER)), importer(ImportLimits(maxOtherBytes = 200_000)))
        assertEquals(ItemOutcome.Failed(0, ImportFailure.TOO_LARGE), report.outcomes.single())
        assertEquals(0, rowCount())
        assertNoBlobs()
    }

    // Revoked grants and failing sources.

    @Test
    fun aGrantRevokedMidCopyIsAccessDeniedAndLeavesNothing() {
        val uri = serveStream("revoked") { RevokedAfter(good = 70_000) }
        val report = commitAll(batch(pendingStream(uri, ItemKind.OTHER)))
        assertEquals(ItemOutcome.Failed(0, ImportFailure.ACCESS_DENIED), report.outcomes.single())
        assertEquals(0, rowCount())
        assertNoBlobs()
    }

    @Test
    fun aSourceThatDisappearsMidCopyIsUnreadableAndLeavesNothing() {
        val uri = serveStream("vanishes") { VanishesAfter(good = 70_000) }
        val report = commitAll(batch(pendingStream(uri, ItemKind.OTHER)))
        assertEquals(ItemOutcome.Failed(0, ImportFailure.UNREADABLE), report.outcomes.single())
        assertEquals(0, rowCount())
        assertNoBlobs()
    }

    @Test
    fun aRuntimeFailureOfTheSourceMidCopyLeavesNothing() {
        val uri = serveStream("unsupported") { UnsupportedAfter(good = 1000) }
        val report = commitAll(batch(pendingStream(uri, ItemKind.OTHER)))
        assertEquals(ItemOutcome.Failed(0, ImportFailure.STORAGE_ERROR), report.outcomes.single())
        assertEquals(0, rowCount())
        assertNoBlobs()
    }

    @Test
    fun aFailureOfTheKeyWrapLeavesNoRowAndNoFileEvenAfterTheBlobWasWritten() {
        val broken = newVault(BrokenWrapper())
        try {
            val id = runBlocking { broken.cases.create("Synthetic broken").id }
            val uri = serve("wrap", ByteArray(70_000))
            val report = runBlocking { importer(vault = broken).commit(id, batch(pendingStream(uri, ItemKind.OTHER)), setOf(0)) }
            assertEquals(ItemOutcome.Failed(0, ImportFailure.KEY_UNAVAILABLE), report.outcomes.single())
            assertEquals(0, runBlocking { broken.evidence.observeForCase(id).first() }.size)
            assertNoBlobs()
        } finally {
            broken.close()
        }
    }

    // Forged share intents.

    private fun forgedSend(uri: Uri, type: String? = "image/png"): Intent =
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uri).setType(type)

    @Test
    fun aForgedIntentNamingAContentUriWithoutAGrantImportsNothing() {
        val uri = serveStream("forged") { throw SecurityException("synthetic: no grant for this uri") }
        val parsed = assertNotNull(IntentReader.read(forgedSend(uri), resolver, "synthetic.attacker"))
        val item = assertIs<PendingItem.Stream>(parsed.items.single())
        assertEquals("synthetic.attacker", parsed.referrerClaim)

        val report = commitAll(parsed)

        assertEquals(ItemOutcome.Failed(item.index, ImportFailure.ACCESS_DENIED), report.outcomes.single())
        assertEquals(0, rowCount())
        assertNoBlobs()
    }

    @Test
    fun aForgedIntentNamingAFilePathIsRejectedAndTheFileIsNeverRead() {
        val secret = File(context.noBackupFilesDir, "synthetic-private-file.bin")
        secret.writeBytes("synthetic-marker-private-file".toByteArray())
        val uris = listOf(
            Uri.fromFile(secret),
            Uri.parse("file:///data/data/${context.packageName}/no_backup/vault/db.key.wrapped"),
            Uri.parse("android.resource://${context.packageName}/raw/synthetic"),
            Uri.parse("http://example.invalid/synthetic.png"),
            Uri.parse("intent:#Intent;end"),
        )
        for (uri in uris) {
            var opened = false
            shadowOpenTracker(uri) { opened = true }
            val parsed = assertNotNull(IntentReader.read(forgedSend(uri), resolver, null))
            assertEquals(PendingItem.Rejected(0, Rejection.UNSUPPORTED_SCHEME), parsed.items.single(), uri.toString())
            val report = commitAll(parsed)
            assertEquals(ItemOutcome.Skipped(0, Rejection.UNSUPPORTED_SCHEME), report.outcomes.single())
            assertTrue(!opened, "the importer opened $uri")
        }
        assertEquals(0, rowCount())
        assertNoBlobs()
    }

    @Test
    fun aForgedIntentWithTextAndAStreamKeepsEachItemSeparate() {
        val uri = serve("forged-both", ByteArray(20) { 4 })
        val intent = forgedSend(uri, "text/plain").putExtra(Intent.EXTRA_TEXT, "synthetic forged text")
        val parsed = assertNotNull(IntentReader.read(intent, resolver, "synthetic.attacker"))
        assertEquals(2, parsed.items.size)
        val report = commitAll(parsed)
        assertEquals(2, report.outcomes.count { it is ItemOutcome.Saved })
        assertEquals(2, rowCount())
    }

    private fun shadowOpenTracker(uri: Uri, onOpen: () -> Unit) {
        shadowOf(resolver).registerInputStreamSupplier(uri) {
            onOpen()
            ByteArrayInputStream(ByteArray(1))
        }
    }

    // Damaged media is preserved as received.

    @Test
    fun truncatedAndCorruptedMediaIsPreservedExactlyAndOnlyClassifiedByItsHead() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val pdf = "%PDF-1.7\n1 0 obj\n<<".toByteArray()
        val noise = ByteArray(2048) { (it * 31 + 7).toByte() }
        val cases = listOf(
            Triple("truncated png", png, "image/png"),
            Triple("truncated jpeg", jpeg, "image/jpeg"),
            Triple("truncated pdf", pdf, "application/pdf"),
            Triple("noise", noise, null),
        )
        val items = cases.mapIndexed { i, (_, bytes, _) ->
            pendingStream(serve("damaged$i", bytes), ItemKind.OTHER, index = i)
        }
        val report = commitAll(batch(*items.toTypedArray()))
        cases.forEachIndexed { i, (label, bytes, detected) ->
            val outcome = saved(report.outcomes[i])
            assertEquals(detected, outcome.detectedMime, label)
            assertContentEquals(bytes, original(outcome.evidenceId), label)
            assertEquals(sha256Hex(bytes), outcome.sha256, label)
        }
    }

    // Plaintext leaks (JVM approximation of the "grep the app data" check; see PlaintextLeakScanTest in analysis).

    @Test
    fun noFileUnderTheAppDataDirectoryHoldsImportedTextAndNothingIsLogged() {
        ShadowLog.reset()
        val marker = "synthetic-marker-importer-7c1e"
        val markers = listOf(marker, "$marker-ml-കൊള്ളില്ല", "$marker-hi-बेकार")
        val needles = markers.flatMap { listOf(it.toByteArray(Charsets.UTF_8), it.toByteArray(Charsets.UTF_16LE), it.toByteArray(Charsets.UTF_16BE)) }
        val items = markers.mapIndexed { i, text -> PendingItem.Text(i, "synthetic pasted $text") }
        val file = serve("leak-file", markers.joinToString("\n").toByteArray(Charsets.UTF_8), Entry(mime = "text/plain", name = "$marker.txt"))
        val stream = pendingStream(file, ItemKind.TEXT_FILE, index = items.size, mime = "text/plain")
        commitAll(batch(*items.toTypedArray(), stream, mechanism = ImportMechanism.SHARE_SEND_MULTIPLE))
        runBlocking { importer().commitNote(caseId, ManualNote("synthetic note ${markers[1]}", null, marker, null, ViewOnceStatus.UNKNOWN, ContentAvailability.NOT_APPLICABLE)) }

        assertEquals(markers.size + 1 + 1, rowCount())
        val files = context.dataDir.walkTopDown().filter { it.isFile }.toList()
        assertTrue(files.isNotEmpty())
        for (scanned in files) {
            val bytes = scanned.readBytes()
            for (needle in needles) {
                assertTrue(!containsBytes(bytes, needle), "marker found in ${scanned.name}")
            }
        }
        val logged = ShadowLog.getLogs()
        assertTrue(logged.none { it.msg.orEmpty().contains(marker) || it.tag.orEmpty().contains(marker) }, "marker found in the log")
        assertEquals(emptyList(), logged.filter { it.tag.orEmpty().startsWith("org.sakshi") || it.tag.orEmpty().startsWith("Sakshi") }.map { it.tag })
    }

    private fun containsBytes(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty() || haystack.size < needle.size) return false
        for (start in 0..haystack.size - needle.size) {
            if (needle.indices.all { haystack[start + it] == needle[it] }) return true
        }
        return false
    }

    /** Delivers [good] bytes and then reports that the URI grant was revoked. */
    private class RevokedAfter(private val good: Int) : InputStream() {
        private var served = 0

        override fun read(): Int {
            if (served >= good) throw SecurityException("synthetic: grant revoked")
            served++
            return 3
        }
    }

    /** Delivers [good] bytes and then reports that the file is gone. */
    private class VanishesAfter(private val good: Int) : InputStream() {
        private var served = 0

        override fun read(): Int {
            if (served >= good) throw FileNotFoundException("synthetic: source removed")
            served++
            return 3
        }
    }

    /** Delivers [good] bytes and then fails with a runtime exception that is not about access. */
    private class UnsupportedAfter(private val good: Int) : InputStream() {
        private var served = 0

        override fun read(): Int {
            if (served >= good) throw UnsupportedOperationException("synthetic")
            served++
            return 3
        }
    }
}
