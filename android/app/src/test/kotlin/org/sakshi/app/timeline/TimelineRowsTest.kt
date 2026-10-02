package org.sakshi.app.timeline

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.sakshi.app.review.categoryLabelText
import org.sakshi.app.review.directionText
import org.sakshi.app.support.AnalysisTestBase
import org.sakshi.app.support.SyntheticChats
import org.sakshi.app.ui.resolve
import org.sakshi.core.model.AssociationReview
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.CategoryReviewStatus
import org.sakshi.core.model.Direction
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventId
import org.sakshi.core.model.ReferenceId
import org.sakshi.core.model.TimeBasis
import org.sakshi.core.model.TimeBounds
import org.sakshi.core.model.TimePrecision
import org.sakshi.core.model.Timestamp
import org.sakshi.core.vault.StoredCoverageGap
import org.sakshi.processing.analysis.EventText

class TimelineRowsTest : AnalysisTestBase() {
    private val kolkata = ZoneId.of("Asia/Kolkata")
    private lateinit var caseId: String

    private fun analysed(text: String = SyntheticChats.EIGHT_MESSAGES): List<Event> {
        caseId = newCase()
        analyseExport(importText(caseId, text))
        return events(caseId)
    }

    private fun bodies(list: List<Event>): Map<String, String> = runBlocking {
        EventText(vault).bodiesOf(list).mapNotNull { (id, body) -> body?.let { id.value to it } }.toMap()
    }

    private fun build(
        list: List<Event>,
        zone: ZoneId = kolkata,
        filter: TimelineFilter = TimelineFilter.ALL,
        gaps: List<StoredCoverageGap> = emptyList(),
    ): TimelineView = TimelineRows.build(list, gaps, bodies(list), emptyMap(), zone, filter)

    private fun TimelineView.events(): List<TimelineEventRow> = items.filterIsInstance<TimelineItem.EventItem>().map { it.row }

    private fun gap(id: String, start: String?, end: String?) =
        StoredCoverageGap(ReferenceId(id), CaseId(caseId), start?.let(::Timestamp), end?.let(::Timestamp), "import_selection")

    private fun undated(base: Event, id: String): Event = base.copy(
        eventId = EventId(id),
        timestamp = TimeBounds(null, null, TimeBasis.UNKNOWN, TimePrecision.UNKNOWN, null, null, null),
    )

    private fun bodyText(row: TimelineEventRow): String? = (row.body as? BodyView.Message)?.full

    @Test
    fun rowsAreInTimeOrderWhateverTheInputOrder() {
        val list = analysed()
        val view = build(list.shuffled(java.util.Random(7)))
        val bodiesInOrder = view.events().map { bodyText(it) }
        assertEquals("hello there", bodiesInOrder.first())
        assertEquals("ok", bodiesInOrder.last())
        assertEquals(8, bodiesInOrder.size)
        val times = view.events().mapNotNull { (it.time.label as? TimeLabel.At)?.instant }
        assertEquals(times.sorted(), times)
    }

    @Test
    fun undatedEventsComeLastUnderTheirOwnHeading() {
        val list = analysed()
        val extra = undated(list.first(), "synthetic-undated")
        val items = build(list + extra).items
        assertEquals(TimelineItem.DayHeader(null), items[items.size - 2])
        assertEquals("synthetic-undated", (items.last() as TimelineItem.EventItem).row.eventId)
        assertEquals(TimeLabel.Unknown, (items.last() as TimelineItem.EventItem).row.time.label)
    }

