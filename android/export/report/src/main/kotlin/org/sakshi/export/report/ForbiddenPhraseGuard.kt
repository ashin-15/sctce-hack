package org.sakshi.export.report

/**
 * Finds words and phrases a report template must never contain (megaplan 20.2, NFR-08). Matching is on whole
 * words, case-insensitive, so the mandated limits line "integrity != legal admissibility" passes while the word
 * "admissible" does not. Verbatim quotes of the user's evidence are not passed through this guard.
 */
public object ForbiddenPhraseGuard {
    /** The forbidden phrases, in lower case. */
    public val PHRASES: List<String> = listOf(
        "guilty", "proves", "proven", "proved", "proof", "admissible", "court-ready", "authentic", "genuine",
        "stalker", "stalkers", "stalking", "harasser", "harassers", "abuser", "abusers", "victim", "victims",
        "perpetrator", "perpetrators", "danger score", "risk score", "crime", "crimes", "criminal", "offence",
        "offences", "offense", "offenses", "illegal", "will happen",
    )

    private val PATTERN: Regex = Regex(
        PHRASES.joinToString("|", prefix = "(?<![\\p{L}\\p{N}])(", postfix = ")(?![\\p{L}\\p{N}])") { Regex.escape(it) },
        RegexOption.IGNORE_CASE,
    )

    /** The forbidden phrases found in [text], lower-cased, in order of appearance. Empty when the text is clean. */
    public fun violations(text: String): List<String> =
        PATTERN.findAll(text).map { it.value.lowercase() }.toList()

    /** @throws IllegalArgumentException naming the phrase when [text] contains a forbidden one. */
    public fun assertClean(text: String) {
        val found = violations(text)
        require(found.isEmpty()) { "Report text contains forbidden wording: ${found.first()}" }
    }
}
