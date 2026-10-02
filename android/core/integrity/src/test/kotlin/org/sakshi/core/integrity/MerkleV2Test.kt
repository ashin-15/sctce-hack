package org.sakshi.core.integrity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MerkleV2Test {
    private fun root(entries: List<ByteArray>): String = Sha256.hex(MerkleV2.root(entries))

    @Test
    fun matchesPythonReferenceVectors() {
        val vectors = Vectors.load()
        assertEquals(12, vectors.size)
        for (vector in vectors) {
            assertEquals(vector.merkleHex, root(vector.entries), "merkle of ${vector.entries.size} entries")
        }
    }

    @Test
    fun flippedByteChangesRoot() {
        val entries = Vectors.sample(5)
        val tampered = entries.map { it.copyOf() }
        tampered[4][0] = (tampered[4][0].toInt() xor 1).toByte()
        assertFalse(root(entries) == root(tampered))
    }

    @Test
    fun swapChangesRoot() {
        val entries = Vectors.sample(4)
        val swapped = entries.toMutableList().also { it[0] = entries[1]; it[1] = entries[0] }
        assertFalse(root(entries) == root(swapped))
    }

    @Test
    fun truncationChangesRoot() {
        val entries = Vectors.sample(6)
        assertFalse(root(entries) == root(entries.dropLast(1)))
    }

    @Test
    fun duplicatedLastEntryOfOddListChangesRoot() {
        val entries = Vectors.sample(3)
        assertFalse(root(entries) == root(entries + entries.last()))
    }

    @Test
    fun emptyListDiffersFromSingleEmptyEntry() {
        assertFalse(root(emptyList()) == root(listOf(ByteArray(0))))
    }
}
