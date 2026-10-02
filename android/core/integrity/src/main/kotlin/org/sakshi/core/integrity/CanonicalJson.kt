package org.sakshi.core.integrity

import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** JSON Canonicalization Scheme (RFC 8785). */
public object CanonicalJson {
    private val JSON_NUMBER: Regex = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")
    private val INTEGER: Regex = Regex("-?[0-9]+")
    private val MAX_SAFE_INTEGER: BigInteger = BigInteger.TWO.pow(53)
    private const val EXPONENT_LIMIT: Int = 21
    private const val SMALL_LIMIT: Int = -6

    public fun encode(element: JsonElement): ByteArray = encodeToString(element).toByteArray(Charsets.UTF_8)

    public fun encodeToString(element: JsonElement): String = buildString { write(element) }

    private fun StringBuilder.write(element: JsonElement) {
        when (element) {
            is JsonNull -> append("null")
            is JsonPrimitive -> writePrimitive(element)
            is JsonArray -> {
                append('[')
                element.forEachIndexed { index, item ->
                    if (index > 0) append(',')
                    write(item)
                }
                append(']')
            }
            is JsonObject -> {
                append('{')
                element.entries.sortedBy { it.key }.forEachIndexed { index, (key, value) ->
                    if (index > 0) append(',')
                    writeString(key)
                    append(':')
                    write(value)
                }
                append('}')
            }
        }
    }

    private fun StringBuilder.writePrimitive(primitive: JsonPrimitive) {
        val content = primitive.content
        when {
            primitive.isString -> writeString(content)
            content == "true" || content == "false" -> append(content)
            else -> append(formatNumber(content))
        }
    }

    private fun StringBuilder.writeString(value: String) {
        append('"')
        var i = 0
        while (i < value.length) {
            val c = value[i]
            when {
                Character.isHighSurrogate(c) -> {
                    require(i + 1 < value.length && Character.isLowSurrogate(value[i + 1])) {
                        "Lone high surrogate at index $i"
                    }
                    append(c).append(value[i + 1])
                    i++
                }
                Character.isLowSurrogate(c) -> throw IllegalArgumentException("Lone low surrogate at index $i")
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\b' -> append("\\b")
                c == '\t' -> append("\\t")
                c == '\n' -> append("\\n")
                c == '\u000C' -> append("\\f")
                c == '\r' -> append("\\r")
                c.code < 0x20 -> append("\\u00").append(Sha256.hex(byteArrayOf(c.code.toByte())))
                else -> append(c)
            }
            i++
        }
        append('"')
    }

    private fun formatNumber(text: String): String {
        require(JSON_NUMBER.matches(text)) { "Not a JSON number: $text" }
        if (INTEGER.matches(text)) {
            require(BigInteger(text).abs() <= MAX_SAFE_INTEGER) { "Integer exceeds 2^53 and cannot round-trip: $text" }
        }
        val value = text.toDouble()
        require(value.isFinite()) { "Number is not finite: $text" }
        if (value == 0.0) return "0"
        val sign = if (value < 0) "-" else ""
        val decimal = BigDecimal(java.lang.Double.toString(Math.abs(value))).stripTrailingZeros()
        val digits = decimal.unscaledValue().toString()
        val k = digits.length
        val n = k - decimal.scale()
        val body = when {
            n in k..EXPONENT_LIMIT -> digits + "0".repeat(n - k)
            n in 1..EXPONENT_LIMIT -> digits.substring(0, n) + "." + digits.substring(n)
            n in (SMALL_LIMIT + 1)..0 -> "0." + "0".repeat(-n) + digits
            else -> {
                val exponent = n - 1
                val mantissa = if (k == 1) digits else digits.substring(0, 1) + "." + digits.substring(1)
                mantissa + "e" + (if (exponent < 0) "-" else "+") + Math.abs(exponent)
            }
        }
        return sign + body
    }
}
