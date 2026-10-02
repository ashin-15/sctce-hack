package org.sakshi.processing.analysis

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Result of [TextDecoder.decode]. */
public sealed interface DecodedText {
    /** Strictly decoded text; [hadBom] records that a byte order mark was present and removed. */
    public data class Text(val text: String, val hadBom: Boolean) : DecodedText

    /** The bytes are not text this module accepts. */
    public data object NotText : DecodedText
}

/**
 * Strict decoder for imported text. Accepts UTF-8 (with or without a byte order mark) and UTF-16 that starts with
 * a byte order mark. Malformed input is refused rather than repaired, and so is any text containing NUL.
 */
public object TextDecoder {
    private const val BOM_UTF8_LENGTH: Int = 3
    private const val BOM_UTF16_LENGTH: Int = 2
    private const val BYTE_MASK: Int = 0xFF
    private const val UTF8_BOM_1: Int = 0xEF
    private const val UTF8_BOM_2: Int = 0xBB
    private const val UTF8_BOM_3: Int = 0xBF
    private const val UTF16_FIRST_BE: Int = 0xFE
    private const val UTF16_FIRST_LE: Int = 0xFF
    private const val NUL: Char = '\u0000'

    public fun decode(bytes: ByteArray): DecodedText {
        val first = bytes.getOrNull(0)?.toInt()?.and(BYTE_MASK)
        val second = bytes.getOrNull(1)?.toInt()?.and(BYTE_MASK)
        val third = bytes.getOrNull(2)?.toInt()?.and(BYTE_MASK)
        return when {
            first == UTF8_BOM_1 && second == UTF8_BOM_2 && third == UTF8_BOM_3 ->
                strict(bytes, BOM_UTF8_LENGTH, Charsets.UTF_8, true)
            first == UTF16_FIRST_BE && second == UTF16_FIRST_LE ->
                strict(bytes, BOM_UTF16_LENGTH, Charsets.UTF_16BE, true)
            first == UTF16_FIRST_LE && second == UTF16_FIRST_BE ->
                strict(bytes, BOM_UTF16_LENGTH, Charsets.UTF_16LE, true)
            else -> strict(bytes, 0, Charsets.UTF_8, false)
        }
    }

    private fun strict(bytes: ByteArray, offset: Int, charset: Charset, hadBom: Boolean): DecodedText =
        try {
            val decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val text = decoder.decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset)).toString()
            if (text.indexOf(NUL) >= 0) DecodedText.NotText else DecodedText.Text(text, hadBom)
        } catch (_: CharacterCodingException) {
            DecodedText.NotText
        }
}
