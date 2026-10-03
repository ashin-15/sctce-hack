package org.sakshi.processing.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sakshi.processing.llm.validation.LegalClaimFilter

public class LegalClaimFilterTest {

    @Test
    public fun detectsImpermissibleGuiltClaims(): Unit {
        val guiltyTexts = listOf(
            "The suspect is guilty of criminal harassment.",
            "This message proves guilt beyond reasonable doubt.",
            "The individual committed a crime under the law.",
            "He was convicted of harassment.",
            "This establishes criminal liability for the sender.",
            "The sender is liable under section 354D.",
        )

        for (text in guiltyTexts) {
            assertTrue("Expected to flag guilt in: $text", LegalClaimFilter.containsImpermissibleClaims(text))
            val result = LegalClaimFilter.filter(text)
            assertTrue("Expected containsImpermissibleClaims to be true for: $text", result.containsImpermissibleClaims)
            assertTrue("Expected flagged phrases for: $text", result.flaggedPhrases.isNotEmpty())
        }
    }

    @Test
    public fun detectsAdmissibilityClaims(): Unit {
        val admissibilityTexts = listOf(
            "This report is admissible in court as proof.",
            "This hash provides guaranteed admissibility for legal proceedings.",
            "This document is conclusive evidence of wrongdoing.",
            "This record legally proves harassment occurred.",
            "The evidence stands in court against the defendant.",
        )

        for (text in admissibilityTexts) {
            assertTrue("Expected to flag admissibility claim in: $text", LegalClaimFilter.containsImpermissibleClaims(text))
            val result = LegalClaimFilter.filter(text)
            assertTrue("Expected containsImpermissibleClaims to be true for: $text", result.containsImpermissibleClaims)
        }
    }

    @Test
    public fun allowsNeutralFactualObservations(): Unit {
        val neutralTexts = listOf(
            "Source [src-1] contains repeated contact attempts from the sender.",
            "The classifier flagged this message with the suggestion 'insult'.",
            "The sender stated 'stop contacting me' on 12 March.",
            "Integrity hash supports tamper detection of the stored export.",
            "User review is required to confirm or reject this suggestion.",
        )

        for (text in neutralTexts) {
            assertFalse("Should not flag neutral text: $text", LegalClaimFilter.containsImpermissibleClaims(text))
            val result = LegalClaimFilter.filter(text)
            assertFalse(result.containsImpermissibleClaims)
            assertTrue(result.flaggedPhrases.isEmpty())
            assertEquals(text, result.filteredText)
        }
    }

    @Test
    public fun sanitizationRedactsForbiddenPhrases(): Unit {
        val input = "The suspect is guilty and this document is admissible in court."
        val sanitized = LegalClaimFilter.sanitize(input)
        assertFalse(sanitized.contains("is guilty", ignoreCase = true))
        assertFalse(sanitized.contains("admissible in court", ignoreCase = true))
        assertTrue(sanitized.contains("[legal claim redacted]"))
    }
}
