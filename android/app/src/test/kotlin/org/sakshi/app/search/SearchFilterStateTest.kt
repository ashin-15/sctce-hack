package org.sakshi.app.search

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.sakshi.app.R
import org.sakshi.app.support.ForbiddenWords
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.SourceKind
import org.sakshi.core.vault.SearchScope

@RunWith(RobolectricTestRunner::class)
class SearchFilterStateTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources
    private val kolkata = ZoneId.of("Asia/Kolkata")
    private val newYork = ZoneId.of("America/New_York")
    private val sam = PersonChoice(ActorId("synthetic-person-1"), "Synthetic Sam")

    @Test
    fun aDayIsReadAsDayMonthYearWithAnyOfThreeSeparators() {
        val expected = DayInput.Valid(LocalDate.of(2026, 10, 3))
        listOf("03/10/2026", "3/10/2026", "03-10-2026", "03.10.2026", "  03/10/2026 ").forEach {
            assertEquals(expected, parseDay(it), it)
        }
        assertEquals(DayInput.Empty, parseDay(""))
        assertEquals(DayInput.Empty, parseDay("   "))
    }

    @Test
    fun anythingElseIsInvalidAndNothingIsGuessed() {
        listOf("31/02/2026", "29/02/2027", "00/10/2026", "03/13/2026", "2026-10-03", "03/10/26", "03/10", "3 October 2026", "03/10-2026", "ab/cd/efgh")
            .forEach { assertEquals(DayInput.Invalid, parseDay(it), it) }
        assertEquals(DayInput.Valid(LocalDate.of(2028, 2, 29)), parseDay("29/02/2028"))
    }

    @Test
    fun fromIsTheStartOfThatDayAndUntilIsTheLastMillisecondOfThatDay() {
        val filters = assertNotNull(SearchFilterState(fromText = "03/10/2026", untilText = "04/10/2026").toSearchFilters(kolkata))
        assertEquals(Instant.parse("2026-10-02T18:30:00Z"), filters.from)
        assertEquals(Instant.parse("2026-10-04T18:29:59.999Z"), filters.until)
    }

    @Test
    fun theDayThatStartsDaylightSavingIsTwentyThreeHoursLong() {
        val filters = assertNotNull(SearchFilterState(fromText = "08/03/2026", untilText = "08/03/2026").toSearchFilters(newYork))
        assertEquals(Instant.parse("2026-03-08T05:00:00Z"), filters.from)
        assertEquals(Instant.parse("2026-03-09T03:59:59.999Z"), filters.until)
    }

    @Test
    fun theDayThatEndsDaylightSavingIsTwentyFiveHoursLong() {
        val filters = assertNotNull(SearchFilterState(fromText = "01/11/2026", untilText = "01/11/2026").toSearchFilters(newYork))
        assertEquals(Instant.parse("2026-11-01T04:00:00Z"), filters.from)
        assertEquals(Instant.parse("2026-11-02T04:59:59.999Z"), filters.until)
    }

    @Test
    fun oneDateAloneLeavesTheOtherEndOpen() {
        val fromOnly = assertNotNull(SearchFilterState(fromText = "03/10/2026").toSearchFilters(kolkata))
        assertNotNull(fromOnly.from)
        assertNull(fromOnly.until)
        val untilOnly = assertNotNull(SearchFilterState(untilText = "03/10/2026").toSearchFilters(kolkata))
        assertNull(untilOnly.from)
        assertNotNull(untilOnly.until)
    }

    @Test
    fun impossibleDatesAndAFromAfterUntilAreProblemsAndAskForNoSearch() {
        assertEquals(DateProblem.FROM_INVALID, SearchFilterState(fromText = "31/02/2026").dateProblem)
        assertEquals(DateProblem.UNTIL_INVALID, SearchFilterState(untilText = "nonsense").dateProblem)
        assertEquals(DateProblem.FROM_AFTER_UNTIL, SearchFilterState(fromText = "05/10/2026", untilText = "04/10/2026").dateProblem)
        assertNull(SearchFilterState(fromText = "04/10/2026", untilText = "04/10/2026").dateProblem)
        listOf(SearchFilterState(fromText = "31/02/2026"), SearchFilterState(fromText = "05/10/2026", untilText = "04/10/2026"))
            .forEach { assertNull(it.toSearchFilters(kolkata)) }
    }

    @Test
    fun everyOtherFilterIsPassedThroughAndOtherItemsStandForTheRestOfTheSourceKinds() {
        val filters = SearchFilterState(
            scope = SearchScope.ACCEPTED_FINDINGS_ONLY,
            personId = sam.id,
            sources = setOf(SourceChoice.EXPORT, SourceChoice.OTHER_ITEM),
        ).toSearchFilters(kolkata)!!
        assertEquals(SearchScope.ACCEPTED_FINDINGS_ONLY, filters.scope)
        assertEquals(sam.id, filters.actorId)
        assertEquals(
            setOf(
                SourceKind.SELECTED_EXPORT,
                SourceKind.NOTIFICATION_EXCERPT,
                SourceKind.SELECTED_IMAGE,
                SourceKind.SELECTED_AUDIO,
                SourceKind.SELECTED_VIDEO,
                SourceKind.SELECTED_DOCUMENT,
            ),
            filters.sourceKinds,
        )
        assertTrue(SearchFilterState().toSearchFilters(kolkata)!!.isUnfiltered)
        assertFalse(SearchFilterState().isActive)
    }

    @Test
    fun everySourceKindBelongsToExactlyOneChoice() {
        val all = SourceChoice.entries.flatMap { it.kinds }
        assertEquals(SourceKind.entries.toSet(), all.toSet())
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun theSummarySaysNoneWhenNothingNarrowsTheSearch() {
        assertEquals("Filters: none", filterSummary(SearchFilterState(), emptyList(), resources))
    }

    @Test
    fun theSummaryNamesEachActiveFilter() {
        val summary = filterSummary(
            SearchFilterState(
                scope = SearchScope.ACCEPTED_FINDINGS_ONLY,
                personId = sam.id,
                sources = setOf(SourceChoice.NOTE, SourceChoice.EXPORT),
                fromText = "3-10-2026",
                untilText = "05/10/2026",
            ),
            listOf(sam),
            resources,
        )
        assertEquals(
            "Filters: Messages with an agreed tag; person: Synthetic Sam; " +
                "from: Chat export, Your note; dates: 03/10/2026 to 05/10/2026",
            summary,
        )
    }

    @Test
    fun theSummaryHandlesOpenEndedDatesAnUnknownPersonAndAnInvalidDate() {
        assertEquals("Filters: dates: from 03/10/2026", filterSummary(SearchFilterState(fromText = "03/10/2026"), emptyList(), resources))
        assertEquals("Filters: dates: up to 03/10/2026", filterSummary(SearchFilterState(untilText = "03/10/2026"), emptyList(), resources))
        assertEquals("Filters: dates: not valid yet", filterSummary(SearchFilterState(fromText = "31/02/2026"), emptyList(), resources))
        assertEquals(
            "Filters: person: a person no longer in this case",
            filterSummary(SearchFilterState(personId = ActorId("synthetic-gone")), listOf(sam), resources),
        )
    }

    @Test
    fun theFilterNotesHaveNoDashesAndTheSummaryNeverStatesACount() {
        val words = listOf(
            R.string.search_filters_note, R.string.search_filters_note_dates, R.string.search_summary_none, R.string.search_date_hint,
            R.string.search_date_error_invalid, R.string.search_date_error_order, R.string.search_scope_all, R.string.search_scope_accepted,
        ).map(resources::getString)
        words.forEach { assertFalse(ForbiddenWords.hasDash(it), it) }
        assertEquals(
            "Text and notes not yet turned into messages, and messages without a known time, are not shown. Both end days are included, in this phone's time zone.",
            resources.getString(R.string.search_filters_note_dates),
        )
    }
}