    @Test
    fun dayHeadersFollowTheGivenZone() {
        val list = analysed()
        fun days(zone: ZoneId) = build(list, zone).items.filterIsInstance<TimelineItem.DayHeader>().map { it.date }
        assertEquals(listOf(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 26)), days(kolkata))
        // 21:03 on the 24th in Kolkata is 08:33 on the 24th in Los Angeles, and 08:15 on the 25th is 19:45 on the 24th.
        val la = days(ZoneId.of("America/Los_Angeles"))
        assertEquals(LocalDate.of(2026, 9, 24), la.first())
        assertTrue(la.size < 4)
        assertEquals(la.toSet().size, la.size)
    }

    @Test
    fun aGapSitsBetweenTheEventsAroundIt() {
        val list = analysed()
        val gap = gap("synthetic-gap", "2026-09-25T10:00:00Z", "2026-09-25T20:00:00Z")
        val items = build(list, gaps = listOf(gap)).items
        val at = items.indexOfFirst { it is TimelineItem.GapItem }
        val before = (items[at - 1] as TimelineItem.EventItem).row
        val after = items.drop(at + 1).first { it is TimelineItem.EventItem } as TimelineItem.EventItem
        assertEquals("I will hurt you if you reply", bodyText(before))
        assertEquals("good morning", bodyText(after.row))
    }

    @Test
    fun aGapWithoutBoundsComesAfterTheDatedEventsAndBeforeTheUndatedOnes() {
        val list = analysed()
        val extra = undated(list.first(), "synthetic-undated")
        val items = build(list + extra, gaps = listOf(gap("synthetic-gap", null, null))).items
        val gapAt = items.indexOfFirst { it is TimelineItem.GapItem }
        assertEquals(TimelineItem.DayHeader(null), items[gapAt + 1])
        assertEquals("ok", bodyText((items[gapAt - 1] as TimelineItem.EventItem).row))
        val gapItem = items[gapAt] as TimelineItem.GapItem
        assertNull(gapItem.start)
        assertNull(gapItem.end)
    }

    @Test
    fun gapsAreHiddenWhenAFilterIsOn() {
        val list = analysed()
        val gap = gap("synthetic-gap", "2026-09-25T10:00:00Z", null)
        assertTrue(build(list, filter = TimelineFilter.NEEDS_REVIEW, gaps = listOf(gap)).items.none { it is TimelineItem.GapItem })
        assertTrue(build(list, gaps = listOf(gap)).items.any { it is TimelineItem.GapItem })
    }

    @Test
    fun eventsInTheSameMinuteGetOneOrderNoteForTheGroup() {
        val list = analysed()
        val base = list.first { bodies(listOf(it)).values.firstOrNull() == "hello there" }
        val twin = base.copy(eventId = EventId("synthetic-twin-1"))
        val triplet = base.copy(eventId = EventId("synthetic-twin-2"))
        val rows = build(list + twin + triplet).events()
        assertEquals(1, rows.count { it.orderNote })
        val firstOfGroup = rows.indexOfFirst { it.orderNote }
        assertEquals(base.timestamp, list.first { it.eventId.value == rows[firstOfGroup].eventId }.timestamp)
        assertEquals(0, build(list).events().count { it.orderNote })
    }

    @Test
    fun filtersAndCountsFollowTheReviewState() {
        val list = analysed()
        val all = build(list)
        assertEquals(8, all.total)
        assertEquals(2, all.needsReview)
        assertEquals(2, build(list, filter = TimelineFilter.NEEDS_REVIEW).events().size)
        assertEquals(0, build(list, filter = TimelineFilter.TAGGED).events().size)

        val insult = list.first { it.categories.any { c -> c.label == CategoryLabel.VERBAL_ABUSE } }
        runBlocking { vault.review.reviewCategory(insult.eventId, 0, CategoryReviewStatus.ACCEPTED) }
        val reviewed = events(caseId)
        assertEquals(1, build(reviewed).needsReview)
        assertEquals(1, build(reviewed, filter = TimelineFilter.TAGGED).events().size)
        assertEquals(1, build(reviewed, filter = TimelineFilter.NEEDS_REVIEW).events().size)
        assertEquals(8, build(reviewed, filter = TimelineFilter.ALL).events().size)
    }

    @Test
    fun tagsAreShownAsSuggestionAcceptedOwnDisagreedOrNotSure() {
        val list = analysed()
        val insult = list.first { it.categories.any { c -> c.label == CategoryLabel.VERBAL_ABUSE } }
        val threat = list.first { it.categories.any { c -> c.label == CategoryLabel.EXPLICIT_THREAT } }
        fun tags(id: EventId) = build(events(caseId)).events().first { it.eventId == id.value }.tags.map { it.kind }
        assertEquals(listOf(TagKind.SUGGESTION), tags(insult.eventId))
        runBlocking {
            vault.review.reviewCategory(insult.eventId, 0, CategoryReviewStatus.ACCEPTED)
            vault.review.addUserTag(insult.eventId, CategoryLabel.INTIMIDATION, listOf(ReferenceId("body")))
            vault.review.reviewCategory(threat.eventId, 0, CategoryReviewStatus.REJECTED, "duplicate")
        }
        assertEquals(listOf(TagKind.ACCEPTED, TagKind.OWN), tags(insult.eventId))
        assertEquals(listOf(TagKind.DISAGREED), tags(threat.eventId))
        runBlocking { vault.review.reviewCategory(threat.eventId, 0, CategoryReviewStatus.UNCERTAIN) }
        assertEquals(listOf(TagKind.NOT_SURE), tags(threat.eventId))
        val ownRow = build(events(caseId)).events().first { it.eventId == insult.eventId.value }
        assertTrue(ownRow.tagged)
        assertTrue(!ownRow.needsReview)
    }

    @Test
    fun aMediaPlaceholderSaysTheTextIsNotAvailable() {
        val rows = build(analysed()).events()
        val media = rows.single { it.body == BodyView.MediaOmitted }
        assertEquals(Direction.INCOMING, media.direction)
        val words = context.getString(org.sakshi.app.R.string.body_media_omitted)
        assertEquals("Text not available: the export says media was omitted", words)
    }

    @Test
    fun anEventWhoseTextCannotBeReadSaysSo() {
        val list = analysed()
        val row = TimelineRows.build(list, emptyList(), emptyMap(), emptyMap(), kolkata, TimelineFilter.ALL).events()
            .first { it.body != BodyView.MediaOmitted }
        assertEquals(BodyView.NotAvailable, row.body)
    }

    @Test
    fun longTextIsCutAtThreeHundredCharactersWithoutSplittingAnEmoji() {
        val long = "😀".repeat(301)
        val list = analysed()
        val event = list.first()
        val row = TimelineRows.row(event, long, emptyMap(), false)
        val body = assertIs<BodyView.Message>(row.body)
        assertEquals(300, body.preview.codePointCount(0, body.preview.length))
        assertTrue(body.truncated)
        assertEquals(long, body.full)
        assertTrue(!assertIs<BodyView.Message>(TimelineRows.row(event, "short", emptyMap(), false).body).truncated)
    }

    @Test
    fun senderStatusSaysWhetherTheNameIsConfirmed() {
        val list = analysed()
        val event = list.first()
        val plain = TimelineRows.senderOf(event, emptyMap())
        assertEquals(SenderStatus.NOT_CONFIRMED, plain.status)
        assertNull(plain.personLabel)
        val actor = org.sakshi.core.model.ActorId("synthetic-person")
        val confirmed = event.copy(sender = event.sender.copy(actorId = actor, associationReview = AssociationReview.CONFIRMED))
        val view = TimelineRows.senderOf(confirmed, mapOf(actor to "synthetic label"))
        assertEquals(SenderStatus.CONFIRMED, view.status)
        assertEquals("synthetic label", view.personLabel)
        assertEquals(event.sender.displayLabel, view.label)
    }

    @Test
    fun timeTextSaysWhatIsKnownAndWhereItCameFrom() {
        val list = analysed()
        val reading = readTime(list.first { bodies(listOf(it)).values.firstOrNull() == "hello there" }.timestamp)
        assertEquals(TimeBasis.SOURCE_CLAIM, reading.basis)
        val clock = timeText(reading.label, kolkata, Locale.US).resolve(context.resources)
        assertTrue(clock.contains("9:03"), clock)
        assertEquals("as written in the export", basisText(reading.basis).resolve(context.resources))
        assertEquals("Time not known", timeText(TimeLabel.Unknown, kolkata, Locale.US).resolve(context.resources))
        val dayOnly = timeText(TimeLabel.DayOnly(Instant.parse("2026-09-24T10:00:00Z")), kolkata, Locale.US).resolve(context.resources)
        assertTrue(dayOnly.contains("time of day not known"), dayOnly)
        assertEquals("Period not known", gapPeriodText(null, null, kolkata, Locale.US).resolve(context.resources))
        assertTrue(gapPeriodText(Instant.EPOCH, null, kolkata, Locale.US).resolve(context.resources).startsWith("From"))
        assertTrue(gapPeriodText(null, Instant.EPOCH, kolkata, Locale.US).resolve(context.resources).startsWith("Until"))
    }

    @Test
    fun everyBasisDirectionAndLabelHasWords() {
        val resources = context.resources
        TimeBasis.entries.forEach { assertTrue(basisText(it).resolve(resources).isNotBlank()) }
        Direction.entries.forEach { assertTrue(directionText(it).resolve(resources).isNotBlank()) }
        CategoryLabel.entries.forEach { assertTrue(categoryLabelText(it).resolve(resources).isNotBlank()) }
        org.sakshi.core.temporal.GapReason.entries.forEach { assertTrue(gapReasonText(it).resolve(resources).isNotBlank()) }
        GapProblem.entries.forEach { assertTrue(gapProblemText(it).resolve(resources).isNotBlank()) }
    }

    @Test
    fun theLabelPhrasesAreTheNeutralOnesFromTheBrief() {
        val resources = context.resources
        assertEquals("Insulting or degrading wording", categoryLabelText(CategoryLabel.VERBAL_ABUSE).resolve(resources))
        assertEquals("Wording that states harm", categoryLabelText(CategoryLabel.EXPLICIT_THREAT).resolve(resources))
        assertEquals("Not sure", categoryLabelText(CategoryLabel.UNKNOWN).resolve(resources))
        assertEquals("Mentions exposing private information", categoryLabelText(CategoryLabel.PRIVACY_EXPOSURE_INDICATOR).resolve(resources))
    }

    @Test
    fun gapInputReadsLocalTimeAndRejectsBadInput() {
        val ok = assertIs<GapParse.Valid>(GapInput.parse("2026-09-24 21:05", "2026-09-25 08:00", kolkata))
        assertEquals(Instant.parse("2026-09-24T15:35:00Z"), ok.start)
        assertEquals(Instant.parse("2026-09-25T02:30:00Z"), ok.end)
        assertEquals(GapParse.Valid(null, ok.end), GapInput.parse("  ", "2026-09-25 08:00", kolkata))
        assertEquals(GapParse.Invalid(GapProblem.NEITHER_BOUND), GapInput.parse("", " ", kolkata))
        assertEquals(GapParse.Invalid(GapProblem.START_FORMAT), GapInput.parse("24/09/2026 21:05", "", kolkata))
        assertEquals(GapParse.Invalid(GapProblem.START_FORMAT), GapInput.parse("2026-02-30 10:00", "", kolkata))
        assertEquals(GapParse.Invalid(GapProblem.END_FORMAT), GapInput.parse("", "2026-09-25 25:00", kolkata))
        assertEquals(GapParse.Invalid(GapProblem.END_BEFORE_START), GapInput.parse("2026-09-25 08:00", "2026-09-24 21:05", kolkata))
    }
}
