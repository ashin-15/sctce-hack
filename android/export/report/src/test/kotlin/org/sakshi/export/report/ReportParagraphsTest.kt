package org.sakshi.export.report

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.sakshi.core.model.Locator
import org.sakshi.core.temporal.fixtures.SyntheticTimelines

class ReportParagraphsTest : ReportTestBase() {
    private fun paragraphs(input: org.sakshi.core.temporal.TemporalInput, options: ReportOptions = ReportOptions()) =
        ReportParagraphs.of(built(runBlocking { builder().build(selectAll(input), options) }).model)

    @Test
    fun everyPartIsHeadedByItsStatusWordAndQuotesAreVerbatim() {
        val input = SyntheticTimelines.b()
        store(input)
        val list = paragraphs(input)
        val words = list.filter { it.style == ParagraphStyle.STATUS }.map { it.text }.toSet()
        assertEquals(
            setOf("OBSERVED", "YOUR STATEMENT", "PATTERN", "UNKNOWN").filter { it in words }.toSet(),
            words.intersect(setOf("OBSERVED", "YOUR STATEMENT", "PATTERN", "UNKNOWN")),
        )
        assertTrue("OBSERVED" in words && "PATTERN" in words && "UNKNOWN" in words)
        val quotes = list.filter { it.style == ParagraphStyle.QUOTE }
        assertTrue(quotes.isNotEmpty() && quotes.all { it.verbatim })
        val headings = list.filter { it.style == ParagraphStyle.HEADING }.map { it.text }
        assertEquals(
            listOf(
                ReportText.SECTION_SCOPE, ReportText.SECTION_TIMELINE, ReportText.SECTION_RECORDS,
                ReportText.SECTION_PATTERNS, ReportText.SECTION_UNKNOWN, ReportText.SECTION_INTEGRITY,
            ),
            headings,
        )
    }

    @Test
    fun theUnreviewedHeadingAppearsOnlyWhenRequested() {
        val input = SyntheticTimelines.b()
        val events = input.events.map { event ->
            event.copy(categories = event.categories.map { it.copy(reviewStatus = org.sakshi.core.model.CategoryReviewStatus.UNREVIEWED) })
        }
        store(input, events)
        assertTrue(paragraphs(input).none { it.text == ReportText.UNREVIEWED_HEADING })
        assertTrue(paragraphs(input, ReportOptions(includeUnreviewedSuggestions = true)).any { it.text == ReportText.UNREVIEWED_HEADING })
    }

    @Test
    fun locatorsAreWrittenInWords() {
        assertEquals("characters 120 to 164 of the saved text", ReportFormat.locator(Locator.Text(120, 164)))
        assertEquals("the whole saved item", ReportFormat.locator(Locator.WholeArtifact))
        assertEquals("3:05.250 to 3:09.000 of the audio", ReportFormat.locator(Locator.AudioTime(185_250, 189_000)))
    }

    @Test
    fun integrityAppendixStatesTheLimitsAndThatTheReportIsNotTheEvidence() {
        val input = SyntheticTimelines.a()
        store(input)
        val model = built(runBlocking { builder().build(selectAll(input), signerKeyId = "cd".repeat(32)) }).model
        val text = ReportParagraphs.of(model).map { it.text }
        assertTrue(text.contains(ReportText.NOT_EVIDENCE))
        ReportText.LIMITS.forEach { assertTrue(it in text) }
        assertTrue(text.any { it.contains("cd".repeat(32)) })
        assertTrue(text.any { it.contains(model.integrity.auditChainHead) })
    }
}
