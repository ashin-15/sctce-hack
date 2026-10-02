package org.sakshi.export.bundle

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

private const val MAX_JSON_DEPTH: Int = 64

/** Parses untrusted JSON text with a nesting limit. */
internal object JsonInput {
    fun parse(text: CharSequence): JsonElement {
        require(withinDepth(text)) { "JSON nesting exceeds $MAX_JSON_DEPTH levels" }
        return Json.parseToJsonElement(text.toString())
    }

    private fun withinDepth(text: CharSequence): Boolean {
        var depth = 0
        var inString = false
        var escaped = false
        for (c in text) {
            when {
                escaped -> escaped = false
                inString && c == '\\' -> escaped = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '{' || c == '[' -> if (++depth > MAX_JSON_DEPTH) return false
                c == '}' || c == ']' -> depth--
            }
        }
        return true
    }
}

internal fun JsonObject.field(key: String): JsonElement =
    this[key] ?: throw IllegalArgumentException("missing field '$key'")

private fun JsonObject.primitive(key: String): JsonPrimitive {
    val value = field(key)
    require(value is JsonPrimitive && value !is JsonNull) { "field '$key' has the wrong type" }
    return value
}

internal fun JsonObject.str(key: String): String {
    val p = primitive(key)
    require(p.isString) { "field '$key' must be a string" }
    return p.content
}

internal fun JsonObject.strOrNull(key: String): String? = if (this[key] == null || this[key] is JsonNull) null else str(key)

internal fun JsonObject.long(key: String): Long {
    val p = primitive(key)
    require(!p.isString) { "field '$key' must be a number" }
    return p.longOrNull ?: throw IllegalArgumentException("field '$key' must be an integer")
}

internal fun JsonObject.int(key: String): Int {
    val value = long(key)
    require(value in Int.MIN_VALUE..Int.MAX_VALUE) { "field '$key' is out of range" }
    return value.toInt()
}

internal fun JsonObject.bool(key: String): Boolean {
    val p = primitive(key)
    require(!p.isString && (p.content == "true" || p.content == "false")) { "field '$key' must be a boolean" }
    return p.content == "true"
}

internal fun JsonObject.optionalDouble(key: String): Double? {
    val value = this[key]
    if (value == null || value is JsonNull) return null
    val p = primitive(key)
    require(!p.isString) { "field '$key' must be a number" }
    return p.doubleOrNull ?: throw IllegalArgumentException("field '$key' must be a number")
}

internal fun JsonObject.obj(key: String): JsonObject =
    field(key) as? JsonObject ?: throw IllegalArgumentException("field '$key' must be an object")

internal fun JsonObject.array(key: String): JsonArray =
    field(key) as? JsonArray ?: throw IllegalArgumentException("field '$key' must be an array")

internal fun JsonObject.objects(key: String): List<JsonObject> =
    array(key).map { it as? JsonObject ?: throw IllegalArgumentException("field '$key' must hold objects") }

internal fun JsonObject.strings(key: String): List<String> = array(key).map {
    require(it is JsonPrimitive && it.isString) { "field '$key' must hold strings" }
    it.content
}

/** Builds an object, leaving out entries whose value is null. */
internal fun jsonObject(vararg entries: Pair<String, JsonElement?>): JsonObject =
    JsonObject(entries.mapNotNull { (key, value) -> value?.let { key to it } }.toMap())

internal fun jsonString(value: String?): JsonElement? = value?.let { JsonPrimitive(it) }

internal fun jsonStrings(values: List<String>): JsonElement = JsonArray(values.map { JsonPrimitive(it) })
