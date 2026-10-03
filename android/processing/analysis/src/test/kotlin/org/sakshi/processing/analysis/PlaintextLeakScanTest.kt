package org.sakshi.processing.analysis

import java.io.File
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.robolectric.shadows.ShadowLog
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.CategoryReviewStatus
import org.sakshi.core.model.EventId
import org.sakshi.core.temporal.EvidenceView
import org.sakshi.core.vault.ReviewResult
import org.sakshi.processing.text.DateOrder

/**
 * The JVM approximation of the megaplan 27.3 check "grep the app data directory and logcat for fixture strings
 * after a full workflow".
 *
 * Limit that matters: under Robolectric the vault database is a plain in-memory SQLite database, so there is no
 * database file here and nothing in this test says anything about encryption of the database at rest. That part
 * is a device test (`PlaintextLeakDeviceTest`). What this test does cover is every file the code writes outside
 * the database: the blob files, and anything else that might be created in the app's data, files, no-backup,
 * cache, code-cache or external directories, plus the Robolectric log. The report builder and exporter are in
 * another module and are not part of this workflow.
 */
class PlaintextLeakScanTest : AnalysisTestBase() {
    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    private val tag = "synthetic-marker-leak-5d2a"
    private val malayalam = "നീ ഒന്നിനും കൊള്ളില്ല"
    private val devanagari = "तुम बेकार हो"
    private val sender = "synthetic-sender-$tag"

    private val markers = listOf(
        "$tag-en", "$tag-ml", "$tag-hi", "synthetic-case-title-$tag", sender, malayalam, devanagari,
    )

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

    private fun runWorkflow() = runBlocking {
        vault.cases.create("synthetic-case-title-$tag")
        val exportId = importText(export)
        assertIs<AnalysisOutcome.NeedsExportOptions>(analysis.analyse(exportId))
        val options = ExportOptions(DateOrder.DAY_MONTH, zone, "synthetic-owner")
        assertIs<AnalysisOutcome.Analysed>(analysis.analyse(exportId, options))
        assertIs<AnalysisOutcome.Analysed>(analysis.analyse(importText("$tag-en pasted: you are worthless")))

        val withCategory = events().first { it.categories.isNotEmpty() }
        assertIs<ReviewResult.Applied>(vault.review.reviewCategory(EventId(withCategory.eventId.value), 0, CategoryReviewStatus.ACCEPTED))
        CasePatterns(vault, clock).refresh(CaseId(caseId), EvidenceView.CONFIRMED_ONLY, zone)
        val hits = vault.search.search(CaseId(caseId), "$tag-ml").hits
        assertTrue(hits.isNotEmpty(), "the workflow must really have stored the marker for the scan to mean anything")
    }

    private fun appFiles(): List<File> {
        val roots = listOfNotNull(
            context.dataDir, context.filesDir, context.noBackupFilesDir, context.cacheDir, context.codeCacheDir,
            context.getExternalFilesDir(null), context.externalCacheDir,
        )
        return roots.flatMap { root -> root.walkTopDown().filter { it.isFile }.toList() }.distinctBy { it.absolutePath }
    }

    private fun contains(haystack: ByteArray, needle: ByteArray): Boolean {
        for (start in 0..haystack.size - needle.size) {
            if (needle.indices.all { haystack[start + it] == needle[it] }) return true
        }
        return false
    }

    private fun filesHoldingAMarker(): List<String> =
        appFiles().filter { file -> file.readBytes().let { bytes -> needles.any { contains(bytes, it) } } }.map { it.name }

    @Test
    fun theScannerFindsAPlaintextControlFile() {
        val control = File(context.cacheDir, "synthetic-control.txt")
        control.writeText("synthetic $tag-en control")
        try {
            assertEquals(listOf("synthetic-control.txt"), filesHoldingAMarker())
        } finally {
            control.delete()
        }
    }

    @Test
    fun noFileOutsideTheDatabaseHoldsAMarkerAfterAFullWorkflow() {
        ShadowLog.reset()
        runWorkflow()

        val files = appFiles()
        assertTrue(files.isNotEmpty(), "the workflow wrote no file at all")
        assertEquals(emptyList(), filesHoldingAMarker())
        val blobPattern = Regex("[0-9a-f]{32}\\.skb")
        val unexpected = files.map { it.name }.filterNot { blobPattern.matches(it) }
        assertEquals(emptyList(), unexpected, "only encrypted blob files may exist when the database is in memory")
        assertEquals(2, files.size, "one blob per imported evidence item")
    }

    @Test
    fun nothingIsLoggedByTheWorkflow() {
        ShadowLog.reset()
        runWorkflow()
        val logged = ShadowLog.getLogs()
        for (item in logged) {
            val text = "${item.tag.orEmpty()} ${item.msg.orEmpty()} ${item.throwable?.message.orEmpty()}"
            assertTrue(markers.none { text.contains(it) }, "a log entry from ${item.tag} holds a marker")
        }
        assertEquals(emptyList(), logged.filter { it.tag.orEmpty().startsWith("org.sakshi") || it.tag.orEmpty().startsWith("Sakshi") }.map { it.tag })
    }
}
