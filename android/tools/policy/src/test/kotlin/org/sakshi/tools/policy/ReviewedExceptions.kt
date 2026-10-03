package org.sakshi.tools.policy

/**
 * Reviewed negated honesty statements, as `file#name` to exact text. Each was read in full and judged to be a
 * limitation of what the app can show, not a claim: both say a stored hash does NOT show that something is "genuine".
 * If a wording changes, the test fails until the entry is reviewed again and the recorded text is updated here.
 */
internal object ReviewedExceptions {
    private const val STRINGS = "app/src/main/res/values/strings.xml"

    val ALL: Map<String, String> = mapOf(
        "$STRINGS#onboarding_limit_hash" to
            "A stored fingerprint (hash) of a file shows that the file has not changed since it was saved. " +
            "It does not show that the file is genuine.",
        "$STRINGS#review_fingerprint_explained" to
            "The fingerprint (hash) only shows whether the saved file has changed since it was saved. " +
            "It does not show who wrote the message or that the message is genuine.",
    )

    /** Hits that are NOT negated honesty statements, awaiting a fix by the owner of the text. None at present. */
    val KNOWN_FINDINGS: Set<String> = emptySet()
}
