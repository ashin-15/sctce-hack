package org.sakshi.processing.ocr

/** A line and the half-open code-point range `[start, end)` its text occupies in [OcrDocument.text]. */
public data class PlacedLine(public val line: OcrLine, public val start: Int, public val end: Int)

/**
 * The text of a recognition result as one string, with each line's place in it. Lines of one block are joined by a
 * line feed and blocks by an empty line, in the engine's order. Line text is kept exactly as recognised.
 */
public class OcrDocument private constructor(public val text: String, public val lines: List<PlacedLine>) {
    public companion object {
        private const val LINE_SEPARATOR: String = "\n"
        private const val BLOCK_SEPARATOR: String = "\n\n"

        public fun of(result: OcrResult): OcrDocument {
            val text = StringBuilder()
            var codePoints = 0
            val placed = ArrayList<PlacedLine>(result.lines.size)
            result.lines.forEachIndexed { index, line ->
                if (index > 0) {
                    val separator = if (line.blockIndex == result.lines[index - 1].blockIndex) LINE_SEPARATOR else BLOCK_SEPARATOR
                    text.append(separator)
                    codePoints += separator.length
                }
                val start = codePoints
                text.append(line.text)
                codePoints += line.text.codePointCount(0, line.text.length)
                placed += PlacedLine(line, start, codePoints)
            }
            return OcrDocument(text.toString(), placed)
        }
    }
}
