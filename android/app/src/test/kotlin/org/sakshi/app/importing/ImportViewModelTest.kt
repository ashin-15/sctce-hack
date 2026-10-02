package org.sakshi.app.importing

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.FileNotFoundException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.sakshi.acquisition.importer.ImportFailure
import org.sakshi.acquisition.importer.ImportMechanism
import org.sakshi.acquisition.importer.ItemOutcome
import org.sakshi.acquisition.importer.PendingBatch
import org.sakshi.acquisition.importer.PendingItem
import org.sakshi.acquisition.importer.Rejection
import org.sakshi.app.support.HookedStream
import org.sakshi.app.support.VaultTestBase
import org.sakshi.core.vault.AcquisitionKind

class ImportViewModelTest : VaultTestBase() {
    private lateinit var model: ImportViewModel
    private val seen = CopyOnWriteArrayList<ImportUiState>()

    @BeforeTest
    fun createModel() {
        model = ImportViewModel(importer, vault.cases, resolver, Dispatchers.Unconfined, scope)
        scope.launch { model.state.collect { seen += it } }
    }

    @AfterTest
    fun clearSeen() = seen.clear()

    private fun batch(vararg items: PendingItem, mechanism: ImportMechanism = ImportMechanism.SHARE_SEND_MULTIPLE) =
        PendingBatch(mechanism, items.toList(), referrerClaim = null)

    private fun previewing(): ImportUiState.Previewing = assertIs(model.state.value)

    /** Sharing reads the case list first, so the preview appears a moment later. */
    private fun share(batch: PendingBatch) {
        model.startShare(batch)
        await(model.state) { it is ImportUiState.Previewing }
    }

    private fun awaitEnd(): ImportUiState = await(model.state) { it is ImportUiState.Finished || it is ImportUiState.Cancelled }

    private fun threeItems(): PendingBatch = batch(
        streamItem(0, serve("a", "synthetic a".toByteArray())),
        streamItem(1, serve("b", "synthetic b".toByteArray())),
        PendingItem.Text(2, "synthetic text"),
    )

    @Test
    fun validItemsStartCheckedAndRejectedOnesCannotBeChecked() {
        val caseId = newCase()
        model.startForCase(
            caseId,
            batch(streamItem(0, serve("a", ByteArray(3))), PendingItem.Rejected(1, Rejection.EMPTY_TEXT), PendingItem.Text(2, "synthetic")),
        )
        assertEquals(setOf(0, 2), previewing().selected)
        model.toggle(1)
        assertEquals(setOf(0, 2), previewing().selected)
        model.toggle(0)
        assertEquals(setOf(2), previewing().selected)
        model.toggle(0)
        assertEquals(setOf(0, 2), previewing().selected)
    }

    @Test
    fun savingNeedsACaseAndAtLeastOneSelectedItem() {
        newCase("synthetic one")
        newCase("synthetic two")
        share(threeItems())
        assertFalse(previewing().canSave)
        model.save()
        assertIs<ImportUiState.Previewing>(model.state.value)

        val second = runBlocking { vault.cases.observe().first() }.active().last().id
        model.chooseCase(second)
        assertTrue(previewing().canSave)
        listOf(0, 1, 2).forEach(model::toggle)
        assertFalse(previewing().canSave)
        model.save()
        assertIs<ImportUiState.Previewing>(model.state.value)
        assertEquals(0, evidenceCount(second))
    }

    @Test
    fun aSingleActiveCaseIsPreselected() {
        val active = newCase("synthetic active")
        val archived = newCase("synthetic archived")
        runBlocking { vault.cases.archive(archived) }
        share(threeItems())
        assertEquals(CaseChoice.Existing(active), previewing().choice)
        assertTrue(previewing().canSave)
    }

    @Test
    fun severalActiveCasesPreselectNothing() {
        newCase("synthetic one")
        newCase("synthetic two")
        share(threeItems())
        assertEquals(CaseChoice.None, previewing().choice)
    }

    @Test
    fun aCaseFromInsideACaseIsFixed() {
        val caseId = newCase()
        newCase("synthetic other")
        model.startForCase(caseId, threeItems())
        assertEquals(CaseChoice.Existing(caseId), previewing().effectiveChoice)
        assertTrue(previewing().canSave)
    }

    @Test
    fun aNewCaseNeedsATitleAndIsCreatedOnlyOnSave() {
        share(threeItems())
        model.chooseNewCase("")
        assertFalse(previewing().canSave)
        model.chooseNewCase("   ")
        assertFalse(previewing().canSave)
        model.chooseNewCase("synthetic fresh")
        assertTrue(previewing().canSave)
        assertTrue(runBlocking { vault.cases.observe().first() }.isEmpty())

        model.save()
        val finished = assertIs<ImportUiState.Finished>(awaitEnd())
        val created = runBlocking { vault.cases.observe().first() }.single()
        assertEquals("synthetic fresh", created.title)
        assertEquals(created.id, finished.caseId)
        assertEquals(3, evidenceCount(created.id))
    }

