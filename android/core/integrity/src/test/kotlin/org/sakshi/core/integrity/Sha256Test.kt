package org.sakshi.core.integrity

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Sha256Test {
    @Test
    fun emptyInput() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.hex(Sha256.digest(ByteArray(0))),
        )
    }

    @Test
    fun abc() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.hex(Sha256.digest("abc".toByteArray(Charsets.UTF_8))),
        )
    }

    @Test
    fun hexRoundTrip() {
        val bytes = ByteArray(256) { it.toByte() }
        val hex = Sha256.hex(bytes)
        assertEquals(512, hex.length)
        assertEquals(hex.lowercase(), hex)
        assertContentEquals(bytes, Sha256.fromHex(hex))
        assertContentEquals(bytes, Sha256.fromHex(hex.uppercase()))
    }

    @Test
    fun badHexRejected() {
        assertFailsWith<IllegalArgumentException> { Sha256.fromHex("abc") }
        assertFailsWith<IllegalArgumentException> { Sha256.fromHex("zz") }
        assertFailsWith<IllegalArgumentException> { Sha256.fromHex("0g") }
        assertFailsWith<IllegalArgumentException> { Sha256.fromHex("+1") }
    }
}
