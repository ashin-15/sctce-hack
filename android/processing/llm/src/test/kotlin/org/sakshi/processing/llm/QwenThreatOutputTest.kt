package org.sakshi.processing.llm

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.sakshi.processing.analysis.ThreatLanguageResultStatus
import org.sakshi.processing.llm.analysis.QwenThreatLanguageClassifier
import org.sakshi.processing.llm.model.LlmSessionManager
import org.sakshi.processing.llm.model.ModelManager

public class QwenThreatOutputTest {
    private val classifier = QwenThreatLanguageClassifier(
        LlmSessionManager(ModelManager(Files.createTempDirectory("qwen_output_test").toFile().apply { deleteOnExit() })),
    )

    @Test
    public fun threatWithExactUniqueQuoteIsASuggestion(): Unit {
        val result = classifier.parseOutput(
            """{"kind":"threat_to_hurt_someone","quote":"I will kill you"}""",
            "🙂 I will kill you tonight.",
        )
        assertEquals(ThreatLanguageResultStatus.POSSIBLE_THREAT_LANGUAGE, result.status)
        assertEquals("I will kill you", result.quote)
    }

    @Test
    public fun quoteMissingFromTheSourceGoesToReview(): Unit {
        val result = classifier.parseOutput(
            """{"kind":"threat_to_hurt_someone","quote":"I will hurt you"}""",
            "I will kill you tonight.",
        )
        assertEquals(ThreatLanguageResultStatus.NEEDS_REVIEW, result.status)
        assertEquals("unmatched_or_ambiguous_quote", result.reasonCode)
    }

    @Test
    public fun repeatedQuoteGoesToReview(): Unit {
        val result = classifier.parseOutput(
            """{"kind":"threat_to_hurt_someone","quote":"I will kill you"}""",
            "I will kill you. Listen, I will kill you.",
        )
        assertEquals("unmatched_or_ambiguous_quote", result.reasonCode)
    }

    @Test
    public fun wordsInsideQuotationMarksGoToReview(): Unit {
        for (source in listOf("She said \"I will kill you\" in the film.", "She said “I will kill you” in the film.")) {
            val result = classifier.parseOutput("""{"kind":"threat_to_hurt_someone","quote":"I will kill you"}""", source)
            assertEquals(ThreatLanguageResultStatus.NEEDS_REVIEW, result.status)
            assertEquals("ambiguous_context", result.reasonCode)
        }
    }

    @Test
    public fun insultAndOrdinaryKindsCarryNoSignal(): Unit {
        for (kind in listOf("insult_or_abuse", "ordinary")) {
            val result = classifier.parseOutput("""{"kind":"$kind","quote":""}""", "Any text.")
            assertEquals(ThreatLanguageResultStatus.NO_SIGNAL_UNCALIBRATED, result.status)
            assertNull(result.quote)
        }
    }

    @Test
    public fun uncertainKindsGoToReview(): Unit {
        for (kind in listOf("figure_of_speech", "reported_or_fiction", "unclear")) {
            val result = classifier.parseOutput("""{"kind":"$kind","quote":""}""", "Any text.")
            assertEquals(ThreatLanguageResultStatus.NEEDS_REVIEW, result.status)
            assertEquals("ambiguous_context", result.reasonCode)
        }
    }

    @Test
    public fun malformedOrUnexpectedOutputGoesToReview(): Unit {
        val malformed = listOf(
            "not json",
            """{"kind":"ordinary"}""",
            """{"kind":"ordinary","quote":"","confidence":"high"}""",
            """{"kind":"ordinary","quote":"Any"}""",
        )
        for (text in malformed) {
            assertEquals("malformed_output", classifier.parseOutput(text, "Any text.").reasonCode)
        }
        assertEquals("unknown_result_enum", classifier.parseOutput("""{"kind":"guilty","quote":""}""", "Any text.").reasonCode)
    }
}
