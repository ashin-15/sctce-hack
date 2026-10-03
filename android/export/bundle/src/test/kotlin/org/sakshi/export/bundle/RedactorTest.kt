package org.sakshi.export.bundle

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import org.junit.Test
import org.sakshi.core.model.CodePointSpan

class RedactorTest {
    private val marker = Redactor.MARKER

    @Test
    fun replacesEachPassageWithTheFixedMarker() {
        val result = Redactor.apply("synthetic one two three", listOf(CodePointSpan(10, 13), CodePointSpan(18, 23)))
        assertEquals("synthetic $marker two $marker", result.text)
        assertEquals(2, result.passageCount)
    }

    @Test
    fun markerDoesNotShowTheRemovedLength() {
        val short = Redactor.apply("abcdef", listOf(CodePointSpan(1, 2)))
        val long = Redactor.apply("abcdef", listOf(CodePointSpan(1, 5)))
        assertEquals("a${marker}cdef", short.text)
        assertEquals("a${marker}f", long.text)
        assertFalse(long.text.contains("bcde"))
    }

    @Test
    fun overlappingAndAdjacentSpansMergeIntoOnePassage() {
        val overlapping = Redactor.apply("0123456789", listOf(CodePointSpan(2, 6), CodePointSpan(4, 8)))
        assertEquals("01${marker}89", overlapping.text)
        assertEquals(1, overlapping.passageCount)
        val adjacent = Redactor.apply("0123456789", listOf(CodePointSpan(5, 7), CodePointSpan(2, 5)))
        assertEquals("01${marker}789", adjacent.text)
        assertEquals(1, adjacent.passageCount)
        val nested = Redactor.apply("0123456789", listOf(CodePointSpan(1, 9), CodePointSpan(3, 4)))
        assertEquals("0${marker}9", nested.text)
        assertEquals(listOf(CodePointSpan(0, 3), CodePointSpan(5, 7)), Redactor.merge(listOf(CodePointSpan(5, 7), CodePointSpan(0, 3)), 10))
    }

    @Test
    fun noSpansLeavesTheTextAlone() {
        assertEquals(RedactedText("synthetic text", 0), Redactor.apply("synthetic text", emptyList()))
    }

    @Test
    fun wholeTextCanBeRemoved() {
        assertEquals(RedactedText(marker, 1), Redactor.apply("abc", listOf(CodePointSpan(0, 3))))
    }

    @Test
    fun rejectsEmptyOutOfRangeAndInvertedSpans() {
        assertFailsWith<IllegalArgumentException> { Redactor.apply("abc", listOf(CodePointSpan(1, 1))) }
        assertFailsWith<IllegalArgumentException> { Redactor.apply("abc", listOf(CodePointSpan(2, 4))) }
        assertFailsWith<IllegalArgumentException> { Redactor.apply("abc", listOf(CodePointSpan(0, 2), CodePointSpan(5, 6))) }
        assertFailsWith<IllegalArgumentException> { CodePointSpan(3, 2) }
        assertFailsWith<IllegalArgumentException> { CodePointSpan(-1, 2) }
    }

    @Test
    fun utf16OffsetsInsideASurrogatePairAreRejected() {
        val text = "a😀b"
        assertFailsWith<IllegalArgumentException> { Redactor.spanFromUtf16(text, 2, 3) }
        assertFailsWith<IllegalArgumentException> { Redactor.spanFromUtf16(text, 0, 2) }
        assertFailsWith<IllegalArgumentException> { Redactor.spanFromUtf16(text, 3, 1) }
        assertFailsWith<IllegalArgumentException> { Redactor.spanFromUtf16(text, 0, 9) }
        assertEquals(CodePointSpan(1, 2), Redactor.spanFromUtf16(text, 1, 3))
    }

    @Test
    fun emojiAreCountedAsOneCodePoint() {
        val text = "a😀b😁c"
        assertEquals("a${marker}b😁c", Redactor.apply(text, listOf(CodePointSpan(1, 2))).text)
        assertEquals("a😀b${marker}c", Redactor.apply(text, listOf(CodePointSpan(3, 4))).text)
    }

    @Test
    fun zeroWidthJoinerSequencesAreCutByCodePoint() {
        val family = "👩‍👧"
        val text = "x${family}y"
        assertEquals(5, text.codePointCount(0, text.length))
        assertEquals("x${marker}y", Redactor.apply(text, listOf(CodePointSpan(1, 4))).text)
        assertEquals("x👩$marker", Redactor.apply(text, listOf(CodePointSpan(2, 5))).text)
    }

    @Test
    fun malayalamAndDevanagariAreCutByCodePoint() {
        val malayalam = "മലയാളം"
        assertEquals("മല${marker}ം", Redactor.apply(malayalam, listOf(CodePointSpan(2, 5))).text)
        val hindi = "हिन्दी"
        assertEquals("${marker}\u0926\u0940", Redactor.apply(hindi, listOf(CodePointSpan(0, 4))).text)
        assertEquals("हि$marker", Redactor.apply(hindi, listOf(CodePointSpan(2, 6))).text)
    }
}
