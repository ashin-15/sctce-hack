package org.sakshi.core.vault

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Before
import org.sakshi.core.integrity.Sha256

class DerivativeStoreTest : VaultTestBase() {
    private lateinit var recording: RecordingAuditLog
    private lateinit var store: DerivativeStore

    @Before
    fun openStore() {
        recording = RecordingAuditLog(db, clock)
        store = DerivativeStore(db, recording, clock, ids, Dispatchers.IO)
    }

    private suspend fun evidenceIn(caseId: String): String =
        evidence.import(request(caseId), ByteArrayInputStream("synthetic".toByteArray())).id

    private suspend fun derivativeRows(): Int = withContext(Dispatchers.IO) {
        db.query("SELECT COUNT(*) FROM derivative", null).use {
            it.moveToFirst()
            it.getInt(0)
        }
    }

    @Test
    fun revisionsCountPerEvidenceAndKind() = runBlocking<Unit> {
        val caseId = cases.create("Synthetic").id
        val first = evidenceIn(caseId)
        val second = evidenceIn(caseId)

        val ocr1 = store.save(first, DerivativeKind.OCR, "one", "synthetic-tool", "1")
        val ocr2 = store.save(first, DerivativeKind.OCR, "two", "synthetic-tool", "1", parentDerivativeId = ocr1.id)
        val transcript = store.save(first, DerivativeKind.TRANSCRIPT, "t", "synthetic-stt", "2")
        val other = store.save(second, DerivativeKind.OCR, "x", "synthetic-tool", "1")

        assertEquals(listOf(1, 2, 1, 1), listOf(ocr1, ocr2, transcript, other).map { it.revision })
        assertEquals(ocr2, store.latest(first, DerivativeKind.OCR))
        assertEquals(ocr1, store.get(ocr1.id))
        assertEquals(listOf(ocr1, ocr2, transcript), store.listForEvidence(first))
        assertEquals(ocr1.id, ocr2.parentDerivativeId)
        assertNull(store.latest(first, DerivativeKind.PARSED_TEXT))
        assertNull(store.get("synthetic-missing"))
    }

    @Test
    fun textAndMetadataAreStoredExactly() = runBlocking<Unit> {
        val evidenceId = evidenceIn(cases.create("Synthetic").id)
        val text = "സിന്തറ്റിക് \r\nहिन्दी परीक्षण\r\n😀 é end \n"
        val saved = store.save(evidenceId, DerivativeKind.PARSED_TEXT, text, "synthetic-parser", "3", null, "{\"map\":1}", "{\"q\":0.5}")

        val loaded = store.get(saved.id)
        assertEquals(saved, loaded)
        assertEquals(text, loaded?.text)
        assertEquals("{\"map\":1}", loaded?.sourceMapJson)
        assertEquals("{\"q\":0.5}", loaded?.qualityJson)
        assertEquals("synthetic-parser", loaded?.toolId)
        assertEquals("3", loaded?.toolVersion)
    }

    @Test
    fun invalidInputsWriteNothing() = runBlocking<Unit> {
        val caseId = cases.create("Synthetic").id
        val evidenceId = evidenceIn(caseId)
        val foreign = store.save(evidenceIn(caseId), DerivativeKind.OCR, "t", "tool", "1")

        assertFailsWith<IllegalArgumentException> { store.save("synthetic-missing", DerivativeKind.OCR, "t", "tool", "1") }
        assertFailsWith<IllegalArgumentException> { store.save(evidenceId, "unknown_kind", "t", "tool", "1") }
        assertFailsWith<IllegalArgumentException> { store.save(evidenceId, DerivativeKind.OCR, "t", "tool", "1", "synthetic-missing") }
        assertFailsWith<IllegalArgumentException> { store.save(evidenceId, DerivativeKind.OCR, "t", "tool", "1", foreign.id) }

        assertEquals(1, derivativeRows())
        assertEquals(1, recording.calls.count { it.startsWith("derivative.saved|") })
    }

