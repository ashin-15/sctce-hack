package org.sakshi.processing.llm.validation

public data class QuoteValidationResult(
    val isValid: Boolean,
    val hallucinatedQuotes: List<String> = emptyList(),
    val validQuotes: List<String> = emptyList(),
)

public object SubstringQuoteValidator {

    private val QUOTE_REGEX: Regex = Regex("\"([^\"]+)\"|“([^”]+)”|'([^']+)'")

    public fun isValidQuote(quote: String, sourceText: String): Boolean {
        if (quote.isBlank()) return false
        val trimmed = quote.trim()
        val stripped = trimmed.removeSurrounding("\"").removeSurrounding("'").removeSurrounding("“", "”").trim()
        if (stripped.isEmpty()) return false
        return sourceText.contains(stripped) || sourceText.contains(trimmed)
    }

    public fun isQuoteExactSubstring(quote: String?, sourceText: String): Boolean {
        if (quote == null || quote.isBlank()) return false
        return isValidQuote(quote, sourceText)
    }

    public fun extractQuotes(text: String): List<String> {
        val quotes = mutableListOf<String>()
        val matches = QUOTE_REGEX.findAll(text)
        for (match in matches) {
            val content = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }
            if (!content.isNullOrBlank()) {
                quotes.add(content.trim())
            }
        }
        return quotes
    }

    public fun validate(quotes: Collection<String>, sourceText: String): QuoteValidationResult {
        val valid = mutableListOf<String>()
        val hallucinated = mutableListOf<String>()

        for (q in quotes) {
            if (isValidQuote(q, sourceText)) {
                valid.add(q)
            } else {
                hallucinated.add(q)
            }
        }

        return QuoteValidationResult(
            isValid = hallucinated.isEmpty(),
            hallucinatedQuotes = hallucinated,
            validQuotes = valid,
        )
    }

    public fun validateTextQuotes(textWithQuotes: String, sourceText: String): QuoteValidationResult {
        val quotes = extractQuotes(textWithQuotes)
        return validate(quotes, sourceText)
    }
}
