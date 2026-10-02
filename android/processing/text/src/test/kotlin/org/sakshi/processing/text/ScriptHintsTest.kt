package org.sakshi.processing.text

// All fixture content used by this test is synthetic. No real conversation data is involved.

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScriptHintsTest {
    private val pythonNames = mapOf(
        "en" to LanguageHint.ENGLISH,
        "hi" to LanguageHint.HINDI,
        "hinglish" to LanguageHint.HINGLISH,
        "ml" to LanguageHint.MALAYALAM,
        "manglish" to LanguageHint.MANGLISH,
        "mixed" to LanguageHint.MIXED,
    )

    @Test
    fun hintEqualsBenchIdentifyOnAllFixtureRows() {
        for (row in TestData.fixtures) {
            val expected = pythonNames.getValue(TestData.expected.getValue(row.id).identify)
            assertEquals(expected, ScriptHints.assess(row.text).hint, "row ${row.id}: ${row.text}")
        }
    }

    @Test
    fun devanagariAndMalayalamAreDetectedByScript() {
        assertEquals("script:devanagari", ScriptHints.assess("तुम बेकार हो").basis)
        assertEquals(setOf(Script.MALAYALAM), ScriptHints.assess("നീ കൊള്ളില്ല").scripts)
        assertEquals(LanguageHint.HINDI, ScriptHints.assess("नमस्ते hello").hint)
    }

    @Test
    fun lexiconBasisNamesTheListsThatMatched() {
        assertEquals("lexicon:hi+en", ScriptHints.assess("tum are late").basis)
        assertEquals(LanguageHint.MANGLISH, ScriptHints.assess("ninte kayyil und").hint)
        assertEquals("lexicon:ml", ScriptHints.assess("ninte kayyil und").basis)
    }

    @Test
    fun asciiWithoutMarkersIsOnlyAHintForEnglish() {
        val assessment = ScriptHints.assess("xyzzy plugh")
        assertEquals(LanguageHint.ENGLISH, assessment.hint)
        assertEquals("default:latin", assessment.basis)
    }

    @Test
    fun otherScriptsOnlyAreUnsupported() {
        for (text in listOf("நீ பயனற்றவர்", "أنت عديم القيمة", "ты бесполезен")) {
            val assessment = ScriptHints.assess(text)
            assertEquals(LanguageHint.UNSUPPORTED, assessment.hint, text)
            assertEquals(setOf(Script.OTHER), assessment.scripts)
            assertEquals("script:unsupported", assessment.basis)
        }
    }

    @Test
    fun textWithoutLettersIsUnsupportedWithNoLettersBasis() {
        for (text in listOf("", "   ", "😀😀", "12345", "?!...")) {
            val assessment = ScriptHints.assess(text)
            assertEquals(LanguageHint.UNSUPPORTED, assessment.hint, text)
            assertEquals("no_letters", assessment.basis)
            assertTrue(assessment.scripts.isEmpty())
        }
    }

    @Test
    fun latinWithOtherScriptFallsBackToLexicon() {
        val assessment = ScriptHints.assess("you are ты")
        assertEquals(LanguageHint.ENGLISH, assessment.hint)
        assertEquals(setOf(Script.LATIN, Script.OTHER), assessment.scripts)
    }
}