    @Test
    fun commitSavesOnlySelectedItemsAndReportsOutcomesByIndex() {
        val caseId = newCase()
        model.startForCase(caseId, threeItems())
        model.toggle(1)
        model.save()
        val finished = assertIs<ImportUiState.Finished>(awaitEnd())

        assertEquals(caseId, finished.caseId)
        assertEquals(listOf(0, 2), finished.report.outcomes.map { it.index })
        finished.report.outcomes.forEach { assertIs<ItemOutcome.Saved>(it) }
        assertEquals(2, evidenceCount(caseId))
        assertEquals(setOf(0, 2), finished.labels.keys)
        assertEquals(ItemLabel.File("synthetic-name-0"), finished.labels[0])
        assertEquals(ItemLabel.SharedText, finished.labels[2])
    }

    @Test
    fun progressCountsEachSelectedItem() {
        val caseId = newCase()
        model.startForCase(caseId, threeItems())
        model.save()
        awaitEnd()
        val progress = seen.filterIsInstance<ImportUiState.Saving>().map { it.done to it.total }
        // The last value may be replaced by the result before an observer sees it.
        assertEquals(listOf(0 to 3, 1 to 3, 2 to 3), progress.take(3))
        assertTrue(progress.all { it.second == 3 })
    }

    @Test
    fun aFailingItemDoesNotStopTheRest() {
        val caseId = newCase()
        val missing = serve("gone") { throw FileNotFoundException() }
        model.startForCase(
            caseId,
            batch(streamItem(0, serve("a", ByteArray(5))), streamItem(1, missing), PendingItem.Text(2, "synthetic")),
        )
        model.save()
        val outcomes = assertIs<ImportUiState.Finished>(awaitEnd()).report.outcomes
        assertIs<ItemOutcome.Saved>(outcomes[0])
        assertEquals(ImportFailure.UNREADABLE, assertIs<ItemOutcome.Failed>(outcomes[1]).reason)
        assertIs<ItemOutcome.Saved>(outcomes[2])
        assertEquals(2, evidenceCount(caseId))
    }

    @Test
    fun cancellingMidWayKeepsWhatWasAlreadySaved() {
        val caseId = newCase()
        val slow = serve("slow") { HookedStream(ByteArray(20_000) { 1 }) { model.cancelSaving() } }
        model.startForCase(caseId, batch(streamItem(0, serve("a", ByteArray(5))), streamItem(1, slow), PendingItem.Text(2, "synthetic")))
        model.save()
        val cancelled = assertIs<ImportUiState.Cancelled>(awaitEnd())

        assertEquals(listOf(0), cancelled.report.outcomes.map { it.index })
        assertIs<ItemOutcome.Saved>(cancelled.report.outcomes.single())
        assertEquals(1, evidenceCount(caseId))
    }

    @Test
    fun lockingDuringSavingCancelsAndLeavesNoPartialEvidence() {
        val caseId = newCase()
        val store = ViewModelStore()
        val locked = ViewModelProvider(
            store,
            viewModelFactory { initializer { ImportViewModel(importer, vault.cases, resolver, Dispatchers.Unconfined, scope) } },
        )[ImportViewModel::class.java]
        val slow = serve("slow") { HookedStream(ByteArray(20_000) { 1 }) { store.clear() } }
        locked.startForCase(caseId, batch(streamItem(0, slow)))
        locked.save()
        assertIs<ImportUiState.Cancelled>(await(locked.state) { it is ImportUiState.Cancelled || it is ImportUiState.Finished })
        assertEquals(0, evidenceCount(caseId))
    }

    @Test
    fun dismissingAPreviewDropsTheBatchWithoutSaving() {
        val caseId = newCase()
        model.startForCase(caseId, threeItems())
        model.dismiss()
        assertEquals(ImportUiState.Idle, model.state.value)
        assertEquals(0, evidenceCount(caseId))
    }

    @Test
    fun pastedTextGoesThroughTheSamePathAndIsStoredAsPastedText() {
        val caseId = newCase()
        model.startPasted(caseId, "synthetic pasted text")
        val item = previewing().batch.items.single()
        assertIs<PendingItem.Text>(item)
        model.save()
        val finished = assertIs<ImportUiState.Finished>(awaitEnd())
        assertEquals(ItemLabel.PastedText, finished.labels[0])
        val kind = runBlocking { vault.evidence.observeForCase(caseId).first().single().acquisitionKind }
        assertEquals(AcquisitionKind.PASTED_TEXT, kind)
    }

    @Test
    fun blankPastedTextIsShownAsRejected() {
        val caseId = newCase()
        model.startPasted(caseId, "   ")
        assertEquals(PendingItem.Rejected(0, Rejection.EMPTY_TEXT), previewing().batch.items.single())
        assertFalse(previewing().canSave)
    }

    @Test
    fun pickedUrisBecomeAFixedCasePreview() {
        val caseId = newCase()
        model.startPicked(caseId, listOf(serve("p1", ByteArray(2)), serve("p2", ByteArray(2))), ImportMechanism.PHOTO_PICKER)
        val state = await(model.state) { it is ImportUiState.Previewing } as ImportUiState.Previewing
        assertEquals(2, state.batch.items.size)
        assertEquals(caseId, state.fixedCaseId)
        assertEquals(ImportMechanism.PHOTO_PICKER, state.batch.mechanism)
    }

    @Test
    fun anEmptyPickIsIgnored() {
        model.startPicked(newCase(), emptyList(), ImportMechanism.DOCUMENT_PICKER)
        assertEquals(ImportUiState.Idle, model.state.value)
    }
}
