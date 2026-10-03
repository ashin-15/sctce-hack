package org.sakshi.tools.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** One user-facing string or report template constant. [key] is `file#name` and is what the exceptions list uses. */
internal data class PhraseSubject(val file: String, val name: String, val line: Int, val text: String) {
    val key: String get() = "$file#$name"
}

/**
 * Rule 6: user-facing text and report templates make no legal conclusion, guilt label, danger score, safety
 * promise or admissibility claim (megaplan 20.2 and NFR-08). Matching is case-insensitive on whole words.
 *
 * Negation is not parsed. A string that hits a phrase must be listed in [REVIEWED_EXCEPTIONS] with its exact current
 * text, after a person judged that it is a limitation or negation statement. Changing the text of an excepted
 * string makes the test fail until it is reviewed again and the recorded text updated.
 */
internal object PhraseScanner {
    /** Superset of `ForbiddenPhraseGuard.PHRASES` in `export/report` (checked by a test). */
    val PHRASES: List<String> = listOf(
        // From ForbiddenPhraseGuard.
        "guilty", "proves", "proven", "proved", "proof", "admissible", "court-ready", "authentic", "genuine",
        "stalker", "stalkers", "stalking", "harasser", "harassers", "abuser", "abusers", "victim", "victims",
        "perpetrator", "perpetrators", "danger score", "risk score", "crime", "crimes", "criminal", "offence",
        "offences", "offense", "offenses", "illegal", "will happen",
        // Added for user-facing text.
        "offender", "offenders", "unlawful", "threat level", "you are safe", "all clear", "no harassment",
        "verified sender", "tamper-proof", "tamper proof", "guaranteed", "legally", "evidence of harassment",
    )

    private fun regexFor(phrases: List<String>): Regex = Regex(
        phrases.joinToString("|", prefix = "(?<![\\p{L}\\p{N}])(", postfix = ")(?![\\p{L}\\p{N}])") {
            Regex.escape(it).replace(" ", "\\E\\s+\\Q")
        },
        RegexOption.IGNORE_CASE,
    )

    private val PATTERN = regexFor(PHRASES)

    fun hits(text: String, pattern: Regex = PATTERN): List<String> =
        pattern.findAll(text).map { it.value.lowercase() }.toList()

    /**
     * Reviewed negated honesty statements: key to exact text. Every entry was read and judged to be a limitation or
     * negation. Filled in from the current hits; see the policy report for the review notes.
     */
    val REVIEWED_EXCEPTIONS: Map<String, String> = ReviewedExceptions.ALL

    /** Real findings that are not negated honesty statements, awaiting a fix. Same keys as [REVIEWED_EXCEPTIONS]. */
    val KNOWN_FINDINGS: Set<String> = ReviewedExceptions.KNOWN_FINDINGS

    fun problems(subjects: List<PhraseSubject>, exceptions: Map<String, String>, known: Set<String>): List<String> {
        val problems = mutableListOf<String>()
        val hitKeys = mutableSetOf<String>()
        subjects.forEach { subject ->
            val found = hits(subject.text)
            if (found.isEmpty()) return@forEach
            hitKeys += subject.key
            val recorded = exceptions[subject.key]
            val where = "${subject.file}:${subject.line}"
            when {
                subject.key in known -> Unit
                recorded == null ->
                    problems += "$where breaks rule 'forbidden phrase' (${found.distinct()}) in '${subject.name}': ${subject.text}"
                recorded != subject.text ->
                    problems += "$where '${subject.name}' is a reviewed exception but its text changed; re-review it and update " +
                        "the recorded text. Recorded: $recorded | Now: ${subject.text}"
            }
        }
        (exceptions.keys + known).filter { it !in hitKeys }.forEach {
            problems += "Stale entry for rule 'forbidden phrase': $it no longer contains a forbidden phrase or no longer exists; remove it"
        }
        return problems
    }

    /** Quoted literals of the `PHRASES` list in the report module's own guard, for the superset check. */
    fun guardPhrases(guardSource: String): List<String> {
        val region = guardSource.substringAfter("val PHRASES").substringBefore("private val PATTERN")
        return Regex("\"([^\"]+)\"").findAll(region).map { it.groupValues[1] }.toList()
    }

