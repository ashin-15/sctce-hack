package org.sakshi.processing.text

// All fixture content used by this test is synthetic. No real conversation data is involved.

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.CodePointSpan
import org.sakshi.core.model.ConfidenceSemantics
import org.sakshi.core.model.slice

class RulesEngineTest {
    private val engine = RulesEngine(BenchCueList.V1, requireReviewedCues = false)
    private val strict = RulesEngine(BenchCueList.V1, requireReviewedCues = true)

    private fun cps(text: String): Int = text.codePointCount(0, text.length)

    @Test
    fun everyMatchOnEveryFixtureSlicesBackToItsPhrase() {
        var total = 0
        for (row in TestData.fixtures) {
            for (match in engine.analyse(row.text).matches) {
                total++
                val sliced = match.span.slice(row.text)
                assertEquals(CaseFolding.fold(match.phrase).text, CaseFolding.fold(sliced).text, "row ${row.id}")
            }
        }
        assertTrue(total > 0)
    }

    @Test
    fun spanAfterEmojiCountsCodePoints() {
        val prefix = "😀😀 you are "
        val match = engine.analyse(prefix + "worthless").matches.single()
        assertEquals(CodePointSpan(cps(prefix), cps(prefix) + 9), match.span)
        assertEquals("worthless", match.span.slice(prefix + "worthless"))
    }

    @Test
    fun spanAfterLengthChangingLowercaseIsMappedBack() {
        val text = "İİ you are worthless"
        assertEquals(4, text.take(2).lowercase(Locale.ROOT).length)
        val match = engine.analyse(text).matches.single()
        assertEquals("worthless", match.span.slice(text))
        assertEquals(CodePointSpan(11, 20), match.span)
    }

    @Test
    fun matchInsideLengthChangingCharactersCoversWholeCodePoints() {
        val text = "Paßword"
        val match = engine.analyse(text).matches.single()
        assertEquals(CodePointSpan(0, 7), match.span)
    }

    @Test
    fun upperCaseTextMatchesAndKeepsOriginalCase() {
        val match = engine.analyse("YOU ARE WORTHLESS").matches.single()
        assertEquals("WORTHLESS", match.span.slice("YOU ARE WORTHLESS"))
    }

    @Test
    fun malayalamAndDevanagariSpans() {
        val ml = "നീ ഒന്നിനും കൊള്ളില്ല"
        assertEquals("കൊള്ളില്ല", engine.analyse(ml).matches.single().span.slice(ml))
        val hi = "😀 तुम बेकार हो"
        val match = engine.analyse(hi).matches.single()
        assertEquals("बेकार", match.span.slice(hi))
        assertEquals(CategoryLabel.VERBAL_ABUSE, match.schemaLabel)
    }

    @Test
    fun zwjSequenceBeforeMatchCountsAsItsCodePoints() {
        val family = "👨‍👩‍👧"
        val text = "$family you are an idiot"
        assertEquals(5, cps(family))
        val match = engine.analyse(text).matches.single()
        assertEquals("idiot", match.span.slice(text))
        assertEquals(cps(family) + " you are an ".length, match.span.start)
    }

    @Test
    fun everyOccurrenceIsReturnedAndOverlapsAreKept() {
        val repeated = engine.analyse("idiot idiot").matches
        assertEquals(listOf(CodePointSpan(0, 5), CodePointSpan(6, 11)), repeated.map { it.span })
        val overlapping = engine.analyse("you will not be safe nahi").matches
        assertEquals(listOf("not be safe", "safe nahi"), overlapping.map { it.phrase })
        assertEquals(listOf(9, 16), overlapping.map { it.span.start })
        assertTrue(overlapping[0].span.end > overlapping[1].span.start)
    }

    @Test
    fun suggestionCarriesVersionAndNoProbability() {
        val signals = engine.analyse("you are worthless")
        assertEquals(SignalStatus.SUGGESTION, signals.status)
        assertEquals("bench-rules-v1", signals.producerVersion)
        assertEquals(ConfidenceSemantics.NOT_APPLICABLE, signals.confidenceSemantics)
        assertEquals(ProducerLabel.INSULT, signals.matches.single().label)
    }

