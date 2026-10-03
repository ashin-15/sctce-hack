package org.sakshi.acquisition.importer

import java.io.File
import java.io.RandomAccessFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.sakshi.core.model.CaseId
import org.sakshi.core.vault.SearchHit

/** Search over the text of notes saved by [EvidenceImporter.commitNote], through the real vault. All data is synthetic. */
class ManualNoteSearchTest : ImporterTestBase() {
    private fun note(text: String, sender: String? = "synthetic sender", app: String? = "synthetic app", time: String? = "synthetic time") =
        ManualNote(text, time, sender, app, ViewOnceStatus.NOT_APPLICABLE, ContentAvailability.NOT_APPLICABLE)

    private fun save(text: String, case: String = caseId, sender: String? = "synthetic sender"): String = runBlocking {
        assertIs<ItemOutcome.Saved>(importer().commitNote(case, note(text, sender))).evidenceId
    }

    private fun hits(query: String, case: String = caseId): List<SearchHit> = runBlocking { vault.search.search(CaseId(case), query).hits }

    @Test
    fun withoutWiringANoteIsNotFound() {
        save("needle in a note")
        assertTrue(hits("needle").isEmpty())
    }

    @Test
    fun wiredSearchFindsNoteTextOnlyAndLabelsTheHit() {
        vault.enableNoteSearch()
        val id = save("Needle in a note", sender = "synthetic-hidden-sender")

        val hit = hits("needle").single()

        assertEquals(id, hit.evidenceId)
        assertTrue(hit.isNote)
        assertEquals(SearchHit.NOTE_DERIVATIVE_KIND, hit.derivativeKind)
        assertEquals("Needle", hit.matches.single().let { it.snippet.substring(it.matchStartInSnippet, it.matchEndInSnippet) })
        assertTrue(hits("synthetic-hidden-sender").isEmpty(), "metadata fields are not searchable")
        assertTrue(hits("synthetic app").isEmpty())
        assertTrue(hits("sakshi-manual-note").isEmpty(), "the stored encoding is not searchable")
    }

    @Test
    fun malayalamDevanagariAndEmojiOffsetsHold() {
        vault.enableNoteSearch()
        save("ഇന്നലെ വിളിച്ചു 😀 फोन किया")

        for (needle in listOf("വിളിച്ചു", "फोन", "😀")) {
            val match = hits(needle).single().matches.single()
            assertEquals(needle, match.snippet.substring(match.matchStartInSnippet, match.matchEndInSnippet))
        }
    }

    @Test
    fun aNoteOfAnotherCaseAndADeletedNoteAreNotFound() {
        vault.enableNoteSearch()
        val otherCase = runBlocking { vault.cases.create("Synthetic other case").id }
        save("needle other", otherCase)
        val mine = save("needle mine")
        assertEquals(listOf(mine), hits("needle").map { it.evidenceId })

        runBlocking { vault.evidence.delete(mine) }

        assertTrue(hits("needle").isEmpty())
        assertEquals(1, hits("needle", otherCase).size)
    }

    @Test
    fun aCorruptedNoteIsSkippedAndTheOthersAreStillFound() {
        vault.enableNoteSearch()
        val namesBefore = blobNames()
        save("needle damaged note")
        val damagedFile = File(blobDirectory, (blobNames() - namesBefore.toSet()).single())
        val intact = save("needle intact note")
        RandomAccessFile(damagedFile, "rw").use { file ->
            file.seek(file.length() - 3)
            val byte = file.read()
            file.seek(file.length() - 3)
            file.write(byte.inv())
        }

        val found = hits("needle")

        assertEquals(listOf(intact), found.map { it.evidenceId })
    }

    @Test
    fun aNoteIsNeverTurnedIntoAnEventOrACategory() {
        vault.enableNoteSearch()
        save("you are worthless and I will find you")

        assertFalse(hits("worthless").isEmpty())
        runBlocking {
            assertTrue(vault.events.loadLatest(CaseId(caseId), FIXED_INSTANT).isEmpty())
        }
    }
}
