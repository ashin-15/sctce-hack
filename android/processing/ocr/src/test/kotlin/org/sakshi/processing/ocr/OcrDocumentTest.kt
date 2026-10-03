package org.sakshi.processing.ocr

import kotlin.test.Test
import kotlin.test.assertEquals

/** Synthetic strings only. */
class OcrDocumentTest {
    private val frame = OcrFrame(10, 10, 1, 0, 0)

    private fun line(text: String, block: Int) = OcrLine(block, text, emptyList(), OcrBox(0, 0, 1, 1), 0.9f, null)

    private fun String.cpSlice(start: Int, end: Int): String = substring(offsetByCodePoints(0, start), offsetByCodePoints(0, end))

    @Test
    fun linesOfABlockAreJoinedByALineFeedAndBlocksByAnEmptyLine() {
        val document = OcrDocument.of(OcrResult(listOf(line("one", 0), line("two", 0), line("three", 1)), frame))
        assertEquals("one\ntwo\n\nthree", document.text)
        assertEquals(listOf(0 to 3, 4 to 7, 9 to 14), document.lines.map { it.start to it.end })
    }

    @Test
    fun spansAreCodePointsSoEmojiAndIndicTextStayAligned() {
        val lines = listOf(line("hi \uD83D\uDE00 there", 0), line("नमस्ते", 0), line("മലയാളം ok", 1))
        val document = OcrDocument.of(OcrResult(lines, frame))
        document.lines.forEach { assertEquals(it.line.text, document.text.cpSlice(it.start, it.end)) }
        assertEquals(document.text.codePointCount(0, document.text.length), document.lines.last().end)
    }

    @Test
    fun anEmptyResultIsAnEmptyDocument() {
        val document = OcrDocument.of(OcrResult(emptyList(), frame))
        assertEquals("", document.text)
        assertEquals(emptyList(), document.lines)
    }
}
