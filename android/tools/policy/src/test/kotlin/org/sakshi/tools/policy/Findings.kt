package org.sakshi.tools.policy

/** One place where a rule is broken. [pattern] is the label of the pattern that matched. */
internal data class Finding(val file: String, val line: Int, val pattern: String, val text: String) {
    fun describe(rule: String): String = "$file:$line breaks rule '$rule' (pattern '$pattern'): ${text.trim()}"
}

/** A named regular expression a line may not match. */
internal class Pattern(val label: String, val regex: Regex)

/**
 * A known finding in code owned by someone else, awaiting a fix. It names exactly one file and one pattern label;
 * wildcards are rejected. When the finding disappears the entry is reported as stale so the list cannot rot.
 */
internal class KnownFinding(val file: String, val pattern: String, val reason: String)

/** Line scanning shared by the checks. Comments are not exempt: a forbidden token in KDoc is reported too. */
internal fun scanLines(file: String, text: String, patterns: List<Pattern>): List<Finding> =
    text.lineSequence().withIndex().flatMap { (index, line) ->
        patterns.filter { it.regex.containsMatchIn(line) }.map { Finding(file, index + 1, it.label, line) }
    }.toList()

/** One-based line number of the character at [offset]. */
internal fun lineAt(text: String, offset: Int): Int = text.substring(0, offset).count { it == '\n' } + 1

/** Turns findings plus the known-findings list into failure messages. An empty result means the check passes. */
internal fun reconcile(rule: String, findings: List<Finding>, known: List<KnownFinding> = emptyList()): List<String> {
    val problems = mutableListOf<String>()
    known.forEach {
        if ('*' in it.file || '*' in it.pattern) {
            problems += "Known finding for rule '$rule' uses a wildcard (${it.file}, ${it.pattern}); name one file and one pattern"
        }
    }
    findings.filter { f -> known.none { it.file == f.file && it.pattern == f.pattern } }
        .forEach { problems += it.describe(rule) }
    known.filter { k -> findings.none { it.file == k.file && it.pattern == k.pattern } }
        .forEach { problems += "Stale known finding for rule '$rule': ${it.file} / ${it.pattern} no longer matches; remove the entry" }
    return problems
}

internal fun assertNoProblems(problems: List<String>) {
    check(problems.isEmpty()) { "Policy violations (${problems.size}):\n" + problems.joinToString("\n") }
}
