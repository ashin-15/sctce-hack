package org.sakshi.tools.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Rule 5: no em dash (U+2014) or en dash (U+2013) in any source, resource, build or markdown file under `android/`
 * (main and test), and no three-dot run inside a `strings.xml` value, where the ellipsis character (U+2026) is
 * required. The dash characters are written as escapes here so this module never contains them literally.
 */
internal object TypographyScanner {
    val DASHES: List<Pattern> = listOf(
        Pattern("em dash U+2014", Regex("\u2014|&#8212;|&#x2014;", RegexOption.IGNORE_CASE)),
        Pattern("en dash U+2013", Regex("\u2013|&#8211;|&#x2013;", RegexOption.IGNORE_CASE)),
    )

    private val THREE_DOTS = Regex("""\.\.\.""")

    fun scanDashes(file: RepoFile): List<Finding> = scanLines(file.path, file.text, DASHES)

    fun scanEllipsis(strings: List<NamedText>): List<Finding> = strings
        .filter { THREE_DOTS.containsMatchIn(it.text) }
        .map { Finding(it.file, it.line, "three dots in string '${it.name}' (use U+2026)", it.text) }
}

class TypographyCheck {
    @Test
    fun `no em or en dash anywhere under the android directory`() {
        val findings = Repo.files.flatMap { TypographyScanner.scanDashes(it) }
        assertNoProblems(reconcile("no em dash or en dash", findings))
    }

    @Test
    fun `string resources use the ellipsis character instead of three dots`() {
        assertNoProblems(reconcile("ellipsis character in strings.xml", TypographyScanner.scanEllipsis(StringResources.parseAll())))
    }
}

class TypographyScannerTest {
    private val emDash = "\u2014"
    private val enDash = "\u2013"

    @Test
    fun `em and en dashes are caught in any file kind`() {
        listOf("a.kt", "b.md", "c.xml", "d.gradle.kts", "e.toml").forEach { name ->
            assertEquals("em dash U+2014", TypographyScanner.scanDashes(RepoFile(name, "one ${emDash} two")).single().pattern)
            assertEquals("en dash U+2013", TypographyScanner.scanDashes(RepoFile(name, "1${enDash}2")).single().pattern)
        }
    }

    @Test
    fun `escaped dash forms in resources are caught`() {
        assertTrue(TypographyScanner.scanDashes(RepoFile("s.xml", "a &#8212; b")).isNotEmpty())
        assertTrue(TypographyScanner.scanDashes(RepoFile("s.xml", "a &#x2013; b")).isNotEmpty())
    }

    @Test
    fun `plain hyphens and minus signs pass`() {
        assertEquals(emptyList(), TypographyScanner.scanDashes(RepoFile("a.kt", "a - b -- c 1-2 ->")))
    }

    @Test
    fun `three dots in a string value are caught and the ellipsis character passes`() {
        val xml = "<resources>\n<string name=\"a\">Loading...</string>\n<string name=\"b\">Loading\u2026</string>\n</resources>"
        val finding = TypographyScanner.scanEllipsis(StringResources.parse("s.xml", xml)).single()
        assertEquals(2, finding.line)
    }

    @Test
    fun `three dots in plural items are caught`() {
        val xml = "<resources>\n<plurals name=\"p\">\n<item quantity=\"one\">Wait...</item>\n</plurals>\n</resources>"
        assertEquals(3, TypographyScanner.scanEllipsis(StringResources.parse("s.xml", xml)).single().line)
    }
}
