package org.sakshi.processing.llm.validation

public data class LegalClaimFilterResult(
    val containsImpermissibleClaims: Boolean,
    val flaggedPhrases: List<String>,
    val filteredText: String,
)

public object LegalClaimFilter {

    private val FORBIDDEN_PATTERNS: List<Regex> = listOf(
        // Legal guilt patterns
        Regex("""\b(?:is|are|was|were|found|proves?|pleaded?|admits?)\s+guilty\b""", RegexOption.IGNORE_CASE),
        Regex("""\bguilt\s+(?:of|beyond\s+reasonable\s+doubt)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bproof\s+of\s+guilt\b""", RegexOption.IGNORE_CASE),
        Regex("""\bproves\s+(?:the\s+)?guilt\b""", RegexOption.IGNORE_CASE),
        Regex("""\bconvicted\s+(?:of)?\b""", RegexOption.IGNORE_CASE),
        Regex("""\bcommitted\s+(?:a|the)\s+crime\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:criminal|legal)\s+liability\b""", RegexOption.IGNORE_CASE),
        Regex("""\bculpable\s+of\b""", RegexOption.IGNORE_CASE),
        Regex("""\bguilty\s+under\b""", RegexOption.IGNORE_CASE),
        Regex("""\bliable\s+under\s+section\b""", RegexOption.IGNORE_CASE),

        // Court admissibility patterns
        Regex("""\b(?:court|legally)\s+admissible\b""", RegexOption.IGNORE_CASE),
        Regex("""\badmissible\s+in\s+court\b""", RegexOption.IGNORE_CASE),
        Regex("""\bguaranteed\s+admissibility\b""", RegexOption.IGNORE_CASE),
        Regex("""\bconclusive\s+(?:legal\s+)?evidence\b""", RegexOption.IGNORE_CASE),
        Regex("""\blegally\s+proves\b""", RegexOption.IGNORE_CASE),
        Regex("""\bstands?\s+in\s+court\b""", RegexOption.IGNORE_CASE),
        Regex("""\bholds?\s+in\s+court\b""", RegexOption.IGNORE_CASE),
        Regex("""\bproof\s+in\s+a\s+court\s+of\s+law\b""", RegexOption.IGNORE_CASE),
        Regex("""\blegally\s+binding\s+proof\b""", RegexOption.IGNORE_CASE),

        // Legal conclusions and statutory references
        Regex("""\bviolates?\s+section\s+\d+\b""", RegexOption.IGNORE_CASE),
        Regex("""\bunlawful\s+under\s+section\b""", RegexOption.IGNORE_CASE),
        Regex("""\boffence\s+punishable\s+under\b""", RegexOption.IGNORE_CASE),
        Regex("""\bguilty\s+verdict\b""", RegexOption.IGNORE_CASE),
    )

    public fun containsImpermissibleClaims(text: String): Boolean {
        return FORBIDDEN_PATTERNS.any { it.containsMatchIn(text) }
    }

    public fun findFlaggedPhrases(text: String): List<String> {
        val matches = mutableListOf<String>()
        for (pattern in FORBIDDEN_PATTERNS) {
            pattern.findAll(text).forEach { match ->
                matches.add(match.value)
            }
        }
        return matches
    }

    public fun sanitize(text: String): String {
        var result = text
        for (pattern in FORBIDDEN_PATTERNS) {
            result = pattern.replace(result, "[legal claim redacted]")
        }
        return result
    }

    public fun filter(text: String): LegalClaimFilterResult {
        val flagged = findFlaggedPhrases(text)
        return LegalClaimFilterResult(
            containsImpermissibleClaims = flagged.isNotEmpty(),
            flaggedPhrases = flagged,
            filteredText = if (flagged.isNotEmpty()) sanitize(text) else text,
        )
    }
}
