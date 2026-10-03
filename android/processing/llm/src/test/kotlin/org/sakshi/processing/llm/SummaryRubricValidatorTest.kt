package org.sakshi.processing.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sakshi.processing.llm.validation.SummaryRubricValidator

public class SummaryRubricValidatorTest {

    private val sourceId: String = "src-001"
    private val sourceEvidence: String =
        "On Monday, the sender wrote 'stop bothering me or else' after four unanswered calls."

    @Test
    public fun validThreeSentenceSummaryPassesAllRubricPoints(): Unit {
        val summary =
            "Source [$sourceId] records incoming messages and calls from the sender. " +
            "The message included the phrase \"stop bothering me or else\" per [$sourceId]. " +
            "Longitudinal context remains preserved for user review in [$sourceId]."

        val result = SummaryRubricValidator.validate(summary, sourceEvidence, sourceId)
        assertTrue(result.isValid)
        assertEquals(3, result.sentenceCount)
        assertEquals(5, result.points.size)
        assertTrue(result.points.all { it.passed })
    }

    @Test
    public fun incorrectSentenceCountFailsRubric(): Unit {
        val twoSentences =
            "Source [$sourceId] records incoming messages. " +
            "The sender wrote \"stop bothering me or else\" in [$sourceId]."

        val result = SummaryRubricValidator.validate(twoSentences, sourceEvidence, sourceId)
        assertFalse(result.isValid)
        assertEquals(2, result.sentenceCount)

        val sentenceCountPoint = result.points.first { it.pointName == "Exactly three sentences" }
        assertFalse(sentenceCountPoint.passed)
    }

    @Test
    public fun sentenceMissingCitationFailsRubric(): Unit {
        val summaryMissingCitation =
            "Source [$sourceId] records incoming messages from the sender. " +
            "The message included the phrase \"stop bothering me or else\". " +
            "Longitudinal context remains preserved for user review in [$sourceId]."

        val result = SummaryRubricValidator.validate(summaryMissingCitation, sourceEvidence, sourceId)
        assertFalse(result.isValid)

        val citationPoint = result.points.first { it.pointName == "Valid source ID citations" }
        assertFalse(citationPoint.passed)
    }

    @Test
    public fun hallucinatedQuoteInSummaryFailsRubric(): Unit {
        val summaryWithHallucination =
            "Source [$sourceId] records incoming messages from the sender. " +
            "The sender explicitly stated \"I will attack you tomorrow\" in [$sourceId]. " +
            "Longitudinal context remains preserved for user review in [$sourceId]."

        val result = SummaryRubricValidator.validate(summaryWithHallucination, sourceEvidence, sourceId)
        assertFalse(result.isValid)

        val quotePoint = result.points.first { it.pointName == "Exact substring quotes" }
        assertFalse(quotePoint.passed)
    }

    @Test
    public fun legalClaimInSummaryFailsRubric(): Unit {
        val summaryWithLegalClaim =
            "Source [$sourceId] records incoming messages from the sender. " +
            "The sender is guilty of criminal harassment as recorded in [$sourceId]. " +
            "Longitudinal context remains preserved for user review in [$sourceId]."

        val result = SummaryRubricValidator.validate(summaryWithLegalClaim, sourceEvidence, sourceId)
        assertFalse(result.isValid)

        val legalPoint = result.points.first { it.pointName == "No legal claims" }
        assertFalse(legalPoint.passed)
    }

    @Test
    public fun sentenceSplitterHandlesQuotesWithPunctuation(): Unit {
        val text =
            "Source [src-1] reported \"Hello! Are you there?\" to the user. " +
            "The user did not answer per [src-1]. " +
            "The interaction ended according to [src-1]."

        val sentences = SummaryRubricValidator.splitSentences(text)
        assertEquals(3, sentences.size)
        assertEquals("Source [src-1] reported \"Hello! Are you there?\" to the user.", sentences[0])
    }
}
