package org.sakshi.app.evidence

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.sakshi.acquisition.importer.ImportMechanism
import org.sakshi.acquisition.importer.PendingBatch
import org.sakshi.acquisition.importer.PendingItem
import org.sakshi.app.support.VaultTestBase

class CaseDetailViewModelTest : VaultTestBase() {
    private fun modelFor(caseId: String) = CaseDetailViewModel(caseId, vault.cases, vault.evidence, scope)

    private fun save(caseId: String, vararg items: PendingItem, mechanism: ImportMechanism = ImportMechanism.SHARE_SEND_MULTIPLE) {
        val batch = PendingBatch(mechanism, items.toList(), referrerClaim = null)
        runBlocking { importer.commit(caseId, batch, items.map { it.index }.toSet()) }
    }

    @Test
    fun theListReflectsImportsWithKindAndAnalysisLabel() {
        val caseId = newCase("synthetic title")
        val model = modelFor(caseId)
        assertTrue(await(model.uiState) { it.loaded }.items.isEmpty())

        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(32)
        save(
            caseId,
            PendingItem.Text(0, "synthetic text"),
            streamItem(1, serve("image", png)),
            streamItem(2, serve("blob", ByteArray(10)), mime = "application/octet-stream"),
        )
        val state = await(model.uiState) { it.items.size == 3 }
        assertEquals("synthetic title", state.title)
        assertEquals(setOf(EvidenceKind.TEXT, EvidenceKind.IMAGE, EvidenceKind.FILE), state.items.map { it.kind }.toSet())
        assertEquals(
            setOf(AnalysisLabel.WAITING_FOR_TEXT, AnalysisLabel.NOT_ANALYSED),
            state.items.map { it.analysis }.toSet(),
        )
        assertEquals(setOf("synthetic text".length.toLong(), png.size.toLong(), 10L), state.items.map { it.byteSize }.toSet())
    }

    @Test
    fun aDeclaredTextFileIsATextFile() {
        val caseId = newCase()
        save(caseId, streamItem(0, serve("t", "synthetic".toByteArray()), mime = "text/plain"))
        val row = await(modelFor(caseId).uiState) { it.items.size == 1 }.items.single()
        assertEquals(EvidenceKind.TEXT_FILE, row.kind)
        assertEquals(AnalysisLabel.WAITING_FOR_TEXT, row.analysis)
    }

    @Test
    fun verifyReportsIntactForAnUntouchedItem() {
        val caseId = newCase()
        save(caseId, PendingItem.Text(0, "synthetic text"))
        val model = modelFor(caseId)
        val id = await(model.uiState) { it.items.size == 1 }.items.single().id
        model.checkIntegrity(id)
        assertEquals(IntegrityStatus.INTACT, await(model.uiState) { it.integrity[id] == IntegrityStatus.INTACT }.integrity[id])
    }

    @Test
    fun verifyReportsATamperedBlobAsNotAuthenticated() {
        val caseId = newCase()
        save(caseId, streamItem(0, serve("big", ByteArray(6000) { 3 })))
        val model = modelFor(caseId)
        val id = await(model.uiState) { it.items.size == 1 }.items.single().id

        val blob = File(context.noBackupFilesDir, "vault/blobs").listFiles().orEmpty().single()
        val bytes = blob.readBytes()
        bytes[40] = (bytes[40].toInt() xor 1).toByte()
        blob.writeBytes(bytes)

        model.checkIntegrity(id)
        val status = await(model.uiState) { it.integrity[id].let { s -> s != null && s != IntegrityStatus.CHECKING } }.integrity[id]
        assertEquals(IntegrityStatus.NOT_AUTHENTICATED, status)
        assertEquals(1, evidenceCount(caseId))
    }

    @Test
    fun verifyReportsAMissingFile() {
        val caseId = newCase()
        save(caseId, streamItem(0, serve("big", ByteArray(100))))
        val model = modelFor(caseId)
        val id = await(model.uiState) { it.items.size == 1 }.items.single().id
        File(context.noBackupFilesDir, "vault/blobs").listFiles().orEmpty().forEach { it.delete() }
        model.checkIntegrity(id)
        assertEquals(
            IntegrityStatus.FILE_MISSING,
            await(model.uiState) { it.integrity[id].let { s -> s != null && s != IntegrityStatus.CHECKING } }.integrity[id],
        )
    }

    @Test
    fun deleteRemovesTheItemAndItsStoredFile() {
        val caseId = newCase()
        save(caseId, PendingItem.Text(0, "synthetic one"), PendingItem.Text(1, "synthetic two"))
        val model = modelFor(caseId)
        val rows = await(model.uiState) { it.items.size == 2 }.items
        model.delete(rows.first().id)
        val remaining = await(model.uiState) { it.items.size == 1 }.items.single()
        assertEquals(rows.last().id, remaining.id)
        assertEquals(1, File(context.noBackupFilesDir, "vault/blobs").listFiles().orEmpty().size)
    }

    @Test
    fun deletingAnUnknownItemRaisesANoticeInsteadOfCrashing() {
        val model = modelFor(newCase())
        model.delete("synthetic-missing")
        assertEquals(DetailMessage.DELETE_FAILED, await(model.uiState) { it.message != null }.message)
        model.messageShown()
        assertNull(await(model.uiState) { it.message == null }.message)
    }

    @Test
    fun archivedAndMissingCasesAreFlagged() {
        val caseId = newCase()
        runBlocking { vault.cases.archive(caseId) }
        assertTrue(await(modelFor(caseId).uiState) { it.loaded && it.archived }.archived)
        assertNull(await(modelFor("synthetic-missing").uiState) { it.loaded }.title)
    }
}
