package org.sakshi.tools.policy

/**
 * Reviewed negated honesty statements, as `file#name` to exact text. An entry is added only after the text was read in
 * full and judged to be a limitation of what the app can show, not a claim. None at present: the fingerprint
 * statements were reworded so that they no longer use a forbidden word.
 */
internal object ReviewedExceptions {
    val ALL: Map<String, String> = emptyMap()

    /** Hits that are NOT negated honesty statements, awaiting a fix by the owner of the text. None at present. */
    val KNOWN_FINDINGS: Set<String> = emptySet()
}
