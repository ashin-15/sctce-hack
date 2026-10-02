package org.sakshi.processing.analysis

/** Maps code point offsets of [text] to UTF-16 indices without rescanning from the start. */
internal class CodePointIndex(private val text: String) {
    /** Total length in code points. */
    val length: Int = text.codePointCount(0, text.length)

    private val offsets: IntArray? = if (length == text.length) null else build()

    private fun build(): IntArray {
        val table = IntArray(length + 1)
        var utf16 = 0
        for (cp in 0 until length) {
            table[cp] = utf16
            utf16 += Character.charCount(text.codePointAt(utf16))
        }
        table[length] = utf16
        return table
    }

    fun utf16(codePoint: Int): Int = offsets?.get(codePoint) ?: codePoint

    fun slice(start: Int, end: Int): String = text.substring(utf16(start), utf16(end))
}
