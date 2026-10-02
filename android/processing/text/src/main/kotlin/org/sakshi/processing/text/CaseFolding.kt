package org.sakshi.processing.text

import java.util.Locale

/**
 * Case-folded text plus, for every UTF-16 unit of the folded string, the index (in code points) of the
 * original code point that produced it. Folding can change length, so positions must be mapped back.
 */
internal class FoldedText(val text: String, private val origin: IntArray) {
    /** Original code point span covering folded UTF-16 range `[start, end)`; whole code points are covered. */
    fun originalRange(start: Int, end: Int): Pair<Int, Int> {
        require(start in 0 until end && end <= text.length) { "Invalid folded range $start..$end" }
        return origin[start] to origin[end - 1] + 1
    }
}

/**
 * Approximation of Python `str.casefold()` built from the JDK: per code point, a small table of full
 * folds that differ from lower-casing, otherwise `lowercase(Locale.ROOT)` of that single code point.
 * Folding per code point avoids context-sensitive rules such as the Greek final sigma.
 * Scripts with case folds that differ from lower-casing (for example Cherokee) are not covered.
 */
internal object CaseFolding {
    private const val ASCII_LIMIT = 0x80
    private const val CASE_OFFSET = 'a' - 'A'

    fun fold(text: String): FoldedText {
        val out = StringBuilder(text.length)
        var origin = IntArray(text.length + 1)
        var index = 0
        var cpIndex = 0
        while (index < text.length) {
            val cp = text.codePointAt(index)
            val folded = foldCodePoint(cp)
            if (out.length + folded.length > origin.size) origin = origin.copyOf(maxOf(origin.size * 2, out.length + folded.length))
            for (unit in folded.indices) origin[out.length + unit] = cpIndex
            out.append(folded)
            index += Character.charCount(cp)
            cpIndex++
        }
        return FoldedText(out.toString(), origin)
    }

    private fun foldCodePoint(cp: Int): String = when {
        cp < ASCII_LIMIT -> (if (cp in 'A'.code..'Z'.code) cp + CASE_OFFSET else cp).toChar().toString()
        else -> special(cp) ?: String(Character.toChars(cp)).lowercase(Locale.ROOT)
    }

    private val SPECIAL: Map<Int, String> = mapOf(
        0x00DF to "ss", 0x1E9E to "ss", 0x03C2 to "\u03C3", 0x017F to "s", 0x00B5 to "\u03BC",
        0x0149 to "\u02BCn", 0x01F0 to "j\u030C",
        0xFB00 to "ff", 0xFB01 to "fi", 0xFB02 to "fl", 0xFB03 to "ffi", 0xFB04 to "ffl",
        0xFB05 to "st", 0xFB06 to "st",
    )

    private fun special(cp: Int): String? = SPECIAL[cp]
}
