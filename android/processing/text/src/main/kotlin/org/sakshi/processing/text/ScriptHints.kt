package org.sakshi.processing.text

/** Coarse language hint for a piece of text. Never a confirmed language. */
public enum class LanguageHint { ENGLISH, HINDI, HINGLISH, MALAYALAM, MANGLISH, MIXED, UNSUPPORTED }

/** Writing systems the hint cares about; every other letter counts as [OTHER]. */
public enum class Script { LATIN, DEVANAGARI, MALAYALAM, OTHER }

/** Result of [ScriptHints.assess]: the [hint], the [scripts] seen, and a short machine-readable [basis]. */
public data class LanguageAssessment(val hint: LanguageHint, val scripts: Set<Script>, val basis: String)

/**
 * Script and small-lexicon language hint, ported from the benchmark heuristic.
 *
 * This is a hint, not language identification: ASCII text is not proof of English, romanised Hindi or
 * Malayalam is recognised only through a few marker words, and the result must stay user-correctable.
 */
public object ScriptHints {
    private const val DEVANAGARI_START = 0x0900
    private const val DEVANAGARI_END = 0x097F
    private const val MALAYALAM_START = 0x0D00
    private const val MALAYALAM_END = 0x0D7F

    private val HINDI_WORDS = setOf("tum", "tumne", "tumhara", "main", "nahi", "karunga", "darr", "neend", "bola", "mera")
    private val MALAYALAM_WORDS = setOf("ninte", "ninne", "njan", "enikku", "ente", "njangal", "tharu", "venam")
    private val ENGLISH_WORDS = setOf("i", "you", "your", "will", "the", "and", "are", "please", "stop", "send")

    /** Assesses [text]. Devanagari wins over Malayalam when both occur, as in the benchmark. */
    public fun assess(text: String): LanguageAssessment {
        val scripts = scriptsOf(text)
        return when {
            Script.DEVANAGARI in scripts -> LanguageAssessment(LanguageHint.HINDI, scripts, "script:devanagari")
            Script.MALAYALAM in scripts -> LanguageAssessment(LanguageHint.MALAYALAM, scripts, "script:malayalam")
            scripts.isEmpty() -> LanguageAssessment(LanguageHint.UNSUPPORTED, scripts, "no_letters")
            Script.LATIN !in scripts -> LanguageAssessment(LanguageHint.UNSUPPORTED, scripts, "script:unsupported")
            else -> lexiconAssessment(text, scripts)
        }
    }

    private fun lexiconAssessment(text: String, scripts: Set<Script>): LanguageAssessment {
        val tokens = tokens(CaseFolding.fold(text).text)
        val hindi = tokens.any { it in HINDI_WORDS }
        val malayalam = tokens.any { it in MALAYALAM_WORDS }
        val english = tokens.any { it in ENGLISH_WORDS }
        val parts = listOfNotNull("hi".takeIf { hindi }, "ml".takeIf { malayalam }, "en".takeIf { english })
        val basis = if (parts.isEmpty()) "default:latin" else "lexicon:" + parts.joinToString("+")
        val hint = when {
            (hindi || malayalam) && english -> LanguageHint.MIXED
            hindi -> LanguageHint.HINGLISH
            malayalam -> LanguageHint.MANGLISH
            else -> LanguageHint.ENGLISH
        }
        return LanguageAssessment(hint, scripts, basis)
    }

    private fun scriptsOf(text: String): Set<Script> {
        val found = mutableSetOf<Script>()
        var index = 0
        while (index < text.length) {
            val cp = text.codePointAt(index)
            index += Character.charCount(cp)
            when {
                cp in DEVANAGARI_START..DEVANAGARI_END -> found += Script.DEVANAGARI
                cp in MALAYALAM_START..MALAYALAM_END -> found += Script.MALAYALAM
                !Character.isLetter(cp) -> Unit
                Character.UnicodeScript.of(cp) == Character.UnicodeScript.LATIN -> found += Script.LATIN
                else -> found += Script.OTHER
            }
        }
        return found
    }

    /** Runs of letters, digits and underscore, matching the benchmark `\w+` split. */
    private fun tokens(folded: String): Set<String> {
        val result = mutableSetOf<String>()
        val current = StringBuilder()
        var index = 0
        while (index < folded.length) {
            val cp = folded.codePointAt(index)
            index += Character.charCount(cp)
            if (Character.isLetterOrDigit(cp) || cp == '_'.code) {
                current.appendCodePoint(cp)
            } else if (current.isNotEmpty()) {
                result += current.toString()
                current.setLength(0)
            }
        }
        if (current.isNotEmpty()) result += current.toString()
        return result
    }
}
