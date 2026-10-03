package org.sakshi.processing.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sakshi.processing.llm.validation.SubstringQuoteValidator

public class SubstringQuoteValidatorTest {

    @Test
    public fun exactSubstringQuoteIsValid(): Unit {
        val source = "I will find you and tell your parents what you did."
        val quote = "tell your parents"
        assertTrue(SubstringQuoteValidator.isValidQuote(quote, source))
        assertTrue(SubstringQuoteValidator.isQuoteExactSubstring(quote, source))
    }

    @Test
    public fun quoteWithSurroundingMarksIsValid(): Unit {
        val source = "I will find you tomorrow."
        assertTrue(SubstringQuoteValidator.isValidQuote("\"find you\"", source))
        assertTrue(SubstringQuoteValidator.isValidQuote("'find you'", source))
        assertTrue(SubstringQuoteValidator.isValidQuote("“find you”", source))
    }

    @Test
    public fun hallucinatedQuoteIsRejected(): Unit {
        val source = "I will find you tomorrow."
        val hallucination = "I will murder you"
        assertFalse(SubstringQuoteValidator.isValidQuote(hallucination, source))
        assertFalse(SubstringQuoteValidator.isQuoteExactSubstring(hallucination, source))

        val result = SubstringQuoteValidator.validate(listOf(hallucination), source)
        assertFalse(result.isValid)
        assertEquals(listOf(hallucination), result.hallucinatedQuotes)
        assertTrue(result.validQuotes.isEmpty())
    }

    @Test
    public fun blankOrEmptyQuoteIsRejected(): Unit {
        val source = "Some valid evidence text."
        assertFalse(SubstringQuoteValidator.isValidQuote("", source))
        assertFalse(SubstringQuoteValidator.isValidQuote("   ", source))
        assertFalse(SubstringQuoteValidator.isQuoteExactSubstring(null, source))
    }

    @Test
    public fun mixedQuotesValidationSeparatesCorrectly(): Unit {
        val source = "Please stop messaging me. I will report this to the police."
        val quotes = listOf("stop messaging me", "call your boss", "report this")

        val result = SubstringQuoteValidator.validate(quotes, source)
        assertFalse(result.isValid)
        assertEquals(listOf("stop messaging me", "report this"), result.validQuotes)
        assertEquals(listOf("call your boss"), result.hallucinatedQuotes)
    }

    @Test
    public fun extractQuotesFindsAllEnclosedQuotes(): Unit {
        val text = "The user stated \"stop it\" and then added 'leave me alone' repeatedly."
        val extracted = SubstringQuoteValidator.extractQuotes(text)
        assertEquals(listOf("stop it", "leave me alone"), extracted)
    }

    @Test
    public fun validateTextQuotesValidatesDirectly(): Unit {
        val source = "Hello, stop calling me right now."
        val textWithValidQuote = "The sender wrote \"stop calling me\" in the chat."
        val textWithHallucination = "The sender wrote \"I hate you\" in the chat."

        assertTrue(SubstringQuoteValidator.validateTextQuotes(textWithValidQuote, source).isValid)
        assertFalse(SubstringQuoteValidator.validateTextQuotes(textWithHallucination, source).isValid)
    }
}
