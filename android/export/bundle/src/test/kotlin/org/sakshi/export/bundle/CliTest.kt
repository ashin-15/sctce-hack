package org.sakshi.export.bundle

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CliTest {
    @get:Rule
    val tmp: TemporaryFolder = TemporaryFolder()

    private val forbidden: Regex =
        Regex("\\b(authentic|genuine|true|admissible|court-ready|proves?|trusted)\\b", RegexOption.IGNORE_CASE)

    private fun run(vararg args: String): Pair<Int, String> {
        val buffer = ByteArrayOutputStream()
        val code = PrintStream(buffer, true, Charsets.UTF_8).use { VerifierCli.run(arrayOf(*args), it) }
        return code to buffer.toString(Charsets.UTF_8)
    }

    /** The verbatim limits list negates claims ("!="), so its lines are excluded from the forbidden-word scan. */
    private fun withoutLimits(text: String): String = text.lines().filter { line -> BundleFormat.LIMITS.none { line.trim() == it } }.joinToString("\n")

    private fun bundle(): Path = tmp.newFolder().toPath().also { BundleWriter.write(sampleContent(), it, SoftwareP256Signer.generate()) }

    @Test
    fun consistentBundleExitsZeroAndPrintsLimits() {
        val (code, text) = run("verify", bundle().toString())
        assertEquals(0, code, text)
        assertTrue(text.contains("Result: CONSISTENT"))
        BundleFormat.LIMITS.forEach { assertTrue(text.contains(it), it) }
        assertTrue(text.trimEnd().endsWith(BundleFormat.CLOSING_SENTENCE))
        assertFalse(forbidden.containsMatchIn(withoutLimits(text)), text)
        println(text)
    }

    @Test
    fun tamperedBundleExitsOneWithoutForbiddenWords() {
        val dir = bundle()
        flipByte(dir.resolve("evidence/$ORIGINAL_ID"))
        val (code, text) = run("verify", dir.toString())
        assertEquals(1, code, text)
        assertTrue(text.contains("[FAILED] file_hashes"))
        assertFalse(forbidden.containsMatchIn(withoutLimits(text)), text)
    }

    @Test
    fun unreadableAndUsageErrorsExitTwo() {
        val (missing, text) = run("verify", tmp.root.toPath().resolve("absent").toString())
        assertEquals(2, missing)
        assertFalse(forbidden.containsMatchIn(withoutLimits(text)), text)
        assertEquals(2, run().first)
        assertEquals(2, run("check", "x").first)
        assertEquals(2, run("verify").first)
    }

    @Test
    fun readmeInsideBundleStatesLimitsWithoutForbiddenWords() {
        val readme = Files.readString(bundle().resolve("verification/README.txt"))
        BundleFormat.LIMITS.forEach { assertTrue(readme.contains(it), it) }
        assertFalse(forbidden.containsMatchIn(withoutLimits(readme)), readme)
    }

    @Test
    fun writesSampleBundleForManualRun() {
        val location = System.getProperty("sakshi.sample") ?: return
        val dir = Path.of(location)
        if (Files.exists(dir)) Files.walk(dir).use { s -> s.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } }
        Files.createDirectories(dir)
        BundleWriter.write(sampleContent(), dir, SoftwareP256Signer.generate())
        assertEquals(0, run("verify", dir.toString()).first)
    }
}
