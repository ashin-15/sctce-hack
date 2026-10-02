package org.sakshi.core.integrity

import java.nio.ByteBuffer

public object MerkleV2 {
    private const val LEAF_PREFIX: Byte = 0x00
    private const val NODE_PREFIX: Byte = 0x01
    private const val ROOT_PREFIX: Byte = 0x02

    public fun root(entries: List<ByteArray>): ByteArray {
        var nodes: List<ByteArray> = if (entries.isEmpty()) {
            listOf(Sha256.digest(byteArrayOf(LEAF_PREFIX)))
        } else {
            entries.map { Sha256.digest(byteArrayOf(LEAF_PREFIX) + it) }
        }
        while (nodes.size > 1) {
            val padded = if (nodes.size % 2 == 1) nodes + nodes.last() else nodes
            nodes = padded.chunked(2) { (left, right) ->
                Sha256.digest(byteArrayOf(NODE_PREFIX) + left + right)
            }
        }
        val buffer = ByteBuffer.allocate(1 + Long.SIZE_BYTES + nodes[0].size)
        buffer.put(ROOT_PREFIX).putLong(entries.size.toLong()).put(nodes[0])
        return Sha256.digest(buffer.array())
    }
}
