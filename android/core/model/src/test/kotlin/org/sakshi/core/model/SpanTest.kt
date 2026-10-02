package org.sakshi.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SpanTest {
    private val emoji = "😀"
    private val family = "👨‍👩‍👧"
    private val malayalam = "മലയാളം"
    private val devanagari = "क्षि"

    @Test
    fun asciiSpanMapsOneToOne() {
        val span = CodePointSpan(1, 3)
        assertEquals(1 until 3, span.toUtf16Range("Hi?!"))
        assertEquals("i?", span.slice("Hi?!"))
    }

    @Test
    fun supplementaryEmojiIsOneCodePointAndTwoUtf16Units() {
        val text = "a${emoji}b"
        assertEquals(1 until 3, CodePointSpan(1, 2).toUtf16Range(text))
        assertEquals(emoji, CodePointSpan(1, 2).slice(text))
        assertEquals("b", CodePointSpan(2, 3).slice(text))
    }

    @Test
    fun zwjSequenceCountsEachCodePoint() {
        val text = "x${family}y"
        assertEquals(5, family.codePointCount(0, family.length))
        assertEquals(family, CodePointSpan(1, 6).slice(text))
        assertEquals("y", CodePointSpan(6, 7).slice(text))
    }

    @Test
    fun malayalamAndDevanagariCombiningMarks() {
        assertEquals(6, malayalam.codePointCount(0, malayalam.length))
        assertEquals("ാള", CodePointSpan(3, 5).slice(malayalam))
        assertEquals("्ष", CodePointSpan(1, 3).slice(devanagari))
        assertEquals(CodePointSpan(1, 3), utf16ToCodePointSpan(devanagari, 1, 3))
    }

    @Test
    fun roundTripSpanToUtf16AndBack() {
        val text = "a${emoji}${family}$malayalam$devanagari"
        val total = text.codePointCount(0, text.length)
        for (start in 0..total) {
            for (end in start..total) {
                val span = CodePointSpan(start, end)
                val range = span.toUtf16Range(text)
                val back = utf16ToCodePointSpan(text, range.first, range.first + range.count())
                assertEquals(span, back)
            }
        }
    }

    @Test
    fun emptySpanIsAllowed() {
        assertEquals(0, CodePointSpan(2, 2).toUtf16Range("abc").count())
        assertEquals("", CodePointSpan(3, 3).slice("abc"))
    }

    @Test
    fun invalidSpansAreRejected() {
        assertFailsWith<IllegalArgumentException> { CodePointSpan(3, 2) }
        assertFailsWith<IllegalArgumentException> { CodePointSpan(-1, 2) }
        assertFailsWith<IllegalArgumentException> { CodePointSpan(0, 4).toUtf16Range("abc") }
        assertFailsWith<IllegalArgumentException> { CodePointSpan(0, 2).toUtf16Range(emoji) }
    }

    @Test
    fun invalidUtf16IndicesAreRejected() {
        val text = "a${emoji}b"
        assertFailsWith<IllegalArgumentException> { utf16ToCodePointSpan(text, -1, 2) }
        assertFailsWith<IllegalArgumentException> { utf16ToCodePointSpan(text, 3, 1) }
        assertFailsWith<IllegalArgumentException> { utf16ToCodePointSpan(text, 0, 5) }
    }

    @Test
    fun indexInsideSurrogatePairIsRejected() {
        val text = "a${emoji}b"
        assertFailsWith<IllegalArgumentException> { utf16ToCodePointSpan(text, 2, 3) }
        assertFailsWith<IllegalArgumentException> { utf16ToCodePointSpan(text, 0, 2) }
    }
}
