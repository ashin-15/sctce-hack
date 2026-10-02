package org.sakshi.app.report

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.sakshi.app.review.EventReviewViewModel
import org.sakshi.app.support.ReportTestBase
import org.sakshi.app.support.SyntheticChats
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.CategoryReviewStatus
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.EpistemicStatus
import org.sakshi.core.temporal.EvidenceView
import org.sakshi.export.bundle.GeneratorInfo
import org.sakshi.export.report.EventBlock
import org.sakshi.export.report.IntegrityAppendix
import org.sakshi.export.report.ObservedPart
import org.sakshi.export.report.PatternBlock
import org.sakshi.export.report.ReportBuildResult
import org.sakshi.export.report.ReportModel
import org.sakshi.export.report.ReportOptions
import org.sakshi.export.report.ReportSelection
import org.sakshi.export.report.ReportText
import org.sakshi.export.report.ScopeStatement
import org.sakshi.export.report.TagPart
import org.sakshi.export.report.TimelineRow
import org.sakshi.export.report.UserStatementPart

class ReportPreviewMappingTest : ReportTestBase() {
    private val observed = ObservedPart("synthetic quote", "synthetic-artifact", "a".repeat(64), "characters 0 to 15 of the saved text", "saved copy of what you imported")
    private val statement = UserStatementPart("synthetic statement", "synthetic-note", "2026-10-02", "the whole saved item")
    private fun tag(status: EpistemicStatus, label: String, review: String) =
        TagPart(status, label, "suggested by a rule", "synthetic-rules-1", "no score applies", review)

    private val pattern = PatternBlock(
        id = "synthetic-pattern",
        heading = "Repeated contact",
        assessment = "candidate, needs review",
        observed = "Six records came in half an hour.",
        interpretation = "synthetic interpretation",
        limitations = listOf("synthetic limit one", "synthetic limit two"),
        supportingEventIds = listOf("synthetic-event-1", "synthetic-event-2"),
        ruleVersion = "synthetic-rules-1",
    )

    private val model = ReportModel(
        title = "synthetic case title",
        reportVersion = 1,
        generatedAt = Instant.parse("2026-10-02T10:00:00Z"),
        generator = GeneratorInfo("synthetic-app-1", listOf("synthetic-rules-1"), emptyList()),
        scope = ScopeStatement(listOf("synthetic scope line one", "synthetic scope line two")),
        timeline = listOf(TimelineRow("synthetic-event-1", "10:00", "as written in the export", "synthetic-sam", "name as it appears in the export, not confirmed", "incoming, received by you", "export you selected")),
        events = listOf(
            EventBlock(
                eventId = "synthetic-event-1",
                revision = 1,
                heading = "Record 1: Message",
                details = listOf("Record id synthetic-event-1, revision 1."),
                observed = listOf(observed),
                userStatements = listOf(statement),
                inferred = listOf(
                    tag(EpistemicStatus.INFERRED, "Verbal abuse", "accepted by you"),
                    tag(EpistemicStatus.USER_REPORTED, "Intimidation", "accepted by you"),
                ),
                unreviewed = listOf(tag(EpistemicStatus.INFERRED, "Explicit threat", "not reviewed by you")),
            ),
        ),
        patterns = listOf(pattern),
        unknowns = listOf("synthetic unknown one", "synthetic unknown two"),
        integrity = IntegrityAppendix(null, null, "b".repeat(64), "c".repeat(64), ReportText.LIMITS, "synthetic derivative note"),
    )

    private val rows = ReportPreviewMapping.rows(model)
    private val blocks = rows.filterIsInstance<PreviewRow.Block>()
    private val allText = rows.flatMap {
        when (it) {
            is PreviewRow.Heading -> listOf(it.text)
            is PreviewRow.Lines -> it.lines
            is PreviewRow.Block -> listOfNotNull(it.title, it.quote) + it.lines
        }
    }

    private fun kinds(kind: BlockKind) = blocks.filter { it.kind == kind }

    @Test
    fun everyKindOfPartAppearsWithItsOwnStatus() {
        assertEquals(EpistemicStatus.OBSERVED, kinds(BlockKind.OBSERVED).single().status)
        assertEquals(EpistemicStatus.USER_REPORTED, kinds(BlockKind.USER_STATEMENT).single().status)
        assertEquals(EpistemicStatus.INFERRED, kinds(BlockKind.ACCEPTED_TAG).single().status)
        assertEquals(EpistemicStatus.USER_REPORTED, kinds(BlockKind.USER_TAG).single().status)
        assertEquals(EpistemicStatus.INFERRED, kinds(BlockKind.UNREVIEWED_SUGGESTION).single().status)
        assertEquals(EpistemicStatus.PATTERN, kinds(BlockKind.PATTERN).single().status)
        assertEquals(EpistemicStatus.UNKNOWN, kinds(BlockKind.UNKNOWNS).single().status)
        assertEquals(BlockKind.entries.toSet(), blocks.map { it.kind }.toSet())
    }

    @Test
    fun theStatusOfAPartIsTheOneItsTypeFixes() {
        assertEquals(EpistemicStatus.OBSERVED, ReportPreviewMapping.statusOf(observed))
        assertEquals(EpistemicStatus.USER_REPORTED, ReportPreviewMapping.statusOf(statement))
        assertEquals(EpistemicStatus.INFERRED, ReportPreviewMapping.statusOf(tag(EpistemicStatus.INFERRED, "x", "y")))
        assertEquals(EpistemicStatus.PATTERN, ReportPreviewMapping.statusOf(pattern))
    }

