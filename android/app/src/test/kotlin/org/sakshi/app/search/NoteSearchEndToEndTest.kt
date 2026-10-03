package org.sakshi.app.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.sakshi.acquisition.importer.ContentAvailability
import org.sakshi.acquisition.importer.ItemOutcome
import org.sakshi.acquisition.importer.ManualNote
import org.sakshi.acquisition.importer.ViewOnceStatus
import org.sakshi.app.SessionServices
import org.sakshi.app.support.VaultTestBase
import org.sakshi.core.vault.DerivativeKind
import org.sakshi.core.vault.SearchHit
import org.sakshi.core.vault.SearchScope

/** Notes the person wrote, found through the real session services, view model and vault. Synthetic data. */
class NoteSearchEndToEndTest : VaultTestBase() {
    private fun saveNote(caseId: String, text: String): String = runBlocking {
        val note = ManualNote(text, null, "synthetic sender", "synthetic app", ViewOnceStatus.NOT_APPLICABLE, ContentAvailability.NOT_APPLICABLE)
        assertIs<ItemOutcome.Saved>(importer.commitNote(caseId, note)).evidenceId
    }

    private fun SearchViewModel.hitsFor(query: String): List<SearchHit> {
        onQueryChanged(query)
        return await(state) { it.query == query && !it.isSearching }.hits
    }

    private fun withModel(block: (String, SearchViewModel) -> Unit) {
        val caseId = newCase()
        SessionServices(vault, context, Dispatchers.Unconfined).use { services ->
            block(caseId, SearchViewModel(caseId, services.vault.search, scope))
        }
    }

    @Test
    fun theTextOfANoteIsFoundAndMarkedAsANote() = withModel { caseId, model ->
        val id = saveNote(caseId, "Synthetic needle I wrote down that evening")
        val hit = model.hitsFor("needle").single()
        assertTrue(hit.isNote)
        assertEquals(id, hit.evidenceId)
        assertNull(hit.eventId, "no event came from this note, so there is nothing to open")
        assertTrue(model.hitsFor("synthetic sender").isEmpty(), "only the words of the note are searched")
    }

    @Test
    fun aNoteIsNotShownWhenAMessageLevelFilterIsSet() = withModel { caseId, model ->
        saveNote(caseId, "Synthetic needle in a note")
        assertEquals(1, model.hitsFor("needle").size)
        model.setScope(SearchScope.ACCEPTED_FINDINGS_ONLY)
        assertTrue(model.hitsFor("needle").isEmpty())
        model.clearFilters()
        model.setFromText("01/01/2020")
        assertTrue(model.hitsFor("needle").isEmpty())
        model.clearFilters()
        assertEquals(1, model.hitsFor("needle").size)
    }

    @Test
    fun aNoteHitIsLabelledYourNoteAndOtherKindsKeepTheirWords() {
        assertEquals("Your note", context.resources.getString(requireNotNull(derivativeKindLabelRes(SearchHit.NOTE_DERIVATIVE_KIND))))
        assertEquals("OCR", context.resources.getString(requireNotNull(derivativeKindLabelRes(DerivativeKind.OCR))))
        assertNull(derivativeKindLabelRes("synthetic_unknown_kind"))
    }
}
