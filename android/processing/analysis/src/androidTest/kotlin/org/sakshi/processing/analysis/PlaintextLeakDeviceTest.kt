package org.sakshi.processing.analysis

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.CategoryReviewStatus
import org.sakshi.core.model.EventId
import org.sakshi.core.temporal.EvidenceView
import org.sakshi.core.vault.AccessClass
import org.sakshi.core.vault.AcquisitionKind
import org.sakshi.core.vault.ImportRequest
import org.sakshi.core.vault.KeystoreKeyWrapper
import org.sakshi.core.vault.ReviewResult
import org.sakshi.core.vault.Vault
import org.sakshi.processing.text.DateOrder

/**
 * Megaplan 27.3 "grep of the app data directory and logcat for fixture strings after a full workflow" on a real
 * device: the real SQLCipher vault, the real Keystore wrapper, import, analysis, review, patterns and search, then
 * every file under the package sandbox (database, journal and WAL side files, blobs, cache, code cache, external
 * app directories) is scanned for synthetic markers in English, Malayalam and Devanagari as UTF-8 and UTF-16, and
 * this process's own logcat is read back. All data is synthetic and stays in the test package sandbox.
 */
class PlaintextLeakDeviceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val wrapper = KeystoreKeyWrapper(alias = "synthetic-leak-${UUID.randomUUID()}", requireUserAuthentication = false)
    private var vault: Vault? = null
    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    private val tag = "synthetic-marker-leak-device-9b41"
    private val malayalam = "നീ ഒന്നിനും കൊള്ളില്ല"
    private val devanagari = "तुम बेकार हो"
    private val sender = "synthetic-sender-$tag"
    private val markers = listOf("$tag-en", "$tag-ml", "$tag-hi", "synthetic-case-title-$tag", sender, malayalam, devanagari)
    private val needles: List<ByteArray> = markers.flatMap {
        listOf(it.toByteArray(Charsets.UTF_8), it.toByteArray(Charsets.UTF_16LE), it.toByteArray(Charsets.UTF_16BE))
    }

    private val export = """
        24/09/2026, 21:03 - $sender: hello $tag-en
        24/09/2026, 21:05 - synthetic-owner: please stop sending these
        25/09/2026, 08:15 - $sender: $tag-en you are an idiot
        25/09/2026, 08:20 - $sender: $tag-ml $malayalam
        25/09/2026, 08:21 - $sender: $tag-hi $devanagari
        26/09/2026, 09:00 - $sender: I will hurt you if you reply
        26/09/2026, 09:30 - synthetic-owner: no more messages
    """.trimIndent() + "\n"

    @After
    fun cleanUp() {
        runCatching { vault?.close() }
        runCatching { wrapper.delete() }
        File(context.noBackupFilesDir, "vault").deleteRecursively()
    }

    private fun importText(opened: Vault, caseId: String, text: String): String = runBlocking {
        val request = ImportRequest(
            caseId, AcquisitionKind.SHARED_TEXT, AccessClass.USER_MEDIATED, "synthetic-device-test",
            "text/plain", "synthetic-origin", "synthetic.txt", null, MAX_BYTES,
        )
        opened.evidence.import(request, ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))).id
    }

    private fun runWorkflow(opened: Vault) = runBlocking {
        val case = opened.cases.create("synthetic-case-title-$tag")
        val clock: () -> Instant = Instant::now
        val analysis = TextAnalysis(opened, RulesEngineFactory.default(), clock, { UUID.randomUUID().toString() })
        val exportId = importText(opened, case.id, export)
        assertIs<AnalysisOutcome.NeedsExportOptions>(analysis.analyse(exportId))
        val options = ExportOptions(DateOrder.DAY_MONTH, zone, "synthetic-owner")
        assertIs<AnalysisOutcome.Analysed>(analysis.analyse(exportId, options))
        assertIs<AnalysisOutcome.Analysed>(analysis.analyse(importText(opened, case.id, "$tag-en pasted: you are worthless")))

        val events = opened.events.loadLatest(CaseId(case.id), Instant.ofEpochMilli(Long.MAX_VALUE))
        val withCategory = events.first { it.categories.isNotEmpty() }
        assertIs<ReviewResult.Applied>(opened.review.reviewCategory(EventId(withCategory.eventId.value), 0, CategoryReviewStatus.ACCEPTED))
        CasePatterns(opened, clock).refresh(CaseId(case.id), EvidenceView.CONFIRMED_ONLY, zone)
        assertTrue(opened.search.search(CaseId(case.id), "$tag-ml").hits.isNotEmpty(), "the marker must really be stored and searchable")
    }

    private fun sandboxFiles(): List<File> {
        val roots = listOfNotNull(
            context.dataDir, context.filesDir, context.noBackupFilesDir, context.cacheDir, context.codeCacheDir,
            context.getExternalFilesDir(null), context.externalCacheDir,
        )
        return roots.flatMap { root -> root.walkTopDown().filter { it.isFile }.toList() }.distinctBy { it.absolutePath }
    }

    private fun contains(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty() || haystack.size < needle.size) return false
        val last = haystack.size - needle.size
        for (start in 0..last) {
            if (haystack[start] == needle[0] && needle.indices.all { haystack[start + it] == needle[it] }) return true
        }
        return false
    }

    private fun filesHoldingAMarker(): List<String> =
        sandboxFiles().filter { file -> file.readBytes().let { bytes -> needles.any { contains(bytes, it) } } }.map { it.absolutePath }

    private fun scanAll(stage: String) {
        val files = sandboxFiles()
        assertTrue(files.any { it.name == "sakshi.db" }, "the encrypted database must exist ($stage)")
        assertEquals(emptyList(), filesHoldingAMarker(), "files holding a marker ($stage)")
        record("leak_scan", "stage" to stage, "files_scanned" to files.size, "bytes_scanned" to files.sumOf { it.length() })
    }

    @Test
    fun theScannerFindsAPlaintextControlFile() {
        val control = File(context.cacheDir, "synthetic-control-leak.txt")
        control.writeText("synthetic $tag-en control")
        try {
            assertTrue(filesHoldingAMarker().contains(control.absolutePath))
        } finally {
            control.delete()
        }
    }

    @Test
    fun noFileInTheSandboxHoldsAMarkerAfterAFullWorkflowOpenOrClosed() {
        val opened = Vault.open(context, wrapper).also { vault = it }
        runWorkflow(opened)
        scanAll("vault open")
        opened.close()
        vault = null
        scanAll("vault closed")
    }

    @Test
    fun theOwnLogcatHoldsNoMarkerAfterAFullWorkflow() {
        val opened = Vault.open(context, wrapper).also { vault = it }
        runWorkflow(opened)
        val process = ProcessBuilder("logcat", "-d", "-v", "threadtime").redirectErrorStream(true).start()
        val log = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        process.waitFor()
        assertTrue(log.isNotEmpty(), "logcat returned nothing, so the check would be vacuous")
        for (marker in markers) {
            assertFalse(log.contains(marker), "logcat holds a marker")
        }
        record("logcat_scan", "chars" to log.length)
    }

    private companion object {
        const val MAX_BYTES = 20_000_000L
    }
}
