package org.sakshi.export.report

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.sakshi.core.temporal.fixtures.SyntheticTimelines

class ForbiddenPhraseGuardTest : ReportTestBase() {
    private fun strings(value: Any?): List<String> = when (value) {
        is String -> listOf(value)
        is Map<*, *> -> value.values.flatMap { strings(it) }
        is List<*> -> value.flatMap { strings(it) }
        else -> emptyList()
    }

    private fun templates(): List<String> = ReportText::class.java.declaredFields
        .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.name != "INSTANCE" }
        .onEach { it.isAccessible = true }
        .flatMap { strings(it.get(null)) }

    @Test
    fun everyTemplateSentenceAndHeadingIsClean() {
        val all = templates()
        assertTrue(all.size > 150, "reflection found only ${all.size} template strings")
        all.forEach { assertEquals(emptyList(), ForbiddenPhraseGuard.violations(it), it) }
    }

    @Test
    fun aBadStringFails() {
        listOf(
            "This proves the sender is guilty.",
            "Court-ready summary",
            "Danger score: 9",
            "The Stalker returned",
            "an illegal act",
            "It will happen again",
            "Victim statement",
        ).forEach { bad ->
            assertTrue(ForbiddenPhraseGuard.violations(bad).isNotEmpty(), bad)
            assertFailsWith<IllegalArgumentException> { ForbiddenPhraseGuard.assertClean(bad) }
        }
        ForbiddenPhraseGuard.assertClean("Observed text and a short summary.")
    }

    @Test
    fun theMandatedLimitsLinesPassAsWholeWords() {
        ReportText.LIMITS.forEach { assertEquals(emptyList(), ForbiddenPhraseGuard.violations(it)) }
        assertTrue(ForbiddenPhraseGuard.violations("not admissible").isNotEmpty())
        assertTrue(ForbiddenPhraseGuard.violations("PROOF").isNotEmpty())
        assertEquals(emptyList(), ForbiddenPhraseGuard.violations("waterproofing and crimson"))
    }

    @Test
    fun everyRenderedNonQuoteStringOfTheTimelinesIsCleanAndHasNoNullOrDash() {
        val inputs = listOf(SyntheticTimelines.a(), SyntheticTimelines.b(), SyntheticTimelines.d(), SyntheticTimelines.e(), SyntheticTimelines.f())
        for (input in inputs) {
            store(input)
            val model = built(runBlocking { builder().build(selectAll(input), ReportOptions(includeUnreviewedSuggestions = true), "ab".repeat(32)) }).model
            val paragraphs = ReportParagraphs.of(model)
            assertTrue(paragraphs.size > 10)
            for (paragraph in paragraphs.filter { !it.verbatim }) {
                assertEquals(emptyList(), ForbiddenPhraseGuard.violations(paragraph.text), paragraph.text)
                assertTrue(!paragraph.text.contains("null"), paragraph.text)
                assertTrue('\u2014' !in paragraph.text && '\u2013' !in paragraph.text, paragraph.text)
            }
        }
    }

    @Test
    fun verbatimQuotesWithForbiddenWordsStayVerbatim() {
        val input = SyntheticTimelines.a()
        store(input)
        val quote = "synthetic: this proves nothing, I am the victim, മലയാളം हिन्दी"
        val model = built(runBlocking { ReportBuilder(vault, FakeQuotes(mapOf("synthetic-a1" to quote)), { FIXED_NOW }, GENERATOR).build(selectAll(input)) }).model
        assertEquals(quote, model.events.single { it.eventId == "synthetic-a1" }.observed.single().quote)
        val paragraphs = ReportParagraphs.of(model)
        val printed = paragraphs.single { it.text == quote }
        assertTrue(printed.verbatim)
        assertTrue(ForbiddenPhraseGuard.violations(printed.text).isNotEmpty())
        assertTrue(paragraphs.filter { !it.verbatim }.all { ForbiddenPhraseGuard.violations(it.text).isEmpty() })
    }
}
