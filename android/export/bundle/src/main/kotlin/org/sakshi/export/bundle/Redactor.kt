package org.sakshi.export.bundle

import org.sakshi.core.model.CodePointSpan
import org.sakshi.core.model.utf16ToCodePointSpan

/** Text with removed passages replaced by [Redactor.MARKER]. [passageCount] is the number of markers inserted. */
public data class RedactedText(val text: String, val passageCount: Int)

/**
 * Removes passages from a quote (megaplan 21.5, FR-15). Every removed passage becomes one fixed marker, so the
 * result does not show how long the removed text was. Spans count Unicode code points, so Malayalam, Devanagari,
 * emoji and joiner sequences are cut exactly where the span says.
 */
public object Redactor {
    /** Replaces each removed passage. It is the same for every passage and does not depend on the removed text. */
    public const val MARKER: String = "[removed]"

    /**
     * Replaces the passages of [text] named by [spans]. Overlapping and adjacent spans become one passage. An empty
     * list leaves the text unchanged.
     *
     * @throws IllegalArgumentException if a span is empty or reaches past the end of [text].
     */
    public fun apply(text: String, spans: List<CodePointSpan>): RedactedText {
        val merged = merge(spans, text.codePointCount(0, text.length))
        if (merged.isEmpty()) return RedactedText(text, 0)
        val out = StringBuilder()
        var index = 0
        var position = 0
        for (span in merged) {
            val start = text.offsetByCodePoints(index, span.start - position)
            val end = text.offsetByCodePoints(start, span.end - span.start)
            out.append(text, index, start).append(MARKER)
            index = end
            position = span.end
        }
        out.append(text, index, text.length)
        return RedactedText(out.toString(), merged.size)
    }

    /**
     * The sorted, non-overlapping passages that [spans] describe within [codePointCount] code points.
     *
     * @throws IllegalArgumentException if a span is empty or reaches past the end.
     */
    public fun merge(spans: List<CodePointSpan>, codePointCount: Int): List<CodePointSpan> {
        for (span in spans) {
            require(span.start < span.end) { "A removed passage must not be empty" }
            require(span.end <= codePointCount) { "A removed passage ends at ${span.end}, past the $codePointCount code points of the text" }
        }
        val merged = mutableListOf<CodePointSpan>()
        for (span in spans.sortedWith(compareBy({ it.start }, { it.end }))) {
            val last = merged.lastOrNull()
            if (last != null && span.start <= last.end) {
                if (span.end > last.end) merged[merged.size - 1] = CodePointSpan(last.start, span.end)
            } else {
                merged += span
            }
        }
        return merged
    }

    /**
     * A span from UTF-16 offsets such as an Android text selection gives.
     *
     * @throws IllegalArgumentException if an offset is outside the text, the range is inverted, or an offset falls
     * inside a surrogate pair.
     */
    public fun spanFromUtf16(text: String, start: Int, end: Int): CodePointSpan = utf16ToCodePointSpan(text, start, end)
}
