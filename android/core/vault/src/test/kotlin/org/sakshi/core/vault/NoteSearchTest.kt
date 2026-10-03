package org.sakshi.core.vault

import java.io.ByteArrayInputStream
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.BoundaryMarker
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.SourceKind
import org.sakshi.core.model.TimeBasis
import org.sakshi.core.model.TimeBounds
import org.sakshi.core.model.TimePrecision
import org.sakshi.core.model.Timestamp

/**
 * Search over manual note text supplied through a [NoteTextSource]. The vault never decodes notes itself, so these
 * tests use an in-memory source; the importer-backed source is tested in the importer module. All data is synthetic.
 */
class NoteSearchTest : ReviewTestBase() {
    private lateinit var derivatives: DerivativeStore
    private lateinit var search: VaultEvidenceSearch
    private val notes = LinkedHashMap<String, String>()
    private val other = "synthetic-case-2"
    private val time = TimeBounds(
        Timestamp("2026-10-01T09:00:00+05:30"), Timestamp("2026-10-01T09:00:00+05:30"),
        TimeBasis.USER_REPORTED, TimePrecision.MINUTE, null, null, null,
    )

    /** Returns the registered text of every note evidence of the case, like a correct importer-backed source. */
    private inner class FakeSource : NoteTextSource {
        var calls = 0

        override suspend fun texts(caseId: CaseId): List<NoteText> {
            calls++
            val own = db.evidenceDao().getIdsForCase(caseId.value).toSet()
            return notes.filterKeys { it in own }.map { NoteText(it.key, it.value) }
        }
    }

    private val source = FakeSource()

    @Before
    fun openSearch() {
        derivatives = DerivativeStore(db, audit, clock, ids, Dispatchers.IO)
        search = VaultEvidenceSearch(db, Dispatchers.IO)
        search.useNoteTextSource(source)
    }

    private suspend fun note(text: String, case: String = EventFixtures.CASE): String {
        val request = ImportRequest(
            case, AcquisitionKind.MANUAL_NOTE, AccessClass.USER_MEDIATED, "synthetic-test", "text/plain", null, null, null, 10_000L,
        )
        val id = evidence.import(request, ByteArrayInputStream("synthetic".toByteArray())).id
        notes[id] = text
        return id
    }

    private suspend fun hits(query: String, filters: SearchFilters = SearchFilters(), case: String = EventFixtures.CASE) =
        search.search(CaseId(case), query, filters).hits

    @Test
    fun aNotesTextIsFoundCaseInsensitivelyAndLabelledAsANote() = runBlocking<Unit> {
        standardCase()
        val id = note("He kept calling me. Please STOP calling.")

        val found = hits("stop")

        val hit = found.single()
        assertEquals(id, hit.evidenceId)
        assertEquals(id, hit.derivativeId)
        assertEquals(SearchHit.NOTE_DERIVATIVE_KIND, hit.derivativeKind)
        assertTrue(hit.isNote)
        assertNull(hit.eventId)
        assertNull(hit.actorLabel)
        assertNull(hit.timestamp)
        val match = hit.matches.single()
        assertEquals("STOP", match.snippet.substring(match.matchStartInSnippet, match.matchEndInSnippet))
    }

    @Test
    fun derivativeHitsAreNotLabelledAsNotes() = runBlocking<Unit> {
        standardCase()
        val evidenceId = evidence.import(request(EventFixtures.CASE), ByteArrayInputStream("synthetic".toByteArray())).id
        derivatives.save(evidenceId, DerivativeKind.OCR, "needle in a derivative", "tool", "1")
        note("needle in a note")

        val found = hits("needle")

        assertEquals(listOf(false, true), found.map { it.isNote })
        assertEquals(DerivativeKind.OCR, found.first().derivativeKind)
    }

    @Test
    fun offsetsAreCorrectForMalayalamDevanagariAndEmoji() = runBlocking<Unit> {
        standardCase()
        note("ഇന്നലെ എന്നെ വിളിച്ചു")
        note("मुझे बार बार फोन किया")
        note("😀😀 emoji 😀 needle 😀")

        val malayalam = hits("വിളിച്ചു").single().matches.single()
        assertEquals("വിളിച്ചു", malayalam.snippet.substring(malayalam.matchStartInSnippet, malayalam.matchEndInSnippet))
        val devanagari = hits("फोन").single().matches.single()
        assertEquals("फोन", devanagari.snippet.substring(devanagari.matchStartInSnippet, devanagari.matchEndInSnippet))
        val emoji = hits("needle").single().matches.single()
        assertEquals("needle", emoji.snippet.substring(emoji.matchStartInSnippet, emoji.matchEndInSnippet))
        val onlyEmoji = hits("😀").single().matches
        assertEquals(4, onlyEmoji.size)
        assertTrue(onlyEmoji.all { it.snippet.substring(it.matchStartInSnippet, it.matchEndInSnippet) == "😀" })
    }

    @Test
    fun aNoteOfAnotherCaseIsNeverReturned() = runBlocking<Unit> {
        standardCase()
        insertCase(other)
        note("needle in the other case", other)
        val mine = note("needle in my case")

        assertEquals(listOf(mine), hits("needle").map { it.evidenceId })
        assertEquals(1, hits("needle", case = other).size)
    }

    @Test
    fun aSourceThatReturnsAForeignOrDeletedNoteIsIgnored() = runBlocking<Unit> {
        standardCase()
        insertCase(other)
        val foreign = note("needle foreign", other)
        val deleted = note("needle deleted")
        evidence.delete(deleted)
        val liar = object : NoteTextSource {
            override suspend fun texts(caseId: CaseId): List<NoteText> = listOf(NoteText(foreign, "needle foreign"), NoteText(deleted, "needle deleted"))
        }
        search.useNoteTextSource(liar)

        assertTrue(hits("needle").isEmpty())
    }