    @Test
    fun saveAppendsOneAuditRowWithIdsAndHashOnly() = runBlocking<Unit> {
        val evidenceId = evidenceIn(cases.create("Synthetic").id)
        val text = "SYNTHETIC-SECRET-TEXT 😀"
        val saved = store.save(evidenceId, DerivativeKind.USER_EDIT, text, "tool", "1")

        val row = recording.calls.single { it.startsWith("derivative.saved|") }
        val hash = Sha256.hex(Sha256.digest(text.toByteArray(Charsets.UTF_8)))
        assertEquals(
            "derivative.saved|derivative|${saved.id}|{\"derivative_id\":\"${saved.id}\",\"evidence_id\":\"$evidenceId\"," +
                "\"kind\":\"user_edit\",\"revision\":1,\"sha256\":\"$hash\"}",
            row,
        )
        assertEquals(false, row.contains("SECRET"))
    }

    @Test
    fun codePointLengthCountsEmojiOnce() = runBlocking<Unit> {
        val evidenceId = evidenceIn(cases.create("Synthetic").id)
        val saved = store.save(evidenceId, DerivativeKind.NORMALISED_VIEW, "a😀b", "tool", "1")

        assertEquals(3, store.codePointLength(saved.id))
        assertEquals(4, saved.text.length)
        assertNull(store.codePointLength("synthetic-missing"))
    }

    @Test
    fun derivativesGoWhenTheirEvidenceIsDeleted() = runBlocking<Unit> {
        val evidenceId = evidenceIn(cases.create("Synthetic").id)
        val saved = store.save(evidenceId, DerivativeKind.OCR, "t", "tool", "1")

        evidence.delete(evidenceId)

        assertNull(store.get(saved.id))
        assertEquals(emptyList(), store.listForEvidence(evidenceId))
    }

    @Test
    fun regionsAreStoredWithTheCallersIdsInOneAuditedSave() = runBlocking<Unit> {
        val evidenceId = evidenceIn(cases.create("Synthetic").id)
        val regions = listOf(
            RegionDraft("synthetic-region-b", 0, "[[0,0],[4,0],[4,2],[0,2]]", "{\"rotation_degrees\":90}"),
            RegionDraft("synthetic-region-a", 0, "[[0,3],[4,3],[4,5],[0,5]]"),
        )
        val saved = store.saveWithRegions(evidenceId, DerivativeKind.OCR, "ab\ncd", "synthetic-ocr", "1", regions)

        val stored = store.regions(saved.id)
        assertEquals(setOf("synthetic-region-a", "synthetic-region-b"), stored.map { it.id }.toSet())
        assertEquals(setOf(saved.id), stored.map { it.derivativeId }.toSet())
        assertEquals("{\"rotation_degrees\":90}", stored.single { it.id == "synthetic-region-b" }.transformJson)
        assertEquals(1, recording.calls.count { it.startsWith("derivative.saved|") })

        evidence.delete(evidenceId)
        assertEquals(emptyList(), store.regions(saved.id))
    }

    @Test
    fun invalidRegionsWriteNothing() = runBlocking<Unit> {
        val evidenceId = evidenceIn(cases.create("Synthetic").id)
        val duplicate = listOf(RegionDraft("synthetic-r", 0, "[]"), RegionDraft("synthetic-r", 0, "[]"))
        assertFailsWith<IllegalArgumentException> { store.saveWithRegions(evidenceId, DerivativeKind.OCR, "t", "tool", "1", duplicate) }
        assertFailsWith<IllegalArgumentException> {
            store.saveWithRegions(evidenceId, DerivativeKind.OCR, "t", "tool", "1", listOf(RegionDraft(" ", 0, "[]")))
        }
        assertFailsWith<IllegalArgumentException> {
            store.saveWithRegions(evidenceId, DerivativeKind.OCR, "t", "tool", "1", listOf(RegionDraft("synthetic-r", -1, "[]")))
        }
        assertEquals(0, derivativeRows())
    }
}