    /** The user-facing literals of the report text object: constants, list items and map values, with their names. */
    fun templates(file: String, source: String): List<PhraseSubject> {
        val subjects = mutableListOf<PhraseSubject>()
        val declaration = Regex("""\bval\s+(\w+)\s*:\s*(String|List<String>|Map<)""")
        val literal = Regex(""""((?:[^"\\]|\\.)*)"""")
        val mapEntry = Regex("""(?:\w+\.)?(\w+)\s+to\s+"((?:[^"\\]|\\.)*)"|"([^"]*)"\s+to\s+"((?:[^"\\]|\\.)*)"""")
        var current = ""
        var kind = ""
        var listIndex = 0
        val constant = StringBuilder()
        var constantLine = 0

        fun flush() {
            if (kind == "String" && constant.isNotEmpty()) subjects += PhraseSubject(file, current, constantLine, constant.toString())
            constant.clear()
        }
        source.lineSequence().forEachIndexed { index, line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("*") || trimmed.startsWith("/*") || trimmed.startsWith("//")) return@forEachIndexed
            val declared = declaration.find(line)
            declared?.let {
                flush()
                current = it.groupValues[1]
                kind = if (it.groupValues[2] == "Map<") "Map" else it.groupValues[2]
                listIndex = 0
                constantLine = index + 1
            }
            when (kind) {
                "String" -> literal.findAll(if (declared != null) line.substringAfter("=", "") else line).forEach { constant.append(unescape(it.groupValues[1])) }
                "List<String>" -> literal.findAll(if (declared != null) line.substringAfter("=", "") else line).forEach {
                    subjects += PhraseSubject(file, "$current[$listIndex]", index + 1, unescape(it.groupValues[1]))
                    listIndex++
                }
                "Map" -> mapEntry.find(line)?.let {
                    val key = it.groupValues[1].ifEmpty { it.groupValues[3] }
                    val value = it.groupValues[2].ifEmpty { it.groupValues[4] }
                    subjects += PhraseSubject(file, "$current[$key]", index + 1, unescape(value))
                }
            }
        }
        flush()
        return subjects
    }

    private fun unescape(raw: String): String = raw.replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\")

    fun repoSubjects(): List<PhraseSubject> {
        val resources = StringResources.parseAll().map { PhraseSubject(it.file, it.name, it.line, it.text) }
        val reportText = Repo.mainSources.filter { it.path == REPORT_TEXT }.flatMap { templates(it.path, it.text) }
        return resources + reportText
    }

    const val REPORT_TEXT: String = "export/report/src/main/kotlin/org/sakshi/export/report/ReportText.kt"
    const val REPORT_GUARD: String = "export/report/src/main/kotlin/org/sakshi/export/report/ForbiddenPhraseGuard.kt"
}

class ForbiddenPhraseCheck {
    @Test
    fun `user-facing strings and report templates contain no forbidden phrase outside reviewed exceptions`() {
        val subjects = PhraseScanner.repoSubjects()
        assertTrue(subjects.any { it.file.endsWith("strings.xml") }, "no strings.xml values were found")
        assertTrue(subjects.any { it.file == PhraseScanner.REPORT_TEXT }, "ReportText.kt templates were not found")
        assertNoProblems(
            PhraseScanner.problems(subjects, PhraseScanner.REVIEWED_EXCEPTIONS, PhraseScanner.KNOWN_FINDINGS),
        )
    }

    @Test
    fun `the phrase list is a superset of the report guard list`() {
        val guard = Repo.mainSources.single { it.path == PhraseScanner.REPORT_GUARD }
        val guardPhrases = PhraseScanner.guardPhrases(guard.text)
        assertTrue(guardPhrases.size > 20, "could not read the guard phrase list")
        val missing = guardPhrases.filter { it !in PhraseScanner.PHRASES }
        assertEquals(emptyList(), missing, "phrases in ForbiddenPhraseGuard missing from the policy list")
    }
}

class PhraseScannerTest {
    @Test
    fun `every listed phrase is caught case-insensitively on word boundaries`() {
        PhraseScanner.PHRASES.forEach { phrase ->
            assertTrue(PhraseScanner.hits("Some ${phrase.uppercase()} text").isNotEmpty(), "not caught: $phrase")
        }
    }

    @Test
    fun `whole words only so similar words pass`() {
        val clean = "authenticity and admissibility are limits. Proofread the legal text. A guarantee is not a promise."
        assertEquals(emptyList(), PhraseScanner.hits(clean))
    }

    @Test
    fun `an unlisted hit fails with the file and line`() {
        val subject = PhraseSubject("s.xml", "bad", 7, "This proves who did it")
        val problem = PhraseScanner.problems(listOf(subject), emptyMap(), emptySet()).single()
        assertTrue(problem.startsWith("s.xml:7 "), problem)
    }

    @Test
    fun `a reviewed exception passes only while its text is unchanged`() {
        val text = "It does not show that the file is genuine"
        val subject = PhraseSubject("s.xml", "limit", 3, text)
        assertEquals(emptyList(), PhraseScanner.problems(listOf(subject), mapOf(subject.key to text), emptySet()))
        val changed = subject.copy(text = "This proves who sent it")
        assertTrue(PhraseScanner.problems(listOf(changed), mapOf(subject.key to text), emptySet()).single().contains("text changed"))
    }

    @Test
    fun `a stale exception is reported`() {
        val problems = PhraseScanner.problems(emptyList(), mapOf("s.xml#gone" to "old"), emptySet())
        assertTrue(problems.single().startsWith("Stale entry"))
    }

    @Test
    fun `templates are extracted with their names from constants lists and maps`() {
        val source = """
            public object T {
                /** Doc with "quoted proves" words. */
                public const val A: String = "One"
                public const val B: String =
                    "Two " +
                        "three."
                public val L: List<String> = listOf(
                    "x != y",
                    "p != q",
                )
                public val M: Map<Kind, String> = mapOf(
                    Kind.FIRST to "first text",
                    "key" to "second text",
                )
            }
        """.trimIndent()
        val byName = PhraseScanner.templates("T.kt", source).associate { it.name to it.text }
        assertEquals(
            mapOf("A" to "One", "B" to "Two three.", "L[0]" to "x != y", "L[1]" to "p != q", "M[FIRST]" to "first text", "M[key]" to "second text"),
            byName,
        )
    }
}
