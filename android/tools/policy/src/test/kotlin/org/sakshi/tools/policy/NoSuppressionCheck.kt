package org.sakshi.tools.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Rule 4: warnings are fixed, never silenced, in main sources and build files. Comments are not exempt, so even a
 * comment that spells out a suppression marker is reported.
 */
internal object SuppressionScanner {
    val PATTERNS: List<Pattern> = listOf(
        Pattern("@Suppress", Regex("""@(?:file:|get:|set:)?Suppress\b""")),
        Pattern("@SuppressLint", Regex("""@SuppressLint\b""")),
        Pattern("@SuppressWarnings", Regex("""@SuppressWarnings\b""")),
        Pattern("//noinspection", Regex("""//\s*noinspection""")),
        Pattern("tools:ignore", Regex("""\btools:ignore\b""")),
        Pattern("lint { disable", Regex("""\blint\s*\{[^}]*\bdisable\b""")),
        Pattern("disable +=", Regex("""^\s*disable\s*(?:\+=|\+\+|=|\()""")),
        Pattern("abortOnError = false", Regex("""\babortOnError\s*=\s*false\b""")),
        Pattern("checkReleaseBuilds = false", Regex("""\bcheckReleaseBuilds\s*=\s*false\b""")),
        Pattern("allWarningsAsErrors = false", Regex("""\ballWarningsAsErrors\s*(?:=|\.set\()\s*false\b""")),
        Pattern("warningsAsErrors = false", Regex("""\bwarningsAsErrors\s*(?:=|\.set\()\s*false\b""")),
        Pattern("suppressWarnings = true", Regex("""\bsuppressWarnings\s*(?:=|\.set\()\s*true\b""")),
    )

    /** Known real findings, awaiting a fix by the owner of the file. None at present. */
    val KNOWN: List<KnownFinding> = emptyList()

    fun scan(file: RepoFile): List<Finding> = scanLines(file.path, file.text, PATTERNS)
}

class NoSuppressionCheck {
    @Test
    fun `main sources and build files suppress nothing`() {
        val files = (Repo.mainSources + Repo.buildFiles).distinctBy { it.path }
        val findings = files.flatMap { SuppressionScanner.scan(it) }
        assertNoProblems(reconcile("no suppressions", findings, SuppressionScanner.KNOWN))
    }
}

class SuppressionScannerTest {
    private fun scan(text: String) = SuppressionScanner.scan(RepoFile("m/build.gradle.kts", text))

    @Test
    fun `each suppression form is caught`() {
        val snippets = listOf(
            "@Suppress(\"UNCHECKED_CAST\")", "@file:Suppress(\"x\")", "@SuppressLint(\"NewApi\")",
            "@SuppressWarnings(\"deprecation\")", "//noinspection Foo", "// noinspection Foo",
            "<application tools:ignore=\"MissingApplicationIcon\">", "lint { disable += \"X\" }",
            "    disable += \"X\"", "abortOnError = false", "checkReleaseBuilds = false",
            "allWarningsAsErrors = false", "allWarningsAsErrors.set(false)", "warningsAsErrors = false",
            "suppressWarnings = true",
        )
        snippets.forEach { assertTrue(scan(it).isNotEmpty(), "not caught: $it") }
    }

    @Test
    fun `strict settings and clean code pass`() {
        val clean = "abortOnError = true\nallWarningsAsErrors.set(true)\nwarningsAsErrors = true\nval suppressed = 1"
        assertEquals(emptyList(), scan(clean))
    }

    @Test
    fun `a comment spelling out a suppression is flagged`() {
        assertEquals(1, scan("// we used to have @Suppress here").size)
    }
}
