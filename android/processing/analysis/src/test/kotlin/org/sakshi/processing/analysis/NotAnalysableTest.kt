package org.sakshi.processing.analysis

import java.io.File
import java.io.RandomAccessFile
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.sakshi.core.vault.AcquisitionKind
import org.sakshi.processing.text.DateOrder

class NotAnalysableTest : AnalysisTestBase() {
    private val pngHead = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(64) { 7 }

    private fun refusal(evidenceId: String): NotAnalysableReason = runBlocking {
        assertIs<AnalysisOutcome.NotAnalysable>(analysis.analyse(evidenceId)).reason
    }

    private fun assertNothingWritten(evidenceId: String) = runBlocking<Unit> {
        assertTrue(events().isEmpty())
        assertTrue(vault.derivatives.listForEvidence(evidenceId).isEmpty())
    }

    @Test
    fun imageIsPreserveOnly() {
        val id = importBytes(pngHead, declaredMime = "image/png", kind = AcquisitionKind.SELECTED_VISUAL_MEDIA)
        assertEquals(NotAnalysableReason.PRESERVE_ONLY_TYPE, refusal(id))
        assertNothingWritten(id)
    }

    @Test
    fun pdfDeclaredAsTextIsRefusedBecauseDetectedTypeWins() {
        val id = importBytes("%PDF-1.7 synthetic".toByteArray(), declaredMime = "text/plain")
        assertEquals(NotAnalysableReason.PRESERVE_ONLY_TYPE, refusal(id))
        assertNothingWritten(id)
    }

    @Test
    fun streamWithoutTextTypeIsPreserveOnly() {
        val id = importBytes("synthetic".toByteArray(), declaredMime = "application/octet-stream", kind = AcquisitionKind.SHARED_STREAM)
        assertEquals(NotAnalysableReason.PRESERVE_ONLY_TYPE, refusal(id))
    }

    @Test
    fun manualNoteIsNotAnalysedHere() {
        val id = importBytes("synthetic note".toByteArray(), kind = AcquisitionKind.MANUAL_NOTE)
        assertEquals(NotAnalysableReason.MANUAL_NOTE, refusal(id))
        assertNothingWritten(id)
    }

    @Test
    fun aNotesWordsAreNeverRunThroughCueMatching() {
        val id = importBytes("you are worthless, send nudes or I will kill you".toByteArray(), kind = AcquisitionKind.MANUAL_NOTE)
        assertEquals(NotAnalysableReason.MANUAL_NOTE, refusal(id))
        assertNothingWritten(id)
        // Searching a note's words (when wired) reads them directly; it never creates events, derivatives or findings.
        assertTrue(runBlocking { vault.search.search(org.sakshi.core.model.CaseId(caseId), "worthless").hits.isEmpty() })
    }

    @Test
    fun oversizeTextIsRefusedBeforeReading() {
        val small = TextAnalysis(vault, RulesEngineFactory.default(), clock, ids, AnalysisLimits(maxTextBytes = 10))
        val id = importText("this text is longer than ten bytes")
        val outcome = runBlocking { small.analyse(id) }
        assertEquals(AnalysisOutcome.NotAnalysable(NotAnalysableReason.TOO_LARGE), outcome)
        assertNothingWritten(id)
    }

    @Test
    fun missingEvidenceIsReported() {
        assertEquals(NotAnalysableReason.EVIDENCE_MISSING, refusal("synthetic-missing"))
    }

    @Test
    fun binaryOrEmptyBytesAreNotText() {
        val binary = importBytes(byteArrayOf(0x61, 0x00, 0x62))
        val empty = importBytes(ByteArray(0))
        val badUtf8 = importBytes(byteArrayOf(0x61, 0xC3.toByte(), 0x28))
        assertEquals(NotAnalysableReason.NOT_TEXT, refusal(binary))
        assertEquals(NotAnalysableReason.NOT_TEXT, refusal(empty))
        assertEquals(NotAnalysableReason.NOT_TEXT, refusal(badUtf8))
        assertTrue(events().isEmpty())
    }

    @Test
    fun tamperedBlobIsUnreadableAndNothingIsWritten() {
        val id = importText("synthetic text that will be damaged ".repeat(10))
        val blob = File(context.noBackupFilesDir, "vault/blobs").listFiles().orEmpty().single()
        RandomAccessFile(blob, "rw").use { file ->
            val position = file.length() - 5
            file.seek(position)
            val original = file.read()
            file.seek(position)
            file.write(original xor 0xFF)
        }
        assertEquals(NotAnalysableReason.UNREADABLE, refusal(id))
        assertNothingWritten(id)
    }

    @Test
    fun secondRunIsRefusedAsAlreadyAnalysedAndAddsNothing() = runBlocking<Unit> {
        val id = importText(SyntheticExports.EIGHT_MESSAGES)
        val options = ExportOptions(DateOrder.DAY_MONTH, ZoneId.of("UTC"), null)
        assertIs<AnalysisOutcome.Analysed>(analysis.analyse(id, options))
        val before = events().size
        assertEquals(NotAnalysableReason.ALREADY_ANALYSED, refusal(id))
        assertEquals(before, events().size)
        assertEquals(1, vault.derivatives.listForEvidence(id).size)
    }

    @Test
    fun secondRunOfPlainTextIsAlsoRefused() = runBlocking<Unit> {
        val id = importText("synthetic plain note")
        assertIs<AnalysisOutcome.Analysed>(analysis.analyse(id))
        assertEquals(NotAnalysableReason.ALREADY_ANALYSED, refusal(id))
        assertEquals(1, events().size)
    }
}
