package org.sakshi.app.support

/** Words and phrases the app's copy must never use. One list for the resource test and the mapping tests. */
object ForbiddenWords {
    val phrases: List<String> = listOf(
        "safe", "protected from", "guaranteed", "court", "admissible", "proves", "proof",
        "secure forever", "nobody can", "first", "authentic", "verified sender",
        "harasser", "abuser", "stalker", "stalking", "guilty", "danger", "risk score", "threat detected",
        "harassment detected", "all clear", "nothing found", "harmless",
        "evidence that will be accepted", "official", "certified", "legally valid", "tamper-proof",
    )

    val patterns: List<Regex> = phrases.map { Regex("\\b${Regex.escape(it)}\\b", RegexOption.IGNORE_CASE) }

    fun found(text: String): List<String> = patterns.filter { it.containsMatchIn(text) }.map { it.pattern }

    private const val EM_DASH = 0x2014
    private const val EN_DASH = 0x2013

    fun hasDash(text: String): Boolean = text.any { it.code == EM_DASH || it.code == EN_DASH }
}
