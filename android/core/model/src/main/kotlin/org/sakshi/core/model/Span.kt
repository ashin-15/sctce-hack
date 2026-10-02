package org.sakshi.core.model

/** Half-open span `[start, end)` measured in Unicode code points. */
public data class CodePointSpan(val start: Int, val end: Int) {
    init {
        require(start >= 0) { "start must be >= 0, was $start" }
        require(start <= end) { "start must be <= end, was $start > $end" }
    }
}

/** Converts to a half-open UTF-16 index range over [text]. */
public fun CodePointSpan.toUtf16Range(text: String): IntRange {
    val total = text.codePointCount(0, text.length)
    require(end <= total) { "Span end $end exceeds text length $total code points" }
    val startUtf16 = text.offsetByCodePoints(0, start)
    val endUtf16 = text.offsetByCodePoints(startUtf16, end - start)
    return startUtf16 until endUtf16
}

/** Converts half-open UTF-16 indices over [text] to a code point span. */
public fun utf16ToCodePointSpan(text: String, startUtf16: Int, endUtf16: Int): CodePointSpan {
    require(startUtf16 >= 0 && endUtf16 >= 0) { "UTF-16 indices must be non-negative" }
    require(startUtf16 <= endUtf16) { "Inverted UTF-16 range $startUtf16..$endUtf16" }
    require(endUtf16 <= text.length) { "UTF-16 index $endUtf16 exceeds text length ${text.length}" }
    require(!splitsSurrogatePair(text, startUtf16)) { "UTF-16 index $startUtf16 splits a surrogate pair" }
    require(!splitsSurrogatePair(text, endUtf16)) { "UTF-16 index $endUtf16 splits a surrogate pair" }
    val start = text.codePointCount(0, startUtf16)
    return CodePointSpan(start, start + text.codePointCount(startUtf16, endUtf16))
}

/** Returns the substring of [text] covered by this span. */
public fun CodePointSpan.slice(text: String): String {
    val range = toUtf16Range(text)
    return text.substring(range.first, range.last + 1)
}

private fun splitsSurrogatePair(text: String, index: Int): Boolean =
    index > 0 && index < text.length &&
        Character.isHighSurrogate(text[index - 1]) && Character.isLowSurrogate(text[index])
