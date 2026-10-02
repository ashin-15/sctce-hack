package org.sakshi.core.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MimeSnifferTest {
    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun text(value: String, pad: Int = 16): ByteArray = value.toByteArray(Charsets.ISO_8859_1).copyOf(pad)

    @Test
    fun recognisesEachSupportedSignature() {
        assertEquals("image/jpeg", MimeSniffer.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0)))
        assertEquals("image/png", MimeSniffer.detect(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0)))
        assertEquals("image/gif", MimeSniffer.detect(text("GIF89a")))
        assertEquals("image/gif", MimeSniffer.detect(text("GIF87a")))
        assertEquals("image/webp", MimeSniffer.detect(text("RIFF").also { "WEBP".toByteArray().copyInto(it, 8) }))
        assertEquals("audio/wav", MimeSniffer.detect(text("RIFF").also { "WAVE".toByteArray().copyInto(it, 8) }))
        assertEquals("application/pdf", MimeSniffer.detect(text("%PDF-1.7")))
        assertEquals("application/zip", MimeSniffer.detect(bytes(0x50, 0x4B, 0x03, 0x04, 0)))
        assertEquals("video/mp4", MimeSniffer.detect(text("\u0000\u0000\u0000\u0018ftypisom")))
        assertEquals("audio/mp4", MimeSniffer.detect(text("\u0000\u0000\u0000\u0018ftypM4A ")))
        assertEquals("audio/ogg", MimeSniffer.detect(text("OggS")))
        assertEquals("audio/mpeg", MimeSniffer.detect(text("ID3")))
        assertEquals("audio/mpeg", MimeSniffer.detect(bytes(0xFF, 0xFB, 0x90, 0x00)))
    }

    @Test
    fun doesNotGuessPlainTextOrUnknownBytes() {
        assertNull(MimeSniffer.detect("hello, this is plain text".toByteArray()))
        assertNull(MimeSniffer.detect(ByteArray(16)))
        assertNull(MimeSniffer.detect(text("RIFF").also { "AVI ".toByteArray().copyInto(it, 8) }))
        assertNull(MimeSniffer.detect(bytes(0xFF, 0xE0, 0x00)))
    }

    @Test
    fun shortInputGivesNull() {
        assertNull(MimeSniffer.detect(ByteArray(0)))
        assertNull(MimeSniffer.detect(bytes(0xFF)))
        assertNull(MimeSniffer.detect(bytes(0xFF, 0xD8)))
        assertNull(MimeSniffer.detect(text("%PDF").copyOf(4)))
    }
}
