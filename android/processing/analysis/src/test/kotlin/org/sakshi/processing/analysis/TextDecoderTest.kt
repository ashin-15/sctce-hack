package org.sakshi.processing.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TextDecoderTest {
    private fun text(bytes: ByteArray): DecodedText.Text = assertIs<DecodedText.Text>(TextDecoder.decode(bytes))

    @Test
    fun decodesMalayalamDevanagariAndEmoji() {
        val sample = "synthetic മലയാളം हिन्दी 😀"
        val decoded = text(sample.toByteArray(Charsets.UTF_8))
        assertEquals(sample, decoded.text)
        assertEquals(false, decoded.hadBom)
    }

    @Test
    fun stripsAndRecordsUtf8Bom() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "synthetic".toByteArray()
        val decoded = text(bytes)
        assertEquals("synthetic", decoded.text)
        assertEquals(true, decoded.hadBom)
    }

    @Test
    fun acceptsUtf16WithBomInBothOrders() {
        val little = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "synthetic 😀".toByteArray(Charsets.UTF_16LE)
        val big = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + "synthetic 😀".toByteArray(Charsets.UTF_16BE)
        assertEquals("synthetic 😀", text(little).text)
        assertEquals("synthetic 😀", text(big).text)
        assertEquals(true, text(little).hadBom)
    }

    @Test
    fun refusesInvalidUtf8() {
        assertEquals(DecodedText.NotText, TextDecoder.decode(byteArrayOf(0x61, 0xC3.toByte(), 0x28)))
        assertEquals(DecodedText.NotText, TextDecoder.decode(byteArrayOf(0xFF.toByte(), 0x41)))
    }

    @Test
    fun refusesTruncatedUtf16() {
        val odd = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x41)
        assertEquals(DecodedText.NotText, TextDecoder.decode(odd))
    }

    @Test
    fun refusesNul() {
        assertEquals(DecodedText.NotText, TextDecoder.decode("a\u0000b".toByteArray()))
    }

    @Test
    fun emptyInputIsEmptyText() {
        assertEquals("", text(ByteArray(0)).text)
    }
}