    @Test
    fun blankTextIsEmptyText() {
        assertEquals(SignalStatus.EMPTY_TEXT, engine.analyse("").status)
        assertEquals(SignalStatus.EMPTY_TEXT, engine.analyse(" \n\t").status)
    }

    @Test
    fun unsupportedScriptYieldsNoMatches() {
        val signals = engine.analyse("ты worthless идиот")
        assertEquals(LanguageHint.ENGLISH, signals.language.hint)
        val onlyOther = engine.analyse("ты бесполезен")
        assertEquals(SignalStatus.UNSUPPORTED_LANGUAGE, onlyOther.status)
        assertTrue(onlyOther.matches.isEmpty())
        assertEquals(SignalStatus.UNSUPPORTED_LANGUAGE, engine.analyse("😀 12345").status)
    }

    @Test
    fun unreviewedCuesAreWithheldWhenReviewIsRequired() {
        val withheld = strict.analyse("you are worthless")
        assertEquals(SignalStatus.CUE_LIST_NOT_REVIEWED, withheld.status)
        assertTrue(withheld.matches.isEmpty())
        assertEquals(SignalStatus.NO_CUE_MATCHED, strict.analyse("see you at lunch").status)
        assertEquals(SignalStatus.NO_CUE_MATCHED, engine.analyse("see you at lunch").status)
    }

    @Test
    fun onlyReviewedLanguagesYieldMatches() {
        val list = CueList("test", BenchCueList.V1.cues, setOf(LanguageHint.ENGLISH))
        val signals = RulesEngine(list, requireReviewedCues = true).analyse("you are worthless, तुम बेकार हो")
        assertEquals(SignalStatus.SUGGESTION, signals.status)
        assertEquals(listOf("worthless"), signals.matches.map { it.phrase })
        val hindiOnly = RulesEngine(list, requireReviewedCues = true).analyse("तुम बेकार हो")
        assertEquals(SignalStatus.CUE_LIST_NOT_REVIEWED, hindiOnly.status)
    }

    @Test
    fun documentsNegatedPhraseStillMatches() {
        // Known weakness: cue matching has no context. This finding must stay a suggestion for human review.
        val signals = engine.analyse("I never said you are worthless")
        assertEquals(SignalStatus.SUGGESTION, signals.status)
        assertEquals(setOf(ProducerLabel.INSULT), signals.matches.map { it.label }.toSet())
    }

    @Test
    fun documentsQuotedPhraseStillMatches() {
        // Known weakness: a quotation of someone else's words matches. This finding must stay a suggestion.
        val signals = engine.analyse("He wrote: \"you are worthless\" and I replied no")
        assertEquals(SignalStatus.SUGGESTION, signals.status)
        assertEquals(1, signals.matches.size)
    }

    @Test
    fun labelMappingCoversEveryProducerLabel() {
        val mapped = ProducerLabel.entries.associateWith { LabelMapping.toSchema(it) }
        assertEquals(ProducerLabel.entries.size, mapped.size)
        assertEquals(CategoryLabel.VERBAL_ABUSE, mapped.getValue(ProducerLabel.INSULT))
        assertEquals(CategoryLabel.EXPLICIT_THREAT, mapped.getValue(ProducerLabel.THREAT))
        assertEquals(CategoryLabel.SEXUAL_PRESSURE, mapped.getValue(ProducerLabel.SEXUAL_HARASSMENT))
        assertEquals(CategoryLabel.VERBAL_ABUSE, mapped.getValue(ProducerLabel.CASTE_RELIGIOUS_SLUR))
        assertEquals(CategoryLabel.PRIVACY_EXPOSURE_INDICATOR, mapped.getValue(ProducerLabel.DOXXING))
        assertEquals(CategoryLabel.CONTROLLING_REQUEST, mapped.getValue(ProducerLabel.COERCIVE_CONTROL))
        assertEquals(1, LabelMapping.VERSION)
    }
}