    @Test
    fun everyFieldOfTheModelReachesTheScreen() {
        val expected = listOf(
            model.title, ReportText.TITLE_SUFFIX, ReportText.NOT_EVIDENCE, "synthetic-app-1", "synthetic-rules-1",
            "synthetic scope line one", "synthetic scope line two",
            "synthetic-sam", "as written in the export", "export you selected",
            "Record 1: Message", "Record id synthetic-event-1, revision 1.",
            "synthetic quote", "synthetic-artifact", "a".repeat(64), "synthetic statement", "synthetic-note",
            "Verbal abuse", "Intimidation", "Explicit threat", "not reviewed by you",
            "Repeated contact", "candidate, needs review", "Six records came in half an hour.", "synthetic interpretation",
            "synthetic limit one", "synthetic limit two", "synthetic-event-2",
            "synthetic unknown one", "synthetic unknown two",
            "b".repeat(64), "c".repeat(64), "synthetic derivative note",
        ) + ReportText.LIMITS + listOf(
            ReportText.SECTION_SCOPE, ReportText.SECTION_TIMELINE, ReportText.SECTION_RECORDS, ReportText.SECTION_PATTERNS,
            ReportText.SECTION_UNKNOWN, ReportText.SECTION_INTEGRITY, ReportText.UNREVIEWED_HEADING, ReportText.ACCEPTED_TAGS_HEADING,
        )
        expected.forEach { piece -> assertTrue(allText.any { piece in it }, "Not on screen: $piece") }
    }

    @Test
    fun anUnreadableQuoteIsSaidInWordsAndKeepsItsStatus() {
        val unreadable = model.copy(events = listOf(model.events.single().copy(observed = listOf(observed.copy(quote = null)))))
        val block = ReportPreviewMapping.rows(unreadable).filterIsInstance<PreviewRow.Block>().single { it.kind == BlockKind.OBSERVED }
        assertEquals(null, block.quote)
        assertEquals(EpistemicStatus.OBSERVED, block.status)
        assertTrue(ReportText.QUOTE_UNAVAILABLE in block.lines)
    }

    @Test
    fun aModelWithoutPatternsSaysSo() {
        val bare = ReportPreviewMapping.rows(model.copy(patterns = emptyList()))
        assertTrue(bare.none { it is PreviewRow.Block && it.kind == BlockKind.PATTERN })
        assertTrue(bare.any { it is PreviewRow.Lines && ReportText.PATTERNS_NONE in it.lines })
    }

    @Test
    fun theRowKeysAreUnique() {
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
    }

    private fun built(caseId: String, options: ReportOptions): ReportModel {
        val all = events(caseId)
        val selection = ReportSelection(CaseId(caseId), all.map { it.eventId }.toSet(), emptySet(), EvidenceView.CONFIRMED_ONLY, TEST_ZONE_FOR_REPORT)
        val result = runBlocking { services().reportBuilder.build(selection, options) }
        return (result as ReportBuildResult.Built).model
    }

    private fun review(caseId: String, label: CategoryLabel, agree: Boolean) {
        val event = events(caseId).first { hasLabel(it, label) }
        val model = EventReviewViewModel(caseId, event.eventId.value, vault, scope)
        await(model.state) { it.loaded }
        val index = event.categories.indexOfFirst { it.label == label }
        if (agree) model.agree(index) else model.disagree(index, org.sakshi.core.vault.ReviewReason.SIGNAL_ABSENT)
        val wanted = if (agree) CategoryReviewStatus.ACCEPTED else CategoryReviewStatus.REJECTED
        await(model.state) { s -> s.event?.categories?.any { it.label == label && it.reviewStatus == wanted } == true }
    }

    @Test
    fun aRejectedCategoryNeverAppearsAndUnreviewedOnesOnlyWithTheOption() {
        val caseId = newCase()
        val text = SyntheticChats.EIGHT_MESSAGES + "26/09/2026, 10:05 - ${SyntheticChats.OTHER}: give me your password\n"
        addChat(caseId, text)
        review(caseId, CategoryLabel.VERBAL_ABUSE, agree = false)
        review(caseId, CategoryLabel.EXPLICIT_THREAT, agree = true)

        val without = ReportPreviewMapping.rows(built(caseId, ReportOptions(includeUnreviewedSuggestions = false)))
        val with = ReportPreviewMapping.rows(built(caseId, ReportOptions(includeUnreviewedSuggestions = true)))
        fun labelled(rows: List<PreviewRow>, label: String) =
            rows.filterIsInstance<PreviewRow.Block>().filter { block -> block.lines.any { it.startsWith(label) } }

        listOf(without, with).forEach { rejectedRows ->
            assertTrue(labelled(rejectedRows, "Verbal abuse").isEmpty(), "A rejected category was shown")
        }
        val accepted = labelled(without, "Explicit threat")
        assertEquals(BlockKind.ACCEPTED_TAG, accepted.single().kind)
        assertEquals(EpistemicStatus.INFERRED, accepted.single().status)
        assertEquals(emptyList(), without.filterIsInstance<PreviewRow.Block>().filter { it.kind == BlockKind.UNREVIEWED_SUGGESTION })

        val unreviewed = with.filterIsInstance<PreviewRow.Block>().filter { it.kind == BlockKind.UNREVIEWED_SUGGESTION }
        assertFalse(unreviewed.isEmpty())
        unreviewed.forEach {
            assertEquals(EpistemicStatus.INFERRED, it.status)
            assertEquals(ReportText.UNREVIEWED_HEADING, it.title)
            assertNotNull(it.lines.firstOrNull { line -> "not reviewed by you" in line })
        }
    }

    private companion object {
        val TEST_ZONE_FOR_REPORT: java.time.ZoneId = java.time.ZoneId.of("Asia/Kolkata")
    }
}
