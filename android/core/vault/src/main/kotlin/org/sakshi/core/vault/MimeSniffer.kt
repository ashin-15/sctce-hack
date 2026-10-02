package org.sakshi.core.vault

import java.io.InputStream

/** Detects a media type from leading bytes. Plain text is deliberately not guessed. */
public object MimeSniffer {
    /** Number of leading bytes [detect] can use. */
    public const val HEAD_SIZE: Int = 16

    private const val MP3_SYNC_MASK = 0xE0
    private const val MPEG_RESERVED_VERSION = 1
    private const val MPEG_RESERVED_LAYER = 0

    /** Returns the detected media type, or null when the bytes are not recognised. */
    public fun detect(head: ByteArray): String? = when {
        head.startsWith(0xFF, 0xD8, 0xFF) -> "image/jpeg"
        head.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> "image/png"
        head.startsWithText("GIF87a") || head.startsWithText("GIF89a") -> "image/gif"
        head.startsWithText("RIFF") -> riffType(head)
        head.startsWithText("%PDF-") -> "application/pdf"
        head.startsWith(0x50, 0x4B, 0x03, 0x04) || head.startsWith(0x50, 0x4B, 0x05, 0x06) -> "application/zip"
        head.hasTextAt(4, "ftyp") -> if (head.hasTextAt(8, "M4A ")) "audio/mp4" else "video/mp4"
        head.startsWithText("OggS") -> "audio/ogg"
        head.startsWithText("ID3") || head.isMpegFrame() -> "audio/mpeg"
        else -> null
    }

    private fun riffType(head: ByteArray): String? = when {
        head.hasTextAt(8, "WEBP") -> "image/webp"
        head.hasTextAt(8, "WAVE") -> "audio/wav"
        else -> null
    }

    private fun ByteArray.isMpegFrame(): Boolean {
        if (size < 2) return false
        val second = this[1].toInt() and 0xFF
        val version = (second shr 3) and 0x3
        val layer = (second shr 1) and 0x3
        return (this[0].toInt() and 0xFF) == 0xFF && (second and MP3_SYNC_MASK) == MP3_SYNC_MASK &&
            version != MPEG_RESERVED_VERSION && layer != MPEG_RESERVED_LAYER
    }

    private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
        size >= prefix.size && prefix.indices.all { (this[it].toInt() and 0xFF) == prefix[it] }

    private fun ByteArray.startsWithText(text: String): Boolean = hasTextAt(0, text)

    private fun ByteArray.hasTextAt(offset: Int, text: String): Boolean =
        size >= offset + text.length && text.indices.all { this[offset + it] == text[it].code.toByte() }
}

/** Passes bytes through while remembering the first [MimeSniffer.HEAD_SIZE] of them. */
internal class HeadCapturingInputStream(private val source: InputStream) : InputStream() {
    private val head = ByteArray(MimeSniffer.HEAD_SIZE)
    private var captured = 0

    fun head(): ByteArray = head.copyOf(captured)

    override fun read(): Int {
        val value = source.read()
        if (value >= 0) remember(byteArrayOf(value.toByte()), 0, 1)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = source.read(buffer, offset, length)
        if (count > 0) remember(buffer, offset, count)
        return count
    }

    private fun remember(buffer: ByteArray, offset: Int, count: Int) {
        val take = minOf(count, head.size - captured)
        if (take > 0) {
            System.arraycopy(buffer, offset, head, captured, take)
            captured += take
        }
    }
}
