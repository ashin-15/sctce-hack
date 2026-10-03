package org.sakshi.tools.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Rule 3: evidence must never reach logcat or standard streams from app code (megaplan security tests). Applies to
 * Kotlin and Java main sources. The only allowed output is the verdict printed by the command-line bundle verifier.
 */
internal object LoggingScanner {
    val PATTERNS: List<Pattern> = listOf(
        Pattern("android.util.Log", Regex("""android\.util\.Log\b""")),
        Pattern("Log.x(", Regex("""\bLog\.[diwev]\(""")),
        Pattern("println(", Regex("""\bprintln\(""")),
        Pattern("printStackTrace(", Regex("""\bprintStackTrace\(""")),
        Pattern("System.out", Regex("""\bSystem\.out\b""")),
        Pattern("System.err", Regex("""\bSystem\.err\b""")),
        Pattern("Timber", Regex("""\bTimber\b""")),
    )

    /**
     * The verifier tool prints only its pass or fail verdict and usage text (VerifierCli.kt lines using `out.println`
     * and `exitProcess(run(args, System.out))`). It handles no evidence text. This file and these two patterns only.
     */
    private const val VERIFIER = "export/bundle/src/main/kotlin/org/sakshi/export/bundle/VerifierCli.kt"
    val ALLOWED: List<KnownFinding> = listOf(
        KnownFinding(VERIFIER, "println(", "command-line verifier prints its verdict and usage"),
        KnownFinding(VERIFIER, "System.out", "command-line verifier writes its verdict to standard output"),
    )

    fun scan(file: RepoFile): List<Finding> =
        if (file.extension == "kt" || file.extension == "java") scanLines(file.path, file.text, PATTERNS) else emptyList()
}

class NoLoggingCheck {
    @Test
    fun `main sources never log or print`() {
        val findings = Repo.mainSources.flatMap { LoggingScanner.scan(it) }
        assertNoProblems(reconcile("no logging of evidence", findings, LoggingScanner.ALLOWED))
    }
}

class LoggingScannerTest {
    private fun scan(text: String) = LoggingScanner.scan(RepoFile("m/src/main/kotlin/A.kt", text))

    @Test
    fun `each logging form is caught`() {
        val snippets = listOf(
            "import android.util.Log", "Log.d(\"t\", m)", "Log.i(\"t\", m)", "Log.w(\"t\", m)", "Log.e(\"t\", m)",
            "Log.v(\"t\", m)", "println(x)", "e.printStackTrace()", "System.out.println(x)", "System.err.print(x)",
            "Timber.d(x)",
        )
        snippets.forEach { assertTrue(scan(it).isNotEmpty(), "not caught: $it") }
    }

    @Test
    fun `clean code and lookalikes pass`() {
        val clean = "val catalog = Catalog()\nfun blog() = Unit\nval x = AuditLog.d(1)\nval pl = printlnLike(2)"
        assertEquals(emptyList(), scan(clean))
    }

    @Test
    fun `a comment mentioning logging is flagged because comments are not exempt`() {
        assertEquals(1, scan("// never call println(x) here").size)
    }

    @Test
    fun `the allowlist names one file and one pattern per entry`() {
        assertEquals(setOf("println(", "System.out"), LoggingScanner.ALLOWED.map { it.pattern }.toSet())
        assertEquals(1, LoggingScanner.ALLOWED.map { it.file }.toSet().size)
    }
}
