package org.sakshi.core.integrity

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class CanonicalJsonTest {
    private fun canon(text: String): String = CanonicalJson.encodeToString(Json.parseToJsonElement(text))

    private fun number(text: String): String = canon("[$text]").removePrefix("[").removeSuffix("]")

    @Test
    fun keyOrderDoesNotMatter() {
        val a = canon("""{"b":1,"a":2,"c":{"z":1,"y":2}}""")
        val b = canon("""{"c":{"y":2,"z":1},"a":2,"b":1}""")
        assertEquals("""{"a":2,"b":1,"c":{"y":2,"z":1}}""", a)
        assertContentEquals(
            CanonicalJson.encode(Json.parseToJsonElement("""{"b":1,"a":2}""")),
            CanonicalJson.encode(Json.parseToJsonElement("""{"a":2,"b":1}""")),
        )
        assertEquals(a, b)
    }

    @Test
    fun keysSortByUtf16CodeUnits() {
        assertEquals("""{"€":1,"😀":2}""".let { canon(it) }, canon("""{"😀":2,"€":1}"""))
        assertEquals("{\"€\":1,\"😀\":2}", canon("""{"😀":2,"€":1}"""))
    }

    @Test
    fun nestedStructuresAndWhitespace() {
        assertEquals(
            """{"a":[1,{"b":null,"c":[true,false]}],"d":{}}""",
            canon(""" { "d" : { } , "a" : [ 1 , { "c" : [ true , false ] , "b" : null } ] } """),
        )
    }

    @Test
    fun stringEscapes() {
        val raw = JsonPrimitive("q\" b\\ s/ \b\t\n\u000C\r \u0001\u001f")
        assertEquals("\"q\\\" b\\\\ s/ \\b\\t\\n\\f\\r \\u0001\\u001f\"", CanonicalJson.encodeToString(raw))
    }

    @Test
    fun nonAsciiIsKeptAsUtf8() {
        val text = "മലയാളം हिन्दी 😀"
        val element = JsonObject(mapOf("t" to JsonPrimitive(text)))
        val bytes = CanonicalJson.encode(element)
        assertEquals("{\"t\":\"$text\"}", bytes.toString(Charsets.UTF_8))
        assertContentEquals("{\"t\":\"$text\"}".toByteArray(Charsets.UTF_8), bytes)
    }

    @Test
    fun numberFormatting() {
        val cases = mapOf(
            "1e21" to "1e+21",
            "1e-7" to "1e-7",
            "0.000001" to "0.000001",
            "100" to "100",
            "100.0" to "100",
            "1E2" to "100",
            "0.5" to "0.5",
            "-0" to "0",
            "0" to "0",
            "333333333.33333329" to "333333333.3333333",
            "4.50" to "4.5",
            "2e-3" to "0.002",
            "-1.5" to "-1.5",
            "123456789012345680000.0" to "123456789012345680000",
            "1.5e300" to "1.5e+300",
            "9007199254740992" to "9007199254740992",
            "-9007199254740992" to "-9007199254740992",
        )
        for ((input, expected) in cases) {
            assertEquals(expected, number(input), "input $input")
        }
    }

    @Test
    fun unsafeIntegerRejected() {
        assertFailsWith<IllegalArgumentException> { number("9007199254740993") }
        assertFailsWith<IllegalArgumentException> { number("-9007199254740993") }
    }

    @Test
    fun nonFiniteAndMalformedNumbersRejected() {
        assertFailsWith<IllegalArgumentException> { CanonicalJson.encode(JsonPrimitive(Double.NaN)) }
        assertFailsWith<IllegalArgumentException> { CanonicalJson.encode(JsonPrimitive(Double.POSITIVE_INFINITY)) }
        assertFailsWith<IllegalArgumentException> { number("1e999") }
    }

    @Test
    fun loneSurrogatesRejected() {
        assertFailsWith<IllegalArgumentException> { CanonicalJson.encode(JsonPrimitive("a\uD800b")) }
        assertFailsWith<IllegalArgumentException> { CanonicalJson.encode(JsonPrimitive("a\uDC00")) }
        assertFailsWith<IllegalArgumentException> { CanonicalJson.encode(JsonObject(mapOf("\uD800" to JsonNull))) }
    }

    @Test
    fun literalsAndStringsStaySeparate() {
        assertEquals("""["true","1",true,null]""", canon("""["true","1",true,null]"""))
    }

    @Test
    fun repeatedEncodingIsStable() {
        val element: JsonElement = JsonObject(
            mapOf("k" to JsonArray(listOf(JsonPrimitive(1.25), JsonPrimitive("v"), JsonNull))),
        )
        val first = CanonicalJson.encode(element)
        assertContentEquals(first, CanonicalJson.encode(element))
        val reparsed = Json.parseToJsonElement(first.toString(Charsets.UTF_8))
        assertContentEquals(first, CanonicalJson.encode(reparsed))
    }
}