    @Test
    fun aDeletedNoteIsNotFound() = runBlocking<Unit> {
        standardCase()
        val id = note("needle soon gone")
        assertEquals(1, hits("needle").size)

        evidence.delete(id)

        assertTrue(hits("needle").isEmpty())
    }

    @Test
    fun withoutASourceResultsAreExactlyTheDerivativeResults() = runBlocking<Unit> {
        standardCase()
        val evidenceId = evidence.import(request(EventFixtures.CASE), ByteArrayInputStream("synthetic".toByteArray())).id
        derivatives.save(evidenceId, DerivativeKind.OCR, "needle in a derivative", "tool", "1")
        note("needle in a note")
        val plain = VaultEvidenceSearch(db, Dispatchers.IO)

        val without = plain.search(CaseId(EventFixtures.CASE), "needle")

        assertEquals(1, without.hits.size)
        assertFalse(without.hits.single().isNote)
        assertEquals(SearchResults.EMPTY, plain.search(CaseId(EventFixtures.CASE), "absent"))
        search.useNoteTextSource(null)
        assertEquals(without, search.search(CaseId(EventFixtures.CASE), "needle"))
    }

    @Test
    fun theSourceIsReadOncePerSearchAndNotForABlankQuery() = runBlocking<Unit> {
        standardCase()
        note("needle")

        hits("   ")
        assertEquals(0, source.calls)
        hits("needle")
        assertEquals(1, source.calls)
        hits("needle")
        assertEquals(2, source.calls)
    }

    @Test
    fun anyEventLevelFilterExcludesANoteWithoutAnEvent() = runBlocking<Unit> {
        standardCase()
        note("needle plain note")

        assertEquals(1, hits("needle").size)
        assertTrue(hits("needle", SearchFilters(from = Instant.parse("2020-01-01T00:00:00Z"))).isEmpty())
        assertTrue(hits("needle", SearchFilters(until = Instant.parse("2030-01-01T00:00:00Z"))).isEmpty())
        assertTrue(hits("needle", SearchFilters(actorId = ActorId(EventFixtures.ACTOR))).isEmpty())
        assertTrue(hits("needle", SearchFilters(sourceKinds = setOf(SourceKind.MANUAL_ENTRY))).isEmpty())
        assertTrue(hits("needle", SearchFilters(scope = SearchScope.ACCEPTED_FINDINGS_ONLY)).isEmpty())
    }

    @Test
    fun aNoteWithABoundaryEventCarriesItAndIsFilteredLikeThatEvent() = runBlocking<Unit> {
        standardCase()
        val id = note("needle I told him to stop")
        val applied = review.addBoundaryFromNote(caseId, id, BoundaryMarker.DO_NOT_CONTACT, ActorId(EventFixtures.ACTOR), time, communicated = true)
        val eventId = assertIs<ReviewResult.Applied>(applied).changedEventIds.single().value

        val unfiltered = hits("needle").single()
        assertEquals(eventId, unfiltered.eventId)
        assertTrue(unfiltered.isNote)
        assertEquals("2026-10-01T09:00:00+05:30", unfiltered.timestamp)

        val inRange = SearchFilters(from = Instant.parse("2026-10-01T00:00:00Z"), until = Instant.parse("2026-10-02T00:00:00Z"))
        assertEquals(listOf(eventId), hits("needle", inRange).map { it.eventId })
        assertEquals(listOf(eventId), hits("needle", SearchFilters(sourceKinds = setOf(SourceKind.MANUAL_ENTRY))).map { it.eventId })
        assertTrue(hits("needle", SearchFilters(from = Instant.parse("2026-10-02T00:00:00Z"))).isEmpty())
        assertTrue(hits("needle", SearchFilters(sourceKinds = setOf(SourceKind.SELECTED_TEXT))).isEmpty())
    }

    @Test
    fun importTimeIsNeverUsedAsTheTimeOfANote() = runBlocking<Unit> {
        standardCase()
        note("needle no event")

        // The vault clock is 2026-10-02; a date filter around it must still not match an event-less note.
        val aroundImport = SearchFilters(from = Instant.parse("2026-10-01T00:00:00Z"), until = Instant.parse("2026-10-03T00:00:00Z"))
        assertTrue(hits("needle", aroundImport).isEmpty())
    }

    @Test
    fun theMatchLimitIsSharedBetweenDerivativesAndNotes() = runBlocking<Unit> {
        standardCase()
        val evidenceId = evidence.import(request(EventFixtures.CASE), ByteArrayInputStream("synthetic".toByteArray())).id
        derivatives.save(evidenceId, DerivativeKind.OCR, "ab ab", "tool", "1")
        note("ab ab ab")
        val capped = VaultEvidenceSearch(db, Dispatchers.IO, maxMatches = 3)
        capped.useNoteTextSource(source)

        val result = capped.search(CaseId(EventFixtures.CASE), "ab")

        assertTrue(result.limitReached)
        assertEquals(3, result.hits.sumOf { it.matches.size })
        assertEquals(listOf(false, true), result.hits.map { it.isNote })
        assertEquals(1, result.hits.last().matches.size)

        val exact = VaultEvidenceSearch(db, Dispatchers.IO, maxMatches = 5)
        exact.useNoteTextSource(source)
        val all = exact.search(CaseId(EventFixtures.CASE), "ab")
        assertFalse(all.limitReached)
        assertEquals(5, all.hits.sumOf { it.matches.size })
    }
}
