package org.sakshi.core.integrity

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class Vector(val entries: List<ByteArray>, val chainHex: String, val merkleHex: String)

internal object Vectors {
    fun load(): List<Vector> {
        val dir = requireNotNull(System.getProperty("sakshi.fixtures")) { "sakshi.fixtures is not set" }
        val root = Json.parseToJsonElement(File(dir, "integrity-vectors.json").readText()).jsonObject
        val generated = root.getValue("generated").jsonArray.map { it.jsonObject }.map { item ->
            val count = item.getValue("count").jsonPrimitive.content.toInt()
            val entries = List(count) { "sakshi synthetic entry $it".toByteArray(Charsets.UTF_8) }
            vector(entries, item)
        }
        val explicit = root.getValue("explicit").jsonArray.map { it.jsonObject }.map { item ->
            val entries = item.getValue("entries_hex").jsonArray.map { Sha256.fromHex(it.jsonPrimitive.content) }
            vector(entries, item)
        }
        return generated + explicit
    }

    private fun vector(entries: List<ByteArray>, item: JsonObject): Vector = Vector(
        entries,
        item.getValue("chain_hex").jsonPrimitive.content,
        item.getValue("merkle_hex").jsonPrimitive.content,
    )

    fun sample(count: Int): List<ByteArray> = List(count) { "entry $it".toByteArray(Charsets.UTF_8) }
}

