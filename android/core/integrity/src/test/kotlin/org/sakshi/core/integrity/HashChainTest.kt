package org.sakshi.core.integrity

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class HashChainTest {
    private fun head(entries: List<ByteArray>): String = Sha256.hex(HashChain.head(entries))

    @Test
    fun matchesPythonReferenceVectors() {
        val vectors = Vectors.load()
        assertEquals(12, vectors.size)
        for (vector in vectors) {
            assertEquals(vector.chainHex, head(vector.entries), "chain of ${vector.entries.size} entries")
        }
    }

    @Test
    fun genesisIsFreshCopy() {
        val first = HashChain.genesis()
        assertContentEquals(ByteArray(32), first)
        first[0] = 1
        assertContentEquals(ByteArray(32), HashChain.genesis())
    }

    @Test
    fun nextRequiresThirtyTwoBytes() {
        assertFailsWith<IllegalArgumentException> { HashChain.next(ByteArray(31), ByteArray(0)) }
    }

    @Test
    fun flippedByteChangesHead() {
        val entries = Vectors.sample(5)
        val original = head(entries)
        val tampered = entries.map { it.copyOf() }
        tampered[2][0] = (tampered[2][0].toInt() xor 1).toByte()
        assertFalse(original == head(tampered))
    }

    @Test
    fun swapChangesHead() {
        val entries = Vectors.sample(5)
        val swapped = entries.toMutableList().also { it[1] = entries[3]; it[3] = entries[1] }
        assertFalse(head(entries) == head(swapped))
    }

    @Test
    fun truncationChangesHead() {
        val entries = Vectors.sample(5)
        assertFalse(head(entries) == head(entries.dropLast(1)))
    }

    @Test
    fun entryBoundariesAreBound() {
        val a = listOf("ab".toByteArray(), "c".toByteArray())
        val b = listOf("a".toByteArray(), "bc".toByteArray())
        assertFalse(head(a) == head(b))
    }
}
