package org.sakshi.core.integrity

import java.security.MessageDigest

public object Sha256 {
    private const val HEX_DIGITS: String = "0123456789abcdef"

    public fun digest(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    public fun hex(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xff
            out.append(HEX_DIGITS[v ushr 4]).append(HEX_DIGITS[v and 0x0f])
        }
        return out.toString()
    }

    public fun fromHex(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(hex.length / 2) { i ->
            val high = Character.digit(hex[2 * i], 16)
            val low = Character.digit(hex[2 * i + 1], 16)
            require(high >= 0 && low >= 0 && hex[2 * i].code < 128 && hex[2 * i + 1].code < 128) {
                "Invalid hex digit near index ${2 * i}"
            }
            ((high shl 4) or low).toByte()
        }
    }
}
